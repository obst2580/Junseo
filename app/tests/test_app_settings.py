"""설정 탭: 키(.env), 채널 값·말투·게시판(주석 보존 줄 단위 수정), 원문 검사 저장, 게시판 점검, 자동 실행."""

import os
import threading
from datetime import datetime

from studio_app.auto import AutoRunner
from studio_app.jobs import Busy, JobRunner
from studio_core.config import KST, Settings, load_channel, set_channel_value, set_community_enabled
from studio_core.envfile import mask, read_env, set_env


def test_keys_roundtrip(client, root):
    s = client.get("/api/settings").json()
    keys = {k["key"]: k for k in s["keys"]}
    assert keys["NAVER_CLIENT_ID"]["set"] and not keys["ANTHROPIC_API_KEY"]["set"]
    r = client.put("/api/settings/keys", json={"values": {"ANTHROPIC_API_KEY": "sk-ant-test-123456789"}})
    assert r.json()["ok"]
    env = (root / ".env").read_text(encoding="utf-8")
    assert (
        "ANTHROPIC_API_KEY=sk-ant-test-123456789" in env and "NAVER_CLIENT_ID=" in env
    )  # .env.example에서 시작
    assert os.environ["ANTHROPIC_API_KEY"] == "sk-ant-test-123456789"
    keys = {k["key"]: k for k in client.get("/api/settings").json()["keys"]}
    assert keys["ANTHROPIC_API_KEY"]["masked"] == "••••6789"
    assert client.put("/api/settings/keys", json={"values": {"PATH": "x"}}).status_code == 400
    client.put("/api/settings/keys", json={"values": {"ANTHROPIC_API_KEY": ""}})
    assert "ANTHROPIC_API_KEY" not in os.environ


def test_channel_tone_community_raw(client, root):
    r = client.put(
        "/api/settings/channel",
        json={
            "youtube_channel_id": "UCmine",
            "competitors": ["UCa", "UCb"],
            "slack_webhook": "",
            "tone": "해설형",
        },
    )
    assert r.json()["ok"]
    cfg = load_channel(Settings(root=root))
    assert cfg.channel.youtube_channel_id == "UCmine" and cfg.channel.competitors == ["UCa", "UCb"]
    assert cfg.script.tone == "해설형"
    assert client.put("/api/settings/channel", json={"tone": "막말형"}).status_code == 400
    w = {"interest": 45, "gap": 15, "persistence": 10, "fit": 30}
    assert client.put("/api/settings/channel", json={"weights": w}).json()["ok"]
    assert load_channel(Settings(root=root)).scoring.weights == {k: float(v) for k, v in w.items()}
    assert client.get("/api/settings").json()["channel"]["weights"] == w
    assert client.put("/api/settings/channel", json={"weights": {"interest": 50}}).status_code == 400
    assert client.get("/api/settings").json()["channel"]["selection_mode"] == "hottest"
    assert client.put("/api/settings/channel", json={"selection_mode": "balanced"}).json()["ok"]
    assert load_channel(Settings(root=root)).selection.mode == "balanced"
    assert client.put("/api/settings/channel", json={"selection_mode": "random"}).status_code == 400
    assert client.put("/api/settings/channel", json={"weights": {**w, "fit": 300}}).status_code == 400
    assert client.put("/api/settings/community", json={"name": "더쿠", "enabled": False}).json()["ok"]
    comm = {c["name"]: c["enabled"] for c in client.get("/api/settings").json()["communities"]}
    assert comm["더쿠"] is False and comm["다모앙"] is True
    assert client.put("/api/settings/community", json={"name": "없는곳", "enabled": False}).status_code == 400
    raw = client.get("/api/settings").json()["raw"]
    assert client.put("/api/settings/raw", json={"text": "channel: [깨진"}).status_code == 400
    assert client.put("/api/settings/raw", json={"text": "scoring:\n  weights: 3\n"}).status_code == 400
    assert (root / "channel.yaml").read_text(encoding="utf-8") == raw  # 실패하면 그대로
    assert client.put("/api/settings/raw", json={"text": raw.replace("UCmine", "UCnew")}).json()["ok"]
    assert load_channel(Settings(root=root)).channel.youtube_channel_id == "UCnew"


def test_probe_job_writes_status(client, root):
    job = client.post("/api/jobs/probe", json={}).json()
    assert job["status"] == "done"
    res = {r["name"]: r for r in job["result"]["results"]}
    assert res["보배드림"]["ok"] and res["보배드림"]["posts"] > 0 and res["보배드림"]["samples"]
    assert (root / "data" / "probe.json").exists()
    probe = client.get("/api/settings").json()["probe"]
    assert probe["results"] and probe["checked_label"].startswith("10월 10일")
    assert not any("게시판 점검" in x for x in client.get("/api/status").json()["setup"])


def test_yaml_line_edits_keep_comments(tmp_path):
    (tmp_path / "channel.yaml").write_text(
        '# 머리 주석\nchannel:\n  youtube_channel_id: ""   # 내 채널\n  competitors:\n    - UCold\n  slack_webhook: ""\n'
        'communities:\n  - name: 가\n    list_url: "https://a.example/{page}"\n    post_pattern: "/v/\\\\d+"\n'
        '  - name: 나\n    list_url: "https://b.example/{page}"\n    post_pattern: "/v/\\\\d+"\n    enabled: false   # 확인 전\n',
        encoding="utf-8",
    )
    st = Settings(root=tmp_path)
    set_channel_value(st, "channel", "youtube_channel_id", "UC1")
    set_channel_value(st, "channel", "competitors", ["UC2", "UC3"])
    set_channel_value(st, "script", "tone", "확신형")  # 없는 구역은 추가
    set_community_enabled(st, "가", False)
    set_community_enabled(st, "나", True)
    text = (tmp_path / "channel.yaml").read_text(encoding="utf-8")
    assert "# 머리 주석" in text and "# 내 채널" in text and "# 확인 전" in text and "UCold" not in text
    cfg = load_channel(st)
    assert cfg.channel.competitors == ["UC2", "UC3"] and cfg.script.tone == "확신형"
    assert [(c.name, c.enabled) for c in cfg.communities] == [("가", False), ("나", True)]


def test_envfile(tmp_path, monkeypatch):
    tpl = tmp_path / ".env.example"
    tpl.write_text("# 키\nA_KEY=\n# B_KEY=예시\n", encoding="utf-8")
    env = tmp_path / ".env"
    monkeypatch.delenv("B_KEY", raising=False)
    set_env(env, {"A_KEY": "1", "B_KEY": "2", "C_KEY": "3"}, template=tpl)
    assert env.read_text(encoding="utf-8") == "# 키\nA_KEY=1\nB_KEY=2\nC_KEY=3\n"
    assert read_env(env) == {"A_KEY": "1", "B_KEY": "2", "C_KEY": "3"} and os.environ["B_KEY"] == "2"
    assert mask("") == "" and mask("short") == "••••" and mask("abcdefghijk") == "••••hijk"
    for k in ("A_KEY", "B_KEY", "C_KEY"):
        monkeypatch.delenv(k, raising=False)


def test_auto_runner(tmp_path):
    fired = []
    ar = AutoRunner(tmp_path / "app.json", lambda now: fired.append(now) or True)
    at = datetime(2026, 10, 8, 17, 5, tzinfo=KST)
    assert ar.tick(at) is None  # 꺼져 있음
    assert ar.set_enabled(True)["enabled"]
    assert ar.tick(at) == "2026-10-08 17:00" and ar.tick(at) is None  # 하루 한 번
    assert ar.tick(datetime(2026, 10, 8, 17, 40, tzinfo=KST)) is None  # 유예 시간 지남
    assert ar.tick(datetime(2026, 10, 9, 5, 31, tzinfo=KST)) == "2026-10-09 05:30"
    assert len(fired) == 2
    busy = AutoRunner(tmp_path / "b.json", lambda now: False)
    busy.set_enabled(True)
    assert busy.tick(at) is None and busy.state()["done"] == []  # 다른 작업 중이면 다음 점검에서
    info = ar.info()
    assert info["times"] == ["05:30", "12:00", "17:00"] and info["next"]


def test_auto_endpoint(client):
    assert client.put("/api/settings/auto", json={"enabled": True}).json()["enabled"]
    assert client.get("/api/status").json()["auto"]["enabled"]


def test_job_runner_busy_and_errors():
    gate = threading.Event()
    runner = JobRunner()
    job = runner.start("t", "느린 작업", lambda say: (say("진행"), gate.wait(5), {"ok": True})[2])
    try:
        runner.start("t2", "두 번째", lambda say: {})
        raise AssertionError("Busy가 나야 함")
    except Busy as e:
        assert "느린 작업" in str(e)
    gate.set()
    for _ in range(100):
        if job.status != "running":
            break
        threading.Event().wait(0.02)
    assert job.status == "done" and job.result == {"ok": True} and any("진행" in x for x in job.log)
    bad = JobRunner(inline=True).start("x", "실패 작업", lambda say: 1 / 0)
    assert bad.status == "error" and "ZeroDivisionError" in bad.error and bad.as_dict()["elapsed"] >= 0


def test_cli_main(root, monkeypatch, capsys):
    import uvicorn

    from studio_app import cli

    calls = []
    monkeypatch.setattr(uvicorn, "run", lambda app, **kw: calls.append((app, kw)))
    monkeypatch.setattr(cli.webbrowser, "open", lambda url: calls.append(url))
    monkeypatch.setattr(cli, "_running", lambda url: False)
    monkeypatch.setattr("studio_app.server.AutoRunner.start", lambda self: None)
    cli.main(["--no-browser", "--port", "8799"])
    assert calls[0][1] == {"host": "127.0.0.1", "port": 8799, "log_level": "warning"}
    assert "http://127.0.0.1:8799/" in capsys.readouterr().out
    monkeypatch.setattr(cli, "_running", lambda url: True)
    cli.main(["--port", "8799"])
    assert calls[-1] == "http://127.0.0.1:8799/" and "이미 켜져" in capsys.readouterr().out


def test_running_probe_false():
    from studio_app.cli import _running

    assert _running("http://127.0.0.1:9/") is False


def test_rejected_claude_key_is_shown(client, root):
    from studio_core.llm import auth_marker

    client.put("/api/settings/keys", json={"values": {"ANTHROPIC_API_KEY": "sk-ant-expired-0000"}})
    st = Settings(root=root)
    auth_marker(st).write_text("AuthenticationError: 401", encoding="utf-8")
    status = client.get("/api/status").json()
    # STUDIO_LLM=offline인 테스트 환경에서도 거부 표시는 설정 안내에 뜬다
    assert any("거부" in x for x in status["setup"])
    client.put("/api/settings/keys", json={"values": {"ANTHROPIC_API_KEY": "sk-ant-new-1111"}})
    assert not auth_marker(st).exists()
    client.put("/api/settings/keys", json={"values": {"ANTHROPIC_API_KEY": ""}})
