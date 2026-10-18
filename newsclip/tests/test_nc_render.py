"""세로 쇼츠 완성본(render): 강조어, 명령 구성, 짧은 실제 렌더."""

import json
import subprocess
import wave
from dataclasses import replace
from pathlib import Path

from PIL import Image

from newsclip.render import (
    SUB_PX,
    SUB_TEXT_Y,
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
    assert Image.open(art.path).mode == "RGBA" and SUB_TEXT_Y - 80 < art.top < SUB_TEXT_Y
    assert 1100 < art.top  # 사진 아랫부분이 검게 이어지는 자리
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
    assert (
        "fade=" not in joined and "28*max(0,1-(t-0.500)/0.18)" in joined
    )  # 전환: 흐려지지 않고 아래에서 올라옴


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


def test_other_day_photo_flag_marks_caption(tmp_path):
    from newsclip.fcpxml import caption_for
    from newsclip.models import Asset, Scene
    from newsclip.pipeline import apply_queries

    asset = Asset(
        id="a1",
        source="naver_image",
        media="image",
        title="발언하는 인물",
        url="x",
        attribution_text="출처: imgnews.naver.net",
        local_path=str(tmp_path / "p.jpg"),
    )
    scene = Scene(id="s001", start_sec=0, end_sec=3, text="이재명이 또 해냈습니다.", topic="도입")
    apply_queries([scene], {"scenes": {"s001": {"q": "인물 발언 장면", "other_day": True}}})
    assert scene.other_day and scene.query == "인물 발언 장면"
    assert caption_for(scene, asset) == "다른 날 사진 · 출처: imgnews.naver.net"
    scene.other_day = False
    assert caption_for(scene, asset) == "출처: imgnews.naver.net"


def test_hook_cue_centered_and_big(tmp_path):
    from newsclip.shorts_render import wrap_hook

    assert wrap_hook("이재명이 또 해냈습니다") == "이재명이 또\n해냈습니다"
    normal = render_cue("이재명이 또", tmp_path / "n.png", (1080, 1920), px=84)
    hook = render_cue(
        wrap_hook("이재명이 또 해냈습니다"), tmp_path / "h.png", (1080, 1920), px=124, center_y=965
    )
    assert hook.height > normal.height * 1.5 and hook.width <= 1080
    assert abs((hook.top + hook.height / 2) - 965) <= 1


def test_hook_colors_yellow_text_red_keywords(tmp_path):
    from newsclip.render import RED, YELLOW

    art = render_cue(
        "이재명이 또 해냈습니다",
        tmp_path / "k.png",
        (1080, 1920),
        keywords={"이재명"},
        px=124,
        center_y=965,
        text_color=YELLOW,
        hot_color=RED,
    )
    pixels = list(Image.open(art.path).convert("RGB").getdata())
    reds = sum(1 for r, g, b in pixels if r > 200 and g < 120 and b < 120)
    yellows = sum(1 for r, g, b in pixels if r > 230 and g > 200 and b < 120)
    assert reds > 500 and yellows > 500


def test_shots_split_long_scenes_and_alternate_photos(tmp_path):
    from newsclip.shorts_render import shots_for

    for name in ("assets/a1.jpg", "assets/a2.jpg", "assets/b1.jpg"):
        (tmp_path / name).parent.mkdir(parents=True, exist_ok=True)
        Image.new("RGB", (1080, 1920), (90, 90, 90)).save(tmp_path / name)
    scenes = [
        {
            "id": "s001",
            "start": 0,
            "end": 6,
            "chosen": "assets/a1.jpg",
            "files": ["assets/a1.jpg", "assets/a2.jpg"],
        },
        {"id": "s002", "start": 6, "end": 8, "chosen": "assets/b1.jpg", "files": ["assets/b1.jpg"]},
    ]
    plain = shots_for(scenes, tmp_path, 8.0, {})
    assert len(plain) == 2
    split = shots_for(scenes, tmp_path, 8.0, {}, max_shot_sec=2.5)
    assert len(split) == 4  # 6초 장면은 3조각, 2초 장면은 그대로
    assert [round(s.end - s.start, 2) for s in split] == [2.0, 2.0, 2.0, 2.0]
    assert [s.path.name for s in split[:3]] == ["a1.jpg", "a2.jpg", "a1.jpg"]  # 같은 장면의 사진을 번갈아
    assert split[0].zoom != split[1].zoom  # 줌 방향도 번갈아
    assert split[0].start == 0.0 and split[-1].end == 8.0


def test_subtitles_follow_given_srt(tmp_path):
    from newsclip.shorts_render import subtitles_for

    audio = tmp_path / "n.wav"
    _wav(audio, 3)
    srt = tmp_path / "s.srt"
    srt.write_text(
        "1\n00:00:00,000 --> 00:00:02,000\n이재명이 또 해냈습니다\n\n2\n00:00:02,000 --> 00:00:03,000\n둘째 줄\n",
        encoding="utf-8",
    )
    arts, _spans, _info = subtitles_for("대본", audio, (1080, 1920), tmp_path, set(), srt=srt)
    assert [(a.start, a.end) for a in arts] == [
        (0.0, 1.5),
        (1.5, 2.0),
        (2.0, 3.0),
    ]  # 훅 → 같은 줄 아래 자막 → 둘째 줄
    assert arts[0].height > arts[1].height


def test_long_subtitle_goes_to_two_lines(tmp_path):
    from newsclip.render import sub_lines

    assert sub_lines("한동훈 의원이 하도 깐죽대니까") == ["한동훈 의원이", "하도 깐죽대니까"]
    assert sub_lines("짧은 자막") == ["짧은 자막"] and sub_lines("첫 줄\n둘째 줄") == ["첫 줄", "둘째 줄"]
    one = render_cue("짧은 자막", tmp_path / "a.png", (1080, 1920))
    two = render_cue("한동훈 의원이 하도 깐죽대니까", tmp_path / "b.png", (1080, 1920))
    assert two.height > one.height * 1.5 and two.top == one.top  # 한 줄·두 줄 모두 첫 줄 자리가 같다


def test_subtitle_shadow_without_box(tmp_path):
    boxed = Image.open(render_cue("짧은 자막", tmp_path / "a.png", (1080, 1920), box=True).path)
    shadow = Image.open(render_cue("짧은 자막", tmp_path / "b.png", (1080, 1920), box=False, shadow=1.0).path)
    assert boxed.getpixel((boxed.width // 2, 2))[3] == 255  # 상자는 위 가장자리까지 불투명
    assert shadow.getpixel((2, 2))[3] == 0  # 그림자만: 모서리는 투명
    dark = sum(1 for r, g, b, a in shadow.getdata() if a > 120 and r < 40 and g < 40 and b < 40)
    assert dark > 1000  # 글자 뒤 진한 그림자


def test_only_one_keyword_highlighted_per_subtitle(tmp_path):
    from newsclip.render import RED, YELLOW

    art = render_cue(
        "홍준표 대구시장이 방송 대담에서",
        tmp_path / "k.png",
        (1080, 1920),
        keywords={"홍준표", "대구시장", "방송", "대담"},
        text_color=YELLOW,
        hot_color=RED,
        box=True,
    )
    img = Image.open(art.path).convert("RGB")
    reds = [
        x
        for x in range(img.width)
        for y in range(0, img.height, 4)
        if img.getpixel((x, y))[0] > 200 and img.getpixel((x, y))[1] < 120
    ]
    # 빨강은 첫 핵심어(홍준표) 자리에만 — 오른쪽 절반에는 없다
    assert reds and max(reds) < img.width * 0.6
