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
    assert "아침 포인트 적중률" in hist and "가중치 조정 제안" in hist
    cfg = (root / "channel.yaml").read_text(encoding="utf-8")
    assert "법사위 통과가 바꾸는 것" in cfg
    out = _run(monkeypatch, "2026-10-08T12:00", "daily")
    assert "수집 결과" in out


def test_daily_schedule_and_defaults():
    assert [a[0] for a in daily_actions(kst("2026-10-08 05:30"))] == ["collect", "slot"]
    assert daily_actions(kst("2026-10-08 12:00")) == [("collect", None, None)]
    sat = daily_actions(kst("2026-10-10 17:00"))
    assert [(a, s) for a, s, _ in sat] == [("collect", None), ("slot", "evening"), ("slot", "weekly")]
    assert sat[-1][2] == kst("2026-10-11 00:00").date()
    assert default_day("weekly", kst("2026-10-08 10:00")) == kst("2026-10-11 00:00").date()
    assert default_day("morning", kst("2026-10-08 10:00")) == kst("2026-10-08 00:00").date()
