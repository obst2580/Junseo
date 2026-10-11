"""API 키·인증 헤더 공통(두 패키지가 공유)."""

from __future__ import annotations

import os


class Unavailable(RuntimeError):
    """키 미설정·예산 소진 등으로 건너뛰는 외부 소스(실패가 아니라 '건너뜀')."""


def naver_base() -> str:
    """네이버 API 호스트. NAVER API HUB 등으로 바뀌면 NAVER_API_BASE로 교체."""
    return os.environ.get("NAVER_API_BASE", "https://openapi.naver.com").rstrip("/")


def naver_headers() -> dict[str, str]:
    """네이버 인증 헤더(헤더 이름도 환경변수로 교체 가능)."""
    cid, secret = os.environ.get("NAVER_CLIENT_ID", ""), os.environ.get("NAVER_CLIENT_SECRET", "")
    if not cid or not secret:
        raise Unavailable("NAVER_CLIENT_ID/NAVER_CLIENT_SECRET 미설정")
    return {
        os.environ.get("NAVER_HEADER_ID", "X-Naver-Client-Id"): cid,
        os.environ.get("NAVER_HEADER_SECRET", "X-Naver-Client-Secret"): secret,
    }


def require(*names: str) -> dict[str, str]:
    """필수 환경변수 묶음. 하나라도 없으면 Unavailable."""
    values = {n: os.environ.get(n, "") for n in names}
    missing = [n for n, v in values.items() if not v]
    if missing:
        raise Unavailable(f"{'/'.join(missing)} 미설정")
    return values
