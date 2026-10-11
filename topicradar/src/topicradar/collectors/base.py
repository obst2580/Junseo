"""수집기 공통 인터페이스와 실패 격리 실행기."""

from __future__ import annotations

import asyncio
import logging
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from datetime import datetime

from topicradar.models import Signal

log = logging.getLogger("topicradar.collect")


class CollectorDisabled(RuntimeError):
    """키 미설정 등으로 건너뛰는 수집기."""


class Collector(ABC):
    """모든 수집기의 기반 클래스."""

    name: str = "base"

    @abstractmethod
    async def fetch(self, since: datetime) -> list[Signal]:
        """since 이후 발행된 신호를 돌려준다."""


@dataclass
class CollectReport:
    """수집 결과 요약."""

    signals: list[Signal] = field(default_factory=list)
    counts: dict[str, int] = field(default_factory=dict)
    errors: dict[str, str] = field(default_factory=dict)
    skipped: dict[str, str] = field(default_factory=dict)


async def collect_all(collectors: list[Collector], since: datetime) -> CollectReport:
    """수집기를 동시에 돌린다. 하나가 실패해도 나머지는 계속."""
    report = CollectReport()

    async def _one(c: Collector) -> None:
        try:
            sigs = await c.fetch(since)
        except CollectorDisabled as e:
            report.skipped[c.name] = str(e)
            log.warning("[%s] 건너뜀: %s", c.name, e)
            return
        except Exception as e:  # 실패 격리: 원인만 기록하고 계속
            report.errors[c.name] = f"{type(e).__name__}: {e}"
            log.warning("[%s] 실패: %s", c.name, e)
            return
        report.counts[c.name] = len(sigs)
        report.signals.extend(sigs)

    await asyncio.gather(*(_one(c) for c in collectors))
    # URL 기준 중복 제거(먼저 들어온 것 유지)
    seen: set[str] = set()
    unique: list[Signal] = []
    for s in report.signals:
        if s.id in seen:
            continue
        seen.add(s.id)
        unique.append(s)
    report.signals = unique
    return report
