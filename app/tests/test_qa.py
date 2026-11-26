"""자동 검수(studio_app.qa): 사용자가 찾아 주던 잔실수를 렌더 직후 스스로 잡는다."""

from newsclip.srt import Cue
from studio_app import qa


def test_subtitle_issues_find_awkward_cuts_but_not_long_quotes():
    cues = [
        Cue(1, 2.0, 3.3, "10월 20일 화요일 국무회의"),
        Cue(2, 3.3, 5.0, "도중 역삼동 화재 보고를"),
        Cue(3, 5.0, 6.7, "세종에서 화상으로 참석"),
        Cue(4, 6.7, 8.5, "중이던 최용철 소방청장에게"),
        Cue(5, 8.5, 9.5, '"인명 구조·소방대원'),
        Cue(6, 9.5, 11.0, '안전 확보에 만전 기해 달라"고'),
        Cue(7, 11.0, 11.2, "주문했는데요,"),
        Cue(8, 11.2, 13.0, "끝났습니다."),
    ]
    got = qa.subtitle_issues(cues)
    cuts = [i["what"] for i in got if i["kind"] == "subtitle_cut"]
    assert any("국무회의 | 도중" in c for c in cuts) and any("참석 | 중이던" in c for c in cuts)
    assert not any("소방대원 | 안전" in c for c in cuts)  # 긴 인용 안은 어쩔 수 없다
    assert any(i["kind"] == "subtitle_flash" and i["at"] == 11.0 for i in got)


def test_photo_issues_flag_watermark_sites(tmp_path):
    scenes = [
        {"start": 0.0, "end": 2.0, "attribution": "출처: imgnews.naver.net", "files": []},
        {"start": 49.6, "end": 52.1, "attribution": "출처: cdn.crowdpic.net", "files": []},
    ]
    got = qa.photo_issues(scenes, tmp_path)
    assert [(i["kind"], i["at"], i["until"]) for i in got] == [("photo_watermark", 49.6, 52.1)]


def test_fix_subtitles_recuts_with_word_times():
    """걸린 자막은 앞뒤와 이어 지금 규칙으로 다시 나누고, 줄 시작은 받은 낱말 시각에 맞춘다."""
    cues = [
        Cue(1, 2.104, 3.31, "10월 20일 화요일 국무회의"),
        Cue(2, 3.31, 4.45, "도중 역삼동 화재 보고를"),
        Cue(3, 4.48, 5.36, "받은 이재명 대통령은,"),
    ]
    words = [
        ["10월", 2.189, 2.381],
        ["20일", 2.415, 2.618],
        ["화요일", 2.652, 2.9],
        ["국무회의", 2.979, 3.295],
        ["도중", 3.329, 3.532],
        ["역삼동", 3.577, 3.972],
        ["화재", 4.006, 4.187],
        ["보고를", 4.243, 4.446],
        ["받은", 4.48, 4.649],
        ["이재명", 4.717, 4.954],
        ["대통령은,", 4.988, 5.36],
    ]
    new, fixed = qa.fix_subtitles(cues, words, 14)
    assert fixed and not qa.subtitle_issues(new)
    hit = next(c for c in new if c.text.startswith("국무회의"))
    assert hit.start == 2.979
    # 낱말 시각이 없으면(직접 올린 음성) 시각을 어림하게 되니 고치지 않는다
    assert qa.fix_subtitles(cues, None, 14)[1] == []


def test_fix_photos_borrows_neighbour_when_chosen_is_watermarked(tmp_path):
    scenes = [
        {
            "start": 47.8,
            "end": 49.6,
            "attribution": "출처: gangnam.go.kr",
            "chosen": "assets/s017_1.jpg",
            "files": ["assets/s017_1.jpg"],
        },
        {
            "start": 49.6,
            "end": 52.1,
            "attribution": "출처: cdn.crowdpic.net",
            "chosen": "assets/s018_1.jpg",
            "files": ["assets/s018_1.jpg", "assets/s018_2.jpg"],
        },
    ]
    new, fixed = qa.fix_photos(scenes, tmp_path)
    assert [f["kind"] for f in fixed] == ["photo_watermark"]
    assert new[1]["chosen"] == "assets/s017_1.jpg" and new[1]["attribution"] == "출처: gangnam.go.kr"
    assert scenes[1]["chosen"] == "assets/s018_1.jpg"  # 원본은 그대로


def test_worker_can_drop_a_mismatched_photo(tmp_path):
    """작업자가 장면 모음을 보고 문장과 안 맞는다고 고른 사진(--drop)은 같은 장면 다른 후보로, 없으면 옆 장면 사진으로."""
    scenes = [
        {
            "start": 11.4,
            "end": 14.9,
            "attribution": "출처: a",
            "chosen": "assets/s005_1.jpg",
            "files": ["assets/s005_1.jpg"],
        },
        {
            "start": 14.9,
            "end": 17.8,
            "attribution": "출처: b",
            "chosen": "assets/s006_1.jpg",
            "files": ["assets/s006_1.jpg", "assets/s006_2.jpg"],
        },
        {
            "start": 17.8,
            "end": 21.5,
            "attribution": "출처: c",
            "chosen": "assets/s007_1.jpg",
            "files": ["assets/s007_1.jpg"],
        },
    ]
    new, fixed = qa.fix_photos(scenes, tmp_path, ("@15.5",))
    assert new[1]["chosen"] == "assets/s006_2.jpg" and [f["kind"] for f in fixed] == ["photo_mismatch"]
    new, _ = qa.fix_photos(scenes, tmp_path, ("assets/s007_1.jpg",))
    assert (
        new[2]["chosen"] == "assets/s006_1.jpg" and new[2]["attribution"] == "출처: b"
    )  # 옆 장면 사진을 출처째


def test_photo_sheet_has_one_tile_per_scene(tmp_path):
    from PIL import Image

    from studio_app.qa import SHEET_COLS, SHEET_W, photo_sheet

    (tmp_path / "assets").mkdir()
    scenes = []
    for k in range(10):
        Image.new("RGB", (1600, 900 if k % 2 else 1600), (k * 20, 80, 120)).save(
            tmp_path / "assets" / f"s{k:03d}_1.jpg"
        )
        scenes.append({"id": f"s{k:03d}", "start": k * 3.0, "chosen": f"assets/s{k:03d}_1.jpg"})
    scenes.append({"id": "s010", "start": 30.0, "chosen": None})  # 사진 없는 장면은 건너뜀
    out = photo_sheet(scenes, tmp_path, tmp_path / "sheet.jpg")
    w, h = Image.open(out).size
    assert w == SHEET_COLS * SHEET_W and h == 2 * (SHEET_W * 9 // 16 + 18)
    assert photo_sheet([{"id": "s0", "chosen": None}], tmp_path, tmp_path / "x.jpg") is None
