"""오래 걸리는 작업(수집·편성·자료 수집 등)을 한 번에 하나씩 백그라운드로 돌리고 진행 로그를 모은다."""

from __future__ import annotations

import logging
import threading
import traceback
import uuid
from collections import deque
from collections.abc import Callable
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any

from studio_core.config import now_kst

log = logging.getLogger("studio.app")

Say = Callable[[str], None]
JobFn = Callable[[Say], dict[str, Any]]
LOGGERS = ("topicradar", "newsclip", "studio")


class Busy(RuntimeError):
    """다른 작업이 돌고 있음."""


@dataclass
class Job:
    """작업 하나."""

    kind: str
    label: str
    params: dict[str, Any] = field(default_factory=dict)
    id: str = field(default_factory=lambda: uuid.uuid4().hex[:8])
    status: str = "running"  # running | done | error
    started: datetime = field(default_factory=now_kst)
    finished: datetime | None = None
    log: list[str] = field(default_factory=list)
    result: dict[str, Any] | None = None
    error: str | None = None

    def say(self, msg: str) -> None:
        """진행 메시지."""
        self.log.append(f"{now_kst():%H:%M:%S} {msg}")

    def as_dict(self) -> dict[str, Any]:
        """화면용."""
        end = self.finished or now_kst()
        return {
            "id": self.id,
            "kind": self.kind,
            "label": self.label,
            "params": self.params,
            "status": self.status,
            "started": self.started.isoformat(),
            "elapsed": round((end - self.started).total_seconds()),
            "log": self.log[-200:],
            "result": self.result,
            "error": self.error,
        }


class _JobLogHandler(logging.Handler):
    def __init__(self, job: Job) -> None:
        super().__init__(level=logging.INFO)
        self.job = job

    def emit(self, record: logging.LogRecord) -> None:
        prefix = "⚠️ " if record.levelno >= logging.WARNING else ""
        self.job.say(prefix + record.getMessage())


class JobRunner:
    """작업 실행기. inline=True면 호출한 스레드에서 바로 실행(테스트용)."""

    def __init__(self, inline: bool = False) -> None:
        self.inline = inline
        self.lock = threading.Lock()
        self.current: Job | None = None
        self.history: deque[Job] = deque(maxlen=20)

    def busy(self) -> bool:
        """작업 중인가."""
        return self.current is not None and self.current.status == "running"

    def start(self, kind: str, label: str, fn: JobFn, **params: Any) -> Job:
        """작업 시작. 이미 돌고 있으면 Busy."""
        with self.lock:
            if self.busy():
                raise Busy(f"'{self.current.label}' 작업이 진행 중입니다")  # type: ignore[union-attr]
            job = Job(kind=kind, label=label, params=params)
            self.current = job
        if self.inline:
            self._run(job, fn)
        else:
            threading.Thread(target=self._run, args=(job, fn), daemon=True).start()
        return job

    def _run(self, job: Job, fn: JobFn) -> None:
        handler = _JobLogHandler(job)
        loggers = [logging.getLogger(n) for n in LOGGERS]
        levels = [lg.level for lg in loggers]
        for lg in loggers:
            lg.addHandler(handler)
            lg.setLevel(logging.INFO)
        job.say(f"{job.label} 시작")
        try:
            job.result = fn(job.say)
            job.status = "done"
            job.say(f"{job.label} 완료")
        except Exception as e:  # 화면에 원인을 보여 준다
            job.status = "error"
            job.error = f"{type(e).__name__}: {e}"
            job.say(f"❌ {job.error}")
            log.warning("작업 실패 %s\n%s", job.label, traceback.format_exc())
        finally:
            job.finished = now_kst()
            for lg, lv in zip(loggers, levels, strict=True):
                lg.removeHandler(handler)
                lg.setLevel(lv)
            self.history.appendleft(job)
