"""topicradar 도메인 모델."""

from __future__ import annotations

import datetime as dt
import hashlib
from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field, model_validator

SlotName = Literal["morning", "evening", "weekly"]
Role = Literal["헤드라인", "서브", "오늘의포인트", "포인트회수", "내일쟁점", "줄기"]
Hit = Literal["hit", "miss", "pending"]
SignalKind = Literal["domestic", "press", "overseas", "schedule", "competitor", "own", "community"]
NEWS_KINDS = ("domestic", "press", "overseas")
TopicKind = Literal["overseas", "announcement", "scheduled", "event"]
Trend = Literal["↑", "→", "↓"]

ECON_CATEGORY = "경제"
POLITICS_CORE = ("국회", "정부", "사법", "선거", "외교안보")


def signal_id(url: str, title: str = "") -> str:
    """URL(없으면 제목) 기반 안정 ID."""
    return hashlib.sha1((url or title).encode("utf-8")).hexdigest()[:16]


class Signal(BaseModel):
    """수집 단위 하나(기사·보도자료·영상·일정)."""

    id: str = ""
    source: str
    kind: SignalKind = "domestic"
    fetched_at: datetime
    published_at: datetime
    title: str
    url: str
    summary: str = ""
    keywords: list[str] = Field(default_factory=list)
    metric: dict = Field(default_factory=dict)

    @model_validator(mode="after")
    def _fill_id(self) -> Signal:
        if not self.id:
            self.id = signal_id(self.url, self.title)
        return self

    @property
    def text(self) -> str:
        """제목 + 요약."""
        return f"{self.title} {self.summary}".strip()


class ScoreDetail(BaseModel):
    """세부 점수와 근거 한 문장."""

    score: float
    reason: str


class Topic(BaseModel):
    """여러 신호를 묶은 주제."""

    id: str
    name: str
    keywords: list[str] = Field(default_factory=list)
    category: str = "정부"
    signals: list[Signal] = Field(default_factory=list)
    first_seen: datetime
    last_seen: datetime
    trend: Trend = "→"
    scores: dict[str, ScoreDetail] = Field(default_factory=dict)
    total: float = 0.0
    covered_in: list[str] = Field(default_factory=list)
    # 슬롯 선정용 보조 필드
    kind: TopicKind = "event"
    scheduled_at: datetime | None = None
    follow_up: str | None = None
    competitor_hits: int = 0

    def window_signals(self, start: datetime, end: datetime) -> list[Signal]:
        """윈도우 안의 신호(경쟁 채널 영상 제외)."""
        return [s for s in self.signals if start <= s.published_at < end and s.kind != "competitor"]

    def news_signals(self, start: datetime | None = None, end: datetime | None = None) -> list[Signal]:
        """뉴스 신호(대본 사실 근거가 되는 것)."""
        sigs = [s for s in self.signals if s.kind in NEWS_KINDS]
        if start is not None and end is not None:
            sigs = [s for s in sigs if start <= s.published_at < end]
        return sigs

    def buzz(self, start: datetime | None = None, end: datetime | None = None) -> list[Signal]:
        """커뮤니티 신호(관심 근거, 사실 근거 아님)."""
        sigs = [s for s in self.signals if s.kind == "community"]
        if start is not None and end is not None:
            sigs = [s for s in sigs if start <= s.published_at < end]
        return sigs

    def top_links(self, n: int = 2) -> list[str]:
        """근거 링크(보도자료·국내 기사 우선)."""
        prio = {"press": 0, "domestic": 1, "schedule": 2, "overseas": 3, "competitor": 9, "own": 9}
        sigs = sorted(self.signals, key=lambda s: (prio.get(s.kind, 5), -s.published_at.timestamp()))
        out: list[str] = []
        for s in sigs:
            if s.url and s.url not in out and s.kind not in ("competitor", "own", "community"):
                out.append(s.url)
            if len(out) >= n:
                break
        return out


class SlotItem(BaseModel):
    """슬롯 구성 항목."""

    topic: Topic
    order: int
    role: Role
    est_seconds: int = 60
    expected: str | None = None  # 아침 포인트의 예상 결과 한 문장
    impact: str | None = None  # "이게 어떻게 되면 뭐가 달라지는지" 한 문장
    actual: str | None = None  # 저녁 회수 시 실제 결과
    hit: Hit | None = None
    hit_reason: str | None = None
    chain: str | None = None  # 주간: 소속 인과 사슬
    note: str | None = None


class RiskFlag(BaseModel):
    """표시 전용 리스크(제외·감점 없음)."""

    kind: Literal["노란딱지", "법적", "여론조사", "구성"]
    topic_id: str | None = None
    message: str


class Chain(BaseModel):
    """주간 인과 사슬."""

    title: str
    summary: str
    topic_ids: list[str]


class SlotPlan(BaseModel):
    """슬롯(아침/저녁/주간) 편성안."""

    date: dt.date
    slot: SlotName
    items: list[SlotItem] = Field(default_factory=list)
    titles: list[str] = Field(default_factory=list)
    thumbnail_text: str = ""
    risk_flags: list[RiskFlag] = Field(default_factory=list)
    chains: list[Chain] = Field(default_factory=list)
    excluded: list[str] = Field(default_factory=list)  # 제외 사유 메모
    window_start: datetime | None = None
    window_end: datetime | None = None
    llm_mode: Literal["claude", "offline"] = "offline"

    @property
    def plan_id(self) -> str:
        """프로젝트 폴더명과 같은 ID."""
        return f"{self.date.isoformat()}_{self.slot}"

    def items_by_role(self, *roles: Role) -> list[SlotItem]:
        """역할로 거른 항목."""
        return [i for i in self.items if i.role in roles]

    def topic_ids(self) -> set[str]:
        """편성된 주제 ID."""
        return {i.topic.id for i in self.items}
