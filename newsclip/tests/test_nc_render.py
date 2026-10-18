"""세로 쇼츠 완성본(render): 강조어, 명령 구성, 짧은 실제 렌더."""

import json
import subprocess
import wave
from dataclasses import replace
from pathlib import Path

from PIL import Image

from newsclip.render import (
    SUB_BOTTOM,
    SUB_PX,
    Overlay,
    RenderPlan,
    Shot,
    build_command,
    fit_sub_px,
    is_hot,
    render,
    render_cue,
)


def test_is_hot_numbers_and_keyword_prefix():
    assert is_hot("60명", set()) and is_hot("숙제는", {"숙제"}) and is_hot("자멸일까요?", {"자멸"})
    assert not is_hot("지금부터입니다", {"숙제"}) and not is_hot("...", {"자멸"})


def _wav(path: Path, seconds: float) -> None:
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(48000)
        w.writeframes(b"\x00\x00" * int(48000 * seconds))


def test_render_cue_box_and_highlight(tmp_path):
    art = render_cue("경기도 총 국회의원 60명", tmp_path / "c.png", (1080, 1920), keywords={"국회"})
    assert art is not None and art.width > 0 and art.height > 0
    assert Image.open(art.path).mode == "RGBA" and art.top + art.height == SUB_BOTTOM
    assert 560 < art.top and art.top + art.height < 1370  # 사진 칸 안쪽 아랫부분
    assert render_cue("\n", tmp_path / "e.png", (1080, 1920)) is None


def test_fit_sub_px_shrinks_only_when_needed():
    assert fit_sub_px(["짧은 자막"], (1080, 1920)) == SUB_PX
    long_line = "이재명 대통령이 빈소에서 고인의 딸이 일곱 살이라는 대답을 듣고 한동안 말을 잇지 못했습니다"
    assert fit_sub_px([long_line], (1080, 1920)) < SUB_PX


def test_build_command_wires_video_audio_and_effects(tmp_path):
    photo = tmp_path / "p.jpg"
    Image.new("RGB", (1080, 1920), (200, 40, 40)).save(photo)
    clip = tmp_path / "v.mp4"
    subprocess.run(
        [
            "ffmpeg",
            "-hide_banner",
            "-loglevel",
            "error",
            "-y",
            "-f",
            "lavfi",
            "-i",
            "testsrc2=size=640x360:rate=30",
            "-t",
            "2",
            "-c:v",
            "libx264",
            "-pix_fmt",
            "yuv420p",
            str(clip),
        ],
        check=True,
    )
    narr, bgm, whoosh = tmp_path / "n.wav", tmp_path / "b.wav", tmp_path / "w.wav"
    _wav(narr, 4)
    _wav(bgm, 4)
    _wav(whoosh, 0.5)
    cue = replace(
        render_cue("숫자 60명", tmp_path / "cue.png", (1080, 1920), keywords=set()), start=0.5, end=2.0
    )
    plan = RenderPlan(
        shots=[Shot(photo, "image", 0, 2), Shot(clip, "video", 2, 4)],
        total=4.0,
        narration=narr,
        headline=photo,
        captions=[Overlay(photo, 0, 2)],
        cues=[cue],
        sfx=[(2.0, whoosh)],
        bgm=bgm,
    )
    cmd = build_command(plan, tmp_path / "out.mp4")
    joined = " ".join(cmd)
    assert "concat=n=2" in joined and "sidechaincompress" in joined and "adelay=2000" in joined
    assert "between(t,0.500,2.000)" in joined and "[vout]" in joined and "[aout]" in joined


def test_render_short_real_file(tmp_path):
    photo = tmp_path / "p.jpg"
    Image.new("RGB", (1080, 1920), (30, 90, 160)).save(photo)
    narr = tmp_path / "n.wav"
    _wav(narr, 3)
    plan = RenderPlan(
        shots=[Shot(photo, "image", 0, 3, zoom=(1.0, 1.04))], total=3.0, narration=narr, size=(1080, 1920)
    )
    out = render(plan, tmp_path / "short.mp4")
    info = json.loads(
        subprocess.run(
            [
                "ffprobe",
                "-v",
                "error",
                "-show_entries",
                "stream=codec_type,width,height:format=duration",
                "-of",
                "json",
                str(out),
            ],
            capture_output=True,
            text=True,
            check=True,
        ).stdout
    )
    video = next(s for s in info["streams"] if s["codec_type"] == "video")
    assert (video["width"], video["height"]) == (1080, 1920)
    assert abs(float(info["format"]["duration"]) - 3.0) < 0.2
