"""SRT 파서·작성기(프리미어 전사 → 내보내기 형식 대응: BOM, CRLF, 점 밀리초, 번호 누락)."""

from __future__ import annotations

import re
from dataclasses import dataclass, field

_TIME = r"(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})"
_ARROW_RE = re.compile(_TIME + r"\s*-->\s*" + _TIME)


@dataclass
class Cue:
    """자막 큐 한 개."""

    index: int
    start: float
    end: float
    text: str
    # 쉼표·마침표 단위 시각(autosub.align이 음성의 쉼에 맞춘 것): (글, 시작, 끝). 자막 줄을 나눌 때 쓴다
    units: list[tuple[str, float, float]] = field(default_factory=list, compare=False, repr=False)


def _sec(h: str, m: str, s: str, ms: str) -> float:
    return int(h) * 3600 + int(m) * 60 + int(s) + int(ms.ljust(3, "0")) / 1000


def parse_srt(text: str) -> list[Cue]:
    """SRT 문자열 → Cue 목록(시작 시각 순)."""
    text = text.lstrip("﻿").replace("\r\n", "\n").replace("\r", "\n")
    cues: list[Cue] = []
    for block in re.split(r"\n\s*\n", text.strip()):
        lines = [ln for ln in block.split("\n") if ln.strip()]
        idx = next((i for i, ln in enumerate(lines) if _ARROW_RE.search(ln)), None)
        if idx is None:
            continue
        m = _ARROW_RE.search(lines[idx])
        assert m is not None
        start, end = _sec(*m.groups()[:4]), _sec(*m.groups()[4:])
        body = " ".join(ln.strip() for ln in lines[idx + 1 :])
        body = re.sub(r"<[^>]+>", "", body).strip()
        if not body or end <= start:
            continue
        cues.append(Cue(index=len(cues) + 1, start=start, end=end, text=body))
    return sorted(cues, key=lambda c: c.start)


def fmt_time(sec: float) -> str:
    """초 → HH:MM:SS,mmm."""
    ms = int(round(sec * 1000))
    h, rem = divmod(ms, 3_600_000)
    m, rem = divmod(rem, 60_000)
    s, ms = divmod(rem, 1000)
    return f"{h:02d}:{m:02d}:{s:02d},{ms:03d}"


def write_srt(cues: list[Cue]) -> str:
    """Cue 목록 → SRT 문자열."""
    out = []
    for n, c in enumerate(cues, start=1):
        out.append(f"{n}\n{fmt_time(c.start)} --> {fmt_time(c.end)}\n{c.text}\n")
    return "\n".join(out)
