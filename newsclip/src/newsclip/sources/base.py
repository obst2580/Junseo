"""검색 소스 공통: 인터페이스, 키 미설정 처리, 일일 호출 예산, 검색 결과 캐시.

무료 한도가 빠듯하다(Google CSE 하루 100회, YouTube search.list 1회=100 units / 하루 10,000).
같은 검색어는 7일 캐시(data/search_cache.json)로 재사용하고, 소스별 하루 호출 수를
data/api_usage.json에 기록해 channel.yaml budget을 넘으면 그 소스를 건너뛴다.
"""

from __future__ import annotations

import asyncio
import json
import logging
import time
from abc import ABC, abstractmethod
from pathlib import Path
from urllib.parse import urlparse

import httpx

from newsclip.models import Asset, SceneKind
from studio_core.config import now_kst
from studio_core.keys import Unavailable

log = logging.getLogger("newsclip.sources")


class SourceUnavailable(Unavailable):
    """키 미설정·예산 소진·서비스 종료로 건너뛰는 소스."""


def domain_of(url: str) -> str:
    """URL → 호스트(www. 제거)."""
    host = urlparse(url).hostname or ""
    return host[4:] if host.startswith("www.") else host


class Budget:
    """소스별 일일 호출 예산."""

    def __init__(self, path: Path, limits: dict[str, int]) -> None:
        self.path = path
        self.limits = limits
        self.data: dict[str, dict[str, int]] = (
            json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}
        )

    def _today(self) -> dict[str, int]:
        return self.data.setdefault(now_kst().date().isoformat(), {})

    def take(self, source: str, cost: int = 1) -> None:
        """호출 1회 차감. 한도 초과면 SourceUnavailable."""
        limit = self.limits.get(source)
        used = self._today().get(source, 0)
        if limit is not None and used + cost > limit:
            raise SourceUnavailable(f"{source} 일일 예산({limit}) 소진")
        self._today()[source] = used + cost
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.path.write_text(json.dumps(self.data, ensure_ascii=False, indent=1), encoding="utf-8")

    def used(self, source: str) -> int:
        """오늘 사용량."""
        return self._today().get(source, 0)


class SearchCache:
    """검색 결과 캐시(기본 7일)."""

    def __init__(self, path: Path, ttl_days: int = 7) -> None:
        self.path = path
        self.ttl = ttl_days * 86400
        self.data: dict[str, dict] = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}

    def get(self, key: str) -> list[Asset] | None:
        """캐시된 후보."""
        hit = self.data.get(key)
        if not hit or time.time() - hit["at"] > self.ttl:
            return None
        return [Asset.model_validate(a) for a in hit["assets"]]

    def put(self, key: str, assets: list[Asset]) -> None:
        """저장."""
        self.data[key] = {"at": time.time(), "assets": [a.model_dump() for a in assets]}
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.path.write_text(json.dumps(self.data, ensure_ascii=False), encoding="utf-8")


class Source(ABC):
    """모든 검색 소스의 기반."""

    name: str = "base"
    media: str = "image"

    def __init__(
        self, client: httpx.AsyncClient, budget: Budget | None = None, cache: SearchCache | None = None
    ) -> None:
        self.client = client
        self.budget = budget
        self.cache = cache
        self._inflight: dict[str, asyncio.Future[list[Asset]]] = {}

    def check_ready(self) -> None:  # noqa: B027 — 키가 필요 없는 소스는 그대로 둔다
        """키가 없으면 SourceUnavailable."""
        return None

    async def search(self, query: str, kind: SceneKind) -> list[Asset]:
        """검색(캐시 → 진행 중 동일 요청 합류 → 예산 차감 → 실제 호출)."""
        key = f"{self.name}|{kind}|{query}"
        if self.cache is not None:
            hit = self.cache.get(key)
            if hit is not None:
                return hit
        if key in self._inflight:
            return await asyncio.shield(self._inflight[key])
        self.check_ready()
        if self.budget is not None:
            self.budget.take(self.name)
        task = asyncio.ensure_future(self._search(query, kind))
        self._inflight[key] = task
        try:
            assets = await task
        finally:
            self._inflight.pop(key, None)
        if self.cache is not None:
            self.cache.put(key, assets)
        return assets

    @abstractmethod
    async def _search(self, query: str, kind: SceneKind) -> list[Asset]:
        """소스별 구현."""
