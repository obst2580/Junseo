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
from datetime import datetime, timedelta, timezone, tzinfo
from pathlib import Path
from typing import Literal
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

import yaml
from dotenv import dotenv_values, load_dotenv
from pydantic import BaseModel, Field


def load_kst(zone: type[ZoneInfo] = ZoneInfo) -> tzinfo:
    """한국 시간대. 윈도우는 시간대 DB가 없을 수 있어(tzdata 미설치) UTC+9 고정으로 대신한다.

    한국은 서머타임이 없으므로 고정 +9와 결과가 같다.
    """
    try:
        return zone("Asia/Seoul")
    except (ZoneInfoNotFoundError, OSError, ValueError):
        return timezone(timedelta(hours=9), "KST")


KST = load_kst()


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


class CommunitySpec(BaseModel):
    """커뮤니티 게시판 하나. 주제 선정과 채널의 주장(커뮤니티 의견)의 출처 — 사실 근거는 기사에서만."""

    name: str
    list_url: str  # 목록 페이지(페이지 번호 자리: 1부터면 {page}, 0부터면 {page0})
    post_pattern: str  # 글 주소 정규식(목록 HTML의 a href에 적용, 상대주소 포함)
    politics_filter: bool = False  # 자유게시판처럼 섞인 곳은 정치 키워드로 거른다
    comment_pattern: str | None = None  # 댓글 수 정규식(그룹1=숫자, 글 링크 뒤 HTML에 적용). 없으면 [12] 형식
    vote_pattern: str | None = None  # 목록의 추천(공감) 수 정규식(그룹1=숫자). 없으면 글 상세에서 읽는다
    title_pattern: str | None = (
        None  # 글 링크 안 HTML에서 제목만 뽑는 정규식(그룹1). 링크에 요약·조회수가 섞인 사이트
    )
    # 글 주소를 post_pattern에 맞은 부분만으로 정리한다(?od=…·&rtn=… 같은 꼬리 제거). 같은 글이 주소만 달리
    # 여러 번 잡히거나, robots.txt가 꼬리 붙은 주소를 막는 사이트. post_pattern이 주소 경로로 시작해야 한다
    canonical_url: bool = False
    # 글 상세(본문·댓글·공감): 선택자는 'div.post_article', '#bo_v_con', 'div.comment_row'처럼 태그·#id·.class
    # (띄어 쓰면 '안쪽', 쉼표로 여러 후보). 비우면 가장 긴 글 덩어리를 본문으로, class에 comment·reply·cmt가
    # 들어간 반복 요소를 댓글로 본다.
    body_selector: str | None = None
    comment_selector: str | None = None
    vote_selector: str | None = None  # 글·댓글 안의 공감 수 요소(비우면 class에 like·symph·vote·recom·good)
    comment_strip_selector: str | None = (
        None  # 댓글 안에서 뺄 요소(닉네임 등 — 글쓴이 정보는 저장하지 않는다)
    )
    comment_vote_regex: str | None = (
        None  # 댓글 글자에서 공감 수(그룹1)를 읽는 정규식. 읽은 부분은 댓글에서 지운다
    )
    detail_posts: int = 8  # 수집할 때마다 반응 큰 글 몇 개의 본문·댓글을 읽을지(0이면 목록만)
    pages: int = 1
    enabled: bool = True
    note: str = ""

    def page_url(self, page: int) -> str:
        """page(1부터)번째 목록 주소."""
        return self.list_url.format(page=page, page0=page - 1)


class BoardSection(BaseModel):
    """주제판: 지금 커뮤니티에서 뜨는 주제(100% 커뮤니티 반응 기준)."""

    window_hours: int = 24  # 최근 몇 시간의 커뮤니티 글로 판을 짤지
    min_posts: int = 2  # 판에 올릴 최소 글 수(반응이 아주 큰 글은 하나여도 올린다)
    size: int = 20  # 판에 보여 줄 주제 수
    derived_queries: int = 10  # 커뮤니티 화제로 만든 네이버 뉴스 검색어 수(사실 근거 기사 확보용)


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
    # 사진 검색에 섞여 나오는 유튜브 썸네일(i.ytimg.com)은 이 채널(뉴스 공식 채널) 것만 쓴다. 비우면 썸네일을 안 씀
    youtube_thumb_channels: list[str] = Field(default_factory=list)


class VideosSection(BaseModel):
    """유튜브 채널 가점·차단 목록."""

    preferred_channels: list[str] = Field(default_factory=list)
    blocked_channels: list[str] = Field(default_factory=list)


class ScriptSection(BaseModel):
    """대본 설정."""

    tone: str = "확신형+입담형"  # 확신형+입담형 | 확신형 | 해설형
    chars_per_minute: int = (
        300  # AI 음성이 1분에 읽는 글자 수(10분·3분 대본 길이의 기준). 음성을 올리면 실제 값이 보인다
    )
    outro: str = ""  # 대본 끝에 언제나 붙이는 채널 마무리 인사(Claude가 쓰지 않고 프로그램이 붙인다)


class TimelineSection(BaseModel):
    """timeline.xml에 깔 글자 트랙."""

    subtitles: bool = False  # 대본 자막(음성에 맞춘 줄 자막 PNG 트랙). 기본 끔
    attribution: bool = True  # 출처 표시(왼쪽 위, 자료마다)


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
    budget: BudgetSection = Field(default_factory=BudgetSection)
    clustering: ClusteringSection = Field(default_factory=ClusteringSection)
    script: ScriptSection = Field(default_factory=ScriptSection)
    timeline: TimelineSection = Field(default_factory=TimelineSection)
    communities: list[CommunitySpec] = Field(default_factory=list)
    board: BoardSection = Field(default_factory=BoardSection)


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


# 화면 설정이 .env에 저장하는 API 키. 운영체제 환경변수에 예전 키가 남아 있어도 .env 값을 쓴다
# (새 키를 넣었는데 예전 키로 계속 거부되는 일을 막는다).
API_KEY_NAMES = (
    "ANTHROPIC_API_KEY",
    "NAVER_CLIENT_ID",
    "NAVER_CLIENT_SECRET",
    "YOUTUBE_API_KEY",
    "GOOGLE_API_KEY",
    "GOOGLE_CSE_ID",
)
ENV_OVERRIDDEN: set[str] = set()  # 운영체제 환경변수 대신 .env 값을 쓴 키 이름


def load_settings(start: Path | None = None) -> Settings:
    """루트를 찾고 .env를 읽어 Settings를 만든다(API 키는 .env가 우선)."""
    root = find_root(start)
    env_file = root / ".env"
    if env_file.exists():
        load_dotenv(env_file, override=False)
        for name, value in dotenv_values(env_file).items():
            value = (value or "").strip()
            if name in API_KEY_NAMES and value and os.environ.get(name, "").strip() != value:
                os.environ[name] = value
                ENV_OVERRIDDEN.add(name)
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


# ---- 화면(설정 탭)에서 channel.yaml을 고칠 때: 주석을 지키려고 해당 줄만 바꾼다 ----------------------


def _split_comment(rest: str) -> tuple[str, str]:
    """'값   # 주석' → (값, '   # 주석'). 따옴표 안의 #은 주석으로 보지 않는다."""
    for m in re.finditer(r"\s+#", rest):
        before = rest[: m.start()]
        if before.count('"') % 2 == 0 and before.count("'") % 2 == 0:
            return before, rest[m.start() :]
    return rest, ""


def _yaml_value(value: object) -> str:
    """파이썬 값 → YAML 흐름 표기(문자열은 큰따옴표, 목록은 [..])."""
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (int, float)):
        return str(value)
    if isinstance(value, (list, tuple)):
        return "[" + ", ".join(_yaml_value(v) for v in value) + "]"
    if isinstance(value, dict):
        return "{" + ", ".join(f"{k}: {_yaml_value(v)}" for k, v in value.items()) + "}"
    return json.dumps(str(value), ensure_ascii=False)


def _validated_write(settings: Settings, text: str) -> None:
    """검사(파싱·스키마) 후 저장. 실패하면 ValueError, 파일은 그대로."""
    try:
        ChannelConfig.model_validate(yaml.safe_load(text) or {})
    except Exception as e:  # yaml 오류·스키마 오류 모두
        raise ValueError(f"channel.yaml 검사 실패: {e}") from e
    settings.channel_path.write_text(text if text.endswith("\n") else text + "\n", encoding="utf-8")


def set_channel_value(settings: Settings, section: str, key: str, value: object) -> None:
    """channel.yaml의 `section.key` 값만 바꾼다(주석·나머지 줄 보존). 없으면 추가."""
    path = settings.channel_path
    lines = path.read_text(encoding="utf-8").splitlines() if path.exists() else []
    start = next(
        (i for i, ln in enumerate(lines) if re.match(rf"^{re.escape(section)}:\s*(#.*)?$", ln)), None
    )
    if start is None:
        lines += ["", f"{section}:", f"  {key}: {_yaml_value(value)}"]
        _validated_write(settings, "\n".join(lines))
        return
    end = next((i for i in range(start + 1, len(lines)) if re.match(r"^[^\s#]", lines[i])), len(lines))
    key_re = re.compile(rf"^(?P<indent>[ \t]+){re.escape(key)}:(?P<rest>.*)$")
    for i in range(start + 1, end):
        m = key_re.match(lines[i])
        if not m:
            continue
        indent = m.group("indent")
        _old, comment = _split_comment(m.group("rest"))
        lines[i] = f"{indent}{key}: {_yaml_value(value)}{comment}"
        j = i + 1  # 블록 목록(- 항목) 줄은 흡수
        while j < end and re.match(rf"^{re.escape(indent)}[ \t]*- ", lines[j]):
            j += 1
        del lines[i + 1 : j]
        break
    else:
        lines.insert(start + 1, f"  {key}: {_yaml_value(value)}")
    _validated_write(settings, "\n".join(lines))


def set_community_enabled(settings: Settings, name: str, enabled: bool) -> None:
    """communities 중 name 게시판의 enabled만 바꾼다(흐름·블록 형식 모두)."""
    path = settings.channel_path
    lines = path.read_text(encoding="utf-8").splitlines()
    flag = "true" if enabled else "false"
    name_re = re.compile(
        rf"""^(?P<indent>[ \t]*)- (?P<flow>\{{)?\s*name:\s*["']?{re.escape(name)}["']?\s*(?=,|\}}|#|$)"""
    )
    for i, ln in enumerate(lines):
        m = name_re.match(ln)
        if not m:
            continue
        if m.group("flow"):
            if re.search(r"enabled:\s*\w+", ln):
                lines[i] = re.sub(r"enabled:\s*\w+", f"enabled: {flag}", ln)
            else:
                lines[i] = re.sub(r"\}(\s*(#.*)?)$", rf", enabled: {flag}}}\1", ln)
            break
        # 블록 형식: 이 항목에 속한 줄(더 깊게 들여 쓴 줄)에서 enabled를 찾고, 없으면 name 다음 줄에 넣는다
        indent = m.group("indent")
        j = i + 1
        while j < len(lines) and (lines[j].startswith(indent + "  ") or not lines[j].strip()):
            em = re.match(r"^(?P<ind>[ \t]+)enabled:(?P<rest>.*)$", lines[j])
            if em:
                _old, comment = _split_comment(em.group("rest"))
                lines[j] = f"{em.group('ind')}enabled: {flag}{comment}"
                break
            j += 1
        else:
            lines.insert(i + 1, f"{indent}  enabled: {flag}")
        break
    else:
        raise KeyError(f"커뮤니티 '{name}' 없음")
    _validated_write(settings, "\n".join(lines))


def save_channel_text(settings: Settings, text: str) -> None:
    """channel.yaml 전체를 검사 후 저장(설정 탭 '고급')."""
    _validated_write(settings, text)
