"""topicradar 테스트 도우미."""

from __future__ import annotations

from datetime import datetime
from pathlib import Path

from studio_core.config import KST
from topicradar.models import Signal, Topic

ROOT = Path(__file__).resolve().parents[2]
FIX = ROOT / "fixtures" / "demo"


def kst(s: str) -> datetime:
    """'2026-10-08 10:00' → KST datetime."""
    return datetime.strptime(s, "%Y-%m-%d %H:%M").replace(tzinfo=KST)


def sig(
    title: str, at: str, summary: str = "", kind: str = "domestic", url: str | None = None, **metric
) -> Signal:
    """테스트용 신호."""
    return Signal(
        source="test",
        kind=kind,  # type: ignore[arg-type]
        fetched_at=kst(at),
        published_at=kst(at),
        title=title,
        url=url or f"https://example.com/{abs(hash((title, at)))}",
        summary=summary,
        metric=metric,
    )


def topic(tid: str, name: str, signals: list[Signal], category: str = "국회", **kw) -> Topic:
    """테스트용 주제."""
    return Topic(
        id=tid,
        name=name,
        keywords=kw.pop("keywords", []),
        category=category,
        signals=signals,
        first_seen=min(s.published_at for s in signals),
        last_seen=max(s.published_at for s in signals),
        **kw,
    )
