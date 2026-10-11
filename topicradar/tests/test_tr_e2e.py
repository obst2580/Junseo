"""fixture로 collect → 아침/저녁/주간 → made → history → pick까지 CLI 통과."""

import json

from typer.testing import CliRunner

from topicradar.cli import app
from topicradar.pipeline import daily_actions, default_day

from trh import kst

runner = CliRunner()


def _run(monkeypatch, now, *args):
    monkeypatch.setenv("STUDIO_NOW", now)
    res = runner.invoke(app, list(args))
    assert res.exit_code == 0, res.output
    return res.output


def test_full_flow(demo_env, monkeypatch):
    root = demo_env
    out = _run(monkeypatch, "2026-10-10T17:00", "collect", "--since", "2026-10-05T00:00")
    assert "naver_news" in out and "rss" in out
    # 커뮤니티 정치 게시판 → 화제 검색어 → 근거 기사 추가 수집
    assert "community" in out and "커뮤니티 화제 → 뉴스 검색어" in out
    _run(monkeypatch, "2026-10-07T05:30", "slot", "morning", "--date", "2026-10-07")
    _run(
        monkeypatch,
        "2026-10-07T05:30",
        "made",
        "--project",
        str(root / "projects/2026-10-07_morning"),
        "--video-id",
        "demo_m1007",
    )
    _run(monkeypatch, "2026-10-07T17:00", "slot", "evening", "--date", "2026-10-07", "--notify")
    plan = json.loads((root / "projects/2026-10-07_evening/plan.json").read_text(encoding="utf-8"))
    recalls = [i for i in plan["items"] if i["role"] == "포인트회수"]
    assert len(recalls) == 3 and sum(i["hit"] == "hit" for i in recalls) == 2
    assert "아침에 말씀드린 대로" in (root / "projects/2026-10-07_evening/script.txt").read_text(
        encoding="utf-8"
    )
    _run(
        monkeypatch,
        "2026-10-07T17:00",
        "made",
        "--project",
        str(root / "projects/2026-10-07_evening"),
        "--video-id",
        "demo_e1007",
        "--title",
        "법사위 통과가 바꾸는 것",
    )
    _run(monkeypatch, "2026-10-08T05:30", "slot", "morning", "--date", "2026-10-08")
    report = (root / "projects/2026-10-08_morning/report.md").read_text(encoding="utf-8")
    assert "전날 저녁에 다룸" in report
    _run(monkeypatch, "2026-10-08T17:00", "slot", "evening", "--date", "2026-10-08")
    ev = json.loads((root / "projects/2026-10-08_evening/plan.json").read_text(encoding="utf-8"))
    hits = {i["topic"]["id"]: i["hit"] for i in ev["items"] if i["role"] == "포인트회수"}
    assert sorted(hits.values()) == ["hit", "hit", "miss"]
    econ = sum(i["topic"]["category"] == "경제" for i in ev["items"])
    assert econ * 2 <= len(ev["items"])
    _run(monkeypatch, "2026-10-10T17:00", "slot", "weekly", "--date", "2026-10-11")
    wk = json.loads((root / "projects/2026-10-11_weekly/plan.json").read_text(encoding="utf-8"))
    assert wk["chains"] and any(i["role"] == "줄기" for i in wk["items"])
    wk_report = (root / "projects/2026-10-11_weekly/report.md").read_text(encoding="utf-8")
    assert "커뮤니티 반응" in wk_report and "뉴스 근거 없는 커뮤니티 화제" in wk_report  # 소문은 편성 안 함
    # pick: 주간 서브 교체
    sub = next(i for i in wk["items"] if i["role"] == "서브")
    other = ev["items"][0]["topic"]["id"]
    _run(
        monkeypatch,
        "2026-10-10T17:00",
        "pick",
        "--project",
        str(root / "projects/2026-10-11_weekly"),
        "--replace",
        str(sub["order"]),
        other,
    )
    wk2 = json.loads((root / "projects/2026-10-11_weekly/plan.json").read_text(encoding="utf-8"))
    assert next(i for i in wk2["items"] if i["order"] == sub["order"])["topic"]["id"] == other
    hist = _run(monkeypatch, "2026-10-11T21:00", "history", "--weeks", "2")
    assert "아침 포인트 적중률" in hist and "주제 선정 방식" in hist
    cfg = (root / "channel.yaml").read_text(encoding="utf-8")
    assert "법사위 통과가 바꾸는 것" in cfg
    out = _run(monkeypatch, "2026-10-08T12:00", "daily")
    assert "수집 결과" in out
    probe = _run(monkeypatch, "2026-10-10T17:00", "probe", "--name", "보배드림")
    assert "보배드림" in probe and "정치 필터 통과" in probe and "다모앙" not in probe


def test_daily_schedule_and_defaults():
    assert [a[0] for a in daily_actions(kst("2026-10-08 05:30"))] == ["collect", "slot"]
    assert daily_actions(kst("2026-10-08 12:00")) == [("collect", None, None)]
    sat = daily_actions(kst("2026-10-10 17:00"))
    assert [(a, s) for a, s, _ in sat] == [("collect", None), ("slot", "evening"), ("slot", "weekly")]
    assert sat[-1][2] == kst("2026-10-11 00:00").date()
    assert default_day("weekly", kst("2026-10-08 10:00")) == kst("2026-10-11 00:00").date()
    assert default_day("morning", kst("2026-10-08 10:00")) == kst("2026-10-08 00:00").date()


def test_draft_then_pick_topics(demo_env, monkeypatch):
    """대본 전에 추천 편성(주제 후보)만 → 사용자가 고른 주제·역할·순서로 확정 → 그때 대본."""
    import pytest

    from studio_core.config import load_channel, load_settings
    from topicradar import pipeline

    assert demo_env.exists()
    _run(monkeypatch, "2026-10-10T17:00", "collect", "--since", "2026-10-05T00:00")
    settings = load_settings()
    cfg = load_channel(settings)
    store = pipeline.open_store(settings)
    day = kst("2026-10-08 00:00").date()
    monkeypatch.setenv("STUDIO_NOW", "2026-10-08T05:30")
    pipeline.run_slot(settings, cfg, store, "morning", day, None)  # 아침 확정본(저녁 회수용)
    monkeypatch.setenv("STUDIO_NOW", "2026-10-08T17:00")
    res = pipeline.run_slot(settings, cfg, store, "evening", day, None, draft=True)
    proj = res.project
    plan = json.loads((proj / "plan.json").read_text(encoding="utf-8"))
    assert plan["status"] == "draft" and plan["candidate_ids"] and not (proj / "script.txt").exists()
    assert all(not i.get("actual") and not i.get("hit") for i in plan["items"])  # 결과 판정·제목은 고른 뒤에
    assert not plan["titles"]
    head = next(i for i in plan["items"] if i["role"] == "헤드라인")["topic"]["id"]
    picks = [(plan["candidate_ids"][0], "헤드라인"), (head, "서브"), (plan["candidate_ids"][1], "서브")]
    with pytest.raises(ValueError):
        pipeline.run_finalize(settings, cfg, store, proj, [(head, "서브")], None)  # 헤드라인 없음
    with pytest.raises(ValueError):
        pipeline.run_finalize(settings, cfg, store, proj, [(head, "포인트회수")], None)  # 고를 수 없는 역할
    out = pipeline.run_finalize(settings, cfg, store, proj, picks, None)
    final = json.loads((proj / "plan.json").read_text(encoding="utf-8"))
    assert final["status"] == "final" and (proj / "script.txt").exists() and final["titles"]
    body = [(i["topic"]["id"], i["role"]) for i in final["items"] if i["role"] in ("헤드라인", "서브")]
    assert body == picks  # 고른 순서 그대로
    assert sum(i["role"] == "포인트회수" for i in final["items"]) == 3  # 아침 예상 회수는 그대로
    assert out.plan.items[0].topic.id == plan["candidate_ids"][0]
