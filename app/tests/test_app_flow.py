"""화면 API로 처음부터 끝까지: 수집 → 편성 → 교체 → 대본 저장 → SRT → 자료 → 검토 → XML → 업로드 기록 → 성과."""

import re

from studio_app import services as sv


def _job(res):
    assert res.status_code == 200, res.text
    job = res.json()
    assert job["status"] == "done", job
    return job


def _ts(s: float) -> str:
    return f"{int(s // 3600):02d}:{int(s % 3600 // 60):02d}:{int(s % 60):02d},{int(s * 1000) % 1000:03d}"


def _srt_from(script: str) -> str:
    lines = [ln.strip() for ln in script.splitlines() if ln.strip() and not ln.strip().startswith("#")]
    out, t = [], 0.5
    for i, ln in enumerate(lines, 1):
        d = max(1.2, len(ln) / 7)
        out.append(f"{i}\n{_ts(t)} --> {_ts(t + d)}\n{ln}\n")
        t += d + 0.25
    return "\n".join(out)


def test_status_and_index(client):
    assert "studio" in client.get("/").text
    st = client.get("/api/status").json()
    assert st["llm"] == "offline" and not st["busy"]
    assert any("Claude API 키" in x for x in st["setup"]) and any("게시판 점검" in x for x in st["setup"])
    assert st["defaults"]["weekly"] == "2026-10-11" and st["suggest"] == "evening"
    assert client.get("/api/jobs/current").json() == {"job": None}
    assert client.get("/api/projects").json() == {"projects": []}


def test_full_flow(client, root, monkeypatch):
    job = _job(client.post("/api/jobs/collect", json={"since": "2026-10-05T00:00"}))
    assert job["result"]["counts"]["community"] > 0 and job["result"]["derived_queries"]
    assert client.get("/api/status").json()["last_collect_label"] == "10월 10일 17:00"
    assert any("커뮤니티 화제" in ln for ln in job["log"])

    # 아침 편성(수집은 방금 했으니 생략)
    monkeypatch.setenv("STUDIO_NOW", "2026-10-07T05:30")
    job = _job(
        client.post("/api/jobs/slot", json={"slot": "morning", "date": "2026-10-07", "collect": False})
    )
    pid = job["result"]["project"]
    assert pid == "2026-10-07_morning"
    lst = client.get("/api/projects").json()["projects"]
    assert lst[0]["id"] == pid and lst[0]["steps"]["plan"] and not lst[0]["steps"]["audio"]
    d = client.get(f"/api/projects/{pid}").json()
    assert d["plan"]["titles"] and d["items"] and d["script"].startswith("## ")
    assert all({"buzz", "links", "risks"} <= set(it) for it in d["items"])

    # 주제 교체
    sub = next(it for it in d["items"] if it["role"] != "포인트회수")
    cands = client.get(f"/api/projects/{pid}/candidates", params={"order": sub["order"]}).json()["candidates"]
    assert cands and all(c["id"] not in {it["topic"]["id"] for it in d["items"]} for c in cands)
    _job(client.post(f"/api/projects/{pid}/pick", json={"order": sub["order"], "topic_id": cands[0]["id"]}))
    d2 = client.get(f"/api/projects/{pid}").json()
    assert next(it for it in d2["items"] if it["order"] == sub["order"])["topic"]["id"] == cands[0]["id"]
    assert client.get(f"/api/projects/{pid}/candidates", params={"order": 99}).status_code == 400

    # 대본 저장 → 점검 갱신(report.md도)
    edited = d2["script"] + "\n#TODO: 편집 테스트\n"
    w = client.put(f"/api/projects/{pid}/script", json={"text": edited}).json()["warnings"]
    assert any("TODO" in x or "확인 주석" in x for x in w)
    assert "편집 테스트" in (root / "projects" / pid / "script.txt").read_text(encoding="utf-8")
    assert "편집자 확인 주석" in (root / "projects" / pid / "report.md").read_text(encoding="utf-8")
    _job(client.post(f"/api/projects/{pid}/rewrite"))
    assert "편집 테스트" not in (root / "projects" / pid / "script.txt").read_text(encoding="utf-8")

    # 저녁: 편성 → SRT 올리기 → 자료 수집 → 검토 → XML
    monkeypatch.setenv("STUDIO_NOW", "2026-10-08T17:00")
    ev = _job(
        client.post("/api/jobs/slot", json={"slot": "evening", "date": "2026-10-08", "collect": False})
    )["result"]["project"]
    assert client.post(f"/api/projects/{ev}/newsclip").json()["status"] == "error"  # SRT 없으면 실패 안내
    script = client.get(f"/api/projects/{ev}").json()["script"]
    bad = client.put(f"/api/projects/{ev}/upload/narration.srt", content=b"not srt")
    assert bad.status_code == 400
    assert client.put(f"/api/projects/{ev}/upload/evil.sh", content=b"x").status_code == 400
    ok = client.put(f"/api/projects/{ev}/upload/narration.srt", content=_srt_from(script).encode("utf-8"))
    assert ok.status_code == 200 and ok.json()["bytes"] > 0
    job = _job(client.post(f"/api/projects/{ev}/newsclip"))
    assert job["result"]["scenes"] > 0 and not job["result"]["xml_errors"]
    sc = client.get(f"/api/projects/{ev}/scenes").json()
    first = sc["scenes"][0]
    assert first["candidates"][0]["preview"].startswith(f"/files/{ev}/")
    assert client.get(first["candidates"][0]["preview"]).status_code == 200
    alt = next((c for c in first["candidates"] if c["preview"] and c["id"] != first["chosen"]["id"]), None)
    if alt:
        assert client.post(
            f"/api/projects/{ev}/scenes/{first['id']}/choose", json={"asset_id": alt["id"]}
        ).json()["ok"]
    assert client.post(
        f"/api/projects/{ev}/scenes/{sc['scenes'][1]['id']}/skip", json={"skipped": True}
    ).json()["ok"]
    video = next(
        (
            (s["id"], c["id"])
            for s in sc["scenes"]
            for c in s["candidates"]
            if c["media"] == "video" and c["local_path"]
        ),
        None,
    )
    if video:
        r = client.post(
            f"/api/projects/{ev}/scenes/{video[0]}/clip_start", json={"asset_id": video[1], "sec": 3}
        )
        assert r.json()["ok"]
    saved = client.post(f"/api/projects/{ev}/scenes/save").json()
    assert saved["ok"], saved
    again = client.get(f"/api/projects/{ev}/scenes").json()
    assert again["scenes"][1]["skipped"] is True
    if alt:
        assert again["scenes"][0]["chosen"]["id"] == alt["id"]
    xml = client.get(f"/files/{ev}/timeline.xml")
    assert xml.status_code == 200 and "<xmeml" in xml.text
    assert client.get("/api/projects").json()["projects"][0]["steps"] == {
        "plan": True,
        "audio": True,
        "assets": True,
        "xml": True,
        "upload": False,
    }

    # 업로드 기록
    assert client.post(f"/api/projects/{ev}/made", json={"video": "아무거나"}).status_code == 400
    job = _job(
        client.post(
            f"/api/projects/{ev}/made",
            json={"video": "https://youtu.be/demoE100801", "title": "직접 쓴 제목"},
        )
    )
    assert job["result"]["video_id"] == "demoE100801"
    summary = client.get(f"/api/projects/{ev}").json()["summary"]
    assert summary["steps"]["upload"] and summary["uploaded_title"] == "직접 쓴 제목"
    assert "직접 쓴 제목" in (root / "channel.yaml").read_text(encoding="utf-8")

    # 성과
    monkeypatch.setenv("STUDIO_NOW", "2026-10-11T21:00")
    hist = _job(client.post("/api/jobs/history", json={"weeks": 2}))
    assert "아침 포인트 적중률" in hist["result"]["markdown"]


def test_errors_and_guards(client, root):
    assert client.get("/api/projects/../etc").status_code == 404
    assert client.get("/api/projects/2026-10-07_lunch").status_code == 400
    assert client.get("/api/projects/2026-10-07_morning").status_code == 404
    assert client.post("/api/jobs/slot", json={"slot": "lunch"}).status_code == 400
    (root / "projects" / "2026-10-09_evening").mkdir(parents=True)
    d = client.get("/api/projects/2026-10-09_evening").json()
    assert d["plan"] is None and d["summary"]["label"].startswith("10월 9일(금)")
    assert client.get("/api/projects/2026-10-09_evening/scenes").status_code == 404
    assert client.put("/api/projects/2026-10-09_evening/upload/narration.srt", content=b"").status_code == 400


def test_open_folder(client, root, monkeypatch):
    opened = []
    monkeypatch.setattr("studio_app.server.open_folder", lambda p: opened.append(p))
    (root / "projects" / "2026-10-09_evening").mkdir(parents=True)
    r = client.post("/api/projects/2026-10-09_evening/open-folder")
    assert r.json()["ok"] and opened[0].name == "2026-10-09_evening"


def test_parse_video_id_and_labels():
    for s in (
        "https://youtu.be/abcDEF12345",
        "https://www.youtube.com/watch?v=abcDEF12345&t=3",
        "abcDEF12345",
        "https://youtube.com/shorts/abcDEF12345",
    ):
        assert sv.parse_video_id(s) == "abcDEF12345"
    assert re.match(
        r"10월 11일\(일\) 주간 심층", sv.label_for(__import__("datetime").date(2026, 10, 11), "weekly")
    )


def test_auto_daily_and_busy(client, root, monkeypatch):
    from studio_app.jobs import Job

    monkeypatch.setenv("STUDIO_NOW", "2026-10-08T05:35")
    app = client.app
    assert app.state.autorun.trigger(sv.now_kst())  # 05:30 규칙: 수집 → 아침 편성
    job = app.state.runner.current
    assert job.status == "done" and job.result["projects"] == ["2026-10-08_morning"]
    # 편성 버튼: 방금 수집했으면 다시 수집하지 않음
    r = client.post("/api/jobs/slot", json={"slot": "morning"}).json()
    assert r["status"] == "done" and any("최근 수집" in ln for ln in r["log"])
    app.state.runner.current = Job(kind="collect", label="수집")
    busy = client.post("/api/jobs/collect", json={})
    assert busy.status_code == 409 and "수집" in busy.json()["error"]
    assert not app.state.autorun.trigger(sv.now_kst())


def test_local_only_guard(root):
    from fastapi.testclient import TestClient

    from studio_app.server import create_app
    from studio_core.config import Settings

    app = create_app(Settings(root=root), inline_jobs=True, auto=False)
    with TestClient(app) as c:
        assert c.get("/api/status").status_code == 200  # 읽기는 헤더 없이도
        r = c.post("/api/jobs/collect", json={})
        assert r.status_code == 403 and "studio 화면" in r.json()["error"]
        assert c.put("/api/settings/keys", json={"values": {}}).status_code == 403
    with TestClient(app, base_url="http://evil.example") as c:
        assert c.get("/api/status").status_code == 403


def _speechy_wav(path, script):
    """대본 문장마다 소리 + 문장 사이 0.5초 쉼(타입캐스트 음성 흉내)."""
    import math
    import struct
    import wave

    from newsclip.autosub import script_sentences

    rate, frames = 16000, bytearray()

    def add(kind, sec):
        for i in range(int(sec * rate)):
            v = int(8000 * math.sin(2 * math.pi * 200 * i / rate)) if kind else 0
            frames.extend(struct.pack("<h", v))

    add(0, 0.3)
    for s in script_sentences(script):
        add(1, 0.08 * sum(len(x) for x in s))
        add(0, 0.5)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(bytes(frames))


def test_ai_voice_flow(client, root, monkeypatch, tmp_path):
    import shutil
    import subprocess

    import pytest

    if shutil.which("ffmpeg") is None:
        pytest.skip("ffmpeg 필요")
    client.post("/api/jobs/collect", json={"since": "2026-10-05T00:00"})
    monkeypatch.setenv("STUDIO_NOW", "2026-10-08T17:00")
    pid = _job(
        client.post("/api/jobs/slot", json={"slot": "evening", "date": "2026-10-08", "collect": False})
    )["result"]["project"]
    n = client.get(f"/api/projects/{pid}/narration-text").json()
    assert n["chars"] > 100 and "#출처" not in n["text"] and "## " not in n["text"]
    assert client.post(f"/api/projects/{pid}/autosub").status_code == 404  # 음성 전
    script = (root / "projects" / pid / "script.txt").read_text(encoding="utf-8")
    wav = tmp_path / "typecast.wav"
    _speechy_wav(wav, script)
    mp3 = tmp_path / "typecast.mp3"
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav), str(mp3)], check=True)
    bad = client.put(f"/api/projects/{pid}/upload-audio", params={"filename": "x.txt"}, content=b"abc")
    assert bad.status_code == 400
    r = client.put(
        f"/api/projects/{pid}/upload-audio", params={"filename": "타입캐스트.mp3"}, content=mp3.read_bytes()
    )
    assert r.status_code == 200, r.text
    res = r.json()
    assert res["cues"] > 10 and res["matched"] >= res["boundaries"] * 0.8
    d = root / "projects" / pid
    assert (d / "narration.wav").exists() and (d / "narration_source.mp3").exists()
    summary = client.get(f"/api/projects/{pid}").json()["summary"]
    assert summary["steps"]["audio"] and summary["srt_auto"] and summary["autosub"]["cues"] == res["cues"]
    assert client.post(f"/api/projects/{pid}/autosub").json()["cues"] == res["cues"]
    # 자동 자막으로 자료 수집까지
    assert _job(client.post(f"/api/projects/{pid}/newsclip"))["result"]["scenes"] > 0
    # 직접 만든 SRT를 올리면 자동 표시 해제
    client.put(f"/api/projects/{pid}/upload/narration.srt", content=(d / "narration.srt").read_bytes())
    assert not client.get(f"/api/projects/{pid}").json()["summary"]["srt_auto"]
