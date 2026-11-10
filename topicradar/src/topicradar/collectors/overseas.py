"""간밤 해외 주요 뉴스(RSS) 수집기.

1차 범위는 RSS만이다. 미국 증시·환율 수치는 별도 피드를 붙이지 않고,
해외·경제 RSS 기사 본문에 나온 숫자만 대본에서 인용한다(절대 규칙 2:
수집된 기사에 있는 수치만 쓴다). 수치 피드가 필요해지면 `MarketProvider`를
구현해 붙인다 — 후보와 제약은 docs/sources.md "증시·환율" 절 참고.
"""

from __future__ import annotations

from datetime import datetime
from typing import Protocol

import httpx

from studio_core.config import FeedSpec
from topicradar.collectors.rss import RssCollector
from topicradar.models import Signal


class MarketProvider(Protocol):
    """증시·환율 요약 제공자(2차). 반환 신호는 kind='overseas'."""

    async def summary(self, since: datetime) -> list[Signal]:
        """지수·환율 요약 신호."""
        ...


class OverseasCollector(RssCollector):
    """해외 RSS를 kind='overseas'로 수집한다."""

    name = "overseas"

    def __init__(
        self, feeds: list[FeedSpec], client: httpx.AsyncClient, market: MarketProvider | None = None
    ) -> None:
        super().__init__([f.model_copy(update={"kind": "overseas"}) for f in feeds], client)
        self.market = market

    async def fetch(self, since: datetime) -> list[Signal]:
        """RSS + (있으면) 시장 요약."""
        sigs = await super().fetch(since)
        if self.market is not None:
            sigs.extend(await self.market.summary(since))
        return sigs
