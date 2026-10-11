"""네이버 뉴스 검색 API 수집기 (NAVER_CLIENT_ID / NAVER_CLIENT_SECRET).

- 엔드포인트·헤더는 studio_core.keys.naver_get이 고른다: NAVER API HUB(새 키, 기본)
  naverapihub.apigw.ntruss.com/search/v1/news, 안 되면 예전 개발자센터 openapi.naver.com/v1/search/news.json.
- 인증 실패는 수집 결과 표에 '실패'로 드러나게 올리고, 그 밖의 오류는 검색어 단위로 격리한다.
"""

from __future__ import annotations

import asyncio
import logging
from datetime import datetime
from email.utils import parsedate_to_datetime

import httpx

from studio_core.config import KST, now_kst
from studio_core.http import HttpError
from studio_core.keys import naver_credentials, naver_get
from studio_core.text import extract_keywords, normalize_title, strip_html
from topicradar.collectors.base import Collector
from topicradar.models import Signal

log = logging.getLogger("topicradar.naver")


def parse_news(payload: dict, query: str, fetched_at: datetime) -> list[Signal]:
    """news.json 응답 → Signal 목록."""
    out: list[Signal] = []
    for item in payload.get("items", []):
        try:
            published = parsedate_to_datetime(item["pubDate"]).astimezone(KST)
        except (KeyError, TypeError, ValueError):
            continue
        title = normalize_title(item.get("title", ""))
        summary = strip_html(item.get("description", ""))
        url = item.get("originallink") or item.get("link") or ""
        if not title or not url:
            continue
        out.append(
            Signal(
                source=f"naver:{query}",
                kind="domestic",
                fetched_at=fetched_at,
                published_at=published,
                title=title,
                url=url,
                summary=summary,
                keywords=extract_keywords(f"{title} {summary}", k=8),
                metric={"query": query, "naver_link": item.get("link", "")},
            )
        )
    return out


class NaverNewsCollector(Collector):
    """설정된 검색어마다 최신순 100건을 읽는다."""

    name = "naver_news"

    def __init__(self, queries: list[str], client: httpx.AsyncClient, display: int = 100) -> None:
        self.queries = queries
        self.client = client
        self.display = display

    async def _one(self, query: str, since: datetime) -> list[Signal]:
        try:
            payload = await naver_get(
                self.client, "news", {"query": query, "display": self.display, "start": 1, "sort": "date"}
            )
        except HttpError as e:
            if e.status in (401, 403):  # 키 문제는 모든 검색어가 같이 실패하므로 바로 알린다
                raise
            log.warning("네이버 뉴스 실패 %s: %s", query, e)
            return []
        except Exception as e:  # 검색어 단위 실패 격리
            log.warning("네이버 뉴스 실패 %s: %s", query, e)
            return []
        return [s for s in parse_news(payload, query, now_kst()) if s.published_at >= since]

    async def fetch(self, since: datetime) -> list[Signal]:
        """검색어별 결과 합치기."""
        naver_credentials()
        chunks = await asyncio.gather(*(self._one(q, since) for q in self.queries))
        return [s for c in chunks for s in c]
