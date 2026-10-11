"""newsclip 데이터 모델."""

from __future__ import annotations

import hashlib
from typing import Literal

from pydantic import BaseModel, Field

SceneKind = Literal["photo", "video", "broll"]
Media = Literal["image", "video"]


def asset_id(source: str, url: str) -> str:
    """소스 + URL 기반 안정 ID."""
    return f"{source}-{hashlib.sha1(url.encode('utf-8')).hexdigest()[:10]}"


class Asset(BaseModel):
    """검색 후보 자료 하나."""

    source: str
    id: str
    title: str = ""
    url: str  # 원본(이미지 URL 또는 영상 시청 URL)
    source_page_url: str = ""  # 출처 페이지(기사·게시물·영상 페이지)
    source_domain: str = ""
    download_url: str = ""
    width: int | None = None
    height: int | None = None
    duration_sec: float | None = None  # 영상: 받은 파일(구간) 길이
    source_duration_sec: float | None = None  # 영상: 원본 전체 길이(구간 재다운로드용)
    author_or_channel: str = ""
    attribution_text: str = ""
    local_path: str | None = None
    clip_start_sec: float | None = None
    score: float = 0.0
    media: Media = "image"
    license: str | None = None  # Wikimedia 등 라이선스가 명시된 경우
    sha256: str | None = None
    score_notes: list[str] = Field(default_factory=list)


class Scene(BaseModel):
    """장면 하나."""

    id: str
    start_sec: float
    end_sec: float
    text: str
    topic: str
    keywords_ko: list[str] = Field(default_factory=list)
    keywords_en: list[str] = Field(default_factory=list)
    kind: SceneKind = "broll"
    candidates: list[Asset] = Field(default_factory=list)
    chosen: Asset | None = None
    skipped: bool = False
    cue_ids: list[int] = Field(default_factory=list)

    @property
    def duration(self) -> float:
        """장면 길이(초)."""
        return max(0.0, self.end_sec - self.start_sec)


class SceneSet(BaseModel):
    """scenes.json."""

    project: str
    fps: int = 30
    width: int = 1920
    height: int = 1080
    scenes: list[Scene] = Field(default_factory=list)
    llm_mode: Literal["claude", "offline"] = "offline"
