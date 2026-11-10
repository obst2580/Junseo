"""studio 화면 테스트 공통: 시연 fixture 전송계층 + 오프라인 LLM + 임시 루트 + 작업 즉시 실행."""

from __future__ import annotations

import shutil
import subprocess
import sys
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from studio_app.server import create_app
from studio_core import http as h
from studio_core.config import Settings

FIX = Path(__file__).resolve().parents[2] / "fixtures" / "demo"


@pytest.fixture(autouse=True)
def _no_sleep(monkeypatch):
    async def _nop(_s):
        return None

    h.set_sleep(_nop)
    monkeypatch.setenv("STUDIO_NEWSCLIP_FAKE_YTDLP", "1")


@pytest.fixture
def root(tmp_path, monkeypatch):
    subprocess.run([sys.executable, str(FIX / "build.py")], check=True, capture_output=True)
    r = tmp_path / "root"
    (r / "data" / "schedule").mkdir(parents=True)
    shutil.copy(FIX / "channel.yaml", r / "channel.yaml")
    shutil.copy(Path(__file__).resolve().parents[2] / ".env.example", r / ".env.example")
    for f in (FIX / "schedule").glob("*.yaml"):
        shutil.copy(f, r / "data" / "schedule" / f.name)
    for k, v in {
        "STUDIO_ROOT": str(r),
        "STUDIO_FIXTURES": str(FIX),
        "STUDIO_LLM": "offline",
        "NAVER_CLIENT_ID": "demo",
        "NAVER_CLIENT_SECRET": "demo",
        "YOUTUBE_API_KEY": "demo",
        "STUDIO_NOW": "2026-10-10T17:00",
    }.items():
        monkeypatch.setenv(k, v)
    for k in ("ANTHROPIC_API_KEY", "GOOGLE_API_KEY", "GOOGLE_CSE_ID"):
        monkeypatch.delenv(k, raising=False)
    return r


@pytest.fixture
def client(root):
    app = create_app(Settings(root=root), inline_jobs=True, auto=False)
    with TestClient(app, headers={"X-Studio": "1"}) as c:
        yield c
