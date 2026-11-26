"""Google Custom Search JSON API (searchType=image) — GOOGLE_API_KEY, GOOGLE_CSE_ID.

주의: Custom Search JSON API는 신규 고객 가입이 막혔고 2027-01-01 종료 예정이다(기존 키만 동작).
그래서 이미지 1순위는 네이버이고, 이 소스는 키가 있을 때만 앞에 끼운다. 종료일 이후에는 자동으로 건너뛴다.
무료 하루 100회, 초과 1,000회당 $5(하루 최대 10,000회).
"""

from __future__ import annotations

import os
from datetime import date

from newsclip.models import Asset, SceneKind, asset_id
from newsclip.sources.base import Source, SourceUnavailable, domain_of
from studio_core.config import now_kst
from studio_core.http import get_json

API = "https://www.googleapis.com/customsearch/v1"
SHUTDOWN = date(2027, 1, 1)


def parse_items(payload: dict) -> list[Asset]:
    """응답 items → Asset."""
    out = []
    for it in payload.get("items", []):
        img = it.get("image", {})
        link = it.get("link", "")
        if not link:
            continue
        page = img.get("contextLink", "")
        out.append(
            Asset(
                source="google_image",
                id=asset_id("google_image", link),
                title=it.get("title", ""),
                url=link,
                source_page_url=page,
                source_domain=domain_of(page) or it.get("displayLink", "") or domain_of(link),
                download_url=link,
                width=img.get("width"),
                height=img.get("height"),
                media="image",
            )
        )
    return out


class GoogleImageSource(Source):
    """Google 이미지 검색."""

    name = "google_image"

    def check_ready(self) -> None:
        """키·종료일 확인."""
        if now_kst().date() >= SHUTDOWN:
            raise SourceUnavailable("Google Custom Search JSON API 종료(2027-01-01)")
        if not os.environ.get("GOOGLE_API_KEY") or not os.environ.get("GOOGLE_CSE_ID"):
            raise SourceUnavailable("GOOGLE_API_KEY/GOOGLE_CSE_ID 미설정")

    async def _search(self, query: str, kind: SceneKind) -> list[Asset]:
        params = {
            "key": os.environ["GOOGLE_API_KEY"],
            "cx": os.environ["GOOGLE_CSE_ID"],
            "q": query,
            "searchType": "image",
            "imgSize": "xlarge",
            "imgType": "photo",
            "num": 10,
            "gl": "kr",
            "lr": "lang_ko",
            "safe": "active",
        }
        return parse_items(await get_json(self.client, API, params=params))
