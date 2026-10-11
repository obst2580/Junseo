import json
from types import SimpleNamespace

import httpx
import pytest
import respx
from pydantic import BaseModel

from studio_core import http as h
from studio_core.config import Settings
from studio_core.fixtures import FixtureTransport
from studio_core.keys import Unavailable, naver_headers, require
from studio_core.llm import ClaudeLLM, LLMConfig, LLMError, LLMRefusal, get_llm, strict_schema, usage_summary


@pytest.fixture(autouse=True)
def no_sleep():
    async def _nop(_s):
        return None

    h.set_sleep(_nop)
    yield


@respx.mock
async def test_request_retries_then_succeeds():
    route = respx.get("https://x.test/a").mock(
        side_effect=[httpx.Response(429), httpx.Response(200, json={"ok": 1})]
    )
    async with httpx.AsyncClient() as c:
        assert await h.get_json(c, "https://x.test/a") == {"ok": 1}
    assert route.call_count == 2


@respx.mock
async def test_request_4xx_fails_fast_and_exhaustion():
    respx.get("https://x.test/b").mock(return_value=httpx.Response(404))
    respx.get("https://x.test/c").mock(side_effect=httpx.ConnectError("down"))
    respx.get("https://x.test/d").mock(return_value=httpx.Response(503))
    async with httpx.AsyncClient() as c:
        with pytest.raises(h.HttpError) as e:
            await h.request(c, "GET", "https://x.test/b")
        assert e.value.status == 404
        with pytest.raises(h.HttpError):
            await h.request(c, "GET", "https://x.test/c", retries=2)
        with pytest.raises(h.HttpError):
            await h.request(c, "GET", "https://x.test/d", retries=2)


def test_make_client_fixture_transport(tmp_path, monkeypatch):
    (tmp_path / "routes.yaml").write_text(
        "routes:\n  - match: 'a\\.test'\n    file: x.json\n", encoding="utf-8"
    )
    (tmp_path / "x.json").write_text('{"v": 1}', encoding="utf-8")
    monkeypatch.setenv("STUDIO_FIXTURES", str(tmp_path))
    c = h.make_client()
    assert isinstance(c._transport, FixtureTransport)  # noqa: SLF001
    monkeypatch.setenv("STUDIO_CONTACT", "me@example.com")
    assert "me@example.com" in h.user_agent()


class Out(BaseModel):
    name: str
    items: list[str]
    child: dict[str, int] | None


def test_strict_schema():
    s = strict_schema(Out)
    assert s["additionalProperties"] is False and set(s["required"]) == {"name", "items", "child"}
    assert "title" not in s


class FakeMessages:
    def __init__(self, text="{}", stop="end_turn"):
        self.text, self.stop, self.calls = text, stop, []

    def create(self, **kw):
        self.calls.append(kw)
        usage = SimpleNamespace(
            input_tokens=10, output_tokens=5, cache_read_input_tokens=3, cache_creation_input_tokens=0
        )
        return SimpleNamespace(
            content=[SimpleNamespace(type="text", text=self.text)],
            usage=usage,
            stop_reason=self.stop,
            model=kw["model"],
            stop_details=None,
            _request_id="req_1",
        )


def fake_client(**kw):
    m = FakeMessages(**kw)
    return SimpleNamespace(beta=SimpleNamespace(messages=m)), m


def test_claude_llm_json_and_usage(tmp_path):
    settings = Settings(root=tmp_path)
    settings.ensure_dirs()
    client, m = fake_client(text=json.dumps({"name": "a", "items": ["x"], "child": None}))
    llm = ClaudeLLM(settings, client=client, config=LLMConfig())
    out = llm.json("cluster", "sys", "prompt", Out)
    assert out.name == "a"
    call = m.calls[0]
    assert call["fallbacks"] == "default" and call["betas"] == ["server-side-fallback-2026-07-01"]
    assert call["output_config"]["format"]["type"] == "json_schema"
    assert call["system"][0]["cache_control"] == {"type": "ephemeral"}
    assert usage_summary(settings)["cluster"] == {"calls": 1, "input_tokens": 10, "output_tokens": 5}
    assert llm.text("script", "s", "p") == json.dumps({"name": "a", "items": ["x"], "child": None})


def test_claude_llm_errors(tmp_path):
    settings = Settings(root=tmp_path)
    settings.ensure_dirs()
    for stop, exc in (("refusal", LLMRefusal), ("max_tokens", LLMError)):
        client, _ = fake_client(stop=stop)
        with pytest.raises(exc):
            ClaudeLLM(settings, client=client).text("t", "s", "p")
    client, _ = fake_client(text="not json")
    with pytest.raises(LLMError):
        ClaudeLLM(settings, client=client).json("t", "s", "p", Out)

    class Boom:
        def create(self, **kw):
            raise RuntimeError("net")

    with pytest.raises(LLMError):
        ClaudeLLM(settings, client=SimpleNamespace(beta=SimpleNamespace(messages=Boom()))).text("t", "s", "p")


def test_get_llm_modes(tmp_path, monkeypatch):
    settings = Settings(root=tmp_path)
    settings.ensure_dirs()
    monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)
    monkeypatch.delenv("ANTHROPIC_AUTH_TOKEN", raising=False)
    monkeypatch.delenv("STUDIO_LLM", raising=False)
    assert get_llm(settings) is None
    monkeypatch.setenv("STUDIO_LLM", "offline")
    assert get_llm(settings, client=object()) is None
    monkeypatch.delenv("STUDIO_LLM")
    assert isinstance(get_llm(settings, client=object()), ClaudeLLM)
    monkeypatch.setenv("STUDIO_MODEL_SCRIPT", "claude-sonnet-5-5")
    assert LLMConfig.from_env().script_model == "claude-sonnet-5-5"


def test_keys(monkeypatch):
    monkeypatch.delenv("NAVER_CLIENT_ID", raising=False)
    with pytest.raises(Unavailable):
        naver_headers()
    monkeypatch.setenv("NAVER_CLIENT_ID", "i")
    monkeypatch.setenv("NAVER_CLIENT_SECRET", "s")
    assert naver_headers()["X-Naver-Client-Id"] == "i"
    with pytest.raises(Unavailable):
        require("DEFINITELY_MISSING_KEY")
