"""API 키·인증 헤더 공통(두 패키지가 공유)."""

from __future__ import annotations

import os
from dataclasses import dataclass
from typing import Any

import httpx


class Unavailable(RuntimeError):
    """키 미설정·예산 소진 등으로 건너뛰는 외부 소스(실패가 아니라 '건너뜀')."""


# ---- 네이버 검색 ----------------------------------------------------------------------------------
# 2026-07-31부터 개발자센터(openapi.naver.com) 검색 키 신규 발급이 끝나고 NAVER API HUB(네이버 클라우드)로 옮겨 갔다.
# 기존 키는 2027-06-30까지 동작. 파라미터와 응답 형식은 같고 호스트·경로·인증 헤더만 다르다.
#   HUB:  https://naverapihub.apigw.ntruss.com/search/v1/{news,image}  X-NCP-APIGW-API-KEY-ID / X-NCP-APIGW-API-KEY
#   예전: https://openapi.naver.com/v1/search/{news.json,image}       X-Naver-Client-Id / X-Naver-Client-Secret
# NAVER_API=auto(기본)면 HUB로 먼저 부르고, 인증 실패(401/403)면 예전 방식으로 한 번 더 부른 뒤 성공한 쪽을 기억한다.

HUB_BASE = "https://naverapihub.apigw.ntruss.com"
LEGACY_BASE = "https://openapi.naver.com"
_PATHS = {
    "hub": {"news": "/search/v1/news", "image": "/search/v1/image"},
    "legacy": {"news": "/v1/search/news.json", "image": "/v1/search/image"},
}
_working: dict[str, str] = {}


@dataclass
class NaverEndpoint:
    """호출 후보 하나."""

    mode: str  # hub | legacy
    url: str
    headers: dict[str, str]


def naver_credentials() -> tuple[str, str]:
    """(Client ID, Client Secret). 없으면 Unavailable."""
    cid, secret = (
        os.environ.get("NAVER_CLIENT_ID", "").strip(),
        os.environ.get("NAVER_CLIENT_SECRET", "").strip(),
    )
    if not cid or not secret:
        raise Unavailable("NAVER_CLIENT_ID/NAVER_CLIENT_SECRET 미설정")
    return cid, secret


def naver_endpoints(kind: str) -> list[NaverEndpoint]:
    """kind(news|image)의 호출 후보(시도 순서대로)."""
    cid, secret = naver_credentials()
    hub = NaverEndpoint(
        "hub",
        os.environ.get("NAVER_HUB_BASE", HUB_BASE).rstrip("/") + _PATHS["hub"][kind],
        {"X-NCP-APIGW-API-KEY-ID": cid, "X-NCP-APIGW-API-KEY": secret},
    )
    legacy = NaverEndpoint(
        "legacy",
        os.environ.get("NAVER_API_BASE", LEGACY_BASE).rstrip("/") + _PATHS["legacy"][kind],
        {"X-Naver-Client-Id": cid, "X-Naver-Client-Secret": secret},
    )
    mode = os.environ.get("NAVER_API", "auto").strip().lower()
    if mode == "hub":
        return [hub]
    if mode == "legacy":
        return [legacy]
    return [legacy, hub] if _working.get("mode") == "legacy" else [hub, legacy]


async def naver_get(client: httpx.AsyncClient, kind: str, params: dict[str, Any]) -> Any:
    """네이버 검색 GET(HUB/예전 방식 자동 선택) → JSON."""
    from studio_core.http import HttpError, get_json

    candidates = naver_endpoints(kind)
    for i, ep in enumerate(candidates):
        try:
            data = await get_json(client, ep.url, params=params, headers=ep.headers)
        except HttpError as e:
            if e.status in (401, 403) and i + 1 < len(candidates):
                continue
            if e.status in (401, 403):
                raise HttpError(
                    f"네이버 인증 실패({e.status}) — Client ID/Secret 확인. 새 키는 네이버 클라우드 NAVER API HUB에서 발급",
                    e.status,
                ) from e
            raise
        _working["mode"] = ep.mode
        return data
    raise Unavailable("네이버 호출 후보 없음")  # pragma: no cover


def require(*names: str) -> dict[str, str]:
    """필수 환경변수 묶음. 하나라도 없으면 Unavailable."""
    values = {n: os.environ.get(n, "") for n in names}
    missing = [n for n, v in values.items() if not v]
    if missing:
        raise Unavailable(f"{'/'.join(missing)} 미설정")
    return values
