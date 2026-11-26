"""Pexels(무료 사진·영상, 키: 환경 변수 PEXELS_API_KEY) — 건강 채널 화면 소재.

사진 /v1/search, 영상 /videos/search. 키는 Authorization 헤더로만 보낸다(주소에 넣지 않는다).
영상은 받을 파일을 하나 골라(세로 화면이면 1080x1920에 가까운 것, 4K는 피함) download_url에 둔다.
Pexels 라이선스: 출처 표기 없이 무료로 쓸 수 있다(그래도 assets.json·credits.txt에 작가·주소를 남긴다).
"""

from __future__ import annotations

import os
import re

from newsclip.models import Asset, SceneKind, asset_id
from newsclip.sources.base import Source, SourceUnavailable
from studio_core.http import get_json

PHOTO_API = "https://api.pexels.com/v1/search"
VIDEO_API = "https://api.pexels.com/videos/search"
LICENSE = "Pexels License"
PER_PAGE = 8
MAX_SIDE = 1080  # 받을 영상 파일의 짧은 쪽 최대(4K 원본은 무겁기만 하다)
MIN_VIDEO_SEC = 3.0


def _key() -> str:
    key = os.environ.get("PEXELS_API_KEY", "").strip()
    if not key:
        raise SourceUnavailable("PEXELS_API_KEY 미설정")
    return key


def slug_title(page_url: str) -> str:
    """'https://www.pexels.com/video/woman-walking-in-park-123/' → 'woman walking in park'(영상은 설명 칸이 없다)."""
    m = re.search(r"/(?:video|photo)/([a-z0-9-]+?)-?\d*/?$", page_url)
    return m.group(1).replace("-", " ").strip() if m else ""


def pick_file(files: list[dict], max_side: int = MAX_SIDE) -> dict | None:
    """영상 파일 중 받을 것: mp4이고 짧은 쪽이 max_side 이하인 것 중 가장 큰 것(없으면 가장 작은 것)."""
    mp4 = [
        f
        for f in files
        if f.get("link") and f.get("width") and f.get("height") and "mp4" in str(f.get("file_type", "mp4"))
    ]
    if not mp4:
        return None
    fit = [f for f in mp4 if min(f["width"], f["height"]) <= max_side]
    if fit:
        return max(fit, key=lambda f: f["width"] * f["height"])
    return min(mp4, key=lambda f: f["width"] * f["height"])


def parse_photos(payload: dict) -> list[Asset]:
    out = []
    for p in payload.get("photos") or []:
        src = p.get("src") or {}
        url = src.get("large2x") or src.get("original") or src.get("large") or ""
        if not url:
            continue
        out.append(
            Asset(
                source="pexels_image",
                id=asset_id("pexels_image", str(p.get("id"))),
                provider_id=str(p.get("id", "")),
                title=(p.get("alt") or slug_title(p.get("url", "")))[:200],
                url=url,
                source_page_url=p.get("url", ""),
                source_domain="pexels.com",
                download_url=url,
                width=p.get("width"),
                height=p.get("height"),
                author_or_channel=p.get("photographer", ""),
                license=LICENSE,
                media="image",
            )
        )
    return out


def parse_videos(payload: dict) -> list[Asset]:
    out = []
    for v in payload.get("videos") or []:
        if float(v.get("duration") or 0) < MIN_VIDEO_SEC:
            continue
        f = pick_file(v.get("video_files") or [])
        if f is None:
            continue
        out.append(
            Asset(
                source="pexels_video",
                id=asset_id("pexels_video", str(v.get("id"))),
                provider_id=str(v.get("id", "")),
                title=slug_title(v.get("url", "")),
                url=f["link"],
                source_page_url=v.get("url", ""),
                source_domain="pexels.com",
                download_url=f["link"],
                width=f.get("width"),
                height=f.get("height"),
                source_duration_sec=float(v.get("duration") or 0) or None,
                author_or_channel=(v.get("user") or {}).get("name", ""),
                license=LICENSE,
                media="video",
            )
        )
    return out


class PexelsSource(Source):
    """Pexels 사진(media=image) 또는 영상(media=video) 검색."""

    cache_tag = "v1"

    def __init__(self, *args, media: str = "image", orientation: str = "", **kwargs) -> None:  # noqa: ANN002, ANN003
        super().__init__(*args, **kwargs)
        self.media = media
        self.name = f"pexels_{media}"
        self.orientation = orientation
        if orientation:
            self.cache_tag = f"v1-{orientation}"

    def check_ready(self) -> None:
        _key()

    async def _search(self, query: str, kind: SceneKind) -> list[Asset]:
        params: dict[str, str | int] = {"query": query, "per_page": PER_PAGE}
        if self.orientation:
            params["orientation"] = self.orientation
        headers = {"Authorization": _key()}
        if self.media == "video":
            return parse_videos(await get_json(self.client, VIDEO_API, params=params, headers=headers))
        return parse_photos(await get_json(self.client, PHOTO_API, params=params, headers=headers))
