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

SUB_LINE = 20  # 자막 한 줄 글자 수(프리미어 캡션용 SRT, 2자까지 넘쳐도 됨)
# 이 말 뒤에서는 자막 줄을 바꾸지 않는다(뒷말과 붙어야 읽힘: '안 읽고', '못 박았습니다')
_NO_BREAK_AFTER = {
    "안",
    "못",
    "더",
    "잘",
    "꼭",
    "다",
    "또",
    "좀",
    "제",
    "그",
    "이",
    "저",
    "한",
    "두",
    "세",
    "몇",
    "온",
    "새",
}
_CLAUSE_END = (",", "고", "며", "면", "서", "데", "지만", "는데요,", "아니라")

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


def subtitle_lines(text: str, limit: int = SUB_LINE) -> list[str]:
    """말 덩어리 → 자막 줄. 줄 수를 정한 뒤 고르게 나누고(외톨이 짧은 줄 없이), 띄어쓰기에서만 자른다.
    절이 끝나는 곳(쉼표·'~고'·'~면')을 조금 더 좋아하고, '안·못' 같은 말 바로 뒤는 피한다."""
    text = text.strip()
    if len(text) <= limit + 2:
        return [text] if text else []
    k = -(-len(text) // (limit + 2))
    spaces = [i for i, ch in enumerate(text) if ch == " "]
    lines, start = [], 0
    for part in range(1, k):
        target = start + (len(text) - start) / (k - part + 1)
        best, best_cost = None, float("inf")
        for i in spaces:
            if i <= start + 3 or i >= len(text) - 3:
                continue
            word = text[:i].rsplit(" ", 1)[-1]
            cost = (
                abs(i - target)
                - (3 if word.endswith(_CLAUSE_END) else 0)
                + (8 if word in _NO_BREAK_AFTER or len(word) == 1 else 0)
            )
            if cost < best_cost:
                best, best_cost = i, cost
        if best is None:
            break
        lines.append(text[start:best].strip())
        start = best + 1
    lines.append(text[start:].strip())
    return [ln for ln in lines if ln]


def _time_at(units: list[tuple[str, float, float]], pos: int, side: str) -> float:
    """덩어리 글의 pos번째 글자 위치의 시각(쉼 단위 시각 사이는 읽는 길이로 나눔). side: 줄 시작이면 'start'."""
    off = 0
    for i, (txt, a, b) in enumerate(units):
        lo, hi = off, off + len(txt)
        if side == "start" and pos <= lo:
            return a
        if lo <= pos <= hi:
            if side == "end" and pos == lo and i > 0:
                return units[i - 1][2]
            if pos >= hi:
                return b
            frac = spoken_len(txt[: pos - lo]) / (spoken_len(txt) or 1.0) if pos > lo else 0.0
            return a + (b - a) * min(1.0, frac)
        off = hi + 1  # 단위 사이 띄어쓰기
    return units[-1][2]


def subtitle_cues(
    cues: list[Cue],
    limit: int = SUB_LINE,
    gap: float = 0.05,
    info: AudioInfo | None = None,
    hold: float = 0.6,
) -> list[Cue]:
    """장면용 큐(말 덩어리) → 자막용 큐(한 줄 limit자 안팎). align이 쉼에 맞춘 쉼 단위 시각(units)이 있으면
    그 시각으로 줄을 나누고(쉼표 숨에서 정확히 바뀜), 없으면 읽는 길이 비율과 그 안의 쉼으로 나눈다.
    다음 자막까지 틈이 hold초보다 짧으면 이어서 띄운다."""
    sil = sorted(info.silences) if info else []
    speech_before, absolute = _speech_clock(info.duration if info else (cues[-1].end if cues else 0.0), sil)
    out: list[Cue] = []
    for c in cues:
        lines = subtitle_lines(c.text, limit) or [c.text]
        joined = " ".join(t for t, _a, _b in c.units)
        if c.units and joined == " ".join(lines):
            spans, pos = [], 0
            for ln in lines:
                spans.append((_time_at(c.units, pos, "start"), _time_at(c.units, pos + len(ln), "end")))
                pos += len(ln) + 1
        else:
            spans = _split_span(c.start, c.end, [spoken_len(x) for x in lines], sil, speech_before, absolute)
        for ln, (a, b) in zip(lines, spans, strict=True):
            end = min(b, c.end) if b >= c.end else b - gap
            out.append(Cue(index=len(out) + 1, start=round(a, 3), end=round(max(a + 0.3, end), 3), text=ln))
    for x, y in zip(out, out[1:], strict=False):  # 짧은 틈은 이어 띄우고, 겹치면 앞 자막을 줄인다
        if x.end <= y.start <= x.end + hold or x.end > y.start:
            x.end = round(max(x.start + 0.2, y.start - 0.02), 3)
    return out


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


_HANGUL_RE = re.compile(r"[가-힣]")
_DIGIT_RE = re.compile(r"\d")
_UPPER_RE = re.compile(r"[A-Z]")
_LOWER_RE = re.compile(r"[a-z]")


def spoken_len(text: str) -> float:
    """읽는 데 걸리는 길이(음절 수 어림). 한글 1, 숫자 한 자리 1.3('11일'→'십일일'), 대문자 약어 한 글자 1.6
    ('SNS'→'에스엔에스'), 영어 소문자 0.5, 쉼표·마침표 0.4(짧은 숨), 띄어쓰기·따옴표 0."""
    n = float(len(_HANGUL_RE.findall(text)))
    n += 1.3 * len(_DIGIT_RE.findall(text)) + 1.6 * len(_UPPER_RE.findall(text))
    n += 0.5 * len(_LOWER_RE.findall(text)) + 0.4 * sum(text.count(ch) for ch in ",.?!…")
    return max(n, 0.5)


def _split_span(s, e, weights, sil, speech_before, absolute):  # noqa: ANN001, ANN202
    """구간 [s, e]를 읽는 길이 비율로 나누고, 나누는 곳 근처에 짧은 쉼(쉼표 숨)이 있으면 그 쉼에 맞춘다.
    반환: 조각마다 (시작, 끝)."""
    if len(weights) <= 1:
        return [(s, e)]
    sp0, sp1 = speech_before(s), speech_before(e)
    total = sum(weights) or 1.0
    inner = [(a, b) for a, b in sil if a > s + 0.05 and b < e - 0.05]
    tol = max(0.35, 0.4 * (sp1 - sp0) / len(weights))
    bounds: list[tuple[float, float]] = []
    acc = 0.0
    for w in weights[:-1]:
        acc += w
        want = sp0 + (sp1 - sp0) * acc / total
        floor = bounds[-1][1] if bounds else s
        best = min(
            ((abs(speech_before((a + b) / 2) - want), a, b) for a, b in inner if a >= floor),
            default=None,
        )
        if best and best[0] <= tol:
            bounds.append((best[1], best[2]))
        else:
            t = max(absolute(want), floor)
            bounds.append((t, t))
    starts = [s, *[b for _a, b in bounds]]
    ends = [*[a for a, _b in bounds], e]
    return list(zip(starts, ends, strict=True))


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


_CLOSERS = "'\"”’」』)]"


def units_of(phrase: str) -> list[tuple[str, str]]:
    """말 덩어리 → 쉼 단위(쉼표·문장 끝마다): (글, 끝 종류 'sent'|'comma'|'none'). TTS는 여기서 숨을 쉰다."""
    out: list[tuple[str, str]] = []
    start, i, n = 0, 0, len(phrase)
    while i < n:
        ch = phrase[i]
        if ch == "," and (i + 1 == n or phrase[i + 1] == " "):
            out.append((phrase[start : i + 1].strip(), "comma"))
            start = i + 1
        elif ch in ".?!…":
            j = i + 1
            while j < n and phrase[j] in _CLOSERS:
                j += 1
            if j == n or phrase[j] == " ":
                out.append((phrase[start:j].strip(), "sent"))
                start, i = j, j - 1
        i += 1
    if phrase[start:].strip():
        out.append((phrase[start:].strip(), "none"))
    return [u for u in out if u[0]] or [(phrase.strip(), "none")]


def _dp_match(expected, kinds, cands, miss_cost, match_pen, skip_cost):  # noqa: ANN001, ANN202
    """경계(예상 말소리 시각·종류)를 쉼 후보(말소리 시각, 시작, 끝)에 순서대로 맞춘다(동적 계획법).
    반환: 경계마다 (쉼 시작, 쉼 끝) — 못 맞추면 None."""
    K, J = len(expected), len(cands)
    inf = float("inf")
    best = [[inf] * (J + 1) for _ in range(K + 1)]
    back: list[list[tuple[int, int, int] | None]] = [[None] * (J + 1) for _ in range(K + 1)]
    best[0][0] = 0.0
    for j in range(1, J + 1):  # 앞쪽 쉼 건너뛰기
        best[0][j] = best[0][j - 1] + skip_cost(cands[j - 1])
        back[0][j] = (0, j - 1, -2)
    for k in range(1, K + 1):
        e, kind = expected[k - 1], kinds[k - 1]
        for j in range(J + 1):
            c = best[k - 1][j] + miss_cost(kind)  # 경계 k: 쉼 없이 예상 시각
            if c < best[k][j]:
                best[k][j], back[k][j] = c, (k - 1, j, -1)
            if j >= 1:
                sp, a, b = cands[j - 1]
                c = best[k - 1][j - 1] + abs(sp - e) + match_pen(kind, b - a)  # 경계 k = 쉼 j
                if c < best[k][j]:
                    best[k][j], back[k][j] = c, (k - 1, j - 1, j - 1)
                c = best[k][j - 1] + skip_cost(cands[j - 1])  # 쉼 j는 경계가 아님
                if c < best[k][j]:
                    best[k][j], back[k][j] = c, (k, j - 1, -2)
    out: list[tuple[float, float] | None] = [None] * K
    k, j = K, min(range(J + 1), key=lambda x: best[K][x] + sum(skip_cost(c) for c in cands[x:]))
    while k > 0 or j > 0:
        prev = back[k][j]
        if prev is None:
            break
        pk, pj, used = prev
        if used >= 0:
            out[k - 1] = (cands[used][1], cands[used][2])
        k, j = pk, pj
    return out


def align(sentences: list[list[str]], info: AudioInfo, gap: float = 0.05) -> tuple[list[Cue], int]:
    """문장(말 덩어리 목록) → 큐. 반환: (큐 목록, 쉼에 맞춘 문장 경계 수).

    대본을 쉼 단위(문장 끝·쉼표)로 나누고, 그 경계들을 음성의 쉼에 순서대로 맞춘다. 맞출 때는 '처음부터의 위치'가
    아니라 **앞에 맞춘 쉼에서 이번 쉼까지 걸린 시간**과 그 사이 글의 읽는 길이를 비교한다 — 말 빠르기가 구간마다
    달라도 오차가 뒤로 쌓이지 않아, 한 문장씩 밀리지 않는다. 문장 끝은 긴 쉼을, 쉼표는 짧은 숨을 좋아한다.
    맞추지 못한 경계는 앞뒤로 맞춘 쉼 사이를 읽는 길이로 나눈다. 큐마다 쉼 단위 시각(units)을 담는다."""
    if not sentences:
        return [], 0
    dur = info.duration
    sil = [(a, b) for a, b in sorted(info.silences) if b > a]
    head = sil[0][1] if sil and sil[0][0] <= 0.05 else 0.0  # 앞 무음
    tail = sil[-1][0] if sil and sil[-1][1] >= dur - 0.05 else dur  # 뒤 무음
    inner = [(a, b) for a, b in sil if a > head and b < tail]
    speech_before, absolute = _speech_clock(dur, sil)
    sp0, sp1 = speech_before(head), speech_before(tail)

    flat: list[tuple[int, int, str, str]] = []  # (문장, 덩어리, 글, 끝 종류)
    for si, sent in enumerate(sentences):
        for pi, ph in enumerate(sent):
            flat += [(si, pi, txt, kind) for txt, kind in units_of(ph)]
    n = len(flat)
    w = [spoken_len(t) for _s, _p, t, _k in flat]
    cum = [0.0]
    for x in w:
        cum.append(cum[-1] + x)
    total = cum[-1] or 1.0
    rate = (sp1 - sp0) / total  # 읽는 길이 1당 말소리 초
    # 경계 k(1..n-1) = 단위 k-1과 k 사이. 문장이 끝나는 경계는 'sent'
    kinds = [""] + ["sent" if flat[k - 1][0] != flat[k][0] else flat[k - 1][3] for k in range(1, n)]
    cands = [(speech_before((a + b) / 2), a, b) for a, b in inner]
    J = len(cands)
    miss_c = {"sent": 1.2, "comma": 0.5, "none": 0.15}
    skip = [0.15 + 1.2 * max(0.0, (b - a) - 0.35) for _sp, a, b in cands]
    skip_pre = [0.0]
    for x in skip:
        skip_pre.append(skip_pre[-1] + x)
    miss_pre = [0.0, 0.0]
    for k in range(1, n):
        miss_pre.append(miss_pre[-1] + miss_c.get(kinds[k], 0.5))

    def miss_sum(k1: int, k2: int) -> float:  # 경계 k1..k2-1을 못 맞춤
        return miss_pre[k2] - miss_pre[k1] if k2 > k1 else 0.0

    def tpen(kind: str, d: float) -> float:
        if kind == "sent":
            return 0.4 if d < 0.3 else 0.0
        if kind == "comma":
            return 0.2 if d > 0.7 else 0.0
        return 0.2

    WIN, KMAX, JMAX = 25.0, 10, 12
    # f[k]: {쉼 j(1..J) 또는 0(시작) → (비용, 이전 (k, j))}
    f: list[dict[int, tuple[float, tuple[int, int] | None]]] = [dict() for _ in range(n)]
    f[0][0] = (0.0, None)
    sp_of = lambda j: sp0 if j == 0 else cands[j - 1][0]  # noqa: E731
    for k in range(1, n):
        e = sp0 + rate * cum[k]
        for j in range(1, J + 1):
            if abs(cands[j - 1][0] - e) > WIN:
                continue
            d = cands[j - 1][2] - cands[j - 1][1]
            best: tuple[float, tuple[int, int] | None] = (float("inf"), None)
            for k2 in range(max(0, k - KMAX), k):
                for j2, (c2, _bk) in f[k2].items():
                    if j2 >= j or j - j2 > JMAX:
                        continue
                    seg = abs((cands[j - 1][0] - sp_of(j2)) - rate * (cum[k] - cum[k2]))
                    c = c2 + seg + miss_sum(k2 + 1, k) + (skip_pre[j - 1] - skip_pre[j2]) + tpen(kinds[k], d)
                    if c < best[0]:
                        best = (c, (k2, j2))
            if best[1] is not None:
                f[k][j] = best
    # 끝(뒤 무음)까지
    end_best: tuple[float, tuple[int, int] | None] = (float("inf"), None)
    for k2 in range(n):
        for j2, (c2, _bk) in f[k2].items():
            seg = abs((sp1 - sp_of(j2)) - rate * (total - cum[k2]))
            c = c2 + seg + miss_sum(k2 + 1, n) + (skip_pre[J] - skip_pre[j2])
            if c < end_best[0]:
                end_best = (c, (k2, j2))
    match: dict[int, int] = {}  # 경계 k → 쉼 j
    node = end_best[1]
    while node and node[0] > 0:
        k, j = node
        match[k] = j
        node = f[k][j][1]
    # 경계 시각: 맞춘 쉼은 (쉼 시작, 쉼 끝), 못 맞춘 경계는 앞뒤 맞춘 쉼 사이를 읽는 길이로 나눔
    anchors = (
        [(0, sp0)] + [(k, speech_before(cands[j - 1][1])) for k, j in sorted(match.items())] + [(n, sp1)]
    )
    bounds: list[tuple[float, float]] = [(head, head)] * (n + 1)
    for (ka, sa), (kb, sb) in zip(anchors, anchors[1:], strict=False):
        for k in range(ka + 1, kb):
            t = absolute(sa + (sb - sa) * (cum[k] - cum[ka]) / ((cum[kb] - cum[ka]) or 1.0))
            bounds[k] = (t, t)
    for k, j in match.items():
        bounds[k] = (cands[j - 1][1], cands[j - 1][2])
    starts = [head, *[bounds[k][1] for k in range(1, n)]]
    ends = [*[bounds[k][0] for k in range(1, n)], tail]
    matched = sum(1 for k in match if kinds[k] == "sent")
    cues: list[Cue] = []
    i = 0
    for si, sent in enumerate(sentences):
        for pi, ph in enumerate(sent):
            parts = []
            while i < n and flat[i][0] == si and flat[i][1] == pi:
                parts.append((flat[i][2], starts[i], max(starts[i] + 0.15, ends[i])))
                i += 1
            start, end = parts[0][1], parts[-1][2]
            cues.append(
                Cue(
                    index=len(cues) + 1,
                    start=round(start, 3),
                    end=round(max(start + 0.3, end - gap), 3),
                    text=ph,
                    units=[(t, round(x, 3), round(y, 3)) for t, x, y in parts],
                )
            )
    for a, b in zip(cues, cues[1:], strict=False):  # 겹침 정리
        if a.end > b.start:
            a.end = round(max(a.start + 0.2, b.start - 0.01), 3)
    return cues, matched


def make_srt(script: str, audio: Path) -> tuple[list[Cue], AudioInfo, int]:
    """대본 + 음성 → (큐, 오디오 정보, 쉼에 맞춘 경계 수)."""
    info = analyze(audio)
    cues, matched = align(script_sentences(script), info)
    return cues, info, matched
