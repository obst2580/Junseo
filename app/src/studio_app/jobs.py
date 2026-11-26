"""오래 걸리는 작업(수집·편성·자료 수집 등)을 한 번에 하나씩 돌리고 진행 로그를 모은다. 중지할 수 있다.

- 보통(화면): 작업마다 별도 프로세스(`python -m studio_app.worker`)를 띄운다. 중지하면 그 프로세스와
  하위 프로세스(yt-dlp 등)를 통째로 끝낸다. 화면 서버는 계속 켜져 있다.
- inline=True(테스트): 호출한 스레드에서 바로 실행한다(중지 불가).
"""

from __future__ import annotations

import json
import logging
import os
import signal
import subprocess
import sys
import threading
import traceback
import uuid
from collections import deque
from collections.abc import Callable
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any

from studio_app import worker
from studio_core.config import Settings, now_kst

log = logging.getLogger("studio.app")

Say = Callable[[str], None]
JobFn = Callable[..., dict[str, Any]]
LOGGERS = worker.LOGGERS


class Busy(RuntimeError):
    """다른 작업이 돌고 있음."""


@dataclass
class Job:
    """작업 하나."""

    kind: str
    label: str
    params: dict[str, Any] = field(default_factory=dict)
    id: str = field(default_factory=lambda: uuid.uuid4().hex[:8])
    status: str = "running"  # running | done | error | cancelled
    started: datetime = field(default_factory=now_kst)
    finished: datetime | None = None
    log: list[str] = field(default_factory=list)
    result: dict[str, Any] | None = None
    error: str | None = None
    cancel_requested: bool = False
    proc: subprocess.Popen | None = field(default=None, repr=False)

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
            "cancellable": self.status == "running" and self.proc is not None,
        }


class _JobLogHandler(logging.Handler):
    def __init__(self, job: Job) -> None:
        super().__init__(level=logging.INFO)
        self.job = job

    def emit(self, record: logging.LogRecord) -> None:
        prefix = "⚠️ " if record.levelno >= logging.WARNING else ""
        self.job.say(prefix + record.getMessage())


def fn_name(fn: JobFn | str) -> str:
    """작업 함수 → 'module:function'(작업 프로세스가 다시 찾을 이름)."""
    return fn if isinstance(fn, str) else f"{fn.__module__}:{fn.__qualname__}"


def kill_tree(proc: subprocess.Popen) -> None:
    """작업 프로세스와 그 하위 프로세스를 끝낸다."""
    if proc.poll() is not None:
        return
    if os.name == "nt":
        subprocess.run(["taskkill", "/PID", str(proc.pid), "/T", "/F"], capture_output=True)
        return
    try:
        os.killpg(proc.pid, signal.SIGTERM)
        proc.wait(timeout=3)
    except (ProcessLookupError, subprocess.TimeoutExpired):
        try:
            os.killpg(proc.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass


class JobRunner:
    """작업 실행기."""

    def __init__(self, inline: bool = False) -> None:
        self.inline = inline
        self.lock = threading.Lock()
        self.current: Job | None = None
        self.history: deque[Job] = deque(maxlen=20)

    def busy(self) -> bool:
        """작업 중인가."""
        return self.current is not None and self.current.status == "running"

    def start(self, kind: str, label: str, fn: JobFn | str, settings: Settings, **params: Any) -> Job:
        """작업 시작(fn(settings, say, **params)). 이미 돌고 있으면 Busy."""
        with self.lock:
            if self.busy():
                raise Busy(f"'{self.current.label}' 작업이 진행 중입니다")  # type: ignore[union-attr]
            job = Job(kind=kind, label=label, params=params)
            self.current = job
        job.say(f"{label} 시작")
        if self.inline:
            self._run_inline(job, fn, settings, params)
        else:
            self._spawn(job, fn_name(fn), settings, params)
        return job

    def cancel(self) -> Job | None:
        """진행 중인 작업 중지. 없으면 None."""
        job = self.current
        if job is None or job.status != "running" or job.proc is None:
            return None
        job.cancel_requested = True
        job.say("중지 요청 — 작업을 끝내는 중")
        kill_tree(job.proc)
        return job

    # ---- 내부 --------------------------------------------------------------------------------
    def _finish(self, job: Job) -> None:
        job.finished = now_kst()
        self.history.appendleft(job)

    def _run_inline(self, job: Job, fn: JobFn | str, settings: Settings, params: dict[str, Any]) -> None:
        func = worker.resolve(fn) if isinstance(fn, str) else fn
        handler = _JobLogHandler(job)
        loggers = [logging.getLogger(n) for n in LOGGERS]
        levels = [lg.level for lg in loggers]
        for lg in loggers:
            lg.addHandler(handler)
            lg.setLevel(logging.INFO)
        try:
            job.result = func(settings, job.say, **params)
            job.status = "done"
            job.say(f"{job.label} 완료")
        except Exception as e:  # 화면에 원인을 보여 준다
            job.status = "error"
            job.error = f"{type(e).__name__}: {e}"
            job.say(f"❌ {job.error}")
            log.warning("작업 실패 %s\n%s", job.label, traceback.format_exc())
        finally:
            for lg, lv in zip(loggers, levels, strict=True):
                lg.removeHandler(handler)
                lg.setLevel(lv)
            self._finish(job)

    def _spawn(self, job: Job, name: str, settings: Settings, params: dict[str, Any]) -> None:
        env = {
            **os.environ,
            "STUDIO_ROOT": str(settings.root),
            "PYTHONIOENCODING": "utf-8",
            "PYTHONUTF8": "1",
        }
        kwargs: dict[str, Any] = {}
        if os.name == "nt":
            kwargs["creationflags"] = subprocess.CREATE_NEW_PROCESS_GROUP  # type: ignore[attr-defined]
        else:
            kwargs["start_new_session"] = True
        try:
            proc = subprocess.Popen(
                [sys.executable, "-m", "studio_app.worker"],
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                env=env,
                cwd=str(settings.root),
                text=True,
                encoding="utf-8",
                errors="replace",
                bufsize=1,
                **kwargs,
            )
        except OSError as e:
            job.status, job.error = "error", f"작업 프로세스를 띄우지 못했습니다: {e}"
            job.say(f"❌ {job.error}")
            self._finish(job)
            return
        job.proc = proc
        assert proc.stdin is not None
        proc.stdin.write(
            json.dumps({"fn": name, "params": params}, ensure_ascii=False, default=worker.encode)
        )
        proc.stdin.close()
        threading.Thread(target=self._pump, args=(job, proc), daemon=True).start()

    def _pump(self, job: Job, proc: subprocess.Popen) -> None:
        """작업 프로세스 출력 → 로그·결과."""
        assert proc.stdout is not None
        for line in proc.stdout:
            line = line.rstrip("\n")
            if not line.startswith(worker.MARK):
                if line.strip():
                    job.say(line.strip())
                continue
            try:
                msg = json.loads(line[len(worker.MARK) :])
            except ValueError:
                job.say(line[len(worker.MARK) :])
                continue
            if msg["t"] == "log":
                job.say(str(msg["v"]))
            elif msg["t"] == "result":
                job.result = msg["v"]
            elif msg["t"] == "error":
                job.error = str(msg["v"])
        code = proc.wait()
        if job.cancel_requested:
            job.status = "cancelled"
            job.say("■ 중지했습니다")
        elif job.error:
            job.status = "error"
            job.say(f"❌ {job.error}")
        elif code == 0 and job.result is not None:
            job.status = "done"
            job.say(f"{job.label} 완료")
        else:
            job.status = "error"
            job.error = f"작업 프로세스가 비정상 종료(코드 {code})"
            job.say(f"❌ {job.error}")
        self._finish(job)
