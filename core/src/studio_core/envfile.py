""".env 읽기·쓰기(화면 설정 탭용). 주석과 순서를 지키고 바꾼 키 줄만 고친다."""

from __future__ import annotations

import os
import re
from pathlib import Path

_LINE_RE = re.compile(r"^\s*(?P<comment>#\s*)?(?P<key>[A-Z][A-Z0-9_]*)\s*=(?P<value>.*)$")


def read_env(path: Path) -> dict[str, str]:
    """주석이 아닌 KEY=값 줄."""
    out: dict[str, str] = {}
    if not path.exists():
        return out
    for ln in path.read_text(encoding="utf-8").splitlines():
        m = _LINE_RE.match(ln)
        if m and not m.group("comment"):
            out[m.group("key")] = m.group("value").strip().strip("'\"")
    return out


def set_env(path: Path, values: dict[str, str], template: Path | None = None) -> None:
    """키 값을 바꾼다. 줄이 있으면 그 자리(주석 처리된 예시 줄이면 주석을 풀어서), 없으면 끝에 추가.

    .env가 없으면 template(.env.example)을 복사해서 시작한다. 현재 프로세스 환경변수도 같이 바꾼다.
    빈 값은 'KEY=' 로 남기고 환경변수에서 지운다.
    """
    if path.exists():
        lines = path.read_text(encoding="utf-8").splitlines()
    elif template is not None and template.exists():
        lines = template.read_text(encoding="utf-8").splitlines()
    else:
        lines = []
    for key, raw in values.items():
        value = raw.strip().replace("\n", "")
        line = f"{key}={value}"
        idx = next(
            (
                i
                for i, ln in enumerate(lines)
                if (m := _LINE_RE.match(ln)) and m.group("key") == key and not m.group("comment")
            ),
            None,
        )
        if idx is None:
            idx = next(
                (i for i, ln in enumerate(lines) if (m := _LINE_RE.match(ln)) and m.group("key") == key),
                None,
            )
        if idx is None:
            lines.append(line)
        else:
            lines[idx] = line
        if value:
            os.environ[key] = value
        else:
            os.environ.pop(key, None)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def mask(value: str) -> str:
    """화면 표시용: 끝 4자리만."""
    if not value:
        return ""
    return "••••" + value[-4:] if len(value) > 8 else "••••"
