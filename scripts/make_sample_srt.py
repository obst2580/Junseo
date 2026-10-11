"""시연용 샘플 SRT/WAV: 대본(script.txt)을 사람이 읽었다고 가정하고 프리미어 전사처럼 만든다.

- 녹음 대상 줄만 사용(헤더·#주석 제외), 초당 약 7자 + 줄 사이 0.25초 쉼
- 프리미어 전사처럼 짧은 줄 두 개를 한 큐로 합치거나 문장부호를 빼는 등 대본과 조금 다르게 만든다
- 같은 길이의 무음 narration.wav(48kHz mono)를 함께 만든다 → newsclip xml --audio 시연

사용: python scripts/make_sample_srt.py projects/2026-10-08_evening
"""

from __future__ import annotations

import re
import sys
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "newsclip" / "src"))

from newsclip.srt import Cue, write_srt  # noqa: E402

CHARS_PER_SEC = 7.0
GAP = 0.25


def make(project: Path) -> tuple[int, float]:
    """SRT·WAV를 쓰고 (큐 수, 길이)를 돌려준다."""
    lines = [
        ln.strip()
        for ln in (project / "script.txt").read_text(encoding="utf-8").splitlines()
        if ln.strip() and not ln.strip().startswith("#")
    ]
    cues: list[Cue] = []
    t = 0.6
    i = 0
    while i < len(lines):
        text = lines[i]
        # 짧은 줄 + 다음 줄을 한 큐로(프리미어 전사의 문장 병합 흉내)
        if len(text) < 12 and i + 1 < len(lines) and len(lines[i + 1]) < 16:
            text = f"{text} {lines[i + 1]}"
            i += 1
        spoken = re.sub(r"[.,…]", "", text) if i % 3 == 0 else text
        dur = max(1.2, len(text) / CHARS_PER_SEC)
        cues.append(Cue(index=len(cues) + 1, start=round(t, 3), end=round(t + dur, 3), text=spoken))
        t += dur + GAP
        i += 1
    (project / "narration.srt").write_text(write_srt(cues), encoding="utf-8")
    total = t + 0.5
    with wave.open(str(project / "narration.wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(48000)
        w.writeframes(b"\x00\x00" * int(48000 * total))
    return len(cues), total


if __name__ == "__main__":
    n, total = make(Path(sys.argv[1]))
    print(f"narration.srt {n}큐, narration.wav {total:.1f}초 (시연용 무음)")
