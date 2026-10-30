"""타입캐스트 API 연결: 목소리 찾기, 긴 대본 나누기, 음성·시각 받기(가짜 서버), 장면 시간 다시 맞추기."""

import base64
import io
import json
import wave

import httpx
import pytest

from newsclip.typecast_tts import (
    MAX_CHARS,
    TypecastError,
    api_key,
    find_voice,
    retime_scenes,
    split_text,
    synthesize,
)


def _wav_b64(seconds: float) -> str:
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(44100)
        w.writeframes(b"\x00\x00" * int(44100 * seconds))
    return base64.b64encode(buf.getvalue()).decode()


def _fake_server(seen: list):
    def handler(req: httpx.Request) -> httpx.Response:
        assert req.headers["X-API-KEY"] == "test-key"
        if req.url.path == "/v3/voices":
            return httpx.Response(
                200,
                json=[
                    {
                        "voice_id": "tc_other",
                        "voice_name": {"kor": "다른 목소리"},
                        "voice_type": "premade",
                        "models": [],
                    },
                    {
                        "voice_id": "tc_gyeom",
                        "voice_name": {"kor": "이겸", "eng": "Lee Gyeom"},
                        "voice_type": "premade",
                        "models": [{"version": "ssfm-v30", "emotions": ["normal", "happy", "angry"]}],
                    },
                ],
            )
        body = json.loads(req.content)
        seen.append(body)
        text = body["text"]
        return httpx.Response(
            200,
            json={
                "audio": _wav_b64(2.0),
                "audio_format": "wav",
                "audio_duration": 2.0,
                "words": [{"text": text.split()[0], "start": 0.1, "end": 0.6}],
                "characters": [
                    {"text": ch, "start": 0.05 * i, "end": 0.05 * (i + 1)} for i, ch in enumerate(text[:20])
                ],
            },
        )

    return httpx.Client(base_url="https://api.typecast.ai", transport=httpx.MockTransport(handler))


def test_api_key_required(monkeypatch):
    monkeypatch.delenv("TYPECAST_API_KEY", raising=False)
    with pytest.raises(TypecastError):
        api_key()


def test_find_voice_by_korean_name(monkeypatch):
    monkeypatch.setenv("TYPECAST_API_KEY", "test-key")
    hit = find_voice("이겸", client=_fake_server([]))
    assert hit == {
        "voice_id": "tc_gyeom",
        "names": ["이겸", "Lee Gyeom"],
        "emotions": ["normal", "happy", "angry"],
    }
    assert find_voice("없는 이름", client=_fake_server([])) is None


def test_split_text_keeps_chunks_under_limit():
    para = "가나다라마바사. " * 150  # 약 1,500자 문단
    chunks = split_text(f"{para}\n\n{para}\n\n짧은 문단.")
    assert len(chunks) >= 2 and all(len(c) <= MAX_CHARS for c in chunks)
    assert split_text("한 문단만 있습니다.") == ["한 문단만 있습니다."]


def test_synthesize_joins_chunks_and_shifts_times(monkeypatch, tmp_path):
    monkeypatch.setenv("TYPECAST_API_KEY", "test-key")
    seen: list = []
    para = "첫째 문장입니다. " * 150
    speech = synthesize(
        f"{para}\n\n둘째 문단 시작. " + "더 이어집니다. " * 150,
        "tc_gyeom",
        tmp_path / "n.wav",
        tempo=1.1,
        remove_silence_ms=200,
        client=_fake_server(seen),
    )
    assert len(seen) == 2 and speech.duration == 4.0
    assert seen[0]["output"]["audio_tempo"] == 1.1 and seen[0]["output"]["remove_silence_ms"] == 200
    assert seen[0]["prompt"]["emotion_type"] == "smart" and "next_text" in seen[0]["prompt"]
    assert "previous_text" in seen[1]["prompt"] and seen[0]["model"] == "ssfm-v30"
    assert speech.words[1][1] == pytest.approx(2.1)  # 둘째 조각 시각은 첫 조각 길이(2초)만큼 민다
    with wave.open(str(speech.path), "rb") as w:
        assert w.getnframes() == 44100 * 4


def test_synthesize_rejects_bad_tempo(tmp_path):
    with pytest.raises(TypecastError):
        synthesize("글", "tc_x", tmp_path / "x.wav", tempo=3.0)


def test_retime_scenes_follows_character_times():
    text = "가나다라 마바사아 자차카타"
    chars = [(ch, i * 0.5, i * 0.5 + 0.5) for i, ch in enumerate(text)]
    scenes = [
        {"id": "s1", "text": "가나다라", "start": 0, "end": 9},
        {"id": "s2", "text": "마바사아", "start": 9, "end": 18},
        {"id": "s3", "text": "자차카타", "start": 18, "end": 27},
    ]
    out = retime_scenes(scenes, chars, duration=7.0)
    assert [(s["start"], s["end"]) for s in out] == [(0.0, 2.5), (2.5, 5.0), (5.0, 7.0)]
    assert out[1]["id"] == "s2"  # 다른 칸은 그대로


def test_unusual_activity_block_gets_plain_reason(monkeypatch, tmp_path):
    """무료 계정이 비정상 사용으로 막히면(403 UNUSUAL_ACTIVITY_DETECTED) 다시 시도하지 말라는 쉬운 이유를 낸다."""
    monkeypatch.setenv("TYPECAST_API_KEY", "test-key")

    def handler(req: httpx.Request) -> httpx.Response:
        return httpx.Response(403, json={"detail": {"code": "UNUSUAL_ACTIVITY_DETECTED", "message": "..."}})

    client = httpx.Client(base_url="https://api.typecast.ai", transport=httpx.MockTransport(handler))
    with pytest.raises(TypecastError, match="유료 API 플랜"):
        synthesize("글", "tc_x", tmp_path / "x.wav", client=client)


def _tone_wav(seconds: float, voiced: list[tuple[float, float]]) -> bytes:
    """voiced 구간만 소리(440Hz)가 나는 WAV 바이트."""
    import math

    rate = 16000
    out = bytearray()
    for i in range(int(rate * seconds)):
        t = i / rate
        v = 8000 if any(a <= t < b for a, b in voiced) else 0
        out += int(v * math.sin(2 * math.pi * 440 * t)).to_bytes(2, "little", signed=True)
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(bytes(out))
    return buf.getvalue()


def test_unlisted_speech_flags_voiced_gap_only():
    from newsclip.typecast_tts import unlisted_speech

    words = [("바로", 0.0, 0.5), ("직후래요.", 0.5, 1.0), ("하루", 2.5, 3.0)]
    # 1.0~2.5초: 낱말 시각은 없는데 소리가 난다(같은 말을 한 번 더 읽음)
    data = _tone_wav(3.0, [(0.0, 3.0)])
    with wave.open(io.BytesIO(data)) as w:
        pcm = w.readframes(w.getnframes())
    assert unlisted_speech(pcm, 2, 16000, words, 3.0) == [(1.0, 2.5)]
    quiet = _tone_wav(3.0, [(0.0, 1.0), (2.5, 3.0)])  # 같은 틈이 조용하면(문장 사이 쉼) 문제없다
    with wave.open(io.BytesIO(quiet)) as w:
        pcm = w.readframes(w.getnframes())
    assert unlisted_speech(pcm, 2, 16000, words, 3.0) == []


def test_per_sentence_rereads_repeated_sentence(monkeypatch, tmp_path):
    """문장마다 읽고, 되풀이가 의심되는 문장만 다시 읽는다(두 번째 시도에서 깨끗하면 그것을 쓴다)."""
    monkeypatch.setenv("TYPECAST_API_KEY", "test-key")
    calls: list[str] = []

    def handler(req: httpx.Request) -> httpx.Response:
        text = json.loads(req.content)["text"]
        calls.append(text)
        repeated = text.startswith("바로") and calls.count(text) == 1  # 첫 시도만 되풀이
        audio = _tone_wav(3.0, [(0.0, 3.0)] if repeated else [(0.0, 1.0)])
        words = [{"text": w, "start": i * 0.5, "end": (i + 1) * 0.5} for i, w in enumerate(text.split()[:2])]
        return httpx.Response(
            200, json={"audio": base64.b64encode(audio).decode(), "words": words, "characters": []}
        )

    client = httpx.Client(base_url="https://api.typecast.ai", transport=httpx.MockTransport(handler))
    speech = synthesize(
        "가을 아침이에요. 바로 잠에서 깬 직후래요.",
        "tc_x",
        tmp_path / "n.wav",
        client=client,
        per_sentence=True,
    )
    assert calls == ["가을 아침이에요.", "바로 잠에서 깬 직후래요.", "바로 잠에서 깬 직후래요."]
    assert speech.retried == 1 and speech.warnings == []
    assert speech.duration == pytest.approx(6.0)  # 깨끗한 두 번째 시도(3초)를 썼다


def test_read_as_changes_sound_only():
    """읽는 법: 보내는 말만 바꾸고(119 → 일일구), 돌려받은 시각은 대본 표기로 되돌린다. 더 긴 숫자는 그대로."""
    from newsclip.typecast_tts import _back, read_as

    table = {"119": "일일구"}
    assert read_as("바로 119, 응급실! 1190원", table) == "바로 일일구, 응급실! 1190원"
    assert read_as("그대로", None) == "그대로"
    assert _back([("바로", 0.0, 0.3), ("일일구,", 0.3, 0.9)], table) == [
        ("바로", 0.0, 0.3),
        ("119,", 0.3, 0.9),
    ]
    chars = [(c, float(i), float(i + 1)) for i, c in enumerate("로일일구")]
    assert [c for c, _, _ in _back(chars, table)] == ["로", "1", "1", "9"]


def test_event_dates_read_by_custom_and_times_map_back(monkeypatch, tmp_path):
    """5·18은 '오일팔'로 읽게 보내고(숫자 '오 십팔'로 읽지 않게), 받은 낱말·글자 시각은 자막에 보이는 '5·18'로 되돌린다."""
    from newsclip.typecast_tts import speakable

    assert speakable("5·18 민주화운동")[0] == "오일팔 민주화운동"
    assert speakable("518 왜곡 망언")[0] == "오일팔 왜곡 망언"
    assert (
        speakable("4·19와 6·25, 12·12 군사반란, 10·26 사태")[0]
        == "사일구와 육이오, 십이십이 군사반란, 십이륙 사태"
    )
    assert speakable("3·1절과 8·15 광복절")[0] == "삼일절과 팔일오 광복절"
    for plain in ("518명이 모였습니다", "지지율 3.5% 하락", "10월 19일 국정감사"):  # 수·날짜는 그대로
        assert speakable(plain) == (plain, [])
    monkeypatch.setenv("TYPECAST_API_KEY", "test-key")
    seen: list = []
    speech = synthesize("5·18은 폭동이라는 말", "tc_gyeom", tmp_path / "n.wav", client=_fake_server(seen))
    assert seen[0]["text"] == "오일팔은 폭동이라는 말"
    assert speech.words[0][0] == "5·18은"
    assert "".join(ch for ch, _a, _b in speech.chars).startswith("5·18은 폭동")
