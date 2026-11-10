"""효과음: 전환·질문 자리 찾기, 합성 WAV, A2 트랙."""

import struct
import wave
import xml.etree.ElementTree as ET

from newsclip.fcpxml import build_xmeml, validate_xmeml
from newsclip.models import Scene, SceneSet
from newsclip.sfx import DING, HIT, WHOOSH, ensure_sfx, sfx_cues


def _scene(i, start, end, text, topic="도입"):
    return Scene(id=f"s{i:03d}", start_sec=start, end_sec=end, text=text, topic=topic)


def test_sfx_cues_transition_words_topic_change_and_question():
    scenes = [
        _scene(1, 0, 3, "이재명 대통령이 빈소에서 말을 잇지 못했습니다."),
        _scene(2, 3, 6, "그런데 진짜 숙제는 지금부터입니다."),  # 전환어 → 전환음
        _scene(3, 6, 9, "보수 내부의 자멸일까요?"),  # 물음표 → 질문음
        _scene(4, 9, 12, "다음 소식입니다.", topic="다른 주제"),  # 주제 바뀜 → 전환음
    ]
    cues = sfx_cues(scenes)
    assert [(c.name, c.at) for c in cues] == [(WHOOSH, 3), (DING, 6), (WHOOSH, 9)]


def test_sfx_cues_keep_a_gap_between_effects():
    scenes = [
        _scene(1, 0, 1, "하지만 이것도 있습니다?"),  # 전환음 + 질문음이 같은 자리 → 하나만
        _scene(2, 0.5, 3, "결국 이런 말입니다."),  # 1초 안쪽 → 건너뜀
    ]
    assert [c.name for c in sfx_cues(scenes)] == [WHOOSH]


def test_ensure_sfx_writes_mono_48k_wav(tmp_path):
    files = ensure_sfx(tmp_path)
    assert set(files) == {WHOOSH, DING, HIT}
    peaks = {}
    for name, path in files.items():
        assert path.parent == tmp_path / "sfx" and path.exists()
        with wave.open(str(path), "rb") as w:
            assert w.getnchannels() == 1 and w.getframerate() == 48000 and w.getsampwidth() == 2
            assert 0.2 < w.getnframes() / 48000 < 1.0
            pcm = w.readframes(w.getnframes())
        peaks[name] = max(abs(v) for v in struct.unpack(f"<{len(pcm) // 2}h", pcm))
    assert peaks[HIT] > peaks[WHOOSH]  # 강조음(받지 못했을 때 합성 팝)은 넘김 소리보다 조금 크게


def test_fcpxml_adds_sfx_track_under_narration(tmp_path):
    scenes = [
        _scene(1, 0, 4, "이재명 대통령이 빈소에서 말을 잇지 못했습니다."),
        _scene(2, 4, 8, "그런데 진짜 숙제는 지금부터입니다."),
        _scene(3, 8, 12, "보수 내부의 자멸일까요?"),
    ]
    wav = tmp_path / "narration.wav"
    with wave.open(str(wav), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(48000)
        w.writeframes(b"\x00\x00" * 48000 * 12)
    sset = SceneSet(project="p", fps=30, width=1080, height=1920, scenes=scenes)
    xml = build_xmeml(sset, tmp_path, fps=30, size=(1080, 1920), audio=wav, attribution=False)
    assert validate_xmeml(xml) == []
    root = ET.fromstring(xml.split("\n", 2)[2])
    audio_tracks = root.findall("./sequence/media/audio/track")
    assert len(audio_tracks) == 2  # A1 내레이션 + A2 효과음
    sfx_clips = audio_tracks[1].findall("clipitem")
    assert [c.findtext("name") for c in sfx_clips] == ["whoosh.wav", "ding.wav"]
    assert [int(c.findtext("start")) for c in sfx_clips] == [120, 240]  # 4초, 8초 × 30fps
    assert (tmp_path / "sfx" / "whoosh.wav").exists()


def test_fcpxml_without_sfx_keeps_one_audio_track(tmp_path):
    scenes = [_scene(1, 0, 4, "보수 내부의 자멸일까요?")]
    wav = tmp_path / "narration.wav"
    with wave.open(str(wav), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(48000)
        w.writeframes(b"\x00\x00" * 48000 * 4)
    sset = SceneSet(project="p", fps=30, width=1080, height=1920, scenes=scenes)
    xml = build_xmeml(sset, tmp_path, fps=30, size=(1080, 1920), audio=wav, attribution=False, sfx=False)
    root = ET.fromstring(xml.split("\n", 2)[2])
    assert len(root.findall("./sequence/media/audio/track")) == 1


def test_hit_uses_recorded_pop_when_fetch_works(tmp_path, monkeypatch):
    """완성본 렌더는 강조음으로 녹음된 팝을 받아 쓴다(앞 쉼을 잘라 48kHz 모노로). 받지 못하면 합성 팝."""
    import subprocess
    import urllib.request

    mp3 = tmp_path / "src.mp3"
    subprocess.run(
        [
            "ffmpeg",
            "-loglevel",
            "error",
            "-f",
            "lavfi",
            "-i",
            "anullsrc=r=44100:cl=stereo:d=0.2",
            "-f",
            "lavfi",
            "-i",
            "sine=f=700:d=0.3",
            "-filter_complex",
            "[0:a][1:a]concat=n=2:v=0:a=1",
            str(mp3),
        ],
        check=True,
    )

    class Resp:
        def __enter__(self):
            return self

        def __exit__(self, *a):
            return False

        def read(self):
            return mp3.read_bytes()

    seen = []
    monkeypatch.setattr(urllib.request, "urlopen", lambda url, timeout: seen.append(url) or Resp())
    hit = ensure_sfx(tmp_path / "p", fetch=True)[HIT]
    assert seen and "2357" in seen[0]
    with wave.open(str(hit), "rb") as w:
        assert w.getnchannels() == 1 and w.getframerate() == 48000
        assert w.getnframes() / 48000 < 0.4  # 앞 0.2초 쉼은 잘라 냈다

    def fail(url, timeout):
        raise OSError("offline")

    monkeypatch.setattr(urllib.request, "urlopen", fail)
    hit2 = ensure_sfx(tmp_path / "q", fetch=True)[HIT]
    assert hit2.exists()  # 합성 팝으로 대신
