"""SQLite 저장소(sqlmodel). 시각은 시간대 포함으로 저장하고 읽을 때 KST로 변환한다."""

from __future__ import annotations

import datetime as dt
from datetime import date, datetime
from pathlib import Path
from typing import Any

from sqlalchemy import JSON, Column
from sqlmodel import Field, Session, SQLModel, create_engine, select

from studio_core.config import KST
from topicradar.models import Board, ScoreDetail, Signal, Topic, VideoPlan


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


class VideoRow(SQLModel, table=True):
    """영상 계획(10분 묶음·3분 단독, 전체를 JSON으로 보관)."""

    __tablename__ = "videos"
    id: str = Field(primary_key=True)
    day: dt.date = Field(index=True)
    format: str
    data: dict = Field(default_factory=dict, sa_column=Column(JSON))
    project_dir: str = ""
    created_at: datetime


class BoardRow(SQLModel, table=True):
    """주제판(지금 뜨는 주제 스냅숏)."""

    __tablename__ = "boards"
    id: str = Field(primary_key=True)
    created_at: datetime = Field(index=True)
    data: dict = Field(default_factory=dict, sa_column=Column(JSON))


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
        """새 신호 수를 돌려준다. 기존 신호는 metric(댓글·공감 수 등)을 병합하고,
        커뮤니티 글은 새로 읽은 본문(summary)이 있으면 바꾼다."""
        new = 0
        with Session(self.engine, expire_on_commit=False) as s:
            for sig in signals:
                row = s.get(SignalRow, sig.id)
                if row is None:
                    s.add(self._sig_row(sig))
                    new += 1
                    continue
                if sig.metric:
                    row.metric = {**(row.metric or {}), **sig.metric}
                if sig.kind == "community" and sig.summary:
                    row.summary = sig.summary
                s.add(row)
            s.commit()
        return new

    def get_signal(self, signal_id: str) -> Signal | None:
        """신호 하나."""
        with Session(self.engine, expire_on_commit=False) as s:
            row = s.get(SignalRow, signal_id)
        return self._sig(row) if row else None

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
        """covered_in에 영상 ID를 추가."""
        with Session(self.engine, expire_on_commit=False) as s:
            row = s.get(TopicRow, topic_id)
            if row is not None and plan_id not in (row.covered_in or []):
                row.covered_in = [*(row.covered_in or []), plan_id]
                s.add(row)
                s.commit()

    # ---- 주제판 ----------------------------------------------------------
    def save_board(self, board: Board) -> None:
        """주제판 저장."""
        with Session(self.engine, expire_on_commit=False) as s:
            row = s.get(BoardRow, board.id) or BoardRow(id=board.id, created_at=_naive(board.created_at))
            row.data = board.model_dump(mode="json")
            s.add(row)
            s.commit()

    def get_board(self, board_id: str) -> Board | None:
        """주제판 조회."""
        with Session(self.engine, expire_on_commit=False) as s:
            row = s.get(BoardRow, board_id)
        return Board.model_validate(row.data) if row else None

    def latest_board(self) -> Board | None:
        """가장 최근 주제판."""
        with Session(self.engine, expire_on_commit=False) as s:
            row = s.exec(select(BoardRow).order_by(BoardRow.created_at.desc()).limit(1)).first()  # type: ignore[attr-defined]
        return Board.model_validate(row.data) if row else None

    # ---- 영상 계획 --------------------------------------------------------
    def save_video(self, plan: VideoPlan, project_dir: str = "") -> None:
        """영상 계획 저장(같은 ID는 덮어씀)."""
        with Session(self.engine, expire_on_commit=False) as s:
            row = s.get(VideoRow, plan.id) or VideoRow(
                id=plan.id, day=plan.date, format=plan.format, created_at=_naive(plan.created_at)
            )
            row.data = plan.model_dump(mode="json")
            row.project_dir = project_dir or row.project_dir
            s.add(row)
            s.commit()

    def get_video(self, video_id: str) -> VideoPlan | None:
        """영상 계획 조회."""
        with Session(self.engine, expire_on_commit=False) as s:
            row = s.get(VideoRow, video_id)
        return VideoPlan.model_validate(row.data) if row else None

    def videos_between(self, start: date, end: date) -> list[VideoPlan]:
        """[start, end] 날짜에 만든 영상 계획(만든 순)."""
        with Session(self.engine, expire_on_commit=False) as s:
            rows = s.exec(
                select(VideoRow)
                .where(VideoRow.day >= start, VideoRow.day <= end)
                .order_by(VideoRow.created_at)  # type: ignore[arg-type]
            ).all()
        return [VideoPlan.model_validate(r.data) for r in rows]

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
