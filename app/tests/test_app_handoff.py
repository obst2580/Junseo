"""화면 API: 주제판 파일 내보내기 → (claude.ai 대본실) → 대본 가져오기 → 다음 단계."""

import json
from urllib.parse import unquote


def _job(res):
    assert res.status_code == 200, res.text
    job = res.json()
    assert job["status"] == "done", job
    return job


def _script(data, ids, fmt):
    by_id = {t["id"]: t for t in data["topics"]}
    lines = []
    for tid in ids:
        t = by_id[tid]
        lines += [f"## {t['heading']}", "대본실에서 쓴 문장인데요,", "이어서 말하듯 씁니다."]
    if fmt == "long":
        lines += ["## 정리", "결국 하나의 이야기입니다."]
    return {
        "kind": "studio-script",
        "version": 1,
        "board_id": data["board_id"],
        "format": fmt,
        "topic_ids": ids,
        "titles": ["대본실 제목"],
        "script": "\n".join(lines),
    }


def test_export_then_import(client, monkeypatch):
    _job(client.post("/api/jobs/collect", json={"since": "2026-10-05T00:00"}))
    assert client.get("/api/board/export").status_code == 404  # 판이 아직 없다
    monkeypatch.setenv("STUDIO_NOW", "2026-10-08T18:00")
    _job(client.post("/api/jobs/board", json={"collect": False}))
    board = client.get("/api/board").json()["board"]

    res = client.get(f"/api/board/export?board_id={board['id']}")
    assert res.status_code == 200 and res.headers["content-type"].startswith("application/json")
    disp = res.headers["content-disposition"]
    assert disp.startswith("attachment") and "studio-주제판-2026-10-08_1800.json" in unquote(disp)
    data = res.json()
    assert data["kind"] == "studio-board" and len(data["topics"]) == len(board["topics"])

    # 가져오기는 studio 화면에서만(X-Studio 헤더), 내용이 틀리면 이유를 알려 준다
    sf = _script(data, [data["topics"][0]["id"]], "short")
    assert client.post("/api/import-script", json=sf, headers={"X-Studio": ""}).status_code == 403
    bad = client.post("/api/import-script", json=data)
    assert bad.status_code == 400 and "주제판 파일" in bad.json()["error"]
    assert client.post("/api/import-script", content=b"").status_code == 400
    assert client.post("/api/import-script", json={**sf, "board_id": "x"}).status_code == 404

    monkeypatch.setenv("STUDIO_NOW", "2026-10-08T18:40")
    r = client.post("/api/import-script", json=sf)
    assert r.status_code == 200, r.text
    pid = r.json()["project"]
    assert pid == "2026-10-08_1840_short"
    d = client.get(f"/api/projects/{pid}").json()
    assert d["plan"]["script_mode"] == "claude" and d["plan"]["script_note"] == "claude.ai 대본실에서 씀"
    assert d["plan"]["titles"][0] == "대본실 제목" and "대본실에서 쓴 문장" in d["script"]
    assert d["summary"]["steps"]["plan"]  # 다음 단계(음성)로 넘어간다
    # 판에는 이 주제가 '만든 영상'으로 남는다
    t0 = client.get("/api/board").json()["board"]["topics"][0]
    assert "10월 8일(목) 18:40 · 3분 단독" in t0["covered"]
    assert json.loads(json.dumps(r.json()))["warnings"]  # 짧은 대본은 점검에 걸린다
