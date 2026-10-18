"""효과음 — 화제가 바뀌는 자리와 질문 자리에 짧은 소리를 얹는다(프리미어 A2 트랙).

- 전환음(whoosh): 주제가 바뀌는 장면, 또는 '그런데·하지만·문제는…'으로 시작하는 장면의 첫머리.
- 질문음(ding): 물음표로 끝나는 장면(시청자에게 묻는 문장)이 시작하는 자리.
- 강조음(hit): 색 글자 강조 자막이 뜨는 자리(쿵 하는 낮은 울림 + 짧은 타격음). 자리는 완성본 렌더가 정한다.

소리는 저작권이 없도록 여기서 합성한다. 프로젝트 폴더의 sfx/whoosh.wav, sfx/ding.wav가 이미 있으면
그 파일을 쓴다(직접 고른 효과음을 넣을 때). 내레이션을 덮지 않게 피크를 낮게 맞춘다.
"""

from __future__ import annotations

import math
import random
import struct
import wave
from dataclasses import dataclass
from pathlib import Path

from newsclip.models import Scene

SR = 48000
PEAK = 0.25  # 내레이션(대략 0.5 이하)보다 확실히 작게
HIT_PEAK = 0.5  # 강조음은 또렷하게 들리도록 더 크게(짧아서 말을 덮지 않는다)
GAP_SEC = 1.0  # 이 간격보다 가까운 효과음은 앞의 것만 낸다
TRANSITION_WORDS = ("그런데", "하지만", "그러나", "반면", "문제는", "결국", "분명한 건", "진짜")
WHOOSH, DING, HIT = "whoosh", "ding", "hit"


@dataclass(frozen=True)
class Cue:
    at: float  # 장면 시작(초)
    name: str  # WHOOSH | DING


def sfx_cues(scenes: list[Scene]) -> list[Cue]:
    """장면 목록 → 효과음 자리(시작 순)."""
    cues: list[Cue] = []
    prev_topic: str | None = None
    for sc in sorted(scenes, key=lambda s: s.start_sec):
        text = sc.text.strip()
        if (prev_topic is not None and sc.topic != prev_topic) or text.startswith(TRANSITION_WORDS):
            cues.append(Cue(sc.start_sec, WHOOSH))
        if text.endswith("?"):
            cues.append(Cue(sc.start_sec, DING))
        prev_topic = sc.topic
    kept: list[Cue] = []
    for cue in cues:
        if not kept or cue.at - kept[-1].at >= GAP_SEC:
            kept.append(cue)
    return kept


def _write_wav(path: Path, samples: list[float], level: float = PEAK) -> None:
    peak = max((abs(s) for s in samples), default=0.0) or 1.0
    frames = b"".join(struct.pack("<h", int(s / peak * level * 32767)) for s in samples)
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(frames)


def _whoosh(sec: float = 0.7, seed: int = 1) -> list[float]:
    """밝은 잡음이 어두워지며 사라지는 소리(한 극 저역통과 필터, 컷오프가 내려간다)."""
    rng = random.Random(seed)
    n = int(SR * sec)
    out: list[float] = []
    lp = 0.0
    for i in range(n):
        t = i / n
        k = 0.02 + 0.3 * (1 - t) ** 2  # 앞은 밝게, 뒤로 갈수록 어둡게
        lp += k * (rng.uniform(-1.0, 1.0) - lp)
        out.append(lp * math.sin(math.pi * t) ** 2)  # 페이드 인·아웃
    return out


def _ding(sec: float = 0.9) -> list[float]:
    """맑은 두 음(기본음 + 옥타브)이 점점 줄어드는 소리."""
    n = int(SR * sec)
    return [
        math.exp(-6.0 * i / SR)
        * (0.7 * math.sin(2 * math.pi * 988 * i / SR) + 0.3 * math.sin(2 * math.pi * 1976 * i / SR))
        for i in range(n)
    ]


def _hit(sec: float = 0.5, seed: int = 7) -> list[float]:
    """쿵: 150Hz에서 45Hz로 떨어지는 낮은 울림 + 맨 앞 30ms 밝은 타격음(잡음)."""
    rng = random.Random(seed)
    n = int(SR * sec)
    out: list[float] = []
    phase = 0.0
    lp = 0.0
    for i in range(n):
        t = i / SR
        freq = 45 + 105 * math.exp(-t / 0.06)
        phase += 2 * math.pi * freq / SR
        attack = min(1.0, t / 0.002)
        thump = attack * math.exp(-t / 0.13) * math.sin(phase)
        noise = rng.uniform(-1.0, 1.0)
        lp += 0.25 * (noise - lp)
        crack = (noise - lp) * math.exp(-t / 0.012)  # 잡음의 높은 쪽만 아주 짧게
        out.append(0.85 * thump + 0.45 * crack)
    return out


_MAKERS = {WHOOSH: _whoosh, DING: _ding, HIT: _hit}
_LEVEL = {HIT: HIT_PEAK}


def ensure_sfx(project_dir: Path) -> dict[str, Path]:
    """sfx/ 폴더에 효과음 WAV를 준비하고 이름 → 경로를 돌려준다(이미 있으면 그대로 쓴다)."""
    out: dict[str, Path] = {}
    for name, make in _MAKERS.items():
        path = project_dir / "sfx" / f"{name}.wav"
        if not path.exists():
            _write_wav(path, make(), _LEVEL.get(name, PEAK))
        out[name] = path
    return out
