"""제작실 작업자: 수집 묶음(문서 크기 안으로) · 기사 본문 · 자료·timeline.xml(경로 자리표시)."""

import asyncio
import json
from pathlib import Path

from studio_app import workroom as wr

DEMO = Path(__file__).resolve().parents[2] / "docs" / "demo"


def test_article_body_yna_and_generic():
    yna = 'x "BODY":"<p>(서울=연합뉴스) 기자 = 첫 문단입니다.</p><p>둘째 문단 \\"인용\\"</p><p>kim@yna.co.kr</p>","HEAD":1'
    body = wr.article_body(yna)
    assert body.startswith("(서울=연합뉴스)") and '"인용"' in body and "@" not in body
    generic = (
        "<p>짧음</p><p>" + "가" * 40 + "</p><p>[사진] 무단 전재 금지 문단입니다 아주 길게 써 둔 문단</p>"
    )
    assert wr.article_body(generic) == "가" * 40
    tail = (
        "<p>"
        + "나" * 40
        + "</p><p>&lt;저작권자(c) 연합뉴스, 무단 전재-재배포 금지&gt;</p><p>"
        + "관련 기사 제목 " * 5
        + "</p>"
    )
    assert wr.article_body(tail) == "나" * 40  # 본문 끝 뒤의 관련 기사·광고는 버린다


def test_related_articles_and_chunk():
    posts = [{"title": "임은정 감찰 지시 너무하네"}, {"title": "임은정 청장 감찰 반발"}]
    arts = [
        {"id": "a1", "title": "행안장관 임은정 감찰 지시"},
        {"id": "a2", "title": "날씨 맑음"},
        {"id": "a3", "title": "임은정 서글퍼"},
    ]
    assert wr.related_articles(posts, arts, set(), 5) == ["a1"]
    items = [{"t": "가" * 1000} for _ in range(50)]
    docs = wr.chunk(items, "posts", limit=10_000)
    assert len(docs) > 1 and sum(len(d["posts"]) for d in docs) == 50
    assert all(len(json.dumps(d, ensure_ascii=False).encode()) <= 10_000 + 3100 for d in docs)


def test_folder_url_and_rebase(tmp_path):
    assert wr.folder_url("C:\\Users\\김\\Videos") == "file://localhost/C:/Users/%EA%B9%80/Videos"
    assert wr.folder_url("/Users/kim/Movies/") == "file://localhost/Users/kim/Movies"
    from newsclip.fcpxml import path_url

    xml = f"<pathurl>{path_url(tmp_path)}/assets/a.jpg</pathurl>"
    assert wr.rebase_xml(xml, tmp_path) == "<pathurl>file://localhost/__STUDIO_BASE__/assets/a.jpg</pathurl>"


def test_collect_bundle(root, monkeypatch, tmp_path):
    monkeypatch.setenv("STUDIO_NOW", "2026-10-08T18:00")
    meta = asyncio.run(wr.collect(tmp_path / "out"))
    out = tmp_path / "out"
    assert meta["kind"] == "studio-raw" and meta["posts"] > 0 and meta["articles"] > 0
    assert meta["post_docs"] == len(list(out.glob("posts-*.json"))) >= 1
    for f in out.glob("*-*.json"):
        assert f.stat().st_size <= wr.DOC_LIMIT + 4000
    posts = json.loads((out / "posts-1.json").read_text(encoding="utf-8"))["posts"]
    assert all("nick" not in json.dumps(p, ensure_ascii=False) for p in posts)
    assert any(p["top_comments"] for p in posts)
    assert "group_task" in meta["rules"] and meta["formats"]["short"]["target_chars"] == [810, 990]
    assert meta["sites"]["다모앙"]["posts"] >= 0


def test_sources_makes_timeline_with_placeholder(root, tmp_path):
    script = (DEMO / "2026-10-08_1810_short" / "script.txt").read_text(encoding="utf-8")
    req = {
        "id": "2026-10-08_1900_short",
        "format": "short",
        "script": script,
        "audio": {
            "duration": 175.0,
            "silences": [[6.0 * i + 5.5, 6.0 * i + 6.0] for i in range(28)],
            "ext": ".mp3",
        },
    }
    man = asyncio.run(wr.sources(req, tmp_path / "out"))
    out = tmp_path / "out"
    xml = (out / "timeline.xml.txt").read_text(encoding="utf-8")
    assert "file://localhost/__STUDIO_BASE__/" in xml and str(root) not in xml
    # timeline.xml이 가리키는 파일은 음성 말고 전부 압축 파일에 들어간다(출처 표시 그림 captions/ 포함)
    import re as _re

    wanted = set(_re.findall(r"__STUDIO_BASE__/([^<]+)</pathurl>", xml)) - {"narration.wav"}
    assert wanted and wanted <= {f["name"] for f in man["files"]}
    assert any(n.startswith("captions/") for n in wanted)
    assert man["report"]["scenes"] > 0 and man["cues"] > 0 and man["audio_name"] == "narration.mp3"
    assert man["files"] and all(
        Path(f["path"]).exists() and f["name"].startswith(("assets/", "captions/")) for f in man["files"]
    )
    # 클라우드에서는 영상을 받지 않고 장면별 유튜브 후보 링크로만 남긴다
    assert {f["media"] for f in man["files"]} == {"image"}
    vids = [v for s in man["scenes"] for v in s["videos"]]
    assert vids and all(v["url"].startswith("https://www.youtube.com/watch?v=") for v in vids)
    assert man["report"]["video_scenes"] == sum(1 for s in man["scenes"] if s["videos"])
    firsts = [s["chosen"] for s in man["scenes"] if s["chosen"]]
    assert len(set(firsts)) == len(firsts)  # 장면마다 다른 사진
    chosen = [s["chosen"] for s in man["scenes"] if s["chosen"]]
    assert chosen and set(chosen) <= {f["name"] for f in man["files"]}
    srt = (out / "narration.srt.txt").read_text(encoding="utf-8")
    assert "-->" in srt
    texts = [b.split("\n", 2)[2] for b in srt.strip().split("\n\n")]
    assert texts and all(len(x) <= 24 for x in texts)  # 자막용: 한 줄 20자 안팎
    assert json.loads((out / "manifest.json").read_text(encoding="utf-8"))["project"] == req["id"]


def test_scene_plan_then_queries(root, tmp_path):
    """장면 나누기만 먼저(작업자가 읽고 검색어를 씀) → 같은 장면 ID로 검색어를 넣어 자료 만들기."""
    script = (DEMO / "2026-10-08_1810_short" / "script.txt").read_text(encoding="utf-8")
    req = {
        "id": "2026-10-08_1910_short",
        "script": script,
        "audio": {"duration": 175.0, "silences": [[6.0 * i + 5.5, 6.0 * i + 6.0] for i in range(28)]},
    }
    plan = wr.plan_scenes(req, tmp_path / "plan")
    assert plan and json.loads((tmp_path / "plan" / "scenes-plan.json").read_text(encoding="utf-8")) == plan
    queries = {
        "scenes": {plan[0]["id"]: "가상정당 대표 기자회견"},
        "videos": {plan[0]["topic"]: "가상정당 대표 발언"},
    }
    man = asyncio.run(wr.sources(req, tmp_path / "out", queries=queries))
    assert [s["id"] for s in man["scenes"]] == [p["id"] for p in plan]
    assert man["scenes"][0]["query"] == "가상정당 대표 기자회견" and man["audio_name"] == "narration.wav"


def test_sources_without_voice_and_no_audio_track(root, tmp_path):
    """음성·자막은 사용자가 넣는다: 음성 분석값 없이도 장면 시각을 어림해 자료를 만들고, timeline.xml에 음성 트랙은 없다."""
    script = (DEMO / "2026-10-08_1810_short" / "script.txt").read_text(encoding="utf-8")
    man = asyncio.run(
        wr.sources({"id": "2026-10-08_1920_short", "script": script, "audio": None}, tmp_path / "out")
    )
    xml = (tmp_path / "out" / "timeline.xml.txt").read_text(encoding="utf-8")
    assert man["timing"] == "estimated" and man["scenes"] and "narration.wav" not in xml
    assert all(s["end"] > s["start"] for s in man["scenes"])


def test_sources_with_voice_uses_heard_words(root, tmp_path, monkeypatch):
    """화면이 올린 음성이 있으면 인식 결과로 시각을 맞추고(timing voice+asr), 듣지 못하면 쉼만으로 맞추며 이유를 남긴다."""
    import newsclip.asr as asr
    from newsclip.autosub import narration_text

    script = (DEMO / "2026-10-08_1810_short" / "script.txt").read_text(encoding="utf-8")
    toks = narration_text(script).split()
    words = [(w, round(i * 0.4, 2), round(i * 0.4 + 0.35, 2)) for i, w in enumerate(toks)]
    voice = tmp_path / "voice.webm"
    voice.write_bytes(b"x")
    req = {
        "id": "2026-10-08_1930_short",
        "script": script,
        "audio": {"duration": len(toks) * 0.4, "silences": []},
    }
    monkeypatch.setattr(asr, "transcribe", lambda path, cache_dir=None: words)
    man = asyncio.run(wr.sources(req, tmp_path / "out", voice=voice))
    assert man["timing"] == "voice+asr" and man["asr"]["words"] == len(words)
    assert man["asr"]["heard"] >= man["asr"]["bounds"] * 0.9

    def deaf(path, cache_dir=None):
        raise asr.AsrUnavailable("ffmpeg가 없습니다")

    monkeypatch.setattr(asr, "transcribe", deaf)
    man = asyncio.run(
        wr.sources(
            {**req, "audio": {"duration": 100.0, "silences": [[10.0, 10.5]]}}, tmp_path / "o2", voice=voice
        )
    )
    assert man["timing"] == "voice" and "ffmpeg" in man["asr"]["error"]


def test_upload_plan_reuses_same_files_and_lists_cleanup(tmp_path):
    """다시 만들 때 내용이 같은 사진은 다시 올리지 않고, 안 쓰게 된 옛 자산은 화면이 지우도록 cleanup에 남긴다."""
    out = tmp_path / "src"
    (out / "assets").mkdir(parents=True)
    same, new = out / "assets" / "s001_1.jpg", out / "assets" / "s002_1.jpg"
    same.write_bytes(b"same photo")
    new.write_bytes(b"new photo")
    (out / "timeline.xml.txt").write_text("<xmeml/>", encoding="utf-8")
    (out / "narration.srt.txt").write_text("1\n00:00:00,000 --> 00:00:01,000\n가\n", encoding="utf-8")
    man = {
        "made_at": "now",
        "scenes": [],
        "report": {"scenes": 2},
        "base_token": "b",
        "timing": "voice+asr",
        "files": [
            {"name": "assets/s001_1.jpg", "path": str(same), "media": "image", "sha": wr._sha(same)},
            {"name": "assets/s002_1.jpg", "path": str(new), "media": "image", "sha": wr._sha(new)},
        ],
    }
    prev = {
        "audio": {"asset_id": "voice"},
        "result": {
            "files": [
                {"name": "assets/s009_1.jpg", "id": "keep", "sha": wr._sha(same)},
                {"name": "assets/s002_1.jpg", "id": "gone", "sha": "old"},
            ],
            "timeline_id": "old-tl",
            "srt_id": "old-srt",
            "cleanup": ["leftover", "voice"],
            "report": {"scenes": 9, "skipped_sources": {"x": 1}},
            "old_key": 1,
        },
    }
    plan = wr.upload_plan(man, prev, out)
    assert plan["reuse"] == {"assets/s001_1.jpg": "keep"} and plan["batches"] == [[str(new)]]
    assert len(plan["texts"]) == 2 and plan["cleanup"] == ["gone", "leftover", "old-srt", "old-tl"]
    up = {str(new): "n1", str(out / "timeline.xml.txt"): "tl", "narration.srt.txt": "srt"}
    doc = wr.result_doc(man, plan, up, prev, out)["result"]
    assert (
        [f["id"] for f in doc["files"]] == ["keep", "n1"]
        and doc["timeline_id"] == "tl"
        and doc["srt_id"] == "srt"
    )
    assert doc["old_key"] == {"__delete__": True} and doc["report"]["skipped_sources"] == {"__delete__": True}
    assert doc["timing"] == "voice+asr" and doc["cleanup"] == plan["cleanup"]
    # 다음 번: 아무것도 안 바뀌면 하나도 안 올린다
    again = wr.upload_plan(man, {"result": doc}, out)
    assert again["batches"] == [] and again["texts"] == [] and again["cleanup"] == plan["cleanup"]


def test_sources_with_slides_draws_slides_instead_of_photos(root, tmp_path):
    """증시 채널: slides.json이 있으면 사진을 찾지 않고 장면마다 슬라이드를 그린다(빈 장면은 앞 슬라이드 유지, 출처 표시 없음)."""
    script = (DEMO / "2026-10-08_1810_short" / "script.txt").read_text(encoding="utf-8")
    req = {"id": "2026-10-13_0900_short", "script": script, "audio": None}
    plan = wr.plan_scenes(req, tmp_path / "plan")
    first, third = plan[0]["id"], plan[2]["id"]
    slides = {
        "source": "자료: 연합뉴스",
        "slides": {
            first: {
                "layout": "number",
                "kicker": "국내 증시 마감",
                "label": "코스피",
                "value": "6,384.24",
                "change": "241.69 (3.65%)",
                "dir": "down",
            },
            third: {"layout": "bullets", "title": "왜 빠졌나", "bullets": ["반도체 약세", "환율 1,450원"]},
        },
    }
    man = asyncio.run(wr.sources(req, tmp_path / "out", slides=slides))
    names = [f["name"] for f in man["files"]]
    assert f"assets/{first}_slide.png" in names and f"assets/{third}_slide.png" in names
    assert len([n for n in names if n.endswith("_slide.png")]) == 2
    assert man["scenes"][1]["chosen"].endswith(f"{first}_slide.png")  # 내용 없는 장면은 앞 슬라이드
    assert man["report"]["by_source"] == {"slide": len(plan)} and man["report"]["borrowed"] == len(plan) - 2
    xml = (tmp_path / "out" / "timeline.xml.txt").read_text(encoding="utf-8")
    assert "cap_" not in xml  # 슬라이드는 출처 표시 그림을 깔지 않는다


def test_sources_shorts_makes_vertical_timeline(root, tmp_path):
    """세로 쇼츠: 1080x1920 시퀀스, 사진도 세로 화면, 상단 제목(썸네일 문구)이 맨 위 트랙에 처음부터 끝까지, 자막 줄은 짧게."""
    import re as _re

    from PIL import Image

    script = (DEMO / "2026-10-08_1810_short" / "script.txt").read_text(encoding="utf-8")
    req = {
        "id": "2026-10-13_1900_shorts",
        "format": "shorts",
        "thumb": "이게 사과입니까",
        "script": script,
        "audio": None,
    }
    man = asyncio.run(wr.sources(req, tmp_path / "out"))
    assert man["size"] == [1080, 1920]
    xml = (tmp_path / "out" / "timeline.xml.txt").read_text(encoding="utf-8")
    fmt = xml.split("<format>", 1)[1].split("</format>", 1)[0]
    assert "<width>1080</width>" in fmt and "<height>1920</height>" in fmt
    heads = [f for f in man["files"] if f["name"].startswith("overlays/headline_")]
    assert len(heads) == 1 and heads[0]["name"] in xml
    total = int(_re.search(r"<sequence[^>]*>.*?<duration>(\d+)</duration>", xml, _re.S).group(1))
    head_clip = xml.split('id="clipitem-head-1"', 1)[1].split("</clipitem>", 1)[0]
    assert "<start>0</start>" in head_clip and f"<end>{total}</end>" in head_clip
    photo = next(f for f in man["files"] if f["name"].startswith("assets/"))
    assert Image.open(photo["path"]).size == (1080, 1920)
    srt = (tmp_path / "out" / "narration.srt.txt").read_text(encoding="utf-8")
    texts = [b.split("\n", 2)[2] for b in srt.strip().split("\n\n")]
    assert texts and all(len(x) <= 18 for x in texts)  # 세로 화면: 한 줄 14자 안팎


def test_vertical_only_for_shorts_format():
    assert wr.is_vertical_format({"format": "shorts"})
    assert not wr.is_vertical_format({"format": "short"}) and not wr.is_vertical_format({})
