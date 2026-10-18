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
    assert "between(t,0.4833,1.9833)" in joined and "[vout]" in joined and "[aout]" in joined
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
    from newsclip.shorts_render import HOOK_CENTER_Y, hook_chunks, subtitles_for

    assert hook_chunks("홍준표 전 대구시장이 방송") == ["홍준표 전", "대구시장이", "방송"]
    audio = tmp_path / "n.wav"
    _wav(audio, 3)
    srt = tmp_path / "s.srt"
    srt.write_text(
        "1\n00:00:00,000 --> 00:00:02,000\n이재명이 또 해냈습니다!\n\n2\n00:00:02,000 --> 00:00:03,000\n둘째 줄\n",
        encoding="utf-8",
    )
    arts, _spans, _info, _hook = subtitles_for("대본", audio, (1080, 1920), tmp_path, set(), srt=srt)
    # 첫 문장(느낌표로 끝)은 훅: '이재명이'를 짧게, 핵심 행위 '또 해냈습니다!'는 크게 강조(글자 수 비율 시각),
    # 그다음은 일반 자막
    assert [(round(a.start, 2), round(a.end, 2)) for a in arts] == [(0.0, 0.73), (0.73, 2.0), (2.0, 3.0)]
    assert not arts[0].intro and arts[1].intro  # 핵심은 강조 자막(흐린 데서 또렷하게 + 강조음)
    for a in arts[:2]:
        assert abs(a.top + a.height / 2 - HOOK_CENTER_Y) <= 1  # 둘 다 사진 칸 가운데
    assert arts[0].height > arts[2].height


def test_hook_shows_quoted_remark_until_hook_ends(tmp_path):
    """훅에 발언이 있으면 "발언"을 크게 띄우고, 뒤가 '~고 했습니다'뿐이면 훅이 끝날 때까지 둔다."""
    from newsclip.shorts_render import hook_key, subtitles_for

    assert hook_key("윤 전 대통령이 한동훈을 '패 죽이고 싶었다'고 했습니다!") == ("quote", "패 죽이고 싶었다")
    assert hook_key("이재명이 또 해냈습니다.") == ("act", "또 해냈습니다")
    assert hook_key("'5·18은 폭동' 발언이 항소심에서 무죄를 받았습니다!") == ("quote", "5·18은 폭동")
    assert hook_key("홍준표 전 대구시장이 방송 대담에서 윤석열 전 대통령과") is None  # 문장이 안 끝남
    audio = tmp_path / "n.wav"
    _wav(audio, 4)
    srt = tmp_path / "s.srt"
    srt.write_text(
        "1\n00:00:00,000 --> 00:00:03,000\n윤 전 대통령이 한동훈을 '패 죽이고 싶었다'고 했습니다!\n\n"
        "2\n00:00:03,000 --> 00:00:04,000\n둘째 줄\n",
        encoding="utf-8",
    )
    arts, _s, _i, _h = subtitles_for("대본", audio, (1080, 1920), tmp_path, set(), srt=srt)
    hook = [a for a in arts if a.path.name.startswith("hook_")]
    # '윤 전 대통령이' / '한동훈을'은 짧게, 그다음 "발언"은 강조 자막
    assert [bool(a.intro) for a in hook] == [False, False, True]
    assert hook[-1].end == 3.0  # '고 했습니다!'는 따로 띄우지 않고 발언을 훅 끝까지


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


def test_cut_editing_switches_photos_on_subtitle_bounds(tmp_path):
    """컷 편집: 장면의 후보 사진을 한 장씩 바꿔 쓰되 자막 경계에서, 줌 인·아웃을 번갈아, 넘기는 장면은 펀치 전환.
    같은 사진을 가깝게 다시 쓰지 않는다(펀치인 없음)."""
    from newsclip.shorts_render import PUSH, XFADE_SEC, cut_pieces, shots_for

    assert cut_pieces(0, 6, [0.8, 1.6, 2.1, 3.0, 4.4, 5.5], 2) == [(0, 3.0), (3.0, 6)]
    assert cut_pieces(0, 6, [0.8, 1.6, 2.1, 3.0, 4.4, 5.5], 3) == [(0, 2.1), (2.1, 4.4), (4.4, 6)]
    assert cut_pieces(0, 6, [], 2) == [(0, 6)]  # 자막 경계가 없으면 말 중간에서 바꾸지 않는다
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
    shots = shots_for(scenes, tmp_path, 8.0, {}, cuts=[1.6, 3.0, 4.4, 6.0, 7.0], xfade_at=[6.0])
    assert [(s.path.name, s.start, s.end) for s in shots] == [
        ("a1.jpg", 0.0, 3.0),
        ("a2.jpg", 3.0, 6.0),
        ("b1.jpg", 6.0, 8.0),
    ]
    assert [s.zoom for s in shots] == [(1.0, 1.0 + PUSH), (1.0 + PUSH, 1.0), (1.0, 1.0 + PUSH)]
    assert [s.xfade for s in shots] == [0.0, 0.0, XFADE_SEC]


def test_hook_chunks_follow_spoken_words():
    """훅 조각은 낱말 시각이 있으면 그 말이 들리는 때에 뜬다(없으면 글자 수 비율)."""
    from newsclip.shorts_render import chunk_starts

    pieces = ["홍준표 전", "대구시장이", "방송"]
    words = [("홍준표", 0.0, 0.44), ("전", 0.44, 0.66), ("대구시장이", 0.66, 1.14), ("방송", 1.14, 1.64)]
    heard = chunk_starts(pieces, 0.0, 1.64, words)
    assert heard[0] == 0.0 and abs(heard[1] - 0.66) < 0.05 and abs(heard[2] - 1.14) < 0.05
    plain = chunk_starts(pieces, 0.0, 1.64, None)
    assert abs(plain[2] - 1.64 * 9 / 11) < 0.01  # 글자 수 비율(3+1+5 : 2)


def test_build_command_zooms_inside_photo_box_and_punches(tmp_path):
    photo = tmp_path / "p.jpg"
    Image.new("RGB", (1080, 1920), (90, 90, 90)).save(photo)
    narration = tmp_path / "n.wav"
    _wav(narration, 2)
    plan = RenderPlan(
        shots=[
            Shot(photo, "image", 0, 1.0),
            Shot(photo, "image", 1.0, 2.0, zoom=(1.32, 1.38), focus=(0.5, 0.33), xfade=0.16),
        ],
        total=2.0,
        narration=narration,
        photo_box=(0, 560, 1080, 1370),
    )
    filt = build_command(plan, tmp_path / "o.mp4")
    filt = filt[filt.index("-filter_complex") + 1]
    assert "crop=1080:810:0:560" in filt and "overlay=0:560" in filt  # 사진 칸만 확대해 제자리에
    assert "trim=end_frame=30" in filt  # 컷 길이는 프레임 단위로
    # 확대 펀치 전환: 앞 컷 끝은 빠르게 다가가고, 다음 컷은 다가간 데서 제자리로(가로·세로 크기 식에 각각)
    assert filt.count("pow(") == 4
    out = render(plan, tmp_path / "o.mp4")
    probe = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "json", str(out)],
        capture_output=True,
        text=True,
        check=True,
    )
    assert abs(float(json.loads(probe.stdout)["format"]["duration"]) - 2.0) < 0.1


def test_emphasis_cues_colored_and_spaced(tmp_path):
    """짧은 핵심 자막은 색 글자로: 센 말 빨강, 직접 인용 노랑, 핵심 인물 초록 — 4초 안에 둘은 안 나온다."""
    from newsclip.render import EMPH_BLUR, EMPH_GREEN, EMPH_RED, EMPH_YELLOW, render_emph
    from newsclip.shorts_render import emph_color, pick_emphasis
    from newsclip.srt import Cue

    assert emph_color('"패 죽이고 싶었다"고', {"홍준표"}) == EMPH_RED
    assert emph_color('"맹신자 중에 끌어모은', set()) == EMPH_YELLOW
    assert emph_color("한동훈 의원이 하도", {"한동훈"}) == EMPH_GREEN
    assert emph_color("그 시절 분위기가 어땠을지", set()) is None  # '분위기'는 '위기'가 아니다
    assert emph_color("사람들이 이제 서로를 배신자로 몰면서", set()) is None  # 길면 보통 자막
    cues = [
        Cue(1, 0, 1, "한동훈 의원이"),
        Cue(2, 2, 3, "배신자 낙인"),
        Cue(3, 5, 6, "막말 논란"),
        Cue(4, 9, 10, "한동훈 의원"),
    ]
    assert pick_emphasis(cues, {"한동훈"}) == {1: EMPH_RED, 3: EMPH_GREEN}  # 빨강 먼저, 그다음 간격 맞는 것
    # 뉴스 채널(sag): 흐름이 늘어질 때만 하나씩 — 훅(0번) 끝에서 8초 지나야, 그때 3초 안의 후보 중 가장 센 것
    cues = [
        Cue(1, 0, 3, "훅"),
        Cue(2, 4.8, 6, "한동훈 의원이"),
        Cue(3, 9, 10, '"맹신자 중에'),
        Cue(4, 11.5, 12, "한동훈 의원"),
        Cue(5, 13, 14, "막말 논란"),
        Cue(6, 18, 19, '"끝까지 오만하다"고'),
        Cue(7, 22, 23, "배신자 낙인"),
    ]
    assert pick_emphasis(cues, {"한동훈"}, start=1, sag=8.0) == {4: EMPH_RED, 6: EMPH_RED}
    art = render_emph("배신자 낙인", tmp_path / "e.png", (1080, 1920), color=EMPH_RED)
    assert art is not None and len(art.intro) == len(EMPH_BLUR) and all(p.exists() for p in art.intro)
    assert Image.open(art.intro[0]).size == Image.open(art.path).size


def test_one_frame_overlays_are_drawn(tmp_path):
    """1프레임짜리 그림(강조 자막의 흐림 단계)도 정확히 그 프레임에 그려진다."""
    photo = tmp_path / "p.jpg"
    Image.new("RGB", (1080, 1920), (0, 0, 0)).save(photo)
    dot = tmp_path / "dot.png"
    Image.new("RGBA", (200, 200), (255, 255, 255, 255)).save(dot)
    narration = tmp_path / "n.wav"
    _wav(narration, 1)
    from newsclip.render import CueArt

    cue = CueArt(path=dot, start=0.5, end=0.8, width=200, height=200, top=800, intro=(dot, dot))
    plan = RenderPlan(
        shots=[Shot(photo, "image", 0, 1.0, zoom=(1.0, 1.0))], total=1.0, narration=narration, cues=[cue]
    )
    out = render(plan, tmp_path / "o.mp4")
    raw = subprocess.run(
        [
            "ffmpeg",
            "-loglevel",
            "error",
            "-i",
            str(out),
            "-vf",
            "crop=10:10:535:895",
            "-f",
            "rawvideo",
            "-pix_fmt",
            "gray",
            "-",
        ],
        capture_output=True,
        check=True,
    ).stdout
    lit = [i for i in range(len(raw) // 100) if raw[i * 100 + 55] > 128]
    assert lit[0] == 15 and lit[-1] == 23  # 0.5초(15번째 프레임)부터 0.8초 전까지


def test_background_holds_still_during_hook(tmp_path):
    """훅이 나오는 동안은 사진을 바꾸지 않고 아주 살짝만 움직인다."""
    from newsclip.shorts_render import HOLD_ZOOM, shots_for

    for name in ("assets/a1.jpg", "assets/a2.jpg"):
        (tmp_path / name).parent.mkdir(parents=True, exist_ok=True)
        Image.new("RGB", (1080, 1920), (90, 90, 90)).save(tmp_path / name)
    scenes = [
        {
            "id": "s001",
            "start": 0,
            "end": 5,
            "chosen": "assets/a1.jpg",
            "files": ["assets/a1.jpg", "assets/a2.jpg"],
        }
    ]
    shots = shots_for(scenes, tmp_path, 5.0, {}, cuts=[0.6, 1.2, 2.2, 3.5, 4.2], hold_until=3.5)
    assert shots[0].start == 0.0 and shots[0].end >= 3.5 and shots[0].zoom == HOLD_ZOOM
    assert len(shots) == 2 and shots[1].start == 3.5


def test_refresh_design_recomposes_old_photos_and_headline(tmp_path):
    """자료를 모은 뒤 디자인이 바뀌었으면: 바탕이 다른 사진만 지금 디자인으로 다시 합성하고 제목 그림을 새로 그린다."""
    from newsclip.shorts_render import refresh_design
    from newsclip.vertical import compose_photo

    (tmp_path / "assets").mkdir()
    (tmp_path / "overlays").mkdir()
    Image.new("RGB", (1080, 1920), (120, 120, 120)).save(
        tmp_path / "assets" / "old.jpg"
    )  # 예전 디자인(회색 바탕)
    compose_photo(Image.new("RGB", (800, 600), (200, 50, 50))).save(tmp_path / "assets" / "now.jpg")
    Image.new("RGBA", (1080, 1920)).save(tmp_path / "overlays" / "headline_old.png")
    assert refresh_design(tmp_path, "보수 내부 폭로전") == 1
    assert [p.name for p in (tmp_path / "overlays").glob("*.png")] == ["headline_now.png"]
    assert Image.open(tmp_path / "assets" / "old.jpg").getpixel((540, 10)) != (120, 120, 120)


def test_quote_pairs_close_and_reopen_split_quotes():
    """인용 하나가 두 자막에 걸치면 앞 조각은 닫고 뒤 조각은 다시 연다. 짝이 맞는 자막은 그대로."""
    from newsclip.shorts_render import pair_quotes

    assert pair_quotes(['"소방이 가장 큰 골치 아픈', '재정 빨대 같다"고 했는데요,', "추 지사 앞으로"]) == [
        '"소방이 가장 큰 골치 아픈"',
        '"재정 빨대 같다"고 했는데요,',
        "추 지사 앞으로",
    ]
    assert pair_quotes(["“사과 바라시니까", "사과”라고\n했거든요."]) == [
        "“사과 바라시니까”",
        "“사과”라고\n했거든요.",
    ]
    assert pair_quotes(['"끝까지 오만하다"고', "‘재정 빨대’라고"]) == [
        '"끝까지 오만하다"고',
        "‘재정 빨대’라고",
    ]
    assert pair_quotes(['"하나', "둘", '셋"이라고']) == ['"하나"', '"둘"', '"셋"이라고']


def test_wide_photo_sits_on_box_top_for_news_styles():
    """칸보다 납작한 사진: 뉴스 디자인은 칸 위에 붙이고(출처 표시가 사진 위), 널스윤서는 예전처럼 가운데."""
    from newsclip.vertical import _box, compose_photo, style_of

    wide = Image.new("RGB", (2000, 1000), (200, 50, 50))  # 2:1 파노라마
    for name, top in (("default", True), ("navy_red", True), ("health", False)):
        st = style_of(name)
        x0, y0, x1, y1 = _box((1080, 1920), st)
        frame = compose_photo(wide, (1080, 1920), name)
        r, g, b = frame.getpixel((540, y0 + 4))
        assert (r > 150 and g < 100) is top, name
