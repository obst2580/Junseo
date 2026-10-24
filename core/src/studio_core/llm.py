"""Claude 호출 래퍼.

- 작업마다 한 번의 배치 호출로 끝내도록 설계한다(cluster, judge, plan, script, scenes).
- JSON 작업은 structured outputs(`output_config.format`)로 스키마를 강제한다.
- 안전 분류기 거절 시 서버 측 대체 모델(`fallbacks: "default"`)로 재시도한다.
- 모든 호출의 토큰 사용량을 `data/logs/llm_usage.jsonl`에 남긴다.
- 키가 없거나 `STUDIO_LLM=offline`이면 `get_llm()`이 None을 돌려주고, 호출하는
  쪽은 결정적 휴리스틱으로 대체한다(시연·테스트용).
"""

from __future__ import annotations

import json
import logging
import os
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any, Literal

from pydantic import BaseModel
from rich.console import Console

from studio_core.config import KST, Settings

log = logging.getLogger("studio.llm")
console = Console(stderr=True)

Role = Literal["analysis", "script"]

DEFAULT_MODEL = "claude-opus-5-5"
FALLBACK_BETA = "server-side-fallback-2026-07-01"


class LLMError(RuntimeError):
    """LLM 호출 실패."""


class LLMRefusal(LLMError):
    """안전 분류기 거절(대체 모델까지 거절)."""


@dataclass(frozen=True)
class LLMConfig:
    """역할별 모델·effort. .env로 바꾼다."""

    analysis_model: str = DEFAULT_MODEL
    script_model: str = DEFAULT_MODEL
    analysis_effort: str = "medium"
    script_effort: str = "high"
    use_fallbacks: bool = True

    @classmethod
    def from_env(cls) -> LLMConfig:
        """환경변수에서 읽는다."""
        return cls(
            analysis_model=os.environ.get("STUDIO_MODEL_ANALYSIS") or DEFAULT_MODEL,
            script_model=os.environ.get("STUDIO_MODEL_SCRIPT") or DEFAULT_MODEL,
            analysis_effort=os.environ.get("STUDIO_EFFORT_ANALYSIS") or "medium",
            script_effort=os.environ.get("STUDIO_EFFORT_SCRIPT") or "high",
            use_fallbacks=(os.environ.get("STUDIO_LLM_FALLBACKS", "1") != "0"),
        )


def strict_schema(model: type[BaseModel]) -> dict[str, Any]:
    """pydantic 모델 → structured outputs용 엄격 스키마.

    모든 object에 additionalProperties=false, 모든 속성을 required로 둔다.
    """
    schema = model.model_json_schema()

    def walk(node: Any) -> None:
        if isinstance(node, dict):
            node.pop("title", None)
            node.pop("default", None)
            if node.get("type") == "object" and "properties" in node:
                node["additionalProperties"] = False
                node["required"] = list(node["properties"].keys())
            for v in node.values():
                walk(v)
        elif isinstance(node, list):
            for v in node:
                walk(v)

    walk(schema)
    return schema


def auth_marker(settings: Settings) -> Path:
    """Claude 키가 거부됐을 때(401/403) 남기는 표시 파일(첫 줄 = 쉬운 설명). 다음 성공 호출이나 새 키 저장 때 지운다."""
    return settings.logs_dir / "llm_auth_error.txt"


def key_problem(key: str) -> str | None:
    """붙여넣은 값이 Claude API 키 모양인지(호출 전에 잡을 수 있는 실수)."""
    key = key.strip()
    if not key:
        return "Claude API 키가 없습니다 — 설정에서 붙여넣고 저장하세요"
    if key.startswith("sk-ant-admin"):
        return "관리자 키(Admin key)입니다 — 대본에는 못 씁니다. Claude Console → API Keys에서 일반 키를 만드세요"
    if not key.startswith("sk-ant-"):
        return "Claude API 키 모양이 아닙니다(sk-ant-로 시작해야 함) — 다른 값을 붙여넣은 것 같습니다"
    if any(c.isspace() for c in key):
        return "키 중간에 공백·줄바꿈이 들어 있습니다 — 다시 복사해 붙여넣으세요"
    return None


def explain_error(e: Exception, model: str = "") -> str:
    """SDK 예외 → 화면에 보여 줄 쉬운 설명."""
    status = getattr(e, "status_code", None)
    if status == 401:
        return (
            "키가 맞지 않습니다(오타이거나 삭제·만료된 키). Claude Console → API Keys에서 새 키를 만들어 "
            "설정에 붙여넣으세요(만료 기간은 '없음' 또는 길게)"
        )
    if status == 403:
        return (
            "이 키로는 권한이 없습니다(키가 속한 조직·워크스페이스 권한 또는 사용 제한). "
            "Claude Console에서 키의 워크스페이스·Limits·Billing을 확인하세요"
        )
    if status == 404:
        return f"모델 '{model}'을(를) 찾을 수 없습니다 — .env의 STUDIO_MODEL_ANALYSIS·STUDIO_MODEL_SCRIPT를 확인하세요"
    if status == 400 and "credit" in str(e).lower():
        return "크레딧(잔액)이 부족합니다 — Claude Console → Billing에서 충전하세요"
    if status == 429:
        return "요청이 너무 많습니다(사용 한도) — 잠시 뒤 다시 하거나 Console → Limits를 확인하세요"
    if status is None and "connect" in type(e).__name__.lower():
        return "Claude 서버에 연결하지 못했습니다(인터넷·방화벽·회사 보안 프로그램 확인)"
    return f"{type(e).__name__}: {e}"


def check_connection(settings: Settings, client: Any = None) -> dict[str, Any]:
    """키 확인: 쓰려는 모델 정보를 조회한다(토큰을 쓰지 않음). 성공하면 거부 표시를 지운다."""
    cfg = LLMConfig.from_env()
    if client is None:
        problem = key_problem(os.environ.get("ANTHROPIC_API_KEY", ""))
        if problem:
            return {"ok": False, "message": problem}
        import anthropic

        client = anthropic.Anthropic(timeout=30.0, max_retries=1)
    models = list(dict.fromkeys([cfg.analysis_model, cfg.script_model]))
    marker = auth_marker(settings)
    for m in models:
        try:
            client.models.retrieve(m)
        except Exception as e:  # 원인별 설명을 화면에 보여 준다
            msg = explain_error(e, m)
            if getattr(e, "status_code", None) in (401, 403):
                marker.write_text(f"{msg}\n{type(e).__name__}: {e}", encoding="utf-8")
            return {"ok": False, "status": getattr(e, "status_code", None), "message": msg}
    marker.unlink(missing_ok=True)
    return {"ok": True, "message": f"연결됨 — {', '.join(models)} 사용 가능", "models": models}


def auth_reason(settings: Settings) -> str:
    """거부 표시 파일의 첫 줄(쉬운 설명). 없으면 빈 문자열."""
    path = auth_marker(settings)
    try:
        return path.read_text(encoding="utf-8").splitlines()[0].strip()
    except (OSError, IndexError):
        return ""


class ClaudeLLM:
    """Anthropic SDK 기반 클라이언트."""

    def __init__(self, settings: Settings, client: Any = None, config: LLMConfig | None = None) -> None:
        self.settings = settings
        self.config = config or LLMConfig.from_env()
        if client is None:
            import anthropic

            client = anthropic.Anthropic(timeout=600.0, max_retries=3)
        self.client = client
        self.usage_path = settings.logs_dir / "llm_usage.jsonl"

    # -- 공개 API ---------------------------------------------------------
    def json(
        self,
        task: str,
        system: str,
        prompt: str,
        output: type[BaseModel],
        *,
        role: Role = "analysis",
        max_tokens: int = 16000,
    ) -> BaseModel:
        """스키마를 강제한 JSON 응답을 pydantic 모델로 돌려준다."""
        schema = strict_schema(output)
        text = self._call(task, system, prompt, role=role, max_tokens=max_tokens, schema=schema)
        try:
            return output.model_validate_json(text)
        except ValueError as e:
            raise LLMError(f"{task}: 응답 JSON 검증 실패: {e}") from e

    def text(
        self, task: str, system: str, prompt: str, *, role: Role = "script", max_tokens: int = 16000
    ) -> str:
        """자유 텍스트 응답."""
        return self._call(task, system, prompt, role=role, max_tokens=max_tokens, schema=None)

    # -- 내부 -------------------------------------------------------------
    def _call(
        self,
        task: str,
        system: str,
        prompt: str,
        *,
        role: Role,
        max_tokens: int,
        schema: dict[str, Any] | None,
    ) -> str:
        model = self.config.analysis_model if role == "analysis" else self.config.script_model
        effort = self.config.analysis_effort if role == "analysis" else self.config.script_effort
        output_config: dict[str, Any] = {"effort": effort}
        if schema is not None:
            output_config["format"] = {"type": "json_schema", "schema": schema}
        kwargs: dict[str, Any] = {
            "model": model,
            "max_tokens": max_tokens,
            # 고정 시스템 프롬프트(채널 정체성·규칙)는 캐시한다.
            "system": [{"type": "text", "text": system, "cache_control": {"type": "ephemeral"}}],
            "messages": [{"role": "user", "content": prompt}],
            "output_config": output_config,
        }
        if self.config.use_fallbacks and not model.startswith("claude-haiku"):
            kwargs["betas"] = [FALLBACK_BETA]
            kwargs["fallbacks"] = "default"
        try:
            resp = self.client.beta.messages.create(**kwargs)
        except Exception as e:  # SDK의 타입별 예외는 상위로 메시지만 전달
            if getattr(e, "status_code", None) in (401, 403):  # 만료·삭제·잘못된 키 → 화면에 알린다
                self._auth_marker().write_text(
                    f"{explain_error(e, model)}\n{type(e).__name__}: {e}", encoding="utf-8"
                )
            raise LLMError(f"{task}: Claude 호출 실패 — {explain_error(e, model)}") from e
        self._auth_marker().unlink(missing_ok=True)
        self._log_usage(task, model, resp)
        if resp.stop_reason == "refusal":
            raise LLMRefusal(f"{task}: 모델이 요청을 거절했습니다({getattr(resp, 'stop_details', None)})")
        if resp.stop_reason == "max_tokens":
            raise LLMError(f"{task}: max_tokens({max_tokens})에 도달해 응답이 잘렸습니다")
        texts = [b.text for b in resp.content if getattr(b, "type", "") == "text"]
        if not texts:
            raise LLMError(f"{task}: 텍스트 응답이 없습니다")
        return texts[0] if schema is not None else "".join(texts)

    def _auth_marker(self) -> Path:
        return auth_marker(self.settings)

    def _log_usage(self, task: str, model: str, resp: Any) -> None:
        usage = getattr(resp, "usage", None)
        row = {
            "ts": datetime.now(KST).isoformat(timespec="seconds"),
            "task": task,
            "model": getattr(resp, "model", model),
            "input_tokens": getattr(usage, "input_tokens", 0) or 0,
            "output_tokens": getattr(usage, "output_tokens", 0) or 0,
            "cache_read_input_tokens": getattr(usage, "cache_read_input_tokens", 0) or 0,
            "cache_creation_input_tokens": getattr(usage, "cache_creation_input_tokens", 0) or 0,
            "stop_reason": getattr(resp, "stop_reason", None),
            "request_id": getattr(resp, "_request_id", None),
        }
        self.usage_path.parent.mkdir(parents=True, exist_ok=True)
        with self.usage_path.open("a", encoding="utf-8") as f:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")
        console.print(
            f"[dim]LLM {task} · {row['model']} · in {row['input_tokens']} / out {row['output_tokens']}"
            f" / cache-read {row['cache_read_input_tokens']}[/dim]"
        )


def get_llm(settings: Settings, client: Any = None) -> ClaudeLLM | None:
    """키가 있으면 ClaudeLLM, 없거나 오프라인 모드면 None."""
    mode = os.environ.get("STUDIO_LLM", "").lower()
    if mode == "offline":
        return None
    has_key = bool(os.environ.get("ANTHROPIC_API_KEY") or os.environ.get("ANTHROPIC_AUTH_TOKEN"))
    if client is None and not has_key and mode != "claude":
        console.print("[yellow]ANTHROPIC_API_KEY 없음 → 오프라인 휴리스틱 모드로 진행합니다.[/yellow]")
        return None
    return ClaudeLLM(settings, client=client)


def usage_summary(settings: Settings) -> dict[str, dict[str, int]]:
    """작업별 누적 토큰."""
    path = settings.logs_dir / "llm_usage.jsonl"
    out: dict[str, dict[str, int]] = {}
    if not path.exists():
        return out
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        row = json.loads(line)
        acc = out.setdefault(row["task"], {"calls": 0, "input_tokens": 0, "output_tokens": 0})
        acc["calls"] += 1
        acc["input_tokens"] += int(row.get("input_tokens", 0))
        acc["output_tokens"] += int(row.get("output_tokens", 0))
    return out
