"""AI 음성 + 대본 → 자막 자동 생성(쉼 맞추기)."""

import math
import shutil
import struct
import wave

import pytest

from newsclip.autosub import AudioInfo, align, analyze, make_srt, narration_text, script_sentences

SCRIPT = """## 금융권 해킹
금융보안원이 2주 전에 이미
위험을 알렸는데도
금융사들이 그대로 뚫렸습니다.
#출처: https://n.example/1
이유가 뭘까요?
#TODO: 원인 보강
외부에 노출된 자산을
그대로 뒀기 때문입니다.

## 내일 쟁점: 1심 선고
내일은 1심 선고가 나옵니다.
결과에 따라 다음 수순이 갈립니다.
"""


def test_sentences_and_narration_text():
    sents = script_sentences(SCRIPT)
    assert [len(s) for s in sents] == [3, 1, 2, 1, 1]
    text = narration_text(SCRIPT)
    assert "#" not in text and "https" not in text
    assert text.startswith("금융보안원이 2주 전에 이미 위험을 알렸는데도 금융사들이 그대로 뚫렸습니다.")
    assert "\n\n내일은 1심 선고가 나옵니다." in text  # 주제마다 단락


def _tone_wav(path, parts, rate=16000):
    """parts: [('t', 초) 소리 / ('s', 초) 무음]."""
    frames = bytearray()
    for kind, sec in parts:
        for i in range(int(sec * rate)):
            v = int(9000 * math.sin(2 * math.pi * 220 * i / rate)) if kind == "t" else 0
            frames += struct.pack("<h", v)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(bytes(frames))


@pytest.mark.skipif(shutil.which("ffmpeg") is None, reason="ffmpeg 필요")
def test_make_srt_snaps_to_pauses(tmp_path):
    sents = script_sentences(SCRIPT)
    parts, starts, t = [("s", 0.4)], [], 0.4
    for n, s in enumerate(sents):
        dur = 0.11 * sum(len(x) for x in s)
        starts.append(t)
        if n == 0:  # 문장 안 짧은 쉼(쉼표) — 경계로 쓰면 안 됨
            parts += [("t", dur / 2), ("s", 0.25), ("t", dur / 2)]
            dur += 0.25
        else:
            parts.append(("t", dur))
        gap = 0.9 if n == 2 else 0.5  # 주제가 바뀌는 곳은 더 길게 쉼
        parts.append(("s", gap))
        t += dur + gap
    wav = tmp_path / "narration.wav"
    _tone_wav(wav, parts)
    cues, info, matched = make_srt(SCRIPT, wav)
    assert len(cues) == sum(len(s) for s in sents) and matched == len(sents) - 1
    firsts, i = [], 0
    for s in sents:
        firsts.append(cues[i].start)
        i += len(s)
    assert all(abs(a - b) < 0.12 for a, b in zip(firsts, starts, strict=True)), (firsts, starts)
    assert all(c.end > c.start for c in cues) and all(
        a.end <= b.start for a, b in zip(cues, cues[1:], strict=False)
    )
    assert info.duration == pytest.approx(t, abs=0.05)


def test_align_without_pauses_falls_back_to_length():
    sents = [["가나다라마바사."], ["가나다라마바사아자차카타파하."]]
    cues, matched = align(sents, AudioInfo(10.0, []))
    assert (
        matched == 0 and cues[0].start == 0 and cues[1].start == pytest.approx(10 * 8 / 23, abs=0.01)
    )  # 글자 수(마침표 포함) 비율


def test_analyze_wav_without_ffmpeg(tmp_path, monkeypatch):
    wav = tmp_path / "a.wav"
    _tone_wav(wav, [("t", 1.0)])
    monkeypatch.setattr(shutil, "which", lambda _n: None)
    info = analyze(wav)
    assert info.duration == pytest.approx(1.0) and info.silences == []
