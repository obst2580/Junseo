"""작업 실행 프로세스: 화면 서버가 `python -m studio_app.worker`로 띄우고, 표준입력으로 작업 내용(JSON)을 받는다.

작업을 별도 프로세스로 돌리는 이유는 **중지** 때문이다. 파이썬 스레드는 밖에서 멈출 수 없지만 프로세스는
통째로 끝낼 수 있다(영상 받던 yt-dlp 같은 하위 프로세스까지). 진행 상황은 표준출력으로 한 줄씩 보낸다.
"""

from __future__ import annotations

import importlib
import json
import logging
import sys
import traceback
from collections.abc import Callable
from datetime import date, datetime
from typing import Any

MARK = "\x1e"  # 구조화된 줄 앞에 붙는 표시(그 밖의 출력은 로그 문장으로 취급)
LOGGERS = ("topicradar", "newsclip", "studio")


def encode(value: Any) -> Any:
    """JSON으로 못 보내는 값(날짜·시각)에 꼬리표를 붙인다."""
    if isinstance(value, datetime):
        return {"__datetime__": value.isoformat()}
    if isinstance(value, date):
        return {"__date__": value.isoformat()}
    raise TypeError(f"보낼 수 없는 값: {type(value).__name__}")


def decode(obj: dict[str, Any]) -> Any:
    """encode의 반대."""
    if "__datetime__" in obj:
        return datetime.fromisoformat(obj["__datetime__"])
    if "__date__" in obj:
        return date.fromisoformat(obj["__date__"])
    return obj


def resolve(name: str) -> Callable[..., dict[str, Any]]:
    """'모듈:함수' 또는 studio_app.services의 함수 이름."""
    mod, _, attr = name.rpartition(":") if ":" in name else ("studio_app.services", "", name)
    return getattr(importlib.import_module(mod), attr)


def emit(kind: str, value: Any) -> None:
    """부모 프로세스로 한 줄 보내기."""
    sys.stdout.write(MARK + json.dumps({"t": kind, "v": value}, ensure_ascii=False, default=str) + "\n")
    sys.stdout.flush()


class _LogHandler(logging.Handler):
    def emit(self, record: logging.LogRecord) -> None:
        prefix = "⚠️ " if record.levelno >= logging.WARNING else ""
        emit("log", prefix + record.getMessage())


def main() -> int:
    """표준입력의 {"fn":..., "params":...}를 실행."""
    spec = json.loads(sys.stdin.read(), object_hook=decode)
    handler = _LogHandler(level=logging.INFO)
    for name in LOGGERS:
        lg = logging.getLogger(name)
        lg.addHandler(handler)
        lg.setLevel(logging.INFO)
    from studio_core.config import load_settings

    settings = load_settings()
    try:
        result = resolve(spec["fn"])(settings, lambda msg: emit("log", msg), **spec.get("params", {}))
    except Exception as e:  # 원인을 화면에 보여 준다
        emit("log", traceback.format_exc().strip().splitlines()[-1])
        emit("error", f"{type(e).__name__}: {e}")
        return 1
    emit("result", result)
    return 0


if __name__ == "__main__":
    sys.exit(main())
