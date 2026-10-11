"""대본 + AI 음성(타입캐스트 웹 등에서 받은 파일) → 자막(SRT) 자동 생성.

음성 인식을 쓰지 않는다. 대본 글자를 이미 알고 있으므로, 음성에서 **조용한 구간**(문장 사이 쉼)을 찾아
대본 문장 경계에 맞춘다(ffmpeg silencedetect).
1. 대본 → 문장 단위(자막 줄 묶음). 헤더·주석은 읽지 않으므로 뺀다.
2. 말소리 시간(쉼을 뺀 시간)을 글자 수 비율로 나눠 문장 경계의 '예상 시각'을 잡는다.
3. 예상 시각과 가장 잘 맞는 쉼을 순서대로 고른다(동적 계획법). 맞는 쉼이 없으면 예상 시각을 쓴다.
4. 문장 안의 자막 줄은 글자 수 비율로 나눈다.
TTS는 문장마다 일정하게 쉬어서 이 방식으로 충분히 맞는다. ffmpeg가 없으면 2단계(글자 비율)만 쓴다.
"""

from __future__ import annotations

import re
import shutil
import subprocess
import wave
from dataclasses import dataclass
from pathlib import Path

from newsclip.srt import Cue

_SENT_END_RE = re.compile(r"[.?!…]['\"”’」』)\]]*$")
_DUR_RE = re.compile(r"Duration:\s*(\d+):(\d+):(\d+(?:\.\d+)?)")
_SIL_START_RE = re.compile(r"silence_start:\s*(-?\d+(?:\.\d+)?)")
_SIL_END_RE = re.compile(r"silence_end:\s*(\d+(?:\.\d+)?)")
AUDIO_EXTS = {".wav", ".mp3", ".m4a", ".aac", ".ogg", ".flac"}


def script_sentences(script: str) -> list[list[str]]:
    """대본 → 문장 목록(각 문장 = 자막 줄 목록). 헤더(##)·주석(#)·빈 줄은 문장 경계로만 쓴다."""
    out: list[list[str]] = []
    cur: list[str] = []
    for raw in script.splitlines():
        ln = raw.strip()
        if not ln or ln.startswith("#"):
            if cur:
                out.append(cur)
                cur = []
            continue
        cur.append(ln)
        if _SENT_END_RE.search(ln):
            out.append(cur)
            cur = []
    if cur:
        out.append(cur)
    return out


PHRASE_MAX = 30  # 이보다 긴 문장은 나눈다(사진 한 장에 대사가 너무 많지 않게)
PHRASE_MIN = 10  # 이보다 짧은 조각은 다음 조각에 붙인다
QUOTE_LONG = 45  # 이보다 긴 따옴표 인용은 인용 안 쉼표에서도 나눈다
_SENT_SPLIT_RE = re.compile(r"(?<=[.?!…])(?:['\"”’」』)\]]*)\s+")
_AFTER_QUOTE_RE = re.compile(r"[\"”](?:이고|이며|며|면서)\s")  # "…쓰던 말"이고 | '우아하게'를…
_LINE_END_RE = re.compile(r"[.?!…,'\"”’]$")


def _sentences_of(text: str) -> list[str]:
    """단락 글 → 문장(문장 끝 부호 뒤에서 자른다, 닫는 따옴표 포함). 글쓴이 줄바꿈(\n)은 그대로 둔다."""
    out, start = [], 0
    for m in _SENT_SPLIT_RE.finditer(text):
        out.append(text[start : m.end()].strip())
        start = m.end()
    if text[start:].strip():
        out.append(text[start:].strip())
    return [x for x in out if x]


def _quote_spans(text: str) -> list[tuple[int, int]]:
    marks = [i for i, ch in enumerate(text) if ch in '"“”']
    return list(zip(marks[0::2], marks[1::2], strict=False))


def split_phrases(sentence: str, max_len: int = PHRASE_MAX, min_len: int = PHRASE_MIN) -> list[str]:
    """문장 → 말 덩어리(장면 하나에 하나). 자르는 곳은 글쓴이 줄바꿈, 쉼표 뒤(낱말 나열 '폭언, 욕설,'은 빼고),
    따옴표 인용이 시작하는 곳 앞, 인용 뒤 '이고·며·면서' 뒤뿐 — 문장·인용 한가운데는 자르지 않는다
    (아주 긴 인용만 인용 안 쉼표에서). 짧은 조각은 다음 조각에 붙인다."""
    flat = sentence.replace("\n", " ")
    if len(flat) <= max_len:
        return [flat]
    cuts: set[int] = set()
    pos = 0
    for part in sentence.split("\n")[:-1]:  # 글쓴이가 바꾼 줄
        pos += len(part) + 1
        cuts.add(pos)
    spans = _quote_spans(flat)
    for a, _b in spans:
        if a > 0 and flat[a - 1] == " ":
            cuts.add(a)
    for m in _AFTER_QUOTE_RE.finditer(flat):
        cuts.add(m.end())
    last = 0
    for i, ch in enumerate(flat):
        if ch != "," or i + 1 >= len(flat) or flat[i + 1] != " ":
            continue
        item = flat[last:i].strip()
        last = i + 1
        span = next(((a, b) for a, b in spans if a < i < b), None)
        if span and span[1] - span[0] <= QUOTE_LONG:
            continue
        if " " not in item:  # 낱말 나열(폭언, 욕설, 비아냥,)은 한 덩어리로
            continue
        cuts.add(i + 2)
    pieces, prev = [], 0
    for c in sorted(cuts | {len(flat)}):
        piece = flat[prev:c].strip()
        if piece:
            pieces.append(piece)
        prev = c
    out: list[str] = []
    buf = ""
    for piece in pieces:
        buf = f"{buf} {piece}" if buf else piece
        if len(buf) >= min_len:
            out.append(buf)
            buf = ""
    if buf:
        if out:
            out[-1] = f"{out[-1]} {buf}"
        else:
            out.append(buf)
    return out


def script_phrases(script: str, max_len: int = PHRASE_MAX) -> list[list[str]]:
    """대본 → 문장 목록(각 문장 = 말 덩어리 목록). 문장을 먼저 잇고 나서 나누므로, 25자 자막 줄로 쓴 예전 대본은
    줄바꿈을 무시하고, 한 줄 한 문장(말 덩어리)으로 쓴 대본은 글쓴이 줄바꿈도 자르는 곳으로 쓴다."""
    spoken = [ln.strip() for ln in script.splitlines() if ln.strip() and not ln.strip().startswith("#")]
    by_writer = bool(spoken) and sum(1 for ln in spoken if _LINE_END_RE.search(ln)) / len(spoken) >= 0.6
    joiner = "\n" if by_writer else " "
    out: list[list[str]] = []
    para: list[str] = []

    def flush() -> None:
        if para:
            out.extend(split_phrases(x, max_len) for x in _sentences_of(joiner.join(para)))
            para.clear()

    for raw in script.splitlines():
        ln = raw.strip()
        if not ln or ln.startswith("#"):
            flush()
            continue
        para.append(ln)
    flush()
    return [x for x in out if x]


def narration_text(script: str) -> str:
    """타입캐스트에 붙여넣을 글: 읽는 문장만, 자막 줄바꿈은 이어 붙이고 단락(주제)마다 빈 줄."""
    paras: list[list[str]] = [[]]
    cur: list[str] = []
    for raw in script.splitlines():
        ln = raw.strip()
        if ln.startswith("## ") or not ln:
            if cur:
                paras[-1].append(" ".join(cur))
                cur = []
            if paras[-1]:
                paras.append([])
            continue
        if ln.startswith("#"):
            continue
        cur.append(ln)
        if _SENT_END_RE.search(ln):
            paras[-1].append(" ".join(cur))
            cur = []
    if cur:
        paras[-1].append(" ".join(cur))
    return "\n\n".join(" ".join(p) for p in paras if p) + "\n"


@dataclass
class AudioInfo:
    """길이와 쉼 구간(초)."""

    duration: float
    silences: list[tuple[float, float]]


def wav_duration(path: Path) -> float:
    """wav 길이(ffmpeg 없이)."""
    with wave.open(str(path), "rb") as w:
        return w.getnframes() / float(w.getframerate())


def analyze(path: Path, noise_db: int = -35, min_silence: float = 0.22) -> AudioInfo:
    """길이와 쉼 구간. ffmpeg가 없으면 쉼 없이 길이만(wav만 가능)."""
    if shutil.which("ffmpeg") is None:
        return AudioInfo(wav_duration(path), [])
    proc = subprocess.run(
        [
            "ffmpeg",
            "-hide_banner",
            "-nostats",
            "-i",
            str(path),
            "-af",
            f"silencedetect=noise={noise_db}dB:d={min_silence}",
            "-f",
            "null",
            "-",
        ],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )
    log = proc.stderr
    m = _DUR_RE.search(log)
    duration = int(m.group(1)) * 3600 + int(m.group(2)) * 60 + float(m.group(3)) if m else 0.0
    if not duration and path.suffix.lower() == ".wav":
        duration = wav_duration(path)
    starts = [max(0.0, float(x)) for x in _SIL_START_RE.findall(log)]
    ends = [float(x) for x in _SIL_END_RE.findall(log)]
    if len(ends) < len(starts):  # 끝까지 조용하면 silence_end가 없다
        ends.append(duration)
    return AudioInfo(duration, list(zip(starts, ends, strict=False)))


def to_wav(src: Path, dst: Path) -> None:
    """mp3·m4a 등 → 48kHz 모노 wav(프리미어 XML 내레이션 트랙용)."""
    if shutil.which("ffmpeg") is None:
        raise RuntimeError("ffmpeg가 없어 mp3 등을 변환할 수 없습니다 — wav로 받거나 ffmpeg를 설치하세요")
    subprocess.run(
        [
            "ffmpeg",
            "-y",
            "-loglevel",
            "error",
            "-i",
            str(src),
            "-ac",
            "1",
            "-ar",
            "48000",
            "-c:a",
            "pcm_s16le",
            str(dst),
        ],
        check=True,
        capture_output=True,
    )


def _speech_clock(duration: float, silences: list[tuple[float, float]]):  # noqa: ANN202
    """절대 시각 ↔ '말소리 시간'(쉼을 뺀 누적 시간) 변환 함수 쌍."""
    sil = sorted((max(0.0, a), min(duration, b)) for a, b in silences if b > a)

    def speech_before(t: float) -> float:
        quiet = sum(max(0.0, min(b, t) - a) for a, b in sil if a < t)
        return t - quiet

    def absolute(sp: float) -> float:
        t, acc = 0.0, 0.0
        for a, b in sil:
            if acc + (a - t) >= sp:
                return t + (sp - acc)
            acc += a - t
            t = b
        return t + (sp - acc)

    return speech_before, absolute


def align(sentences: list[list[str]], info: AudioInfo, gap: float = 0.05) -> tuple[list[Cue], int]:
    """문장(자막 줄 묶음) → 자막 큐. 반환: (큐 목록, 쉼에 맞춘 경계 수)."""
    if not sentences:
        return [], 0
    dur = info.duration
    sil = [(a, b) for a, b in sorted(info.silences) if b > a]
    head = sil[0][1] if sil and sil[0][0] <= 0.05 else 0.0  # 앞 무음
    tail = sil[-1][0] if sil and sil[-1][1] >= dur - 0.05 else dur  # 뒤 무음
    inner = [(a, b) for a, b in sil if a > head and b < tail]
    speech_before, absolute = _speech_clock(dur, sil)
    sp0, sp1 = speech_before(head), speech_before(tail)
    lens = [max(1, sum(len(x) for x in s)) for s in sentences]
    total = sum(lens)
    expected, acc = [], 0
    for n in lens[:-1]:
        acc += n
        expected.append(sp0 + (sp1 - sp0) * acc / total)
    # 동적 계획법: 경계 k를 쉼 j에 맞추거나(비용 = 말소리 시간 차) 예상 시각을 그대로(벌점) 쓴다. 순서 유지.
    cand = [(speech_before((a + b) / 2), a, b) for a, b in inner]
    K, J = len(expected), len(cand)
    miss = max(1.5, (sp1 - sp0) / max(len(sentences), 1) * 0.6)
    inf = float("inf")
    # best[k][j]: 경계 0..k-1을 쓰고 쉼은 j개 이전까지만 쓴 최소 비용
    best = [[inf] * (J + 1) for _ in range(K + 1)]
    choice: list[list[tuple[int, int] | None]] = [[None] * (J + 1) for _ in range(K + 1)]
    for j in range(J + 1):
        best[0][j] = 0.0
    for k in range(1, K + 1):
        e = expected[k - 1]
        for j in range(J + 1):
            # (1) 쉼 없이 예상 시각
            c = best[k - 1][j] + miss
            if c < best[k][j]:
                best[k][j], choice[k][j] = c, (j, -1)
            # (2) 쉼 j-1 사용
            if j >= 1 and best[k - 1][j - 1] < inf:
                c2 = best[k - 1][j - 1] + abs(cand[j - 1][0] - e)
                if c2 < best[k][j]:
                    best[k][j], choice[k][j] = c2, (j - 1, j - 1)
            # (3) 쉼 j-1 건너뛰기(문장 안 쉼표 등)
            if j >= 1 and best[k][j - 1] < best[k][j]:
                best[k][j], choice[k][j] = best[k][j - 1], choice[k][j - 1]
    cuts: list[tuple[float, float]] = []
    k, j = K, min(range(J + 1), key=lambda x: best[K][x])
    while k > 0:
        prev = choice[k][j]
        assert prev is not None
        pj, used = prev
        if used >= 0:
            cuts.append((cand[used][1], cand[used][2]))
        else:
            t = absolute(expected[k - 1])
            cuts.append((t, t))
        k, j = k - 1, pj
    cuts.reverse()
    matched = sum(1 for a, b in cuts if b > a)
    # 문장 구간 → 자막 줄
    cues: list[Cue] = []
    starts = [head, *[b for _a, b in cuts]]
    ends = [*[a for a, _b in cuts], tail]
    for sent, s, e in zip(sentences, starts, ends, strict=True):
        e = max(e, s + 0.4 * len(sent))
        n = sum(len(x) for x in sent) or 1
        t = s
        for line in sent:
            d = (e - s) * len(line) / n
            cues.append(
                Cue(
                    index=len(cues) + 1, start=round(t, 3), end=round(max(t + 0.3, t + d - gap), 3), text=line
                )
            )
            t += d
    for a, b in zip(cues, cues[1:], strict=False):  # 겹침 정리
        if a.end > b.start:
            a.end = round(max(a.start + 0.2, b.start - 0.01), 3)
    return cues, matched


def make_srt(script: str, audio: Path) -> tuple[list[Cue], AudioInfo, int]:
    """대본 + 음성 → (큐, 오디오 정보, 쉼에 맞춘 경계 수)."""
    info = analyze(audio)
    cues, matched = align(script_sentences(script), info)
    return cues, info, matched
