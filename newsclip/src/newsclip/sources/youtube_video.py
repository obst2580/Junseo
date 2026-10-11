"""YouTube Data API v3 search.list + videos.list (YOUTUBE_API_KEY) → 영상 후보.

- search.list(type=video, regionCode=KR, relevanceLanguage=ko, order=relevance): 1회 100 units.
  하루 10,000 units 기준 약 100회 → 영상 장면은 주제 단위 검색어를 캐시로 재사용한다.
- videos.list(contentDetails): 길이(구간 다운로드 범위 계산용), 1 unit.
- channel.yaml videos.blocked_channels(방송사, Content ID 회피)는 결과에서 제외,
  preferred_channels는 rank.py가 가점.
"""

from __future__ import annotations

import os
import re

from newsclip.models import Asset, SceneKind, asset_id
from newsclip.sources.base import Source, SourceUnavailable
from studio_core.http import get_json

API = "https://www.googleapis.com/youtube/v3"
MAX_RESULTS = 25  # 검색 1회 비용(100 units)은 개수와 상관없다 — 방송사를 빼고도 후보가 넉넉하게
_DUR_RE = re.compile(r"PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?")


def parse_duration(iso: str) -> float | None:
    """ISO 8601 PT#H#M#S → 초."""
    m = _DUR_RE.fullmatch(iso or "")
    if not m:
        return None
    h, mi, s = (int(x) if x else 0 for x in m.groups())
    return float(h * 3600 + mi * 60 + s)


def normalize_channel(name: str) -> str:
    """채널명 비교용 정규화(공백·대소문자 무시)."""
    return re.sub(r"\s+", "", name).lower()


def is_blocked(channel: str, blocked: list[str]) -> bool:
    """차단 채널 여부(정규화 후 일치 또는 포함)."""
    c = normalize_channel(channel)
    return any(b and (normalize_channel(b) == c or normalize_channel(b) in c) for b in blocked)


def default_clip_start(duration: float | None) -> float:
    """기본 시작점: 길이의 10%(최대 60초) — 도입 로고·인사 건너뛰기."""
    if not duration:
        return 0.0
    return float(min(60.0, round(duration * 0.1)))


class YouTubeVideoSource(Source):
    """유튜브 영상 검색."""

    name = "youtube_video"
    media = "video"
    cache_tag = f"n{MAX_RESULTS}"  # 결과 개수를 바꾸면 예전(적게 받은) 캐시를 쓰지 않게

    def __init__(self, *args: object, blocked: list[str] | None = None, **kwargs: object) -> None:
        super().__init__(*args, **kwargs)  # type: ignore[arg-type]
        self.blocked = blocked or []

    def check_ready(self) -> None:
        """키 확인."""
        if not os.environ.get("YOUTUBE_API_KEY"):
            raise SourceUnavailable("YOUTUBE_API_KEY 미설정")

    async def _search(self, query: str, kind: SceneKind) -> list[Asset]:
        key = os.environ["YOUTUBE_API_KEY"]
        found = await get_json(
            self.client,
            f"{API}/search",
            params={
                "part": "snippet",
                "q": query,
                "type": "video",
                "regionCode": "KR",
                "relevanceLanguage": "ko",
                "order": "relevance",
                "maxResults": MAX_RESULTS,
                "videoEmbeddable": "true",
                "key": key,
            },
        )
        items = [
            it
            for it in found.get("items", [])
            if not is_blocked(it["snippet"].get("channelTitle", ""), self.blocked)
        ]
        ids = [it["id"]["videoId"] for it in items]
        details: dict[str, dict] = {}
        if ids:
            payload = await get_json(
                self.client,
                f"{API}/videos",
                params={"part": "contentDetails,snippet", "id": ",".join(ids), "key": key},
            )
            details = {v["id"]: v for v in payload.get("items", [])}
        out = []
        for it in items:
            vid = it["id"]["videoId"]
            sn = it["snippet"]
            det = details.get(vid, {})
            dur = parse_duration(det.get("contentDetails", {}).get("duration", ""))
            hd = det.get("contentDetails", {}).get("definition") == "hd"
            url = f"https://www.youtube.com/watch?v={vid}"
            out.append(
                Asset(
                    source="youtube_video",
                    id=asset_id("youtube_video", url),
                    title=sn.get("title", ""),
                    url=url,
                    source_page_url=url,
                    source_domain="youtube.com",
                    download_url=url,
                    width=1920 if hd else 640,
                    height=1080 if hd else 360,
                    duration_sec=dur,
                    author_or_channel=sn.get("channelTitle", ""),
                    clip_start_sec=default_clip_start(dur),
                    media="video",
                )
            )
        return out
