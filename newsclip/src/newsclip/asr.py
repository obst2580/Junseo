"""음성 인식(faster-whisper)으로 단어마다 들린 시각을 얻는다 — 자막을 '말이 멈춘 곳'이 아니라 실제로 들린 말에 맞추려고.

자막 글은 대본 그대로 쓰고, 인식 결과는 시각을 잡는 데만 쓴다(autosub.asr_boundaries). 그래서 인식이 몇 글자
틀려도 괜찮고, 작은 모델(small)로 충분하다. 모델은 처음 한 번 Hugging Face에서 받는다(small ≈ 480MB).
같은 음성은 다시 듣지 않게 결과를 파일 해시로 저장해 둔다.
"""

from __future__ import annotations

import hashlib
import json
import os
import shutil
import subprocess
from pathlib import Path

from newsclip.autosub import Word

MODEL = os.environ.get("STUDIO_ASR_MODEL", "small")


class AsrUnavailable(RuntimeError):
    """인식할 수 없음(프로그램·ffmpeg 없음 등) — 쉼만으로 맞춘다."""


def _pcm16k(path: Path):  # noqa: ANN202
    """ffmpeg로 16kHz 모노 float32(인식 입력). webm·mp3·wav 모두."""
    import numpy as np

    if shutil.which("ffmpeg") is None:
        raise AsrUnavailable("ffmpeg가 없습니다")
    proc = subprocess.run(
        ["ffmpeg", "-v", "error", "-i", str(path), "-ac", "1", "-ar", "16000", "-f", "s16le", "-"],
        capture_output=True,
        check=False,
    )
    if proc.returncode != 0 or not proc.stdout:
        raise AsrUnavailable("음성 파일을 읽지 못했습니다: " + proc.stderr.decode("utf-8", "replace")[-200:])
    return np.frombuffer(proc.stdout, np.int16).astype(np.float32) / 32768.0


def transcribe(path: Path, cache_dir: Path | None = None, model: str = MODEL) -> list[Word]:
    """음성 → [(말, 시작초, 끝초)]. cache_dir가 있으면 같은 파일·모델은 저장한 결과를 쓴다."""
    digest = hashlib.sha256(path.read_bytes()).hexdigest()[:20]
    cached = cache_dir / f"asr-{model}-{digest}.json" if cache_dir else None
    if cached and cached.exists():
        return [tuple(w) for w in json.loads(cached.read_text(encoding="utf-8"))]  # type: ignore[misc]
    try:
        from faster_whisper import WhisperModel
    except ImportError as e:  # pragma: no cover - 설치 환경에 따라
        raise AsrUnavailable("faster-whisper가 설치되지 않았습니다(uv sync)") from e
    audio = _pcm16k(path)
    wm = WhisperModel(model, device="cpu", compute_type="int8", cpu_threads=os.cpu_count() or 4)
    segs, _info = wm.transcribe(
        audio,
        language="ko",
        word_timestamps=True,
        beam_size=1,
        temperature=0.0,  # 다시 듣기(온도 올려 재시도) 없이 한 번만 — 시각만 쓰니 충분하고 빠르다
        condition_on_previous_text=False,  # 앞 구간 글에 끌려 같은 말을 되풀이하지 않게
    )
    words: list[Word] = [
        (w.word.strip(), round(w.start, 3), round(w.end, 3))
        for s in segs
        for w in (s.words or [])
        if w.word.strip()
    ]
    if cached:
        cached.parent.mkdir(parents=True, exist_ok=True)
        cached.write_text(json.dumps(words, ensure_ascii=False), encoding="utf-8")
    return words
