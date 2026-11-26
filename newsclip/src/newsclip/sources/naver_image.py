"""네이버 검색 API 이미지 (NAVER_CLIENT_ID/SECRET) — 국내 사건·장소·인물, 기본 1순위.

응답에는 이미지 원본 link만 있고 게시 페이지 URL은 없다 → 출처는 이미지 호스트 도메인으로 기록한다.
"""

from __future__ import annotations

from newsclip.models import Asset, SceneKind, asset_id
from newsclip.sources.base import Source, domain_of
from studio_core.keys import naver_credentials, naver_get
from studio_core.text import strip_html


def parse_items(payload: dict) -> list[Asset]:
    """items → Asset."""
    out = []
    for it in payload.get("items", []):
        link = it.get("link", "")
        if not link:
            continue
        try:
            w = int(it.get("sizewidth") or 0) or None
            h = int(it.get("sizeheight") or 0) or None
        except ValueError:
            w = h = None
        out.append(
            Asset(
                source="naver_image",
                id=asset_id("naver_image", link),
                title=strip_html(it.get("title", "")),
                url=link,
                source_page_url=link,
                source_domain=domain_of(link),
                download_url=link,
                width=w,
                height=h,
                media="image",
            )
        )
    return out


class NaverImageSource(Source):
    """네이버 이미지 검색."""

    name = "naver_image"

    def check_ready(self) -> None:
        """키 확인(없으면 Unavailable)."""
        naver_credentials()

    async def _search(self, query: str, kind: SceneKind) -> list[Asset]:
        payload = await naver_get(
            self.client, "image", {"query": query, "display": 10, "sort": "sim", "filter": "large"}
        )
        return parse_items(payload)
