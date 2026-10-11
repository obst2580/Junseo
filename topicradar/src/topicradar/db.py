"""SQLite 저장소(sqlmodel). 시각은 시간대 포함으로 저장하고 읽을 때 KST로 변환한다."""

from __future__ import annotations

import datetime as dt
from datetime import date, datetime
from pathlib import Path
from typing import Any

from sqlalchemy import JSON, Column
from sqlmodel import Field, Session, SQLModel, create_engine, select

from studio_core.config import KST
from topicradar.models import ScoreDetail, Signal, SlotPlan, Topic


def _naive(dt: datetime | None) -> datetime | None:
    """DB 저장용: 항상 시간대 정보가 있는 값으로 맞춘다(naive는 KST로 간주)."""
    if dt is None:
        return None
    return dt.replace(tzinfo=KST) if dt.tzinfo is None else dt


def _aware(dt: datetime | None) -> datetime | None:
    if dt is None:
        return None
    return dt.replace(tzinfo=KST) if dt.tzinfo is None else dt.astimezone(KST)


class SignalRow(SQLModel, table=True):
    """수집 신호."""

    __tablename__ = "signals"
    id: str = Field(primary_key=True)
    source: str
    kind: str = Field(index=True)
    fetched_at: datetime
    published_at: datetime = Field(index=True)
    title: str
    url: str
    summary: str = ""
    keywords: list = Field(default_factory=list, sa_column=Column(JSON))
    metric: dict = Field(default_factory=dict, sa_column=Column(JSON))
    topic_id: str | None = Field(default=None, index=True)


class TopicRow(SQLModel, table=True):
    """주제."""

    __tablename__ = "topics"
    id: str = Field(primary_key=True)
    name: str
    keywords: list = Field(default_factory=list, sa_column=Column(JSON))
    category: str
    kind: str = "event"
    scheduled_at: datetime | None = None
    follow_up: str | None = None
    first_seen: datetime
    last_seen: datetime = Field(index=True)
    trend: str = "→"
    scores: dict = Field(default_factory=dict, sa_column=Column(JSON))
    total: float = 0.0
    covered_in: list = Field(default_factory=list, sa_column=Column(JSON))


class PlanRow(SQLModel, table=True):
    """슬롯 편성안(전체를 JSON으로 보관)."""

    __tablename__ = "plans"
    id: str = Field(primary_key=True)
    day: dt.date = Field(index=True)
    slot: str
    data: dict = Field(default_factory=dict, sa_column=Column(JSON))
    project_dir: str = ""
    created_at: datetime


class UploadRow(SQLModel, table=True):
    """업로드 기록과 성과."""

    __tablename__ = "uploads"
    video_id: str = Field(primary_key=True)
    plan_id: str = Field(index=True)
    title: str = ""
    uploaded_at: datetime
    predicted_total: float = 0.0
    views: int | None = None
    likes: int | None = None
    stats_fetched_at: datetime | None = None


class MetaRow(SQLModel, table=True):
    """키-값 메타(마지막 수집 시각 등)."""

    __tablename__ = "meta"
    key: str = Field(primary_key=True)
    value: str


class Store:
    """저장소 파사드."""

    def __init__(self, db_path: Path | str) -> None:
        url = "sqlite://" if str(db_path) == ":memory:" else f"sqlite:///{db_path}"
        self.engine = create_engine(url)
        SQLModel.metadata.create_all(self.engine)

    def close(self) -> None:
        """연결 반납."""
        self.engine.dispose()

    def __enter__(self) -> Store:
        return self

    def __exit__(self, *exc: object) -> None:
        self.close()

    # ---- 신호 ------------------------------------------------------------
    def upsert_signals(self, signals: list[Signal]) -> int:
        """새 신호 수를 돌려준다(기존 ID는 갱신하지 않음, 단 metric은 병합)."""
        new = 0
        with Session(self.engine, expire_on_commit=False) as s:
            for sig in signals:
                row = s.get(SignalRow, sig.id)
                if row is None:
                    s.add(self._sig_row(sig))
                    new += 1
                elif sig.metric:
                    row.metric = {**(row.metric or {}), **sig.metric}
                    s.add(row)
            s.commit()
        return new

    @staticmethod
    def _sig_row(sig: Signal, topic_id: str | None = None) -> SignalRow:
        return SignalRow(
            id=sig.id,
            source=sig.source,
            kind=sig.kind,
            fetched_at=_naive(sig.fetched_at),
            published_at=_naive(sig.published_at),
            title=sig.title,
            url=sig.url,
            summary=sig.summary,
            keywords=list(sig.keywords),
            metric=dict(sig.metric),
            topic_id=topic_id,
        )

    @staticmethod
    def _sig(row: SignalRow) -> Signal:
        return Signal(
            id=row.id,
            source=row.source,
            kind=row.kind,  # type: ignore[arg-type]
            fetched_at=_aware(row.fetched_at),
            published_at=_aware(row.published_at),
            title=row.title,
            url=row.url,
            summary=row.summary,
            keywords=list(row.keywords or []),
            metric=dict(row.metric or {}),
        )

    def signals_between(self, start: datetime, end: datetime, kinds: set[str] | None = None) -> list[Signal]:
        """[start, end) 신호."""
        with Session(self.engine, expire_on_commit=False) as s:
            q = select(SignalRow).where(
                SignalRow.published_at >= _naive(start), SignalRow.published_at < _naive(end)
            )
            rows = s.exec(q.order_by(SignalRow.published_at)).all()
        return [self._sig(r) for r in rows if kinds is None or r.kind in kinds]

    def schedule_signals_for(self, day: date) -> list[Signal]:
        """수동 일정(schedule) 신호 중 해당 날짜 일정."""
        with Session(self.engine, expire_on_commit=False) as s:
            rows = s.exec(select(SignalRow).where(SignalRow.kind == "schedule")).all()
        out = []
        for r in rows:
            at = (r.metric or {}).get("scheduled_at")
            if at and datetime.fromisoformat(at).date() == day:
                out.append(self._sig(r))
        return out

    def assign(self, topic_id: str, signal_ids: list[str]) -> None:
        """신호를 주제에 배정."""
        with Session(self.engine, expire_on_commit=False) as s:
            for sid in signal_ids:
                row = s.get(SignalRow, sid)
                if row is not None:
                    row.topic_id = topic_id
                    s.add(row)
            s.commit()

    def assigned_map(self, signal_ids: list[str]) -> dict[str, str]:
        """{signal_id: topic_id} (배정된 것만)."""
        out: dict[str, str] = {}
        with Session(self.engine, expire_on_commit=False) as s:
            for i in range(0, len(signal_ids), 500):
                chunk = signal_ids[i : i + 500]
                rows = s.exec(select(SignalRow).where(SignalRow.id.in_(chunk))).all()  # type: ignore[union-attr]
                out.update({r.id: r.topic_id for r in rows if r.topic_id})
        return out

    def signals_of(self, topic_id: str) -> list[Signal]:
        """주제에 배정된 신호 전부."""
        with Session(self.engine, expire_on_commit=False) as s:
            rows = s.exec(
                select(SignalRow).where(SignalRow.topic_id == topic_id).order_by(SignalRow.published_at)
            ).all()
        return [self._sig(r) for r in rows]

    # ---- 주제 ------------------------------------------------------------
    def save_topic(self, t: Topic) -> None:
        """주제 저장(신호 배정 포함)."""
        with Session(self.engine, expire_on_commit=False) as s:
            row = s.get(TopicRow, t.id) or TopicRow(
                id=t.id,
                name=t.name,
                category=t.category,
                first_seen=_naive(t.first_seen),
                last_seen=_naive(t.last_seen),
            )
            row.name = t.name
            row.keywords = list(t.keywords)
            row.category = t.category
            row.kind = t.kind
            row.scheduled_at = _naive(t.scheduled_at)
            row.follow_up = t.follow_up
            row.first_seen = _naive(t.first_seen)
            row.last_seen = _naive(t.last_seen)
            row.trend = t.trend
            row.scores = {k: v.model_dump() for k, v in t.scores.items()}
            row.total = t.total
            row.covered_in = list(t.covered_in)
            s.add(row)
            s.commit()
        self.assign(t.id, [sig.id for sig in t.signals if sig.kind != "competitor"])

    def get_topic(self, topic_id: str, with_signals: bool = True) -> Topic | None:
        """주제 조회."""
        with Session(self.engine, expire_on_commit=False) as s:
            row = s.get(TopicRow, topic_id)
        if row is None:
            return None
        return self._topic(row, self.signals_of(topic_id) if with_signals else [])

    @staticmethod
    def _topic(row: TopicRow, signals: list[Signal]) -> Topic:
        return Topic(
            id=row.id,
            name=row.name,
            keywords=list(row.keywords or []),
            category=row.category,
            signals=signals,
            first_seen=_aware(row.first_seen),
            last_seen=_aware(row.last_seen),
            trend=row.trend,  # type: ignore[arg-type]
            scores={k: ScoreDetail(**v) for k, v in (row.scores or {}).items()},
            total=row.total,
            covered_in=list(row.covered_in or []),
            kind=row.kind,  # type: ignore[arg-type]
            scheduled_at=_aware(row.scheduled_at),
            follow_up=row.follow_up,
        )

    def topics_since(self, since: datetime) -> list[Topic]:
        """last_seen이 since 이후인 주제."""
        with Session(self.engine, expire_on_commit=False) as s:
            rows = s.exec(select(TopicRow).where(TopicRow.last_seen >= _naive(since))).all()
        return [self._topic(r, self.signals_of(r.id)) for r in rows]

    def mark_covered(self, topic_id: str, plan_id: str) -> None:
        """covered_in에 슬롯을 추가."""
        with Session(self.engine, expire_on_commit=False) as s:
            row = s.get(TopicRow, topic_id)
            if row is not None and plan_id not in (row.covered_in or []):
                row.covered_in = [*(row.covered_in or []), plan_id]
                s.add(row)
                s.commit()

    # ---- 편성안 ----------------------------------------------------------
    def save_plan(self, plan: SlotPlan, project_dir: str = "") -> None:
        """편성안 저장(같은 ID는 덮어씀)."""
        with Session(self.engine, expire_on_commit=False) as s:
            row = s.get(PlanRow, plan.plan_id) or PlanRow(
                id=plan.plan_id, day=plan.date, slot=plan.slot, created_at=_naive(datetime.now(KST))
            )
            row.data = plan.model_dump(mode="json")
            row.project_dir = project_dir or row.project_dir
            s.add(row)
            s.commit()

    def get_plan(self, day: date, slot: str) -> SlotPlan | None:
        """편성안 조회."""
        with Session(self.engine, expire_on_commit=False) as s:
            row = s.get(PlanRow, f"{day.isoformat()}_{slot}")
        return SlotPlan.model_validate(row.data) if row else None

    def plans_between(self, start: date, end: date) -> list[SlotPlan]:
        """[start, end] 날짜의 편성안."""
        with Session(self.engine, expire_on_commit=False) as s:
            rows = s.exec(
                select(PlanRow).where(PlanRow.day >= start, PlanRow.day <= end).order_by(PlanRow.day)
            ).all()
        order = {"morning": 0, "evening": 1, "weekly": 2}
        plans = [SlotPlan.model_validate(r.data) for r in rows]
        return sorted(plans, key=lambda p: (p.date, order[p.slot]))

    # ---- 업로드 ----------------------------------------------------------
    def record_upload(self, row: UploadRow) -> None:
        """업로드 기록."""
        with Session(self.engine, expire_on_commit=False) as s:
            existing = s.get(UploadRow, row.video_id)
            if existing:
                for k, v in row.model_dump().items():
                    setattr(existing, k, v)
                s.add(existing)
            else:
                s.add(row)
            s.commit()

    def uploads(self, since: datetime | None = None) -> list[UploadRow]:
        """업로드 목록."""
        with Session(self.engine, expire_on_commit=False) as s:
            q = select(UploadRow)
            if since is not None:
                q = q.where(UploadRow.uploaded_at >= _naive(since))
            return list(s.exec(q.order_by(UploadRow.uploaded_at)).all())

    def update_stats(self, video_id: str, views: int | None, likes: int | None) -> None:
        """조회수 갱신."""
        with Session(self.engine, expire_on_commit=False) as s:
            row = s.get(UploadRow, video_id)
            if row:
                row.views, row.likes = views, likes
                row.stats_fetched_at = _naive(datetime.now(KST))
                s.add(row)
                s.commit()

    # ---- 메타 ------------------------------------------------------------
    def get_meta(self, key: str) -> str | None:
        """메타 값."""
        with Session(self.engine, expire_on_commit=False) as s:
            row = s.get(MetaRow, key)
        return row.value if row else None

    def set_meta(self, key: str, value: Any) -> None:
        """메타 저장."""
        with Session(self.engine, expire_on_commit=False) as s:
            row = s.get(MetaRow, key) or MetaRow(key=key, value="")
            row.value = str(value)
            s.add(row)
            s.commit()
