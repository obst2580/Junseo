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
    assert man["report"]["scenes"] > 0 and man["cues"] > 0 and man["audio_name"] == "narration.mp3"
    assert man["files"] and all(
        Path(f["path"]).exists() and f["name"].startswith("assets/") for f in man["files"]
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
    assert "-->" in (out / "narration.srt.txt").read_text(encoding="utf-8")
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
