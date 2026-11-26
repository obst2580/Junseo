import json
from types import SimpleNamespace

import httpx
import pytest
import respx
from pydantic import BaseModel

from studio_core import http as h
from studio_core.config import Settings
from studio_core.fixtures import FixtureTransport
from studio_core.keys import Unavailable, naver_credentials, naver_endpoints, require
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
        naver_credentials()
    monkeypatch.setenv("NAVER_CLIENT_ID", "i")
    monkeypatch.setenv("NAVER_CLIENT_SECRET", "s")
    assert naver_credentials() == ("i", "s")
    eps = naver_endpoints("image")
    assert [e.mode for e in eps] == ["hub", "legacy"] and eps[0].url.endswith("/search/v1/image")
    monkeypatch.setenv("NAVER_API", "hub")
    assert [e.mode for e in naver_endpoints("news")] == ["hub"]
    monkeypatch.setenv("NAVER_API", "legacy")
    assert naver_endpoints("news")[0].url == "https://openapi.naver.com/v1/search/news.json"
    with pytest.raises(Unavailable):
        require("DEFINITELY_MISSING_KEY")


def test_claude_auth_error_marker(tmp_path):
    from studio_core.llm import auth_marker

    settings = Settings(root=tmp_path)
    settings.ensure_dirs()

    class Expired(Exception):
        status_code = 401

    class Boom:
        def create(self, **kw):
            raise Expired("invalid x-api-key")

    bad = ClaudeLLM(settings, client=SimpleNamespace(beta=SimpleNamespace(messages=Boom())))
    with pytest.raises(LLMError):
        bad.text("script", "s", "p")
    assert "invalid x-api-key" in auth_marker(settings).read_text(encoding="utf-8")
    good, _ = fake_client(text="ok")
    ClaudeLLM(settings, client=good, config=LLMConfig()).text("script", "s", "p")
    assert not auth_marker(settings).exists()  # 성공하면 지운다


def test_key_problem_and_explain_error():
    from studio_core.llm import explain_error, key_problem

    assert "없습니다" in key_problem("")
    assert "관리자 키" in key_problem("sk-ant-admin01-abc")
    assert "sk-ant-" in key_problem("AIzaSyD-google-key")
    assert "공백" in key_problem("sk-ant-api03-ab cd")
    assert key_problem(" sk-ant-api03-abcdef ") is None

    def err(code, msg="x"):
        e = Exception(msg)
        e.status_code = code
        return e

    assert "삭제·만료" in explain_error(err(401))
    assert "권한이 없습니다" in explain_error(err(403))
    assert "claude-x" in explain_error(err(404), "claude-x")
    assert "크레딧" in explain_error(err(400, "Your credit balance is too low"))
    assert "연결하지 못했습니다" in explain_error(type("APIConnectionError", (Exception,), {})("down"))


def test_check_connection(tmp_path, monkeypatch):
    from studio_core.llm import auth_marker, auth_reason, check_connection

    settings = Settings(root=tmp_path)
    settings.ensure_dirs()
    monkeypatch.delenv("STUDIO_MODEL_ANALYSIS", raising=False)
    monkeypatch.setenv("STUDIO_MODEL_SCRIPT", "claude-sonnet-5-5")
    seen = []

    class Models:
        def __init__(self, fail=None):
            self.fail = fail

        def retrieve(self, model):
            seen.append(model)
            if self.fail:
                e = Exception("invalid x-api-key")
                e.status_code = self.fail
                raise e
            return SimpleNamespace(id=model)

    ok = check_connection(settings, client=SimpleNamespace(models=Models()))
    assert ok["ok"] and seen == ["claude-opus-5-5", "claude-sonnet-5-5"] and "사용 가능" in ok["message"]
    bad = check_connection(settings, client=SimpleNamespace(models=Models(fail=401)))
    assert not bad["ok"] and bad["status"] == 401 and "삭제·만료" in bad["message"]
    assert auth_marker(settings).exists() and "삭제·만료" in auth_reason(settings)
    assert check_connection(settings, client=SimpleNamespace(models=Models()))["ok"]
    assert not auth_marker(settings).exists() and auth_reason(settings) == ""
    monkeypatch.setenv("ANTHROPIC_API_KEY", "not-a-key")
    assert "sk-ant-" in check_connection(settings)["message"]  # 호출 전에 모양부터


def test_env_file_key_wins_over_os_env(tmp_path, monkeypatch):
    from studio_core.config import ENV_OVERRIDDEN, load_settings

    (tmp_path / "channel.yaml").write_text("channel: {}\n", encoding="utf-8")
    (tmp_path / ".env").write_text("ANTHROPIC_API_KEY=sk-ant-new-key\nOTHER_SETTING=file\n", encoding="utf-8")
    monkeypatch.setenv("STUDIO_ROOT", str(tmp_path))
    monkeypatch.setenv("ANTHROPIC_API_KEY", "sk-ant-old-expired")  # 운영체제에 남은 예전 키
    monkeypatch.setenv("OTHER_SETTING", "os")
    load_settings()
    import os

    assert os.environ["ANTHROPIC_API_KEY"] == "sk-ant-new-key" and "ANTHROPIC_API_KEY" in ENV_OVERRIDDEN
    assert os.environ["OTHER_SETTING"] == "os"  # 키가 아닌 설정은 운영체제 값 그대로
    ENV_OVERRIDDEN.discard("ANTHROPIC_API_KEY")
