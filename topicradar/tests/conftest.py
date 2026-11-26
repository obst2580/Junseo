"""topicradar 테스트 공통 fixture."""

from __future__ import annotations

import shutil
import subprocess
import sys

import pytest

from studio_core import http as h

from trh import FIX


@pytest.fixture(autouse=True)
def _no_sleep():
    async def _nop(_s):
        return None

    h.set_sleep(_nop)


@pytest.fixture
def demo_env(tmp_path, monkeypatch):
    """fixture 전송계층 + 오프라인 LLM + 임시 루트."""
    subprocess.run([sys.executable, str(FIX / "build.py")], check=True, capture_output=True)
    root = tmp_path / "root"
    (root / "data" / "schedule").mkdir(parents=True)
    shutil.copy(FIX / "channel.yaml", root / "channel.yaml")
    for f in (FIX / "schedule").glob("*.yaml"):
        shutil.copy(f, root / "data" / "schedule" / f.name)
    for k, v in {
        "STUDIO_ROOT": str(root),
        "STUDIO_FIXTURES": str(FIX),
        "STUDIO_LLM": "offline",
        "NAVER_CLIENT_ID": "demo",
        "NAVER_CLIENT_SECRET": "demo",
        "YOUTUBE_API_KEY": "demo",
        "STUDIO_NOW": "2026-10-10T17:00",
    }.items():
        monkeypatch.setenv(k, v)
    monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)
    return root
