"""루트 공통 설정(.env, channel.yaml) 로딩.

두 패키지(topicradar, newsclip)는 같은 루트를 공유한다. 루트는
`STUDIO_ROOT` 환경변수가 있으면 그 값을, 없으면 현재 디렉터리에서 위로
올라가며 `channel.yaml`이 있는 첫 디렉터리를 쓴다.
"""

from __future__ import annotations

import json
import os
import re
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Literal
from zoneinfo import ZoneInfo

import yaml
from dotenv import load_dotenv
from pydantic import BaseModel, Field

KST = ZoneInfo("Asia/Seoul")


def now_kst() -> datetime:
    """현재 KST 시각. 시연·테스트 재현을 위해 STUDIO_NOW(ISO)로 고정할 수 있다."""
    fixed = os.environ.get("STUDIO_NOW")
    if fixed:
        dt = datetime.fromisoformat(fixed)
        return dt.replace(tzinfo=KST) if dt.tzinfo is None else dt.astimezone(KST)
    return datetime.now(KST)


FeedKind = Literal["domestic", "press", "overseas"]


class FeedSpec(BaseModel):
    """RSS 피드 한 개."""

    name: str
    url: str
    kind: FeedKind = "domestic"
    category: str | None = None


class ChannelSection(BaseModel):
    """channel.yaml의 channel 섹션."""

    youtube_channel_id: str = ""
    stance: str = ""
    categories: list[str] = Field(
        default_factory=lambda: ["국회", "정부", "사법", "선거", "외교안보", "국제", "경제", "노동", "복지"]
    )
    competitors: list[str] = Field(default_factory=list)
    past_titles: list[str] = Field(default_factory=list)
    slack_webhook: str = ""


class SourcesSection(BaseModel):
    """수집 소스 설정."""

    rss: list[FeedSpec] = Field(default_factory=list)
    overseas_rss: list[FeedSpec] = Field(default_factory=list)
    naver_queries: list[str] = Field(default_factory=list)
    kogl: list[str] = Field(default_factory=list)


class ImagesSection(BaseModel):
    """이미지 도메인 가점·차단 목록."""

    preferred_domains: list[str] = Field(default_factory=list)
    blocked_domains: list[str] = Field(default_factory=list)


class VideosSection(BaseModel):
    """유튜브 채널 가점·차단 목록."""

    preferred_channels: list[str] = Field(default_factory=list)
    blocked_channels: list[str] = Field(default_factory=list)


class ScoringSection(BaseModel):
    """점수 가중치(합계 100)."""

    weights: dict[str, float] = Field(
        default_factory=lambda: {"interest": 40.0, "gap": 20.0, "persistence": 10.0, "fit": 30.0}
    )


class ClusteringSection(BaseModel):
    """군집 보조 설정."""

    generic_tokens: list[str] = Field(default_factory=list)  # 정당명 등 어디에나 나오는 말(연결 근거 약화)


class BudgetSection(BaseModel):
    """외부 API 일일 호출 예산(무료 한도 보호용)."""

    google_cse_per_day: int = 90
    youtube_search_per_day: int = 80
    naver_per_day: int = 20000


class ChannelConfig(BaseModel):
    """channel.yaml 전체."""

    channel: ChannelSection = Field(default_factory=ChannelSection)
    sources: SourcesSection = Field(default_factory=SourcesSection)
    images: ImagesSection = Field(default_factory=ImagesSection)
    videos: VideosSection = Field(default_factory=VideosSection)
    scoring: ScoringSection = Field(default_factory=ScoringSection)
    budget: BudgetSection = Field(default_factory=BudgetSection)
    clustering: ClusteringSection = Field(default_factory=ClusteringSection)


@dataclass(frozen=True)
class Settings:
    """실행 경로와 키."""

    root: Path

    @property
    def data_dir(self) -> Path:
        """data/ 디렉터리."""
        return self.root / "data"

    @property
    def db_path(self) -> Path:
        """SQLite 경로."""
        return self.data_dir / "studio.db"

    @property
    def projects_dir(self) -> Path:
        """projects/ 디렉터리."""
        return self.root / "projects"

    @property
    def logs_dir(self) -> Path:
        """로그 디렉터리(토큰 사용량 등)."""
        return self.data_dir / "logs"

    @property
    def channel_path(self) -> Path:
        """channel.yaml 경로."""
        return self.root / "channel.yaml"

    def env(self, key: str, default: str = "") -> str:
        """환경변수 조회(빈 문자열은 미설정으로 본다)."""
        value = os.environ.get(key, default)
        return value.strip() if value else default

    def ensure_dirs(self) -> None:
        """필요한 디렉터리를 만든다."""
        for d in (self.data_dir, self.projects_dir, self.logs_dir):
            d.mkdir(parents=True, exist_ok=True)


def find_root(start: Path | None = None) -> Path:
    """studio 루트를 찾는다."""
    env_root = os.environ.get("STUDIO_ROOT")
    if env_root:
        return Path(env_root).expanduser().resolve()
    here = (start or Path.cwd()).resolve()
    for d in (here, *here.parents):
        if (d / "channel.yaml").exists():
            return d
    return here


def load_settings(start: Path | None = None) -> Settings:
    """루트를 찾고 .env를 읽어 Settings를 만든다."""
    root = find_root(start)
    env_file = root / ".env"
    if env_file.exists():
        load_dotenv(env_file, override=False)
    settings = Settings(root=root)
    settings.ensure_dirs()
    return settings


def load_channel(settings: Settings) -> ChannelConfig:
    """channel.yaml을 읽는다. 없으면 기본값."""
    path = settings.channel_path
    if not path.exists():
        return ChannelConfig()
    raw = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    return ChannelConfig.model_validate(raw)


_PAST_TITLES_RE = re.compile(r"^(?P<indent>[ \t]+)past_titles:(?P<rest>[^#]*)(?P<comment>#.*)?$")


def append_past_title(settings: Settings, title: str) -> bool:
    """channel.yaml의 past_titles에 제목을 추가한다.

    주석을 보존하기 위해 YAML 전체를 다시 쓰지 않고 past_titles 항목만
    흐름 형식 리스트 한 줄로 바꿔 쓴다(블록 형식 목록이면 그 줄들까지 대체).
    이미 있으면 False.
    """
    path = settings.channel_path
    cfg = load_channel(settings)
    if title in cfg.channel.past_titles:
        return False
    titles = [*cfg.channel.past_titles, title]
    flow = json.dumps(titles, ensure_ascii=False)
    text = path.read_text(encoding="utf-8") if path.exists() else "channel:\n  past_titles: []\n"
    lines = text.splitlines()
    out: list[str] = []
    i, replaced = 0, False
    while i < len(lines):
        m = None if replaced else _PAST_TITLES_RE.match(lines[i])
        if m is None:
            out.append(lines[i])
            i += 1
            continue
        indent = m.group("indent")
        comment = (m.group("comment") or "# 업로드 후 자동 추가").strip()
        out.append(f"{indent}past_titles: {flow}   {comment}")
        replaced = True
        i += 1
        # 블록 형식(- 항목) 줄은 흡수한다.
        while i < len(lines) and re.match(rf"^{re.escape(indent)}[ \t]*- ", lines[i]):
            i += 1
    if not replaced:
        out = [ln if ln.strip() != "channel:" else f"channel:\n  past_titles: {flow}" for ln in out]
    path.write_text("\n".join(out) + "\n", encoding="utf-8")
    return True
