"""RSS 수집기(키 불필요). 피드 하나가 실패해도 나머지 피드는 계속."""

from __future__ import annotations

import asyncio
import calendar
import logging
from datetime import UTC, datetime

import feedparser
import httpx

from studio_core.config import KST, FeedSpec, now_kst
from studio_core.http import request
from studio_core.text import extract_keywords, normalize_title, strip_html
from topicradar.collectors.base import Collector
from topicradar.models import Signal

log = logging.getLogger("topicradar.rss")


def _entry_time(entry: feedparser.FeedParserDict) -> datetime | None:
    for key in ("published_parsed", "updated_parsed"):
        st = entry.get(key)
        if st:
            return datetime.fromtimestamp(calendar.timegm(st), tz=UTC).astimezone(KST)
    return None


def parse_feed(content: bytes, feed: FeedSpec, fetched_at: datetime) -> list[Signal]:
    """RSS/Atom 바이트 → Signal 목록."""
    parsed = feedparser.parse(content)
    out: list[Signal] = []
    for e in parsed.entries:
        published = _entry_time(e)
        link = e.get("link") or ""
        title = normalize_title(e.get("title") or "")
        if not title or not link or published is None:
            continue
        summary = strip_html(e.get("summary") or e.get("description") or "")[:600]
        tags = [t.get("term") for t in e.get("tags", []) if t.get("term")]
        out.append(
            Signal(
                source=f"rss:{feed.name}",
                kind=feed.kind,
                fetched_at=fetched_at,
                published_at=published,
                title=title,
                url=link,
                summary=summary,
                keywords=(tags or extract_keywords(f"{title} {summary}", k=8))[:10],
                metric={"feed": feed.name, **({"category_hint": feed.category} if feed.category else {})},
            )
        )
    return out


class RssCollector(Collector):
    """여러 RSS 피드를 동시에 읽는다."""

    name = "rss"

    def __init__(self, feeds: list[FeedSpec], client: httpx.AsyncClient) -> None:
        self.feeds = feeds
        self.client = client
        self.feed_errors: dict[str, str] = {}

    async def _fetch_feed(self, feed: FeedSpec, since: datetime) -> list[Signal]:
        try:
            resp = await request(self.client, "GET", feed.url)
        except Exception as e:  # 피드 단위 실패 격리
            self.feed_errors[feed.name] = str(e)
            log.warning("RSS 실패 %s: %s", feed.name, e)
            return []
        now = now_kst()
        return [s for s in parse_feed(resp.content, feed, now) if s.published_at >= since]

    async def fetch(self, since: datetime) -> list[Signal]:
        """since 이후 항목."""
        results = await asyncio.gather(*(self._fetch_feed(f, since) for f in self.feeds))
        return [s for chunk in results for s in chunk]
