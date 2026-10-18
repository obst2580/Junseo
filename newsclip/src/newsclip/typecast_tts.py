"""타입캐스트 API로 대본을 읽는다 — 음성(WAV)과 낱말·글자별 시각을 함께 받는다.

- 키: 환경 변수 TYPECAST_API_KEY(저장소·화면·로그에 남기지 않는다).
- 모델 ssfm-v30, 스마트 이모션(앞뒤 문장을 함께 넘겨 문맥에 맞게 읽기), 읽는 속도 audio_tempo(0.5~2.0),
  문장 사이 쉼 remove_silence_ms, 음량 target_lufs.
- 한 번에 2,000자까지라서 긴 대본은 문단·문장 단위로 나눠 읽고 이어 붙인다. 뒤 조각의 시각은 앞 조각 길이만큼 민다.
- 받은 시각은 그 음성에 맞춘 값이라 자막·장면 시간을 따로 짐작하지 않아도 된다.
"""

from __future__ import annotations

import base64
import io
import os
import re
import wave
from dataclasses import dataclass, field
from pathlib import Path

import httpx

from newsclip.autosub import Word

API = "https://api.typecast.ai"
MAX_CHARS = 2000
MODEL = "ssfm-v30"
USER_AGENT = "studio-newsclip/1"
_SENT_RE = re.compile(r"(?<=[.!?…])\s+")


class TypecastError(RuntimeError):
    """타입캐스트 요청 실패(키 없음·크레딧 부족·한도 초과 등). 메시지는 화면에 그대로 보여 줄 수 있게 쓴다."""


@dataclass
class Speech:
    """읽은 결과: 음성 파일, 길이, 낱말 시각, 글자 시각(공백 포함)."""

    path: Path
    duration: float
    words: list[Word] = field(default_factory=list)
    chars: list[Word] = field(default_factory=list)


def api_key() -> str:
    key = os.environ.get("TYPECAST_API_KEY", "").strip()
    if not key:
        raise TypecastError("TYPECAST_API_KEY가 없습니다(환경 설정에 넣은 뒤 새 세션에서 쓸 수 있습니다).")
    return key


def _client(client: httpx.Client | None) -> httpx.Client:
    return client or httpx.Client(base_url=API, timeout=120.0)


def _headers() -> dict[str, str]:
    return {"X-API-KEY": api_key(), "User-Agent": USER_AGENT, "Content-Type": "application/json"}


def _check(resp: httpx.Response) -> None:
    if resp.status_code == 200:
        return
    reasons = {
        400: "요청 값이 잘못됐습니다",
        401: "API 키가 없거나 틀렸습니다",
        402: "크레딧이 부족합니다",
        403: "이 키로는 쓸 수 없습니다(예전 Starter 키·휴면 계정·키 앞뒤 공백)",
        404: "목소리나 모델을 찾지 못했습니다",
        422: "요청 값 검사에 걸렸습니다",
        429: "요청이 너무 많습니다(동시 요청 한도)",
    }
    detail = resp.text[:300]
    if "UNUSUAL_ACTIVITY" in detail:
        # 무료 계정을 클라우드 서버에서 부르면 타입캐스트가 비정상 사용으로 막는다 — 다시 시도하면 계정이 정지될 수 있다
        raise TypecastError(
            "타입캐스트가 무료 계정의 비정상 사용으로 막았습니다(클라우드 서버에서 부른 요청). 다시 시도하지 말고, "
            "유료 API 플랜(Lite 이상)으로 바꾸거나 타입캐스트 웹에서 받은 음성 파일을 올려 주세요."
        )
    raise TypecastError(
        f"타입캐스트 {resp.status_code}: {reasons.get(resp.status_code, '서버 오류')} — {detail}"
    )


def find_voice(name: str, model: str = MODEL, client: httpx.Client | None = None) -> dict | None:
    """목소리 이름(한국어·영어 표시 이름)으로 찾는다. 반환: {voice_id, names, emotions} 또는 None."""
    c = _client(client)
    resp = c.get("/v3/voices", params={"model": model}, headers=_headers())
    _check(resp)
    data = resp.json()
    voices = data if isinstance(data, list) else data.get("voices") or data.get("data") or []
    target = name.strip()

    def names_of(v: dict) -> list[str]:
        vn = v.get("voice_name")
        return [str(x) for x in vn.values()] if isinstance(vn, dict) else [str(vn or "")]

    exact = [v for v in voices if target in names_of(v)]
    partial = [v for v in voices if any(target in n for n in names_of(v))]
    hit = (exact or partial or [None])[0]
    if hit is None:
        return None
    emotions = next((m.get("emotions", []) for m in hit.get("models", []) if m.get("version") == model), [])
    return {"voice_id": hit["voice_id"], "names": names_of(hit), "emotions": emotions}


def split_text(text: str, limit: int = MAX_CHARS) -> list[str]:
    """limit자 안쪽 조각으로 나눈다: 문단(빈 줄)을 먼저 묶고, 너무 긴 문단은 문장 끝에서 자른다."""
    pieces: list[str] = []
    for para in [p.strip() for p in text.split("\n\n") if p.strip()]:
        if len(para) <= limit:
            pieces.append(para)
            continue
        cur = ""
        for sent in _SENT_RE.split(para):
            if cur and len(cur) + 1 + len(sent) > limit:
                pieces.append(cur)
                cur = sent
            else:
                cur = f"{cur} {sent}".strip()
        if cur:
            pieces.append(cur)
    chunks: list[str] = []
    for p in pieces:
        if chunks and len(chunks[-1]) + 2 + len(p) <= limit:
            chunks[-1] = f"{chunks[-1]}\n\n{p}"
        else:
            chunks.append(p)
    return chunks


def _edge(text: str, last: bool) -> str:
    sents = [s for s in _SENT_RE.split(text.strip()) if s]
    return (sents[-1] if last else sents[0])[:300] if sents else ""


def synthesize(
    text: str,
    voice_id: str,
    out: Path,
    *,
    model: str = MODEL,
    tempo: float = 1.0,
    remove_silence_ms: int | None = None,
    target_lufs: float = -14.0,
    smart: bool = True,
    client: httpx.Client | None = None,
) -> Speech:
    """대본(읽는 글) → WAV + 낱말·글자 시각. 조각이 여러 개면 이어 붙이고 시각을 민다."""
    if not 0.5 <= tempo <= 2.0:
        raise TypecastError("읽는 속도(tempo)는 0.5~2.0 사이여야 합니다.")
    c = _client(client)
    chunks = split_text(text)
    frames: list[bytes] = []
    params: tuple | None = None
    words: list[Word] = []
    chars: list[Word] = []
    offset = 0.0
    for i, chunk in enumerate(chunks):
        output: dict = {"audio_format": "wav", "audio_tempo": tempo, "target_lufs": target_lufs}
        if remove_silence_ms is not None:
            output["remove_silence_ms"] = remove_silence_ms
        body: dict = {
            "model": model,
            "voice_id": voice_id,
            "text": chunk,
            "language": "kor",
            "output": output,
        }
        if smart:
            prompt: dict = {"emotion_type": "smart"}
            if i > 0:
                prompt["previous_text"] = _edge(chunks[i - 1], last=True)
            if i + 1 < len(chunks):
                prompt["next_text"] = _edge(chunks[i + 1], last=False)
            body["prompt"] = prompt
        resp = c.post("/v1/text-to-speech/with-timestamps", json=body, headers=_headers())
        _check(resp)
        data = resp.json()
        with wave.open(io.BytesIO(base64.b64decode(data["audio"])), "rb") as w:
            if params is None:
                params = w.getparams()
            frames.append(w.readframes(w.getnframes()))
            dur = w.getnframes() / float(w.getframerate())
        words += [
            (s["text"], round(s["start"] + offset, 3), round(s["end"] + offset, 3))
            for s in data.get("words") or []
        ]
        chars += [
            (s["text"], round(s["start"] + offset, 3), round(s["end"] + offset, 3))
            for s in data.get("characters") or []
        ]
        offset += dur
    out.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(out), "wb") as w:
        assert params is not None
        w.setparams(params)
        for fr in frames:
            w.writeframes(fr)
    return Speech(path=out, duration=round(offset, 3), words=words, chars=chars)


def retime_scenes(scenes: list[dict], chars: list[Word], duration: float) -> list[dict]:
    """장면(대사 글)을 새 음성의 글자 시각에 맞춰 시작·끝을 다시 정한다. 공백을 빼고 대사 앞부분으로 찾는다.
    못 찾은 장면은 앞뒤 장면 사이를 고르게 나눈다."""
    stream = [(ch, s) for ch, s, _e in chars if ch.strip()]
    joined = "".join(ch for ch, _s in stream)
    starts: list[float | None] = []
    pos = 0
    for sc in scenes:
        key = re.sub(r"\s+", "", str(sc.get("text", "")))[:12]
        idx = joined.find(key, pos) if key else -1
        if idx < 0:
            starts.append(None)
            continue
        starts.append(stream[idx][1])
        pos = idx + len(key)
    if starts and starts[0] is None:
        starts[0] = 0.0
    # 못 찾은 장면: 앞뒤로 찾은 시각 사이를 고르게
    k = 0
    while k < len(starts):
        if starts[k] is not None:
            k += 1
            continue
        j = k
        while j < len(starts) and starts[j] is None:
            j += 1
        a = starts[k - 1] if k > 0 else 0.0
        b = starts[j] if j < len(starts) else duration
        for n, idx in enumerate(range(k, j), start=1):
            starts[idx] = a + (b - a) * n / (j - k + 1)
        k = j
    out = []
    for n, sc in enumerate(scenes):
        start = 0.0 if n == 0 else float(starts[n])
        end = float(starts[n + 1]) if n + 1 < len(scenes) else duration
        out.append({**sc, "start": round(start, 3), "end": round(max(end, start), 3)})
    return out
