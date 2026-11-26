"""Pixabay(무료 사진·영상, 키: 환경 변수 PIXABAY_API_KEY) — 건강 채널 화면 소재(Pexels 다음).

Pixabay는 키를 주소(key=…)에 넣어야 해서, 실패 메시지·로그에 키가 남지 않게 메시지에서 키를 지운다.
영상 API에는 방향 거르기가 없다 — 가로 영상은 받을 때 세로 화면 칸에 맞춰 가운데를 잘라 쓴다(newsclip.download).
Pixabay Content License: 출처 표기 없이 무료로 쓸 수 있다(그래도 assets.json·credits.txt에 작가·주소를 남긴다).
"""

from __future__ import annotations

import os

from newsclip.models import Asset, SceneKind, asset_id
from newsclip.sources.base import Source, SourceUnavailable
from studio_core.http import get_json

PHOTO_API = "https://pixabay.com/api/"
VIDEO_API = "https://pixabay.com/api/videos/"
LICENSE = "Pixabay Content License"
PER_PAGE = 8
MIN_VIDEO_SEC = 3.0
SIZES = ("large", "medium", "small", "tiny")  # 영상 파일 크기(큰 것부터) — 짧은 쪽 1080 이하인 가장 큰 것


class PixabayError(RuntimeError):
    """Pixabay 호출 실패(메시지에 키 없음)."""


def _key() -> str:
    key = os.environ.get("PIXABAY_API_KEY", "").strip()
    if not key:
        raise SourceUnavailable("PIXABAY_API_KEY 미설정")
    return key


def scrub(text: str, key: str) -> str:
    """메시지에서 키를 지운다."""
    return text.replace(key, "***") if key else text


def _tags_title(tags: str) -> str:
    return " ".join(t.strip() for t in str(tags).split(",") if t.strip())[:200]


def parse_photos(payload: dict) -> list[Asset]:
    out = []
    for h in payload.get("hits") or []:
        url = h.get("largeImageURL") or h.get("webformatURL") or ""
        if not url:
            continue
        out.append(
            Asset(
                source="pixabay_image",
                id=asset_id("pixabay_image", str(h.get("id"))),
                provider_id=str(h.get("id", "")),
                title=_tags_title(h.get("tags", "")),
                url=url,
                source_page_url=h.get("pageURL", ""),
                source_domain="pixabay.com",
                download_url=url,
                width=h.get("imageWidth"),
                height=h.get("imageHeight"),
                author_or_channel=h.get("user", ""),
                license=LICENSE,
                media="image",
            )
        )
    return out


def pick_file(videos: dict, max_side: int = 1080) -> dict | None:
    files = [videos.get(k) or {} for k in SIZES]
    files = [f for f in files if f.get("url") and f.get("width") and f.get("height")]
    fit = [f for f in files if min(f["width"], f["height"]) <= max_side]
    return (fit or files[-1:] or [None])[0]


def parse_videos(payload: dict) -> list[Asset]:
    out = []
    for h in payload.get("hits") or []:
        if float(h.get("duration") or 0) < MIN_VIDEO_SEC:
            continue
        f = pick_file(h.get("videos") or {})
        if f is None:
            continue
        out.append(
            Asset(
                source="pixabay_video",
                id=asset_id("pixabay_video", str(h.get("id"))),
                provider_id=str(h.get("id", "")),
                title=_tags_title(h.get("tags", "")),
                url=f["url"],
                source_page_url=h.get("pageURL", ""),
                source_domain="pixabay.com",
                download_url=f["url"],
                width=f.get("width"),
                height=f.get("height"),
                source_duration_sec=float(h.get("duration") or 0) or None,
                author_or_channel=h.get("user", ""),
                license=LICENSE,
                media="video",
            )
        )
    return out


class PixabaySource(Source):
    """Pixabay 사진(media=image) 또는 영상(media=video) 검색."""

    cache_tag = "v1"

    def __init__(self, *args, media: str = "image", orientation: str = "", **kwargs) -> None:  # noqa: ANN002, ANN003
        super().__init__(*args, **kwargs)
        self.media = media
        self.name = f"pixabay_{media}"
        self.orientation = orientation
        if orientation and media == "image":
            self.cache_tag = f"v1-{orientation}"

    def check_ready(self) -> None:
        _key()

    async def _search(self, query: str, kind: SceneKind) -> list[Asset]:
        key = _key()
        params: dict[str, str | int] = {
            "key": key,
            "q": query[:100],
            "per_page": PER_PAGE,
            "safesearch": "true",
        }
        if self.media == "image":
            params["image_type"] = "photo"
            if self.orientation:
                params["orientation"] = "vertical" if self.orientation == "portrait" else "horizontal"
        try:
            payload = await get_json(
                self.client, VIDEO_API if self.media == "video" else PHOTO_API, params=params
            )
        except Exception as e:  # 키가 든 주소가 메시지에 섞여도 지운다
            raise PixabayError(scrub(f"{type(e).__name__}: {e}", key)) from None
        return parse_videos(payload) if self.media == "video" else parse_photos(payload)
