"""fixture로 collect → 주제판 → 10분 묶음·3분 단독 → rewrite → made → history → probe까지 CLI 통과."""

import json

import pytest
from typer.testing import CliRunner

from topicradar.cli import app

runner = CliRunner()


def _run(monkeypatch, now, *args, code=0):
    monkeypatch.setenv("STUDIO_NOW", now)
    res = runner.invoke(app, list(args))
    assert res.exit_code == code, res.output
    return res.output


def test_full_flow(demo_env, monkeypatch):
    root = demo_env
    out = _run(monkeypatch, "2026-10-10T17:00", "collect", "--since", "2026-10-05T00:00")
    assert "naver_news" in out and "rss" in out
    # 커뮤니티: 목록 + 반응 큰 글의 본문·댓글 → 화제 검색어 → 근거 기사 추가 수집
    assert "community:보배드림(본문·댓글)" in out and "커뮤니티 화제 → 뉴스 검색어" in out
    out = _run(monkeypatch, "2026-10-08T18:00", "board", "--notify")
    assert "주제판 2026-10-08_180000" in out and "10분 묶음 추천" in out and "Slack 전송 완료" in out

    from studio_core.config import load_settings
    from topicradar import pipeline

    store = pipeline.open_store(load_settings())
    board = store.latest_board()
    names = [t.name for t in board.topics]
    # 100% 커뮤니티 반응 순: 커뮤니티 글이 없는 뉴스 주제는 판에 없다
    assert all(t.opinion.posts >= 1 for t in board.topics) and len(names) >= 5
    assert any("플랫폼노동자 기본법" in n for n in names[:3]) and any("정치자금" in n for n in names[:3])
    assert [t.score for t in board.topics] == sorted((t.score for t in board.topics), reverse=True)
    lead = board.topics[0]
    assert lead.opinion.samples and any(s.kind == "comment" and s.votes for s in lead.opinion.samples)
    assert board.bundles and all(2 <= len(b.topic_ids) <= 3 for b in board.bundles)
    assert board.bundles[0].link.endswith("이야기입니다.")

    # 10분 묶음(추천 묶음 1)
    _run(monkeypatch, "2026-10-08T18:05", "video", "--format", "long", "--bundle", "1")
    long_dir = root / "projects/2026-10-08_1805_long"
    plan = json.loads((long_dir / "plan.json").read_text(encoding="utf-8"))
    assert plan["format"] == "long" and 2 <= len(plan["items"]) <= 3 and plan["link"]
    assert (
        plan["target_chars"] == [2700, 3300] and abs(sum(i["est_seconds"] for i in plan["items"]) - 600) <= 10
    )
    script = (long_dir / "script.txt").read_text(encoding="utf-8")
    heads = [ln for ln in script.splitlines() if ln.startswith("## ")]
    assert len(heads) == len(plan["items"]) + 1 and heads[-1] == "## 정리"
    assert "#확인: 커뮤니티 주장" in script
    report = (long_dir / "report.md").read_text(encoding="utf-8")
    assert "10분 묶음" in report and "## 커뮤니티 의견" in report and "대표 글·댓글" in report

    # 3분 단독(판 1위) — 같은 분에 또 만들면 폴더 이름이 겹치지 않는다
    _run(monkeypatch, "2026-10-08T18:10", "video", "--format", "short", "--pick", "1")
    _run(monkeypatch, "2026-10-08T18:10", "video", "--format", "short", "--pick", "2")
    short = json.loads((root / "projects/2026-10-08_1810_short/plan.json").read_text(encoding="utf-8"))
    assert len(short["items"]) == 1 and short["items"][0]["topic"]["id"] == lead.topic_id
    assert short["target_chars"] == [810, 990] and (root / "projects/2026-10-08_1810-2_short").is_dir()
    # 형식에 맞지 않게 고르면 거절
    bad = _run(monkeypatch, "2026-10-08T18:12", "video", "--format", "long", "--pick", "1", code=1)
    assert "10분 묶음은 연관 주제 2~3개" in str(bad) or bad == ""
    # 만든 영상은 주제에 '다룸'으로 남는다(다음 판에 표시)
    assert "2026-10-08_1810_short" in store.get_topic(lead.topic_id).covered_in

    out = _run(monkeypatch, "2026-10-08T18:20", "rewrite", "--project", str(long_dir))
    assert "2026-10-08_1805_long" in out
    _run(
        monkeypatch,
        "2026-10-08T21:00",
        "made",
        "--project",
        str(long_dir),
        "--video-id",
        "demo_long1008",
        "--title",
        "기본법 통과, 새한당은 끝까지 반대",
    )
    _run(
        monkeypatch,
        "2026-10-08T21:00",
        "made",
        "--project",
        str(root / "projects/2026-10-08_1810_short"),
        "--video-id",
        "demo_short1008a",
    )
    hist = _run(monkeypatch, "2026-10-11T21:00", "history", "--weeks", "2")
    assert "형식별 성과" in hist and "10분 묶음" in hist and "3분 단독" in hist and "52,000" in hist
    assert "기본법 통과, 새한당은 끝까지 반대" in (root / "channel.yaml").read_text(encoding="utf-8")
    probe = _run(monkeypatch, "2026-10-10T17:00", "probe", "--name", "보배드림")
    assert "보배드림" in probe and "정치 필터 통과" in probe and "다모앙" not in probe
    # 댓글 가장 많은 글(선고 연기, 댓글 77) 하나의 상세: 댓글 내용과 공감 수까지 읽힌다
    assert "글 상세" in probe and "[44] 시간 끌기 아닌가요" in probe


def test_video_needs_board(demo_env, monkeypatch):
    out = _run(monkeypatch, "2026-10-08T18:00", "video", "--format", "short", "--pick", "1", code=2)
    assert "주제판이 없습니다" in out


def test_make_plan_validates_counts(demo_env, monkeypatch):
    from studio_core.config import load_channel, load_settings
    from topicradar import pipeline

    _run(monkeypatch, "2026-10-10T17:00", "collect", "--since", "2026-10-05T00:00")
    settings = load_settings()
    cfg, store = load_channel(settings), pipeline.open_store(settings)
    monkeypatch.setenv("STUDIO_NOW", "2026-10-08T18:00")
    board = pipeline.run_board(cfg, store, None)
    ids = [t.topic_id for t in board.topics]
    for picks, fmt, msg in (
        (ids[:2], "short", "하나만"),
        (ids[:1], "long", "2~3개"),
        (ids[:4], "long", "2~3개"),
        ([ids[0], ids[0]], "long", "두 번"),
    ):
        with pytest.raises(ValueError, match=msg):
            pipeline.run_video(settings, cfg, store, board.id, picks, fmt, None)
    with pytest.raises(KeyError):
        pipeline.run_video(settings, cfg, store, board.id, ["nope", ids[0]], "long", None)
    with pytest.raises(FileNotFoundError):
        pipeline.run_video(settings, cfg, store, "no-board", ids[:1], "short", None)
