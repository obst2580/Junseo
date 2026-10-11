"""커뮤니티 정치 게시판 수집기 — 주제 선정의 관심 신호.

원칙
- 커뮤니티 글은 "시청층이 지금 무엇에 반응하나"를 재는 데만 쓴다. 대본의 사실 근거로는 쓰지 않는다
  (사실은 뉴스 기사에서만: 절대 규칙 2, 음모론 금지). 그래서 제목·주소·댓글 수·시각만 저장하고 본문은 받지 않는다.
- robots.txt를 지키고, 게시판마다 목록 1~2쪽만, 요청 사이 1초 쉰다. 하루 3회 수집 기준.

파싱은 사이트별 HTML 구조에 덜 의존하도록 '글 주소 정규식'으로 a 태그를 찾고, 그 링크 뒤쪽 텍스트에서
댓글 수([12], 같은 글로 가는 숫자 링크, 또는 comment_pattern)와 작성 시각(HH:MM, MM.DD, YYYY.MM.DD, n분 전)을 찾는다. 사이트 개편으로 패턴이 맞지 않으면
`topicradar probe`로 확인하고 channel.yaml의 post_pattern을 고친다.
"""

from __future__ import annotations

import html as htmllib
import logging
import re
from dataclasses import dataclass
from datetime import datetime, time, timedelta
from urllib.parse import urljoin, urlparse
from urllib.robotparser import RobotFileParser

import httpx

from studio_core.config import KST, CommunitySpec, now_kst
from studio_core.http import pause, request, user_agent
from studio_core.text import normalize_title, strip_html
from topicradar.collectors.base import Collector
from topicradar.models import Signal

log = logging.getLogger("topicradar.community")

_A_RE = re.compile(r"<a\b[^>]*?href\s*=\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", re.I | re.S)
_COMMENT_RE = re.compile(r"[\[(](\d{1,5})[\])]")
_ABS_DATE_RE = re.compile(r"(?<!\d)(\d{2,4})[.\-/](\d{1,2})[.\-/](\d{1,2})(?:\s+(\d{1,2}):(\d{2}))?")
_MD_RE = re.compile(r"(?<![\d.])(\d{1,2})[.\-/](\d{1,2})(?:\s+(\d{1,2}):(\d{2}))?(?![\d.])")
_HM_RE = re.compile(r"(?<!\d)(\d{1,2}):(\d{2})(?::\d{2})?(?!\d)")
_REL_RE = re.compile(r"(\d{1,3})\s*(분|시간)\s*전")

# 정치글 판별(혼합 게시판용): 기관·직책·절차·정당 어휘
POLITICS_WORDS = [
    "국회",
    "본회의",
    "법사위",
    "상임위",
    "국정감사",
    "국감",
    "의원",
    "여당",
    "야당",
    "여야",
    "원내",
    "대표",
    "탄핵",
    "대통령",
    "대통령실",
    "총리",
    "장관",
    "국무회의",
    "정부",
    "청와대",
    "검찰",
    "수사",
    "기소",
    "재판",
    "선고",
    "법원",
    "헌재",
    "대법원",
    "특검",
    "선거",
    "공천",
    "여론조사",
    "지지율",
    "경선",
    "후보",
    "외교",
    "북한",
    "국방",
    "법안",
    "입법",
    "개정안",
    "시행령",
    "예산",
    "정책",
    "가결",
    "부결",
    "표결",
    "노조",
    "파업",
    "기본법",
    "더불어민주당",
    "민주당",
    "국민의힘",
    "조국혁신당",
    "개혁신당",
    "진보당",
    "정의당",
]


@dataclass
class BoardPost:
    """목록에서 뽑은 글 하나."""

    title: str
    url: str
    comments: int
    posted_at: datetime


def parse_time(text: str, now: datetime) -> datetime | None:
    """목록 행 텍스트에서 작성 시각(KST)."""
    m = _REL_RE.search(text)
    if m:
        n = int(m.group(1))
        return now - (timedelta(minutes=n) if m.group(2) == "분" else timedelta(hours=n))
    if "어제" in text:
        hm = _HM_RE.search(text)
        t = time(int(hm.group(1)), int(hm.group(2))) if hm else time(12, 0)
        return datetime.combine(now.date() - timedelta(days=1), t, tzinfo=KST)
    m = _ABS_DATE_RE.search(text)
    if m:
        y, mo, d = int(m.group(1)), int(m.group(2)), int(m.group(3))
        y = y + 2000 if y < 100 else y
        hh, mm = (int(m.group(4)), int(m.group(5))) if m.group(4) else (12, 0)  # 날짜만 있으면 정오로
        try:
            return datetime(y, mo, d, hh, mm, tzinfo=KST)
        except ValueError:
            return None
    m = _MD_RE.search(text)
    if m:
        mo, d = int(m.group(1)), int(m.group(2))
        hh, mm = (int(m.group(3)), int(m.group(4))) if m.group(3) else (12, 0)
        try:
            dt = datetime(now.year, mo, d, hh, mm, tzinfo=KST)
        except ValueError:
            return None
        return dt.replace(year=now.year - 1) if dt > now + timedelta(days=1) else dt
    m = _HM_RE.search(text)
    if m:
        dt = datetime.combine(now.date(), time(int(m.group(1)) % 24, int(m.group(2)) % 60), tzinfo=KST)
        return dt - timedelta(days=1) if dt > now + timedelta(minutes=5) else dt
    return None


def is_political(title: str, extra: list[str] | None = None) -> bool:
    """제목이 정치글인가."""
    return any(w in title for w in [*POLITICS_WORDS, *(extra or [])])


def parse_board(html_text: str, spec: CommunitySpec, base_url: str, now: datetime) -> list[BoardPost]:
    """목록 HTML → 글 목록.

    같은 글을 가리키는 링크(제목 링크 + 댓글 수 링크 등)는 하나로 묶고, 그 글 영역(다음 '다른 글' 링크 전까지)에서
    댓글 수와 시각을 찾는다. 공지·너무 짧은 제목은 뺀다.
    """
    pattern = re.compile(spec.post_pattern)
    hits = []
    for m in _A_RE.finditer(html_text):
        url = urljoin(base_url, htmllib.unescape(m.group(1)))
        pm = pattern.search(url)
        if pm:
            hits.append((m, url, pm.group(0)))
    groups: list[list[tuple[re.Match[str], str, str]]] = []
    for h in hits:
        if groups and groups[-1][0][2] == h[2]:
            groups[-1].append(h)
        else:
            groups.append([h])
    out: dict[str, BoardPost] = {}
    for gi, g in enumerate(groups):
        key = g[0][2]
        best = max(g, key=lambda h: len(strip_html(h[0].group(2))))
        title_raw = strip_html(best[0].group(2))
        start = g[0][0].end()
        end = groups[gi + 1][0][0].start() if gi + 1 < len(groups) else start + 1500
        seg_html = html_text[start : min(end, start + 1500)]
        seg = strip_html(seg_html)
        if title_raw.lstrip().startswith(("공지", "[공지]", "【공지】")):
            continue
        cm = re.search(spec.comment_pattern, seg_html) if spec.comment_pattern else None
        cm = cm or _COMMENT_RE.search(title_raw)
        # 같은 글을 가리키는 숫자뿐인 링크(댓글 수 링크)도 댓글 수로 본다
        linked = next(
            (x for x in (strip_html(h[0].group(2)).strip() for h in g if h is not best) if x.isdigit()), None
        )
        cm = cm or (None if linked else _COMMENT_RE.search(seg[:40]))
        comments = int(cm.group(1)) if cm else int(linked or 0)
        title = normalize_title(_COMMENT_RE.sub("", title_raw)).strip()
        if len(title) < 4:
            continue
        url = g[0][1].split("#", 1)[0]
        posted = parse_time(seg, now) or now - timedelta(minutes=1)
        if key not in out:
            out[key] = BoardPost(title=title, url=url, comments=comments, posted_at=posted)
    return list(out.values())


class RobotsCache:
    """호스트별 robots.txt 판정."""

    def __init__(self, client: httpx.AsyncClient) -> None:
        self.client = client
        self.parsers: dict[str, RobotFileParser | None] = {}

    async def allowed(self, url: str) -> bool:
        """robots.txt가 허용하면 True(없거나 못 읽으면 허용으로 본다)."""
        host = f"{urlparse(url).scheme}://{urlparse(url).netloc}"
        if host not in self.parsers:
            try:
                resp = await self.client.get(f"{host}/robots.txt")
                if resp.status_code >= 400:
                    self.parsers[host] = None
                else:
                    rp = RobotFileParser()
                    rp.parse(resp.text.splitlines())
                    self.parsers[host] = rp
            except httpx.HTTPError:
                self.parsers[host] = None
        rp = self.parsers[host]
        return True if rp is None else rp.can_fetch(user_agent(), url)


class CommunityCollector(Collector):
    """설정된 커뮤니티 게시판 목록을 읽어 Signal(kind='community')로."""

    name = "community"

    def __init__(
        self, specs: list[CommunitySpec], client: httpx.AsyncClient, extra_politics: list[str] | None = None
    ) -> None:
        self.specs = [s for s in specs if s.enabled]
        self.client = client
        self.extra = extra_politics or []
        self.robots = RobotsCache(client)
        self.site_errors: dict[str, str] = {}
        self.site_counts: dict[str, int] = {}

    async def fetch_site(self, spec: CommunitySpec, since: datetime) -> list[Signal]:
        """게시판 하나(실패 격리)."""
        now = now_kst()
        out: list[Signal] = []
        for page in range(1, spec.pages + 1):
            url = spec.page_url(page)
            if not await self.robots.allowed(url):
                self.site_errors[spec.name] = "robots.txt가 수집을 허용하지 않음 — 건너뜀"
                log.warning("[%s] robots.txt 금지: %s", spec.name, url)
                return out
            try:
                resp = await request(self.client, "GET", url)
            except Exception as e:  # 게시판 단위 실패 격리
                self.site_errors[spec.name] = str(e)
                log.warning("[%s] 목록 실패: %s", spec.name, e)
                return out
            posts = parse_board(resp.text, spec, url, now)
            if page == 1 and not posts:  # 글 주소 패턴이 사이트와 안 맞을 때(개편·잘못된 게시판 id)
                self.site_errors[spec.name] = (
                    "목록에서 글을 하나도 못 찾음 — post_pattern 확인(topicradar probe)"
                )
                return out
            for post in posts:
                if post.posted_at < since:
                    continue
                if spec.politics_filter and not is_political(post.title, self.extra):
                    continue
                out.append(
                    Signal(
                        source=f"community:{spec.name}",
                        kind="community",
                        fetched_at=now,
                        published_at=post.posted_at,
                        title=post.title,
                        url=post.url,
                        summary="",
                        metric={"site": spec.name, "comments": post.comments},
                    )
                )
            if page < spec.pages:
                await pause(1.0)
        self.site_counts[spec.name] = len(out)
        return out

    async def fetch(self, since: datetime) -> list[Signal]:
        """모든 게시판(사이트끼리는 동시에, 사이트 안에서는 순차)."""
        import asyncio

        chunks = await asyncio.gather(*(self.fetch_site(s, since) for s in self.specs))
        return [s for c in chunks for s in c]
