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
