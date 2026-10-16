"""대본 + AI 음성(타입캐스트 웹 등에서 받은 파일) → 자막(SRT) 자동 생성.

대본 글자는 이미 알고 있으므로 자막 글은 대본 그대로 쓰고, **시각만** 음성에서 찾는다.
1. 대본 → 말 덩어리 → 쉼 단위(문장 끝·쉼표).
2. 음성의 조용한 구간(쉼)을 찾는다(화면은 브라우저에서, 여기서는 ffmpeg silencedetect).
3. 음성 인식(asr.py, faster-whisper) 결과가 있으면 대본 글자와 인식한 글자를 맞대어 경계마다 '들린 시각'을 잡는다.
   인식 시각은 0.3~1초쯤 흔들리므로, 그 근처의 실제 쉼에 붙여 정확한 시각을 쓴다.
4. 인식 결과가 없으면 쉼 사이 걸린 시간과 대본 읽는 길이를 비교해 경계를 쉼에 맞춘다(동적 계획법).
"""

from __future__ import annotations

import bisect
import difflib
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
    lines = _split_lines(text, k)
    for more in (1, 2):  # 띄어쓰기 자리 탓에 한 줄이 너무 길어지면(limit+4자 넘게) 한 줄 더 나눈다
        if max(len(ln) for ln in lines) <= limit + 4:
            break
        lines = _split_lines(text, k + more)
    return lines


def _split_lines(text: str, k: int) -> list[str]:
    """text를 띄어쓰기에서 k줄 안팎으로 고르게 나눈다(subtitle_lines의 한 번 나누기)."""
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
    words: list[Word] | None = None,
) -> list[Cue]:
    """장면용 큐(말 덩어리) → 자막용 큐(한 줄 limit자 안팎). align이 쉼에 맞춘 쉼 단위 시각(units)이 있으면
    그 시각으로 줄을 나누고(쉼표 숨에서 정확히 바뀜), 없으면 읽는 길이 비율과 그 안의 쉼으로 나눈다.
    words(음성 인식 결과)가 있으면 쉼 단위 한가운데서 바뀌는 줄도 그 줄 첫 말이 들린 시각(근처에 쉼이 있으면
    그 쉼)에 맞춘다. 다음 자막까지 틈이 hold초보다 짧으면 이어서 띄운다."""
    sil = sorted(info.silences) if info else []
    speech_before, absolute = _speech_clock(info.duration if info else (cues[-1].end if cues else 0.0), sil)
    rows: list[
        tuple[str, float, float, float, bool]
    ] = []  # (줄, 시작, 끝, 덩어리 끝, 쉼 단위 가운데서 시작?)
    for c in cues:
        lines = subtitle_lines(c.text, limit) or [c.text]
        joined = " ".join(t for t, _a, _b in c.units)
        if c.units and joined == " ".join(lines):
            spans, mids, pos = [], [], 0
            starts = set()
            off = 0
            for t, _a, _b in c.units:
                starts.add(off)
                off += len(t) + 1
            for ln in lines:
                spans.append((_time_at(c.units, pos, "start"), _time_at(c.units, pos + len(ln), "end")))
                mids.append(pos not in starts)
                pos += len(ln) + 1
        else:
            spans = _split_span(c.start, c.end, [spoken_len(x) for x in lines], sil, speech_before, absolute)
            mids = [False] + [True] * (len(lines) - 1)
        rows += [(ln, a, b, c.end, m) for ln, (a, b), m in zip(lines, spans, mids, strict=True)]
    if words and len(rows) > 1:
        heard = asr_boundaries([r[0] for r in rows], words, [(r[1], r[2]) for r in rows])
        for i in range(1, len(rows)):
            hb, (ln, a, b, ce, mid), prev = heard[i], rows[i], rows[i - 1]
            if not mid or hb is None:
                continue
            p = _near_pause(hb, sil)
            s0, e0 = (p[1], p[0]) if p else (hb[0], hb[0])
            if prev[1] + 0.3 < s0 < b - 0.3:  # 앞 줄 시작과 이 줄 끝 사이일 때만
                rows[i - 1] = (prev[0], prev[1], e0, prev[3], prev[4])
                rows[i] = (ln, s0, b, ce, mid)
    out: list[Cue] = []
    for ln, a, b, ce, _m in rows:
        end = min(b, ce) if b >= ce else b - gap
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


_ASR_CHAR_RE = re.compile(r"[가-힣0-9A-Za-z]")
Word = tuple[str, float, float]  # (인식한 말, 시작, 끝)


def _chars(text: str) -> str:
    return "".join(_ASR_CHAR_RE.findall(text)).lower()


# 인식기(whisper)의 단어 경계는 쉼 근처에서 흔들린다: 실제 쉼보다 0.3~0.8초 이르게(앞 단어 끝을 일찍 끊고 쉼을
# 뒷 단어에 붙임), 가끔은 0.4초쯤 늦게 잡는다. 그래서 들린 시각 h에서 [쉼 시작-EARLY, 쉼 끝+LATE] 안의 쉼은 그
# 경계의 쉼으로 본다.
# 다만 짧은 쉼은 단어 안의 받침·된소리 닫힘('뭉|친')이거나 경계 다음 단어 뒤의 숨일 수도 있어서, 쉼 앞뒤로 두
# 단어가 말할 시간이 남는 쉼만 경계로 본다(asr_boundaries가 주는 lo·hi).
ASR_EARLY, ASR_LATE = 0.8, 0.5
Heard = tuple[
    float, float, float
]  # (들린 시각, 쉼이 시작할 수 있는 가장 이른 때, 쉼이 끝날 수 있는 가장 늦은 때)


def _pause_gap(hb: Heard, a: float, b: float) -> float:
    """들린 경계 hb에서 쉼 (a, b)까지의 거리(인식기 흔들림 창 안이면 0). 두 단어 시간이 안 남으면 아주 멂(9)."""
    h, lo, hi = hb
    if a < lo or b > hi:
        return 9.0
    return max(0.0, (a - ASR_EARLY) - h, h - (b + ASR_LATE))


def _near_pause(hb: Heard, silences: list[tuple[float, float]]) -> tuple[float, float] | None:
    """들린 경계 hb의 쉼으로 볼 것(창 안에서 가장 가까운 것) 또는 None."""
    h = hb[0]
    best = None
    for a, b in silences:
        if _pause_gap(hb, a, b) == 0.0:
            d = 0.0 if a <= h <= b else min(abs(h - a), abs(h - b))
            if best is None or d < best[0]:
                best = (d, a, b)
    return (best[1], best[2]) if best else None


def _band_match(S: list[str], ts: list[float], A: list[str], ta: list[float], win: float) -> list[int]:
    """대본 글자 S(어림 시각 ts)와 인식 글자 A(들린 시각 ta)를 순서대로 맞댄다(편집 거리 동적 계획법).
    대본 글자 i는 들린 시각이 ts[i]±win 안인 인식 글자하고만 맞댈 수 있다 — 같은 말이 되풀이돼도 엉뚱한
    회차에 붙지 않는다. 반환: 대본 글자마다 맞은(같은 글자) 인식 글자 번호, 없으면 -1."""
    n = len(S)
    neg = float("-inf")
    lo = [bisect.bisect_left(ta, ts[i] - win) for i in range(n)]
    hi = [max(lo[i], bisect.bisect_right(ta, ts[i] + win)) for i in range(n)]
    # D[i][j - base[i]] = S[:i]와 A[:j]를 맞댄 최고 점수(같은 글자 +2, 다른 글자 -1, 한쪽 건너뛰기 -1)
    top = [hi[0]] + hi
    base = [0] + [
        min(max(0, lo[i] - 1), top[i]) for i in range(n)
    ]  # 앞 줄과 겹치게(인식이 빈 구간에서도 이어지게)
    rows: list[list[float]] = [[-float(j) for j in range(top[0] + 1)]]
    back: list[list[int]] = [[2] * (top[0] + 1)]  # 0 대각, 1 위(대본 건너뜀), 2 왼쪽(인식 건너뜀)
    for i in range(1, n + 1):
        b0, t = base[i], top[i]
        pb, pt, prow = base[i - 1], top[i - 1], rows[i - 1]
        row = [neg] * (t - b0 + 1)
        bk = [1] * (t - b0 + 1)
        c = S[i - 1]
        for j in range(b0, t + 1):
            best, how = neg, 1
            if pb <= j <= pt and prow[j - pb] > neg:  # 위
                best = prow[j - pb] - 1
            if lo[i - 1] < j <= hi[i - 1] and pb <= j - 1 <= pt and prow[j - 1 - pb] > neg:  # 대각(맞댐)
                v = prow[j - 1 - pb] + (2 if A[j - 1] == c else -1)
                if v > best:
                    best, how = v, 0
            if j > b0 and row[j - 1 - b0] - 1 > best:  # 왼쪽
                best, how = row[j - 1 - b0] - 1, 2
            row[j - b0], bk[j - b0] = best, how
        rows.append(row)
        back.append(bk)
    m = [-1] * n
    i, j = n, max(range(base[n], top[n] + 1), key=lambda x: rows[n][x - base[n]])
    while i > 0 and j >= 0:
        how = back[i][j - base[i]]
        if how == 0:
            if A[j - 1] == S[i - 1]:
                m[i - 1] = j - 1
            i, j = i - 1, j - 1
        elif how == 1:
            i -= 1
        else:
            j -= 1
    return m


def asr_boundaries(
    texts: list[str],
    words: list[Word],
    approx: list[tuple[float, float]] | None = None,
    reach: int = 6,
    win: float = 10.0,
) -> list[Heard | None]:
    """대본 쉼 단위 글(texts) 사이 경계 k(1..n-1)마다 음성 인식이 들은 경계(Heard, 없으면 None). 길이 n+1.

    대본 글자와 인식한 글자를 맞대어 경계 앞뒤 글자가 들린 시각을 찾는다. approx(쉼 단위마다 쉼만으로 어림한
    (시작, 끝))가 있으면 그 시각 ±win초 안에서만 맞대고(_band_match), 없으면 difflib으로 전체를 맞댄다.
    경계가 두 단어 사이면 앞 단어 끝과 뒷 단어 시작의 가운데, 한 단어 안(띄어쓰기 차이)이면 그 글자 자리.
    경계 앞뒤 reach 글자 안에 맞는 글자가 없으면(인식이 틀린 곳) None.
    lo·hi: 경계 앞 단어 시작 + 그 글자 수만큼 말할 최소 시간, 뒷 단어 끝 - 그 최소 시간(글자당 평균 시간의 절반)."""
    S: list[str] = []
    ts: list[float] = []
    pos: list[int] = []
    for k, t in enumerate(texts):
        pos.append(len(S))
        cs = _chars(t)
        S.extend(cs)
        if approx:
            a, b = approx[k]
            ts.extend(a + (b - a) * (i + 0.5) / len(cs) for i in range(len(cs)))
    A: list[str] = []
    at: list[tuple[float, float, int]] = []  # 인식 글자마다 (시작, 끝, 단어 번호)
    for wi, (w, ws, we) in enumerate(words):
        cs = _chars(w)
        for i, c in enumerate(cs):
            A.append(c)
            at.append((ws + (we - ws) * i / len(cs), ws + (we - ws) * (i + 1) / len(cs), wi))
    if approx:
        ta = [x[0] for x in at]
        for i in range(1, len(ta)):  # 시각이 거꾸로 가지 않게(이분 탐색용)
            ta[i] = max(ta[i], ta[i - 1])
        m = _band_match(S, ts, A, ta, win)
    else:
        m = [-1] * len(S)
        for a, b, size in difflib.SequenceMatcher(None, S, A, autojunk=False).get_matching_blocks():
            for i in range(size):
                m[a + i] = b + i
    first: dict[int, int] = {}  # 단어 번호 → 그 단어 첫 인식 글자 번호
    for j, (_s, _e, wi) in enumerate(at):
        first.setdefault(wi, j)
    span = sum(we - ws for _w, ws, we in words)
    per = 0.5 * span / (len(A) or 1)  # 글자 하나 말할 최소 시간(평균의 절반)
    out: list[Heard | None] = [None] * (len(texts) + 1)
    for k in range(1, len(texts)):
        p = pos[k]
        left = next((i for i in range(p - 1, max(-1, p - 1 - reach), -1) if m[i] >= 0), None)
        right = next((i for i in range(p, min(len(S), p + reach)) if m[i] >= 0), None)
        if left is None and right is None:
            continue
        if left is None or right is None:
            h = at[m[right]][0] if left is None else at[m[left]][1]
            out[k] = (h, h - 2.0, h + 2.0)
            continue
        li, ri = m[left], m[right]
        wl, wr = at[li][2], at[ri][2]
        if wr != wl:  # 두 단어 사이
            h = (words[wl][2] + words[wr][1]) / 2
            nl, nr = len(_chars(words[wl][0])), len(_chars(words[wr][0]))
            out[k] = (h, words[wl][1] + per * nl, words[wr][2] - per * nr)
        else:  # 한 단어 안(띄어쓰기 차이)
            nb = li - first[wl] + 1
            na = len(_chars(words[wl][0])) - nb
            out[k] = (at[li][1], words[wl][1] + per * nb, words[wl][2] - per * na)
    return out


def align(
    sentences: list[list[str]], info: AudioInfo, gap: float = 0.05, words: list[Word] | None = None
) -> tuple[list[Cue], int]:
    """문장(말 덩어리 목록) → 큐. 반환: (큐 목록, 쉼에 맞춘 문장 경계 수).

    대본을 쉼 단위(문장 끝·쉼표)로 나누고, 그 경계들을 음성의 쉼에 순서대로 맞춘다. 맞출 때는 '처음부터의 위치'가
    아니라 **앞에 맞춘 쉼에서 이번 쉼까지 걸린 시간**과 그 사이 글의 읽는 길이를 비교한다 — 말 빠르기가 구간마다
    달라도 오차가 뒤로 쌓이지 않아, 한 문장씩 밀리지 않는다. 문장 끝은 긴 쉼을, 쉼표는 짧은 숨을 좋아한다.
    맞추지 못한 경계는 앞뒤로 맞춘 쉼 사이를 읽는 길이로 나눈다. 큐마다 쉼 단위 시각(units)을 담는다.

    words(음성 인식 결과)가 있으면 경계마다 '들린 시각'에서 먼 쉼은 거의 고르지 않고, 들린 시각이 쉼 안이면
    그 쉼을 놓치는 값도 크게 둔다 — 말 빠르기 어림보다 실제로 들린 말을 믿는다. 쉼에 못 맞춘 경계도
    들린 시각을 쓴다."""
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
    heard: list[Heard | None] = [None] * (n + 1)
    if (
        words
    ):  # 쉼만으로 먼저 어림한 시각 근처에서 인식 결과를 맞댄다(같은 말이 되풀이돼도 엉뚱한 곳에 안 붙게)
        rough, _m = align(sentences, info, gap)
        approx = [(x, y) for c in rough for _t, x, y in c.units]
        if len(approx) == n:
            heard = asr_boundaries([t for _s, _p, t, _k in flat], words, approx)

    def far(k: int, j: int) -> float:  # 경계 k의 들린 시각에서 쉼 j까지 거리(_near_pause 창 안이면 0)
        hb = heard[k]
        return 0.0 if hb is None else _pause_gap(hb, cands[j - 1][1], cands[j - 1][2])

    miss_c = {"sent": 1.2, "comma": 0.5, "none": 0.15}
    skip = [0.15 + 1.2 * max(0.0, (b - a) - 0.35) for _sp, a, b in cands]
    skip_pre = [0.0]
    for x in skip:
        skip_pre.append(skip_pre[-1] + x)
    on_pause = [heard[k] is not None and any(far(k, j) <= 0.1 for j in range(1, J + 1)) for k in range(n)]
    miss_pre = [0.0, 0.0]
    for k in range(1, n):
        miss_pre.append(miss_pre[-1] + miss_c.get(kinds[k], 0.5) + (2.0 if on_pause[k] else 0.0))

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
            if (far(k, j) > 2.5) if heard[k] is not None else (abs(cands[j - 1][0] - e) > WIN):
                continue
            d = cands[j - 1][2] - cands[j - 1][1]
            asr = 4.0 * max(0.0, far(k, j) - 0.1)
            best: tuple[float, tuple[int, int] | None] = (float("inf"), None)
            for k2 in range(max(0, k - KMAX), k):
                for j2, (c2, _bk) in f[k2].items():
                    if j2 >= j or j - j2 > JMAX:
                        continue
                    seg = abs((cands[j - 1][0] - sp_of(j2)) - rate * (cum[k] - cum[k2]))
                    c = (
                        c2
                        + seg
                        + miss_sum(k2 + 1, k)
                        + (skip_pre[j - 1] - skip_pre[j2])
                        + tpen(kinds[k], d)
                        + asr
                    )
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
        lo, hi = absolute(sa), absolute(sb)
        prev = lo
        for k in range(ka + 1, kb):
            t = absolute(sa + (sb - sa) * (cum[k] - cum[ka]) / ((cum[kb] - cum[ka]) or 1.0))
            hb = heard[k]
            if hb is not None and lo < hb[0] < hi:
                t = hb[0]
            t = min(max(t, prev + 0.05), hi - 0.05) if hi - prev > 0.1 else t
            bounds[k] = (t, t)
            prev = t
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
