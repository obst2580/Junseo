"""HTTP 공통: 15초 타임아웃, 지수 백오프 재시도, 시연용 fixture 전송계층 연결."""

from __future__ import annotations

import asyncio
import logging
import os
from collections.abc import Awaitable, Callable
from pathlib import Path
from typing import Any

import httpx

log = logging.getLogger("studio.http")

DEFAULT_TIMEOUT = 15.0
USER_AGENT = "studio/0.1 (daily-politics-explainer tooling; contact: {contact})"
RETRY_STATUS = {408, 425, 429, 500, 502, 503, 504}

Sleep = Callable[[float], Awaitable[None]]
_sleep: Sleep = asyncio.sleep


def set_sleep(fn: Sleep) -> None:
    """테스트에서 백오프 대기를 바꿔 끼운다."""
    global _sleep
    _sleep = fn


def user_agent() -> str:
    """Wikimedia 등 User-Agent 정책을 지키는 UA 문자열."""
    return USER_AGENT.format(contact=os.environ.get("STUDIO_CONTACT", "unset"))


def make_client(timeout: float = DEFAULT_TIMEOUT, **kwargs: Any) -> httpx.AsyncClient:
    """공통 AsyncClient. STUDIO_FIXTURES가 있으면 fixture 전송계층을 쓴다."""
    headers = {"User-Agent": user_agent(), **kwargs.pop("headers", {})}
    fixtures = os.environ.get("STUDIO_FIXTURES")
    if fixtures and "transport" not in kwargs:
        from studio_core.fixtures import FixtureTransport

        kwargs["transport"] = FixtureTransport(Path(fixtures))
    return httpx.AsyncClient(timeout=timeout, headers=headers, follow_redirects=True, **kwargs)


class HttpError(RuntimeError):
    """재시도 후에도 실패한 요청."""

    def __init__(self, message: str, status: int | None = None) -> None:
        super().__init__(message)
        self.status = status


async def request(
    client: httpx.AsyncClient,
    method: str,
    url: str,
    *,
    retries: int = 3,
    backoff: float = 1.0,
    **kwargs: Any,
) -> httpx.Response:
    """지수 백오프로 재시도하는 요청. 4xx(429 제외)는 즉시 실패."""
    last: Exception | None = None
    for attempt in range(retries):
        try:
            resp = await client.request(method, url, **kwargs)
        except (httpx.TransportError, httpx.TimeoutException) as e:
            last = e
            log.warning("요청 실패(%s/%s) %s: %s", attempt + 1, retries, url, e)
        else:
            if resp.status_code < 400:
                return resp
            if resp.status_code not in RETRY_STATUS:
                raise HttpError(f"{resp.status_code} {url}: {resp.text[:200]}", resp.status_code)
            last = HttpError(f"{resp.status_code} {url}", resp.status_code)
            log.warning("재시도 대상 응답(%s/%s) %s: %s", attempt + 1, retries, url, resp.status_code)
        if attempt < retries - 1:
            await _sleep(backoff * (2**attempt))
    if isinstance(last, HttpError):
        raise last
    raise HttpError(f"요청 실패 {url}: {last}")


async def get_json(client: httpx.AsyncClient, url: str, **kwargs: Any) -> Any:
    """GET 후 JSON."""
    resp = await request(client, "GET", url, **kwargs)
    return resp.json()
