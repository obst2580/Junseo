"""음성 읽기 검수: 만든 음성(타입캐스트)을 음성 인식으로 다시 듣고 대본과 대조한다.

타입캐스트가 준 낱말 시각(대본 낱말 그대로)과 들린 말(faster-whisper)을 같은 시각끼리 견줘서
- 숫자 사이 기호를 엉뚱하게 읽은 곳('형사항소2-3부' → '2대3부'),
- 숫자가 바뀌거나 빠진 곳('100만' → '10만'),
- 문장을 건너뛰거나 두 번 읽은 곳(들린 글자가 대본보다 훨씬 적거나 많음)
을 찾는다. 인식은 이름·낱말을 자주 틀리게 받아 적으므로(손솔 → 손소) 이름·낱말 철자는 견주지 않는다.
"""

from __future__ import annotations

import re

_SEP_RE = re.compile(r"\d\s*([-~/:])\s*\d")
_DIGITS = re.compile(r"\d+")
_HANGUL = re.compile(r"[가-힣]")
SENT_LOW, SENT_HIGH = 0.55, 1.7  # 문장마다 들린 글자 수 ÷ 대본 글자 수가 이 밖이면 건너뜀·되풀이 의심


def _heard(asr: list, a: float, b: float, pad: float = 0.12) -> str:
    """a~b초에 들린 말(겹치는 인식 낱말을 이어 붙임)."""
    return " ".join(str(w[0]) for w in asr if float(w[2]) > a - pad and float(w[1]) < b + pad)


def number_issues(tc_words: list, asr: list) -> list[dict]:
    """숫자가 든 대본 낱말마다 그때 들린 말과 견준다."""
    out: list[dict] = []
    for w, s, e in tc_words:
        w = str(w)
        if not _DIGITS.search(w):
            continue
        heard = _heard(asr, float(s), float(e))
        got = "".join(_DIGITS.findall(heard))
        want = "".join(_DIGITS.findall(w))
        sep = _SEP_RE.search(w)
        if sep and re.search(r"\d\s*[가-힣]+\s*\d", heard) and sep.group(1) in "-/~":
            # '2-3'을 '2대3'처럼 기호를 말로 바꿔 읽음(~는 '에서'·'부터'로 읽으면 괜찮다)
            between = re.search(r"\d\s*([가-힣]+)\s*\d", heard).group(1)
            if not (sep.group(1) == "~" and between in ("에서", "부터", "내지")):
                out.append({"kind": "reading_symbol", "at": float(s), "word": w, "heard": heard.strip()})
                continue
        # '20일'(이십 일)은 '이십일'과 소리가 같아 인식이 21로 받아 적는다 — 0으로 끝나는 날짜는 +1도 맞게 친다
        same_sound = bool(
            w.endswith(("일", "일은", "일에", "일부터", "일까지"))
            and want.endswith("0")
            and got == want[:-1] + "1"
        )
        if got and want and got != want and want not in got and not same_sound:
            out.append({"kind": "reading_number", "at": float(s), "word": w, "heard": heard.strip()})
    return out


def sentence_issues(tc_words: list, asr: list) -> list[dict]:
    """문장마다(문장 끝 낱말까지) 들린 글자 수를 대본 글자 수와 견준다 — 건너뜀·되풀이 의심."""
    out: list[dict] = []
    sent: list = []
    for item in tc_words:
        sent.append(item)
        if not re.search(r"[.?!…][\"'”’]*$", str(item[0])):
            continue
        text = " ".join(str(x[0]) for x in sent)
        want = len(_HANGUL.findall(text))
        if want >= 12:
            heard = _heard(asr, float(sent[0][1]), float(sent[-1][2]), pad=0.05)
            got = len(_HANGUL.findall(heard)) + 2 * len(
                _DIGITS.findall(heard)
            )  # 숫자는 받아 적으면 글자가 줄어든다
            ratio = got / want
            if ratio < SENT_LOW or ratio > SENT_HIGH:
                kind = "reading_skip" if ratio < SENT_LOW else "reading_repeat"
                out.append({"kind": kind, "at": float(sent[0][1]), "word": text[:40], "heard": heard[:60]})
        sent = []
    return out


def check_reading(tc_words: list, asr: list) -> list[dict]:
    return sorted(number_issues(tc_words, asr) + sentence_issues(tc_words, asr), key=lambda x: x["at"])


def confirm(audio, items: list[dict], tc_words: list) -> list[dict]:  # noqa: ANN001 - Path
    """걸린 곳만 앞뒤 0.6초를 잘라 정밀하게(beam 5) 다시 듣고, 그래도 다르게 들리는 것만 남긴다(인식이 숫자를
    잘못 받아 적는 일이 있다 — '이십 일'을 21일로)."""
    import os

    from newsclip.asr import _pcm16k

    if not items:
        return []
    try:
        from faster_whisper import WhisperModel
    except ImportError:
        return items
    pcm = _pcm16k(audio)
    wm = WhisperModel("small", device="cpu", compute_type="int8", cpu_threads=os.cpu_count() or 4)
    kept = []
    for it in items:
        if it["kind"] not in ("reading_symbol", "reading_number"):
            kept.append(it)
            continue
        w = next((x for x in tc_words if float(x[1]) == it["at"]), None)
        if w is None:
            continue
        a, b = max(0.0, float(w[1]) - 0.6), float(w[2]) + 0.6
        segs, _ = wm.transcribe(pcm[int(a * 16000) : int(b * 16000)], language="ko", beam_size=5)
        heard = " ".join(s.text for s in segs).strip()
        again = number_issues([w], [(heard, float(w[1]), float(w[2]))])
        if again:
            kept.append({**it, "heard": heard})
    return kept
