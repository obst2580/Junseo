"""자동 실행: 화면을 켜 두면 05:30·12:00·17:00에 수집·편성을 돌린다(cron 없이).

켜고 끄기는 data/app.json에 저장한다. 같은 시각 작업은 하루 한 번만(앱을 다시 켜도 중복 실행 안 함).
"""

from __future__ import annotations

import json
import threading
from collections.abc import Callable
from datetime import datetime, time, timedelta
from pathlib import Path
from typing import Any

from studio_core.config import now_kst

TIMES = [time(5, 30), time(12, 0), time(17, 0)]
GRACE = timedelta(minutes=20)  # 이 시각 안에 켜져 있으면 실행


class AutoRunner:
    """1분마다 시각을 보고 할 일이 있으면 trigger(now)를 부른다."""

    def __init__(self, state_path: Path, trigger: Callable[[datetime], bool]) -> None:
        self.state_path = state_path
        self.trigger = trigger
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None

    def state(self) -> dict[str, Any]:
        """{'enabled': bool, 'done': [...]}."""
        if self.state_path.exists():
            try:
                return json.loads(self.state_path.read_text(encoding="utf-8"))
            except ValueError:
                pass
        return {"enabled": False, "done": []}

    def _save(self, st: dict[str, Any]) -> None:
        st["done"] = st.get("done", [])[-20:]
        self.state_path.write_text(json.dumps(st, ensure_ascii=False), encoding="utf-8")

    def set_enabled(self, enabled: bool) -> dict[str, Any]:
        """켜기·끄기."""
        st = self.state()
        st["enabled"] = enabled
        self._save(st)
        return self.info()

    def due(self, now: datetime) -> str | None:
        """지금 실행할 시각 키('2026-10-08 17:00') 또는 None."""
        for t in TIMES:
            at = datetime.combine(now.date(), t, tzinfo=now.tzinfo)
            if at <= now < at + GRACE:
                return f"{now.date().isoformat()} {t:%H:%M}"
        return None

    def tick(self, now: datetime | None = None) -> str | None:
        """한 번 점검. 실행했으면 그 키."""
        now = now or now_kst()
        st = self.state()
        if not st.get("enabled"):
            return None
        key = self.due(now)
        if key is None or key in st.get("done", []):
            return None
        if not self.trigger(now):  # 다른 작업 중이면 다음 점검에서 다시
            return None
        st.setdefault("done", []).append(key)
        self._save(st)
        return key

    def info(self) -> dict[str, Any]:
        """화면용."""
        st = self.state()
        now = now_kst()
        upcoming = []
        for d in (0, 1):
            for t in TIMES:
                at = datetime.combine(now.date() + timedelta(days=d), t, tzinfo=now.tzinfo)
                if at > now:
                    upcoming.append(at)
        nxt = min(upcoming)
        return {
            "enabled": bool(st.get("enabled")),
            "times": [f"{t:%H:%M}" for t in TIMES],
            "next": f"{nxt:%m-%d %H:%M}",
            "done": st.get("done", [])[-5:],
        }

    def start(self) -> None:
        """백그라운드 점검 시작."""
        if self._thread:
            return

        def loop() -> None:
            while not self._stop.wait(60):
                try:
                    self.tick()
                except Exception:  # 자동 실행 오류는 작업 로그에 남고, 점검은 계속
                    pass

        self._thread = threading.Thread(target=loop, daemon=True)
        self._thread.start()

    def stop(self) -> None:
        """종료."""
        self._stop.set()
