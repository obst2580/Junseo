"""topicradar 도메인 모델."""

from __future__ import annotations

import datetime as dt
import hashlib
from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field, model_validator

VideoFormat = Literal["long", "short", "shorts"]
# opinion: 언론 사설·칼럼(채널 시선의 출처 — 사실 근거가 아니라 의견. 보수 채널은 커뮤니티 글처럼 쓴다)
SignalKind = Literal["domestic", "press", "overseas", "schedule", "competitor", "own", "community", "opinion"]
NEWS_KINDS = ("domestic", "press", "overseas")
TopicKind = Literal["overseas", "announcement", "scheduled", "event"]
Trend = Literal["↑", "→", "↓"]

ECON_CATEGORY = "경제"
POLITICS_CORE = ("국회", "정부", "사법", "선거", "외교안보")


class FormatSpec(BaseModel):
    """영상 형식: 10분 묶음(연관 주제 2~3개) / 3분 단독(주제 1개) / 세로 쇼츠(주제 1개, 1분, 1080x1920)."""

    name: str
    minutes: float
    min_topics: int
    max_topics: int
    vertical: bool = False  # 세로 화면(timeline.xml 1080x1920)


FORMATS: dict[str, FormatSpec] = {
    "long": FormatSpec(name="10분 묶음", minutes=10, min_topics=2, max_topics=3),
    "short": FormatSpec(name="3분 단독", minutes=3, min_topics=1, max_topics=1),
    # 쇼츠는 1분을 넘기지 않게 0.8분 분량으로 잡는다(분당 600자 기준 430~530자). 음성은 분당 580자 안팎이라
    # 530자가 약 55초 — 장면 사이 쉼(3~5초)을 더해도 1분 안쪽이다.
    "shorts": FormatSpec(name="세로 쇼츠", minutes=0.8, min_topics=1, max_topics=1, vertical=True),
}


def target_chars(fmt: str, chars_per_minute: int) -> tuple[int, int]:
    """형식별 대본 글자 수 목표(읽는 부분, ±10%)."""
    n = FORMATS[fmt].minutes * chars_per_minute
    return int(round(n * 0.9, -1)), int(round(n * 1.1, -1))


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
    # 군집 보조 필드(기사 속 일정·다음 수순)
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
        """커뮤니티 글(반응·의견의 근거, 사실 근거 아님)."""
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


class OpinionSample(BaseModel):
    """의견 근거가 된 커뮤니티 글·댓글 하나(화면 표시·대본 참고용, 그대로 인용하지 않는다)."""

    site: str
    kind: Literal["post", "comment"]
    text: str
    votes: int = 0
    comments: int = 0
    url: str = ""


class Opinion(BaseModel):
    """주제 하나에 대한 커뮤니티 의견 — 채널이 대신 말할 주장."""

    stance: str  # 다수 의견 한 문장
    points: list[str] = Field(default_factory=list)  # 주장·근거(공감 많은 순)
    minority: str | None = None  # 소수·반대 의견
    questions: list[str] = Field(default_factory=list)  # 시청자가 궁금해하는 것
    posts: int = 0
    comments: int = 0
    votes: int = 0
    samples: list[OpinionSample] = Field(default_factory=list)
    by: Literal["claude", "heuristic"] = "heuristic"

    @property
    def heat_label(self) -> str:
        """'글 12 · 댓글 340 · 공감 1,200'."""
        parts = [f"글 {self.posts}", f"댓글 {self.comments:,}"]
        if self.votes:
            parts.append(f"공감 {self.votes:,}")
        return " · ".join(parts)


class RiskFlag(BaseModel):
    """표시 전용 리스크(제외·감점 없음)."""

    kind: Literal["노란딱지", "법적", "여론조사", "구성"]
    topic_id: str | None = None
    message: str


class BoardTopic(BaseModel):
    """주제판의 주제 하나(점수·의견은 판을 만든 시점 기준)."""

    topic_id: str
    name: str
    category: str
    score: float
    reason: str
    trend: Trend = "→"
    opinion: Opinion
    articles: int = 0  # 사실 근거가 될 기사 수
    links: list[str] = Field(default_factory=list)
    covered_in: list[str] = Field(default_factory=list)  # 이미 만든 영상


class Bundle(BaseModel):
    """10분 묶음 추천: 연관 주제 2~3개와 잇는 고리."""

    topic_ids: list[str]
    link: str


class Board(BaseModel):
    """주제판: 지금 커뮤니티에서 뜨는 주제."""

    id: str
    created_at: datetime
    window_start: datetime
    window_end: datetime
    topics: list[BoardTopic] = Field(default_factory=list)
    bundles: list[Bundle] = Field(default_factory=list)
    notes: list[str] = Field(default_factory=list)
    llm_mode: Literal["claude", "offline"] = "offline"

    def get(self, topic_id: str) -> BoardTopic | None:
        """판의 주제."""
        return next((t for t in self.topics if t.topic_id == topic_id), None)


class VideoItem(BaseModel):
    """영상에 넣은 주제 하나."""

    topic: Topic
    order: int
    opinion: Opinion | None = None
    est_seconds: int = 60
    note: str | None = None


class VideoPlan(BaseModel):
    """영상 하나(10분 묶음 / 3분 단독)의 계획. id = 프로젝트 폴더명."""

    id: str
    format: VideoFormat
    created_at: datetime
    board_id: str | None = None
    items: list[VideoItem] = Field(default_factory=list)
    link: str | None = None  # 10분 묶음: 주제들을 하나의 이야기로 잇는 고리
    target_chars: tuple[int, int] = (2700, 3300)
    titles: list[str] = Field(default_factory=list)
    thumbnail_text: str = ""
    risk_flags: list[RiskFlag] = Field(default_factory=list)
    notes: list[str] = Field(default_factory=list)
    window_start: datetime | None = None
    window_end: datetime | None = None
    llm_mode: Literal["claude", "offline"] = "offline"
    script_mode: Literal["claude", "offline"] = "offline"  # 대본을 누가 썼나
    script_note: str | None = None  # 오프라인 골격이 된 이유(키 없음·Claude 실패 사유)

    @property
    def date(self) -> dt.date:
        """만든 날(KST)."""
        return self.created_at.date()

    @property
    def spec(self) -> FormatSpec:
        """형식 정보."""
        return FORMATS[self.format]

    def topic_ids(self) -> set[str]:
        """넣은 주제 ID."""
        return {i.topic.id for i in self.items}
