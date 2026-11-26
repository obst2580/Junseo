"""newsclip 테스트 공통 fixture."""

from __future__ import annotations

import pytest

from studio_core import http as h


@pytest.fixture(autouse=True)
def _no_sleep(monkeypatch):
    async def _nop(_s):
        return None

    h.set_sleep(_nop)
    monkeypatch.setenv("STUDIO_NEWSCLIP_FAKE_YTDLP", "1")
