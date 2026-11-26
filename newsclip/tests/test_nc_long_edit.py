"""정치는푸르다 10분 롱폼·3분 단독 완성 편집(newsclip.long_edit) — 챕터·카드·자막·효과음·timeline.xml 트랙."""

import subprocess
from pathlib import Path

from PIL import Image

from newsclip import long_edit as le
from newsclip.fcpxml import build_xmeml, validate_xmeml
from newsclip.models import SceneSet
from newsclip.srt import Cue

from nch import asset, scene


def _project(tmp_path: Path) -> list:
    """콜드 오픈 2장면 · 챕터 둘(장면 2개씩) · 정리 · 끝 인사 장면, 장면마다 사진 한 장."""
    topics = [
        ("도입", "오늘 가장 센 장면입니다."),
        ("도입", "오늘은 감사원, 한동훈 딸 수사 차례로 짚어 보겠습니다."),
        ("감사원 '타이거파' 논란 — 정권 바뀌어도 버티는 감사위원", "박지원 의원이 몰아붙였습니다."),
        ("감사원 '타이거파' 논란 — 정권 바뀌어도 버티는 감사위원", '"타이거파 부두목, 사퇴하라"고 했습니다.'),
        ("한동훈 딸 재수사 — 무혐의 3년 만에 다시 열린 수사", "그런데 수사가 다시 열렸습니다."),
        ("한동훈 딸 재수사 — 무혐의 3년 만에 다시 열린 수사", "경찰은 재수사를 검토합니다."),
        ("정리", "여러분 생각은 어떠세요?"),
        ("정리", "더 나은 대한민국을 위해서 정치는푸르다 채널 구독해주시고, 매일 찾아뵙겠습니다."),
    ]
    scenes = []
    for k, (topic, text) in enumerate(topics):
        p = tmp_path / "assets" / f"s{k + 1:03d}_1.jpg"
        p.parent.mkdir(parents=True, exist_ok=True)
        Image.new("RGB", (1600, 1000), (40 + 20 * k, 60, 90)).save(p)
        a = asset("naver_image", f"https://i/{k}.jpg", local_path=str(p), width=1600, height=1000, title=text)
        scenes.append(
            scene(
                f"s{k + 1:03d}", k * 15.0, k * 15.0 + 15.0, topic=topic, text=text, candidates=[a], chosen=a
            )
        )
    return scenes


def test_headings_chapters_and_youtube_chapters():
    assert le.split_heading("한동훈 딸 재수사 — 무혐의 3년 만에") == ("한동훈 딸 재수사", "무혐의 3년 만에")
    assert le.split_heading("정리") == ("정리", "")
    scenes = [
        scene(f"s{k}", k * 30.0, k * 30.0 + 30, topic=t)
        for k, t in enumerate(["도입", "가 — 요약", "가 — 요약", "나", "정리"])
    ]
    chs = le.chapters_of(scenes)
    assert [(c.no, c.title, c.summary, c.start, c.end) for c in chs] == [
        (0, "오프닝", "", 0.0, 30.0),
        (1, "가", "요약", 30.0, 90.0),
        (2, "나", "", 90.0, 120.0),
        (-1, "정리", "", 120.0, 150.0),
    ]
    assert le.youtube_chapters(chs) == "0:00 오프닝\n0:30 1. 가\n1:30 2. 나\n2:00 정리"
    assert le.youtube_chapters(chs[:2]) == ""  # 유튜브 챕터는 3개 이상


def test_build_cards_subtitles_sounds_and_timeline(tmp_path):
    scenes = _project(tmp_path)
    total = 120.0
    lines = [Cue(k + 1, k * 5.0, k * 5.0 + 4.5, f"자막 {k}번 줄입니다") for k in range(24)]
    lines[10] = Cue(11, 50.0, 54.5, "막말 논란")  # 센 말 → 강조 자막(흐름이 14초 늘어진 뒤)
    quotes = {
        "s004": {"text": "타이거파 부두목, 사퇴하라", "who": "박지원 더불어민주당 의원", "when": "10월 12일"}
    }
    edit = le.build(tmp_path, scenes, lines, total=total, channel="정치는푸르다", quotes=quotes)
    shade, subs, chrome, cards = edit.tracks
    kinds = [(p.name.split("_")[0], a, b) for p, a, b in cards]
    assert [k for k, _a, _b in kinds] == ["toc", "chapter", "quote", "chapter", "chapter", "end"]
    assert kinds[0][1] == 15.0  # 목차를 부르는 문장 장면에서
    assert kinds[1][1:] == (30.0, 30.0 + le.CHAPTER_CARD_SEC) and kinds[2][1] == 45.0
    assert kinds[-1][1] == 105.0 and kinds[-1][2] == total  # 채널 끝 인사부터 끝 화면
    assert [round(c.start) for c in edit.chapters] == [0, 30, 60, 90] and len(chrome) == 4
    assert len(shade) == 1 and shade[0][1:] == (0.0, total)
    # 카드에 가린 자막은 빼고, 강조 자막은 흐름이 늘어질 때만(매머드빵 색 글자)
    assert all(not (a <= (s + e) / 2 < b) for _p, s, e in subs for _q, a, b in cards)
    emph = next(p for p, a, _b in subs if a == 50.0)
    reds = [
        px for px in Image.open(emph).convert("RGBA").getdata() if px[3] > 200 and px[0] > 200 and px[1] < 80
    ]
    assert len(reds) > 500  # 센 말은 빨간 강조 자막
    names = {p.name for p, _a, _b in cards}
    assert all((tmp_path / "long" / n).exists() for n in names)
    whoosh = [at for at, p in edit.sfx if p.stem == "whoosh"]
    assert {30.0, 60.0, 90.0} <= set(whoosh) and any(p.stem == "hit" for _at, p in edit.sfx)
    assert all(p.suffix == ".mp4" for _at, p in edit.sfx)  # 제작실에 올릴 수 있게
    assert [m[2] for m in edit.markers] == ["1. 감사원 '타이거파' 논란", "2. 한동훈 딸 재수사", "정리"]
    assert edit.youtube.splitlines()[0] == "0:00 오프닝"
    # timeline.xml: 그림 트랙 4개 + 음성 + 효과음 + 배경음악(끝까지 반복, 크기 낮춤) + 챕터 마커
    narr, bgm = tmp_path / "narration.mp4", tmp_path / "bgm.mp4"
    for path, sec in ((narr, total), (bgm, 50.0)):
        subprocess.run(
            ["ffmpeg", "-y", "-loglevel", "error", "-f", "lavfi", "-i", "anullsrc=r=48000:cl=mono", "-t", str(sec),
             "-c:a", "aac", str(path)],
            check=True,
        )  # fmt: skip
    xml = build_xmeml(
        SceneSet(project="p", scenes=scenes),
        tmp_path,
        audio=narr,
        style="long",
        overlays=edit.tracks,
        sfx_list=edit.sfx,
        bgm=(bgm, 50.0, le.BGM_LEVEL),
        markers=edit.markers,
    )
    assert validate_xmeml(xml) == []
    assert xml.count("clipitem-o4-") == len(cards) and xml.count("clipitem-o1-") == 1
    assert (
        xml.count("clipitem-a3-") == 3 and xml.count(f"<value>{le.BGM_LEVEL}</value>") == 3
    )  # 50초 × 3 ≥ 120초
    assert "narration.mp4" in xml and xml.count("clipitem-a2-") == len(edit.sfx)
    assert "1. 감사원 '타이거파' 논란" in xml and "<name>챕터</name>" in xml


def test_three_minute_single_topic_has_no_toc(tmp_path):
    """3분 단독: 콜드 오픈 · 주제 하나 · 정리 — 목차 카드 없이 챕터 카드부터, 끝 화면까지."""
    scenes = [sc for sc in _project(tmp_path) if not sc.topic.startswith("한동훈")]
    for k, sc in enumerate(scenes):
        sc.start_sec, sc.end_sec = k * 15.0, k * 15.0 + 15.0
    total = 90.0
    lines = [Cue(k + 1, k * 5.0, k * 5.0 + 4.5, f"자막 {k}번 줄입니다") for k in range(18)]
    edit = le.build(tmp_path, scenes, lines, total=total, channel="정치는푸르다")
    kinds = [p.name.split("_")[0] for p, _a, _b in edit.tracks[-1]]
    assert kinds == ["chapter", "chapter", "end"]  # 주제 하나 + 정리, 목차 없음
    assert [c.no for c in edit.chapters] == [0, 1, -1]
    assert edit.youtube.splitlines() == ["0:00 오프닝", "0:30 1. 감사원 '타이거파' 논란", "1:00 정리"]


def test_subtitle_marks_and_flash_merge():
    """강조는 줄의 센 말·짧은 인용만 색으로(따옴표가 반쪽인 줄은 강조 안 함), 1초도 안 뜨는 짧은 꼬리 줄은 앞 줄에 붙는다."""
    from newsclip.render import EMPH_RED, EMPH_YELLOW

    assert le._mark("'응급전용 주차' 논란에 휘말렸고,", set()) == (
        "'응급전용 주차'",
        EMPH_YELLOW,
    )  # 흔한 '논란'보다 인용
    assert le._mark("박지원 의원이 막말을 했습니다", set()) == ("막말을", EMPH_RED)
    assert le._mark("주차' 논란에 휘말렸고,", set()) is None  # 인용이 끊긴 줄
    lines = [
        Cue(1, 0.0, 2.5, '"소방이 가장 큰 골치 아픈 재정 빨대 같다"고'),
        Cue(2, 2.5, 3.0, "말했는데요,"),
        Cue(3, 3.0, 3.6, "바로 이어서"),
        Cue(4, 3.6, 6.0, "부 의원이 마이크를 잡았습니다."),
    ]
    out = le._merge_flashes(lines)
    assert [c.text for c in out] == [
        '"소방이 가장 큰 골치 아픈 재정 빨대 같다"고\n말했는데요,',
        "바로 이어서 부 의원이 마이크를 잡았습니다.",
    ]
    assert (out[0].start, out[0].end, out[1].start, out[1].end) == (0.0, 3.0, 3.0, 6.0)


def test_cards_render_and_fit(tmp_path):
    ch = le.Chapter(2, "아주 긴 챕터 제목이 들어가도 넘치지 않게", "요약 한 줄", 0, 10)
    out = le.render_chapter_card(ch, None, tmp_path / "c.png")
    assert Image.open(out).size == le.SIZE
    q = le.render_quote_card(
        {"text": "말", "who": "누구", "when": ""}, None, ch, "정치는푸르다", tmp_path / "q.png"
    )
    assert Image.open(q).size == le.SIZE
    sub = le.render_sub("자막 한 줄", tmp_path / "s.png")
    im = Image.open(sub)
    assert im.mode == "RGBA" and im.getbbox()[3] <= le.SUB_BOTTOM + 30  # 아래 끝을 재생 막대 위에
    # 강조 줄도 글꼴·크기는 보통 줄과 같고(높이 같음), 그 말만 색
    plain = Image.open(le.render_sub("검찰이 결국 영장을 쳤다", tmp_path / "p.png"))
    em = Image.open(le.render_sub("검찰이 결국 영장을 쳤다", tmp_path / "e.png", le.RED, "영장"))
    assert plain.getbbox()[1] == em.getbbox()[1] and plain.getbbox()[3] == em.getbbox()[3]
    px = [em.getpixel((x, y)) for x in range(0, le.SIZE[0], 3) for y in range(800, 1000, 3)]
    assert any(p[3] > 200 and p[0] > 200 and p[1] < 80 for p in px)  # 빨간 글자
    assert any(p[3] > 200 and min(p[:3]) > 230 for p in px)  # 나머지는 흰 글자


def test_first_sentence_is_plain_subtitles_by_default(tmp_path):
    """기본(HOOK_CARD 끔): 첫 문장도 센 말만 크게 띄우지 않고 말한 그대로 보통 자막, 강조 색·팝 없이."""
    assert le.HOOK_CARD is False
    scenes = _project(tmp_path)
    lines = [
        Cue(1, 0.2, 1.8, "오세훈 서울시장이"),
        Cue(2, 1.8, 3.6, "항소심에서 무죄를 받았습니다!"),
        *[Cue(k + 3, 4.0 + k * 5.0, 8.5 + k * 5.0, f"자막 {k}번 줄입니다") for k in range(20)],
    ]
    edit = le.build(tmp_path, scenes, lines, total=120.0, channel="정치는푸르다")
    subs = edit.tracks[1]
    assert not [p for p, _a, _b in subs if p.name.startswith("hook_")]
    assert [(a, b_) for _p, a, b_ in subs if a < 3.6] == [(0.2, 1.8), (1.8, 3.6)]  # 두 줄 다 보통 자막
    assert not any(p.stem == "hit" and at < 3.6 for at, p in edit.sfx)


def test_opening_hook_fast_cuts_and_speaker_photo(tmp_path, monkeypatch):
    """초반 30초(HOOK_CARD 켬): 첫 문장은 화면 한가운데 큰 글씨(따옴표 안 발언은 손글씨 노랑), 콜드 오픈 장면은
    사진을 더 자주 바꾸고, 발언 카드 사진은 그 말을 한 사람 이름이 제목에 든 사진만 쓴다."""
    monkeypatch.setattr(le, "HOOK_CARD", True)
    scenes = _project(tmp_path)
    extra = tmp_path / "assets" / "s001_2.jpg"
    Image.new("RGB", (1600, 1000), (200, 120, 60)).save(extra)
    b = asset(
        "naver_image",
        "https://i/extra.jpg",
        local_path=str(extra),
        width=1600,
        height=1000,
        title="다른 사진",
    )
    scenes[0].candidates.append(b)
    lines = [
        Cue(1, 0.2, 1.8, "박지원 의원이 김영신 감사위원을"),
        Cue(2, 1.8, 3.6, "‘타이거파 부두목’이라고 불렀습니다!"),
        *[Cue(k + 3, 4.0 + k * 5.0, 8.5 + k * 5.0, f"자막 {k}번 줄입니다") for k in range(20)],
    ]
    quotes = {"s004": {"text": "타이거파 부두목, 사퇴하라", "who": "박지원 더불어민주당 의원"}}
    edit = le.build(tmp_path, scenes, lines, total=120.0, channel="정치는푸르다", quotes=quotes)
    subs = edit.tracks[1]
    hook = [(p, a, b_) for p, a, b_ in subs if p.name.startswith("hook_")]
    # 조각으로 넘기지 않고 센 말(따옴표 발언) 한 줄이 첫 문장 내내 — 아래 자막은 그 말이 든 줄만 빠진다
    assert len(hook) == 1 and hook[0][1] == 0.2 and hook[0][2] == 3.6
    under = [a for p, a, _b in subs if a < 3.6 and not p.name.startswith("hook_")]
    assert under == [0.2]  # '박지원 의원이 김영신 감사위원을'은 아래 자막으로 남는다
    yellow = [
        px
        for px in Image.open(hook[0][0]).convert("RGBA").getdata()
        if px[3] > 200 and px[2] < 60 and px[0] > 200
    ]
    assert len(yellow) > 2000  # 발언은 손글씨 노랑
    assert any(p.stem == "hit" and abs(at - hook[0][1]) < 1.5 for at, p in edit.sfx)  # 핵심 발언에 팝
    first = [s for s in edit.v1 if s.id.startswith("s001")]
    assert [s.id for s in first] == ["s001", "s001b"] and first[0].chosen.local_path != first[
        1
    ].chosen.local_path
    assert first[0].end_sec == first[1].start_sec and first[1].end_sec == 15.0
    assert [s.id for s in edit.v1 if s.id.startswith("s005")] == ["s005"]  # 콜드 오픈 뒤는 그대로
    by_id = {s.id: s for s in scenes}
    ch = [c for c in edit.chapters if c.no == 1][0]
    assert le._speaker_photo(quotes["s004"], by_id["s004"], ch.scenes) == Path(
        by_id["s003"].chosen.local_path
    )
    assert (
        le._speaker_photo({"who": "홍길동 의원"}, by_id["s004"], ch.scenes) is None
    )  # 다른 사람 사진은 안 붙인다
