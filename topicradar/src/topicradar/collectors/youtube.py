"""YouTube Data API v3 수집기 (YOUTUBE_API_KEY).

경쟁 채널의 최근 24시간 업로드·조회수를 읽어 공백도 계산에 쓰고,
내 채널 영상 통계는 history에서 쓴다.

쿼터: search.list(100 units) 대신 업로드 재생목록(playlistItems.list, 1 unit)
+ videos.list(1 unit)를 쓴다. 경쟁 채널 10개 × 하루 3회 수집 ≈ 60 units.
"""

from __future__ import annotations

import asyncio
import logging
import os
from datetime import datetime

import httpx

from studio_core.config import KST, now_kst
from studio_core.http import get_json
from studio_core.text import extract_keywords
from topicradar.collectors.base import Collector, CollectorDisabled
from topicradar.models import Signal

log = logging.getLogger("topicradar.youtube")

API = "https://www.googleapis.com/youtube/v3"


def api_key() -> str:
    """키가 없으면 CollectorDisabled."""
    key = os.environ.get("YOUTUBE_API_KEY", "")
    if not key:
        raise CollectorDisabled("YOUTUBE_API_KEY 미설정")
    return key


def uploads_playlist(channel_id: str) -> str:
    """UC... 채널 ID → UU... 업로드 재생목록 ID."""
    return "UU" + channel_id[2:] if channel_id.startswith("UC") else channel_id


def _parse_time(value: str) -> datetime:
    return datetime.fromisoformat(value.replace("Z", "+00:00")).astimezone(KST)


async def video_stats(client: httpx.AsyncClient, ids: list[str]) -> dict[str, dict]:
    """videos.list(snippet,statistics) → {video_id: {...}} (50개씩)."""
    key = api_key()
    out: dict[str, dict] = {}
    for i in range(0, len(ids), 50):
        chunk = ids[i : i + 50]
        payload = await get_json(
            client, f"{API}/videos", params={"part": "snippet,statistics", "id": ",".join(chunk), "key": key}
        )
        for item in payload.get("items", []):
            st = item.get("statistics", {})
            sn = item.get("snippet", {})
            out[item["id"]] = {
                "title": sn.get("title", ""),
                "description": sn.get("description", ""),
                "channel_id": sn.get("channelId", ""),
                "channel_title": sn.get("channelTitle", ""),
                "published_at": sn.get("publishedAt"),
                "views": int(st.get("viewCount", 0)),
                "likes": int(st.get("likeCount", 0)) if "likeCount" in st else None,
                "comments": int(st.get("commentCount", 0)) if "commentCount" in st else None,
            }
    return out


class YouTubeCollector(Collector):
    """경쟁 채널 최근 업로드 수집."""

    name = "youtube"

    def __init__(self, competitors: list[str], client: httpx.AsyncClient) -> None:
        self.competitors = competitors
        self.client = client

    async def _recent_ids(self, channel_id: str, key: str, since: datetime) -> list[str]:
        try:
            payload = await get_json(
                self.client,
                f"{API}/playlistItems",
                params={
                    "part": "contentDetails",
                    "playlistId": uploads_playlist(channel_id),
                    "maxResults": 25,
                    "key": key,
                },
            )
        except Exception as e:  # 채널 단위 실패 격리
            log.warning("유튜브 채널 실패 %s: %s", channel_id, e)
            return []
        ids = []
        for item in payload.get("items", []):
            cd = item.get("contentDetails", {})
            published = cd.get("videoPublishedAt")
            if published and _parse_time(published) >= since:
                ids.append(cd["videoId"])
        return ids

    async def fetch(self, since: datetime) -> list[Signal]:
        """since 이후 업로드(공백도의 '최근 24시간' 판정은 score.py가 슬롯 시각 기준으로 한다)."""
        if not self.competitors:
            raise CollectorDisabled("channel.yaml competitors 비어 있음")
        key = api_key()
        id_lists = await asyncio.gather(*(self._recent_ids(c, key, since) for c in self.competitors))
        ids = [i for chunk in id_lists for i in chunk]
        if not ids:
            return []
        stats = await video_stats(self.client, ids)
        now = now_kst()
        out = []
        for vid, st in stats.items():
            out.append(
                Signal(
                    source=f"youtube:{st['channel_title']}",
                    kind="competitor",
                    fetched_at=now,
                    published_at=_parse_time(st["published_at"]) if st.get("published_at") else now,
                    title=st["title"],
                    url=f"https://www.youtube.com/watch?v={vid}",
                    summary=st["description"][:300],
                    keywords=extract_keywords(f"{st['title']} {st['description'][:300]}", k=8),
                    metric={k: st[k] for k in ("views", "likes", "comments", "channel_id")}
                    | {"video_id": vid},
                )
            )
        return out
