"""효과음 — 화제가 바뀌는 자리와 질문 자리에 짧은 소리를 얹는다(프리미어 A2 트랙).

- 전환음(whoosh): 주제가 바뀌는 장면, 또는 '그런데·하지만·문제는…'으로 시작하는 장면의 첫머리.
- 질문음(ding): 물음표로 끝나는 장면(시청자에게 묻는 문장)이 시작하는 자리.
- 강조음(hit): 색 글자 강조 자막이 뜨는 자리의 짧고 깔끔한 '팝'(정신없지 않게). 녹음된 효과음(Mixkit 무료
  라이선스 — 상업적 사용·출처 표시 없이 가능, 파일 자체를 다시 배포하지는 않으므로 저장소에 넣지 않고 처음 쓸 때
  받아 프로젝트 sfx/에 둔다)을 쓰고, 받지 못하면 합성한 팝으로 대신한다. 자리는 완성본 렌더가 정한다.

전환음·질문음은 저작권이 없도록 여기서 합성한다. 프로젝트 폴더의 sfx/whoosh.wav, sfx/ding.wav, sfx/hit.wav가
이미 있으면 그 파일을 쓴다(직접 고른 효과음을 넣을 때). 내레이션을 덮지 않게 피크를 낮게 맞춘다.
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
HIT_PEAK = 0.55  # 강조음 최대 크기(0.9는 크다는 의견 — 약 4dB 낮춤)
HIT_EARLY = 0.03  # 팝을 자막(=말이 시작되는 때)보다 이만큼 먼저 — 말 직전 쉼에 떨어져야 묻히지 않는다
# 강조음 후보(Mixkit 효과음 번호): A 아주 짧은 팝, B 팝(기본), C 나무 '탁', D 펀치
HIT_CHOICES = {"A": 2354, "B": 2357, "C": 2182, "D": 2150}
HIT_CHOICE = "B"
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


def _pop(sec: float = 0.25) -> list[float]:
    """받지 못했을 때 쓰는 합성 팝: 900→500Hz로 빠르게 떨어지는 짧은 소리."""
    n = int(SR * sec)
    out: list[float] = []
    phase = 0.0
    for i in range(n):
        t = i / SR
        phase += 2 * math.pi * (500 + 400 * math.exp(-t / 0.02)) / SR
        out.append(min(1.0, t / 0.002) * math.exp(-t / 0.05) * math.sin(phase))
    return out


def _fetch_hit(path: Path, choice: str = HIT_CHOICE) -> bool:
    """강조음 녹음 파일을 받아 48kHz 모노 WAV(앞 쉼 잘라 냄, 크기 HIT_PEAK)로 저장. 실패하면 False."""
    import shutil
    import subprocess
    import urllib.request

    num = HIT_CHOICES.get(choice, HIT_CHOICES["B"])
    url = f"https://assets.mixkit.co/active_storage/sfx/{num}/{num}-preview.mp3"
    if shutil.which("ffmpeg") is None:
        return False
    try:
        with urllib.request.urlopen(url, timeout=20) as resp:  # noqa: S310 - 고정된 https 주소
            data = resp.read()
    except OSError:
        return False
    path.parent.mkdir(parents=True, exist_ok=True)
    src = path.with_suffix(".mp3")
    src.write_bytes(data)
    proc = subprocess.run(
        [
            "ffmpeg",
            "-loglevel",
            "error",
            "-i",
            str(src),
            "-af",
            "silenceremove=start_periods=1:start_threshold=-45dB",
            "-ac",
            "1",
            "-ar",
            str(SR),
            "-f",
            "s16le",
            "-",
        ],
        capture_output=True,
    )
    src.unlink(missing_ok=True)
    if proc.returncode != 0 or len(proc.stdout) < 2:
        return False
    pcm = proc.stdout[: len(proc.stdout) // 2 * 2]
    samples = [v / 32768 for v in struct.unpack(f"<{len(pcm) // 2}h", pcm)]
    _write_wav(path, samples, HIT_PEAK)  # 최대 크기를 HIT_PEAK로 맞춘다
    return True


_MAKERS = {WHOOSH: _whoosh, DING: _ding, HIT: _pop}
_LEVEL = {HIT: HIT_PEAK}


def ensure_sfx(project_dir: Path, fetch: bool = False) -> dict[str, Path]:
    """sfx/ 폴더에 효과음 WAV를 준비하고 이름 → 경로를 돌려준다(이미 있으면 그대로 쓴다 — 직접 고른 소리를 넣을 때).
    fetch면 강조음은 녹음된 효과음을 받아 쓰고(완성본 렌더), 아니면·실패하면 합성한다."""
    out: dict[str, Path] = {}
    for name, make in _MAKERS.items():
        path = project_dir / "sfx" / f"{name}.wav"
        if not path.exists() and not (name == HIT and fetch and _fetch_hit(path)):
            _write_wav(path, make(), _LEVEL.get(name, PEAK))
        out[name] = path
    return out
