"""효과음 — 화제가 바뀌는 자리와 질문 자리에 짧은 소리를 얹는다(프리미어 A2 트랙).

- 전환음(whoosh): 주제가 바뀌는 장면, 또는 '그런데·하지만·문제는…'으로 시작하는 장면의 첫머리.
- 질문음(ding): 물음표로 끝나는 장면(시청자에게 묻는 문장)이 시작하는 자리.
- 강조음(hit): 색 글자 강조 자막이 뜨는 자리. 휴대폰 스피커에서도 또렷하게 들리도록 중·고음 위주로 —
  0.18초 '슉' 올라오는 소리(HIT_LEAD) 끝에 '탁'(밝은 타격) + '펑'(중음) + '쿵'(저음). 자리는 완성본 렌더가 정한다.

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
HIT_PEAK = 1.0  # 강조음은 또렷하게 들리도록 크게(짧아서 말을 덮지 않고, 완성본 끝 리미터가 찢어짐을 막는다)
HIT_LEAD = 0.18  # 강조음 파일 앞부분의 올라오는 소리 길이 — 이만큼 먼저 틀어 '탁'이 자막과 함께 나게
HIT_EARLY = (
    0.06  # '탁'을 자막(=말이 시작되는 때)보다 이만큼 먼저 — 말 직전 쉼에 떨어져야 말소리에 묻히지 않는다
)
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


def _hit(seed: int = 7) -> list[float]:
    """올라오는 '슉'(HIT_LEAD초) → '쾅': 밝은 타격(탁) + 거친 중고음(800~3000Hz 잡음, 휴대폰에서 잘 들리는 대역)
    + 400→180Hz로 떨어지는 중음을 세게 찌그러뜨린 몸통(배음이 많아 작은 스피커에서도 들림) + 낮은 쿵 + 울림 꼬리."""
    rng = random.Random(seed)
    lead = int(SR * HIT_LEAD)
    n = lead + int(SR * 0.6)
    out: list[float] = []
    lp = lp2 = hp_lp = band_hi = band_lo = 0.0
    ph_mid = ph_sub = 0.0
    for i in range(n):
        noise = rng.uniform(-1.0, 1.0)
        if i < lead:  # 슉: 점점 밝아지고 커지는 잡음
            t = i / lead
            lp += (0.03 + 0.45 * t * t) * (noise - lp)
            out.append(0.8 * (t**2) * lp)
            continue
        t = (i - lead) / SR
        hp_lp += 0.3 * (noise - hp_lp)
        snap = (noise - hp_lp) * math.exp(-t / 0.025)  # 탁
        band_hi += 0.5 * (noise - band_hi)
        band_lo += 0.08 * (noise - band_lo)
        crunch = (band_hi - band_lo) * math.exp(-t / 0.09)  # 거친 중고음
        f_mid = 180 + 220 * math.exp(-t / 0.04)
        ph_mid += 2 * math.pi * f_mid / SR
        punch = math.tanh(4.0 * math.sin(ph_mid)) * math.exp(-t / 0.15)  # 몸통(찌그러뜨린 중음)
        f_sub = 45 + 45 * math.exp(-t / 0.05)
        ph_sub += 2 * math.pi * f_sub / SR
        boom = math.sin(ph_sub) * math.exp(-t / 0.16)  # 쿵
        lp2 += 0.08 * (noise - lp2)
        tail = lp2 * math.exp(-t / 0.25)  # 울림 꼬리
        attack = min(1.0, t / 0.001)
        out.append(attack * (0.8 * snap + 1.4 * crunch + 0.9 * punch + 0.35 * boom + 0.5 * tail))
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
