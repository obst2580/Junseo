"""공유마당(한국저작권위원회, gongu.copyright.or.kr) 사진 — 공공누리·자유이용 저작물(키 불필요).

상업적 이용·변경이 되는 이용조건만 받는다: 공공누리 제1유형(출처표시), CC BY, CC0, 기증저작물 자유이용,
보호기간 만료 저작물. 비영리(NC)·변경금지(ND)·동일조건(SA)·공공누리 2~4유형은 버린다.
공공누리 1유형·CC BY는 출처 표시가 이용 조건이라 credits.txt(영상 설명란)에 저작권자·이용조건·주소를 꼭 남긴다.

검색은 한국어로 한다(작업자가 queries.json 장면에 "ko"로 적은 검색어 — 사이트가 태그로 찾고 낱말을 모두 맞춰서,
한두 낱말이 잘 나온다; 두 낱말로 하나도 없으면 첫 낱말로 다시 찾는다). 제목이 '동해_강원 1'처럼 뭉뚱그린 사진이 많아
상세의 키워드를 제목 뒤에 붙여 순위를 매긴다. 목록의 미리보기는 작아서
(416px 안팎) 사진마다 상세 페이지에서 원본 주소(b_tbumb)를 읽는다 — 그래서 검색 한 번에 상세 몇 건을 더 연다.
2026-10-29 확인: 정책브리핑(korea.kr) 사진·영상은 글만 공공누리 4유형이라 못 쓰고, KTV 뉴스는 무단전재 금지라 못 쓴다.
"""

from __future__ import annotations

import html
import re

from newsclip.models import Asset, SceneKind, asset_id
from newsclip.sources.base import Source
from studio_core.http import request

BASE = "https://gongu.copyright.or.kr"
LIST_URL = f"{BASE}/gongu/wrt/wrtCl/listWrtImage.do"
VIEW_URL = f"{BASE}/gongu/wrt/wrt/view.do"
MENU = "200018"  # 이미지
PER_SEARCH = 6  # 검색 한 번에 상세 페이지를 여는 사진 수

# 목록·상세의 이용조건 표시(이미지 alt) → 쓸 수 있으면 짧은 이름
OK_LICENSES = (
    ("공공누리 공공저작물 자유이용허락 1유형", "공공누리 제1유형(출처표시)"),
    ("CC0", "CC0"),
    ("CC BY[", "CC BY"),
    ("기증저작물 자유이용", "기증저작물 자유이용"),
    ("만료", "보호기간 만료 저작물"),
)
NG_WORDS = ("비영리", "변경금지", "동일조건", "상업적 이용금지", "2유형", "3유형", "4유형", "NC", "ND", "SA")

_ITEM_RE = re.compile(r"<li>\s*<div class=\"bg_box\">(.*?)</li>", re.S)
_SN_RE = re.compile(r"view\.do\?wrtSn=(\d+)")
_TITLE_RE = re.compile(r'photoCon_head">\s*([^<]+?)\s*<')
_TAG_RE = re.compile(r'<p class="tag">\s*([^<]*?)\s*</p>')
_LIC_ALT_RE = re.compile(r'<img src="/static/gongu/img/common/[^"]+" alt="([^"]*)"')


def license_name(alt: str) -> str | None:
    """이용조건 표시 → 쓸 수 있는 조건의 짧은 이름(못 쓰면 None)."""
    text = alt.strip()
    if not text or any(w in text for w in NG_WORDS):
        return None
    for key, name in OK_LICENSES:
        if key in text:
            return name
    return None


def parse_list(page: str) -> list[dict]:
    """목록 → [{sn, title, author, license}] — 쓸 수 있는 이용조건만."""
    out = []
    for block in _ITEM_RE.findall(page):
        sn = _SN_RE.search(block)
        title = _TITLE_RE.search(block)
        if not sn or not title or not title.group(1).strip():
            continue
        lic = next((n for alt in _LIC_ALT_RE.findall(block) if (n := license_name(alt))), None)
        if lic is None:
            continue
        tag = _TAG_RE.search(block)
        out.append(
            {
                "sn": sn.group(1),
                "title": html.unescape(title.group(1)).strip(),
                "author": html.unescape(tag.group(1)).strip() if tag else "",
                "license": lic,
            }
        )
    return out


def parse_view(page: str, sn: str) -> tuple[str, str, str] | None:
    """상세 → (원본 이미지 주소, 저작권자, 키워드). 원본 주소가 없으면 None."""
    m = re.search(rf'(/gongu/wrt/cmmn/wrtFileImageView\.do\?wrtSn={sn}&(?:amp;)?[^"\']*b_tbumb[^"\']*)', page)
    if not m:
        return None
    url = BASE + html.unescape(m.group(1))
    text = re.sub(r"\s+", " ", re.sub(r"<[^>]+>", " ", page))
    owner = re.search(r"저작\(권\)자\s+(.+?)\s+\(저작물", text)
    # 키워드: <h3 class="section-title">키워드</h3> 아래 태그 목록(메뉴의 '키워드 저작물'과 헷갈리지 않게)
    sec = re.search(r'section-title">\s*키워드\s*</h3>(.*?)</ul>', page, re.S)
    keys = (
        " ".join(html.unescape(k).strip() for k in re.findall(r">([^<>]+)</a>", sec.group(1))) if sec else ""
    )
    return url, (owner.group(1).strip() if owner else ""), keys[:120]


class GonguSource(Source):
    """공유마당 사진(한국어 검색어)."""

    name = "gongu_image"
    media = "image"
    cache_tag = "v2"

    def __init__(self, *args, media: str = "image", orientation: str = "", **kwargs) -> None:  # noqa: ANN002, ANN003, ARG002
        super().__init__(*args, **kwargs)

    async def _list(self, query: str) -> list[dict]:
        resp = await request(
            self.client, "GET", LIST_URL, params={"menuNo": MENU, "searchWrd": query[:60], "pageUnit": 24}
        )
        return parse_list(resp.text)

    async def _search(self, query: str, kind: SceneKind) -> list[Asset]:  # noqa: ARG002
        items = await self._list(query)
        words = query.split()
        if not items and len(words) > 1:  # 태그를 모두 맞춰야 나와서 두 낱말이면 비기 쉽다
            items = await self._list(words[0])
        out: list[Asset] = []
        for item in items[:PER_SEARCH]:
            page_url = f"{VIEW_URL}?wrtSn={item['sn']}&menuNo={MENU}"
            try:
                view = await request(self.client, "GET", page_url, retries=2)
            except Exception:  # 상세 하나 실패는 건너뛴다
                continue
            got = parse_view(view.text, item["sn"])
            if got is None:
                continue
            url, owner, keys = got
            out.append(
                Asset(
                    source=self.name,
                    id=asset_id(self.name, item["sn"]),
                    provider_id=item["sn"],
                    title=f"{item['title']} {keys}".strip(),
                    url=url,
                    source_page_url=page_url,
                    source_domain="gongu.copyright.or.kr",
                    download_url=url,
                    author_or_channel=owner or item["author"],
                    license=item["license"],
                    media="image",
                )
            )
        return out
