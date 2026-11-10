"""newsclip run/xml/search CLI와 검토 UI를 fixture로 통과."""

import json
import shutil
import subprocess
import sys

import pytest
from fastapi.testclient import TestClient
from typer.testing import CliRunner

from newsclip.cli import app
from newsclip.fcpxml import validate_xmeml
from newsclip.review.app import create_app

from nch import FIX, ROOT

runner = CliRunner()

SCRIPT = """## 국방위 국감 북 미사일 탐지
8일 국방부 국정감사에서
북한 신형 미사일 탐지가 늦었다는
질타가 이어졌습니다.
#출처: https://news.example-yna.kr/article/1
국방부는 발사 준비 시간이 짧아졌기 때문이라고
해명했습니다.

## 아침 포인트 회수: 플랫폼노동자 기본법
아침에 말씀드린 대로, 국회는 8일 본회의를 열고
플랫폼노동자 기본법을 가결했습니다.
그래서 다음은, 고용노동부가 시행령 준비에 들어갑니다.

## 내일 쟁점: 세수 결손
기획재정부가 올해 세수 결손 전망을 발표했습니다.
"""


@pytest.fixture
def project(tmp_path, monkeypatch):
    subprocess.run([sys.executable, str(FIX / "build.py")], check=True, capture_output=True)
    root = tmp_path / "root"
    root.mkdir()
    shutil.copy(FIX / "channel.yaml", root / "channel.yaml")
    proj = root / "projects" / "2026-10-08_evening"
    proj.mkdir(parents=True)
    (proj / "script.txt").write_text(SCRIPT, encoding="utf-8")
    subprocess.run(
        [sys.executable, str(ROOT / "scripts" / "make_sample_srt.py"), str(proj)],
        check=True,
        capture_output=True,
    )
    for k, v in {
        "STUDIO_ROOT": str(root),
        "STUDIO_FIXTURES": str(FIX),
        "STUDIO_LLM": "offline",
        "NAVER_CLIENT_ID": "d",
        "NAVER_CLIENT_SECRET": "d",
        "YOUTUBE_API_KEY": "d",
        "STUDIO_NOW": "2026-10-08T18:30",
    }.items():
        monkeypatch.setenv(k, v)
    monkeypatch.delenv("GOOGLE_API_KEY", raising=False)  # 기본 결정: Google 키 없음 → 네이버가 1순위
    monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)
    return proj


def test_run_xml_search_and_review(project):
    res = runner.invoke(app, ["run", "--project", str(project), "--per-scene", "2"])
    assert res.exit_code == 0, res.output
    assert "google_image" in res.output and "GOOGLE_API_KEY" in res.output  # 건너뛴 소스 경고
    data = json.loads((project / "scenes.json").read_text(encoding="utf-8"))
    chosen = [s["chosen"] for s in data["scenes"]]
    assert all(chosen) and {c["source"] for c in chosen} <= {"naver_image", "youtube_video", "wikimedia"}
    assert not any(c["author_or_channel"] in ("YTN", "KBS News") for c in chosen)
    assert validate_xmeml((project / "timeline.xml").read_text(encoding="utf-8")) == []
    assets = json.loads((project / "assets.json").read_text(encoding="utf-8"))["assets"]
    assert all(a["attribution_text"].startswith(("출처:", "사진:")) for a in assets)

    res = runner.invoke(
        app, ["xml", "--project", str(project), "--audio", "narration.wav", "--fps", "25", "--zoom", "1.06"]
    )
    assert res.exit_code == 0 and "25fps" in res.output
    xml = (project / "timeline.xml").read_text(encoding="utf-8")
    assert "<timebase>25</timebase>" in xml and "narration.wav" in xml

    res = runner.invoke(app, ["search", "국회 본회의", "--kind", "video"])
    assert res.exit_code == 0 and "youtube_video" in res.output and "google_image: 건너뜀" in res.output

    client = TestClient(create_app(project))
    assert "newsclip 검토" in client.get("/").text
    scenes = client.get("/api/scenes").json()["scenes"]
    sc = next(s for s in scenes if len([c for c in s["candidates"] if c["preview"]]) >= 2)
    second = [c for c in sc["candidates"] if c["preview"]][1]
    assert client.get(second["preview"]).status_code == 200
    assert client.post(f"/api/scenes/{sc['id']}/choose", json={"asset_id": second["id"]}).json()["ok"]
    assert client.post(f"/api/scenes/{sc['id']}/choose", json={"asset_id": "nope"}).status_code == 400
    assert client.post(f"/api/scenes/{scenes[-1]['id']}/skip", json={"skipped": True}).json()["ok"]
    vid_scene = next(
        (s for s in scenes if any(c["media"] == "video" and c["preview"] for c in s["candidates"])), None
    )
    if vid_scene:
        vid = next(c for c in vid_scene["candidates"] if c["media"] == "video" and c["preview"])
        assert client.post(
            f"/api/scenes/{vid_scene['id']}/clip_start", json={"asset_id": vid["id"], "sec": 42}
        ).json()["ok"]
    assert client.post("/api/scenes/s999/skip", json={"skipped": True}).status_code == 404
    saved = client.post("/api/save").json()
    assert saved["ok"], saved
    after = json.loads((project / "scenes.json").read_text(encoding="utf-8"))
    target = next(s for s in after["scenes"] if s["id"] == sc["id"])
    assert target["chosen"]["id"] == second["id"] and after["scenes"][-1]["skipped"] is True
    if vid_scene:
        v = next(
            c
            for s in after["scenes"]
            if s["id"] == vid_scene["id"]
            for c in s["candidates"]
            if c["id"] == vid["id"]
        )
        assert v["clip_start_sec"] == 42
    assert "건너뛰기" in (project / "timeline.xml").read_text(encoding="utf-8")
