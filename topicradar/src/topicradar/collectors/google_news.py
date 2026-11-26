"""구글 뉴스 검색 RSS 수집기(키 불필요) — 네이버 검색 키가 없을 때 같은 검색어로 대신 받는다.

- https://news.google.com/rss/search?q=<검색어> when:<N>d&hl=ko&gl=KR&ceid=KR:ko — 검색어당 최신 기사 최대 100건.
- 항목 링크는 news.google.com/rss/articles/<ID> 중계 주소라 기사 본문을 바로 읽을 수 없다. 본문을 받을 기사만
  decode_google_link로 원문 주소를 푼다(기사 페이지의 서명값 → batchexecute 한 번, 기사당 요청 2번).
- 제목 끝의 " - 매체"는 떼고 매체 이름은 metric.press에 둔다.
"""

from __future__ import annotations

import asyncio
import json
import logging
import re
from datetime import datetime
from urllib.parse import quote, urlparse

import feedparser
import httpx

from studio_core.config import FeedSpec, now_kst
from studio_core.http import request
from studio_core.text import extract_keywords
from topicradar.collectors.base import Collector
from topicradar.collectors.rss import parse_feed
from topicradar.models import Signal

log = logging.getLogger("topicradar.google_news")

SEARCH_URL = "https://news.google.com/rss/search?q={q}&hl=ko&gl=KR&ceid=KR:ko"
ARTICLE_URL = "https://news.google.com/rss/articles/{id}?hl=ko&gl=KR&ceid=KR:ko"
BATCH_URL = "https://news.google.com/_/DotsSplashUi/data/batchexecute"
_SG = re.compile(r'data-n-a-sg="([^"]+)"')
_TS = re.compile(r'data-n-a-ts="([^"]+)"')
_CTX = [["X", "X", ["X", "X"], None, None, 1, 1, "US:en", None, 1, None, None, None, None, None, 0, 1],
        "X", "X", 1, [1, 1, 1], 1, 1, None, 0, 0, None, 0]


def is_google_link(url: str) -> bool:
    """구글 뉴스 중계 주소인가."""
    return urlparse(url).hostname == "news.google.com"


def split_press(title: str) -> tuple[str, str]:
    """'제목 - 매체' → (제목, 매체). 매체가 없으면 (제목, '')."""
    head, sep, tail = title.rpartition(" - ")
    if sep and head and 0 < len(tail) <= 30:
        return head.strip(), tail.strip()
    return title, ""


async def decode_google_link(client: httpx.AsyncClient, url: str) -> str:
    """news.google.com 중계 주소 → 기사 원문 주소. 못 풀면 빈 문자열."""
    path = urlparse(url).path.rstrip("/").split("/")
    if len(path) < 2 or path[-2] not in ("articles", "read"):
        return ""
    art = path[-1]
    try:
        page = (await request(client, "GET", ARTICLE_URL.format(id=art), retries=2)).text
        sg, ts = _SG.search(page), _TS.search(page)
        if not sg or not ts:
            return ""
        inner = ["garturlreq", _CTX, art, int(ts.group(1)) if ts.group(1).isdigit() else ts.group(1), sg.group(1)]
        body = "f.req=" + quote(json.dumps([[["Fbv4je", json.dumps(inner, separators=(",", ":")), None, "1"]]],
                                           separators=(",", ":")))
        resp = await request(client, "POST", BATCH_URL, retries=2, content=body,
                             headers={"Content-Type": "application/x-www-form-urlencoded;charset=UTF-8"})
        text = resp.text
        text = text.split("\n\n", 1)[1] if "\n\n" in text else text
        text = text.lstrip()
        if text.startswith(")]}'"):
            text = text.split("\n", 1)[1]
        for row in json.loads(text):
            if isinstance(row, list) and len(row) >= 3 and row[0] == "wrb.fr" and isinstance(row[2], str):
                payload = json.loads(row[2])
                if isinstance(payload, list) and len(payload) > 1 and payload[0] == "garturlres":
                    return str(payload[1])
    except Exception as e:  # 하나 실패는 중계 주소 그대로
        log.info("구글 뉴스 주소 풀기 실패 %s: %s", url, e)
    return ""


class GoogleNewsCollector(Collector):
    """설정된 검색어마다 구글 뉴스 검색 RSS를 읽는다(네이버 키 없을 때의 대안)."""

    name = "google_news"

    def __init__(self, queries: list[str], client: httpx.AsyncClient, days: int = 2) -> None:
        self.queries = queries
        self.client = client
        self.days = days

    async def _one(self, query: str, since: datetime, sem: asyncio.Semaphore) -> list[Signal]:
        days = max(1, min(7, (now_kst() - since).days + 1, self.days if self.days else 7))
        url = SEARCH_URL.format(q=quote(f"{query} when:{days}d"))
        async with sem:
            try:
                resp = await request(self.client, "GET", url, retries=2)
            except Exception as e:  # 검색어 단위 실패 격리
                log.warning("구글 뉴스 실패 %s: %s", query, e)
                return []
        feed = FeedSpec(name=f"google:{query}", url=url, kind="domestic")
        press = {e.get("link", ""): (e.get("source") or {}).get("title", "") for e in feedparser.parse(resp.content).entries}
        out: list[Signal] = []
        for s in parse_feed(resp.content, feed, now_kst()):
            if s.published_at < since:
                continue
            title, tail = split_press(s.title)  # 보통 normalize_title이 이미 뗐다
            s.title = title
            s.source = f"google:{query}"
            s.summary = ""  # 구글 RSS 설명은 제목 링크뿐
            s.keywords = extract_keywords(title, k=8)
            s.metric = {"query": query, "press": press.get(s.url) or tail, "google_link": s.url}
            out.append(s)
        return out

    async def fetch(self, since: datetime) -> list[Signal]:
        """검색어별 결과 합치기(같은 기사는 하나로)."""
        sem = asyncio.Semaphore(4)
        chunks = await asyncio.gather(*(self._one(q, since, sem) for q in self.queries))
        seen: set[str] = set()
        out: list[Signal] = []
        for s in (s for c in chunks for s in c):
            key = s.title.replace(" ", "")
            if key in seen:
                continue
            seen.add(key)
            out.append(s)
        return out
