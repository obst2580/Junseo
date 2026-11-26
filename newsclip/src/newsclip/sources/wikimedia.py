"""Wikimedia Commons (키 불필요) — 정치인·기관 공식 사진 보조.

generator=search&gsrnamespace=6(File:) + prop=imageinfo(extmetadata)로 작가·라이선스를 읽는다.
Wikimedia User-Agent 정책상 연락처가 담긴 UA가 필요하다(STUDIO_CONTACT).
"""

from __future__ import annotations

from newsclip.models import Asset, SceneKind, asset_id
from newsclip.sources.base import Source
from studio_core.http import get_json, user_agent
from studio_core.text import strip_html

API = "https://commons.wikimedia.org/w/api.php"


def parse_pages(payload: dict) -> list[Asset]:
    """query.pages → Asset(이미지 MIME만)."""
    pages = payload.get("query", {}).get("pages", {})
    out = []
    for page in sorted(pages.values(), key=lambda p: p.get("index", p.get("pageid", 0))):
        info = (page.get("imageinfo") or [{}])[0]
        if not str(info.get("mime", "")).startswith("image/"):
            continue
        meta = info.get("extmetadata", {})
        author = strip_html(meta.get("Artist", {}).get("value", "")) or "작자 미상"
        lic = strip_html(meta.get("LicenseShortName", {}).get("value", "")) or "라이선스 확인 필요"
        url = info.get("url", "")
        out.append(
            Asset(
                source="wikimedia",
                id=asset_id("wikimedia", url),
                title=page.get("title", ""),
                url=url,
                source_page_url=info.get("descriptionurl", ""),
                source_domain="commons.wikimedia.org",
                download_url=info.get("thumburl") or url,
                width=info.get("width"),
                height=info.get("height"),
                author_or_channel=author,
                license=lic,
                media="image",
            )
        )
    return out


class WikimediaSource(Source):
    """Wikimedia Commons 이미지 검색."""

    name = "wikimedia"

    async def _search(self, query: str, kind: SceneKind) -> list[Asset]:
        params = {
            "action": "query",
            "format": "json",
            "generator": "search",
            "gsrsearch": query,
            "gsrnamespace": 6,
            "gsrlimit": 5,
            "prop": "imageinfo",
            "iiprop": "url|size|mime|extmetadata",
            "iiurlwidth": 1920,
        }
        # Wikimedia UA 정책: 연락처가 담긴 User-Agent를 요청마다 명시한다.
        return parse_pages(
            await get_json(self.client, API, params=params, headers={"User-Agent": user_agent()})
        )
