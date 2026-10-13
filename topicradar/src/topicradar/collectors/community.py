"""커뮤니티 정치 게시판 수집기 — 주제 선정과 채널 주장(커뮤니티 의견)의 출처.

원칙
- 무엇을 다룰지(반응)와 무엇을 주장할지(의견)는 커뮤니티 글·댓글·공감 수로 정한다.
  사실·수치·인용의 근거로는 쓰지 않는다(사실은 뉴스 기사에서만, 음모론 금지).
- 목록: 제목·주소·댓글 수·(있으면) 추천 수·시각. 상세: 반응이 큰 글 몇 개만 본문·댓글·댓글 공감 수.
  닉네임 등 글쓴이 정보는 저장하지 않는다.
- robots.txt를 지키고, 게시판마다 목록 1~2쪽 + 글 상세 몇 개만, 요청 사이 1초 쉰다.

목록 파싱은 사이트별 HTML 구조에 덜 의존하도록 '글 주소 정규식'으로 a 태그를 찾고, 그 링크 뒤쪽 텍스트에서
댓글 수([12], 같은 글로 가는 숫자 링크, 또는 comment_pattern)와 작성 시각(HH:MM, MM.DD, YYYY.MM.DD, n분 전)을 찾는다.
상세 파싱은 선택자(body_selector·comment_selector)가 있으면 그대로, 없으면 '가장 긴 글 덩어리 = 본문',
'class에 comment·reply·cmt가 든 반복 요소 = 댓글'로 찾는다. 사이트 개편으로 맞지 않으면 `topicradar probe`로 확인한다.
"""

from __future__ import annotations

import html as htmllib
import logging
import re
from collections.abc import Callable
from dataclasses import dataclass
from datetime import datetime, time, timedelta
from urllib.parse import urljoin, urlparse
from urllib.robotparser import RobotFileParser

import httpx

from studio_core.config import KST, CommunitySpec, now_kst
from studio_core.http import pause, request, user_agent
from studio_core.text import normalize_title, strip_html
from topicradar.collectors.base import Collector
from topicradar.collectors.htmltree import Node, parse_html
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
    votes: int | None = None


@dataclass
class Comment:
    """댓글 하나(글쓴이 정보 없이 내용과 공감 수만)."""

    text: str
    votes: int = 0


@dataclass
class PostDetail:
    """글 상세."""

    body: str
    votes: int | None
    comments: list[Comment]


def decode_html(resp: httpx.Response) -> str:
    """응답 → 글자. 헤더에 문자셋이 없으면 <meta charset>을 본다(뽐뿌 등 EUC-KR 사이트)."""
    ctype = resp.headers.get("content-type", "")
    if "charset=" in ctype.lower():
        return resp.text
    head = resp.content[:4096].decode("ascii", errors="ignore")
    m = re.search(r"""<meta[^>]+charset\s*=\s*["']?([\w-]+)""", head, re.I)
    enc = (m.group(1) if m else "utf-8").lower()
    try:
        return resp.content.decode(enc, errors="replace")
    except LookupError:
        return resp.content.decode("utf-8", errors="replace")


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
        # 제목 정규식이 있으면 그 정규식에 맞는 링크 중에서 고른다(같은 글로 가는 링크가 제목·요약 두 개인 사이트)
        titled = [h for h in g if spec.title_pattern and re.search(spec.title_pattern, h[0].group(2), re.S)]
        best = max(titled or g, key=lambda h: len(strip_html(h[0].group(2))))
        inner = best[0].group(2)
        tm = re.search(spec.title_pattern, inner, re.S) if spec.title_pattern else None
        title_raw = strip_html(tm.group(1)) if tm else strip_html(inner)
        start = g[0][0].end()
        end = groups[gi + 1][0][0].start() if gi + 1 < len(groups) else start + 1500
        seg_html = html_text[start : min(end, start + 1500)]
        seg = strip_html(seg_html)
        if title_raw.lstrip().startswith(("공지", "[공지]", "【공지】")):
            continue
        # 댓글·공감 수는 글 링크 뒤에서 먼저 찾고, 없으면 링크 안(제목·요약과 한 덩어리인 사이트)에서
        cm = (
            (re.search(spec.comment_pattern, seg_html) or re.search(spec.comment_pattern, inner))
            if spec.comment_pattern
            else None
        )
        cm = cm or _COMMENT_RE.search(title_raw)
        # 같은 글을 가리키는 숫자뿐인 링크(댓글 수 링크)도 댓글 수로 본다
        linked = next(
            (x for x in (strip_html(h[0].group(2)).strip() for h in g if h is not best) if x.isdigit()), None
        )
        cm = cm or (None if linked else _COMMENT_RE.search(seg[:40]))
        comments = int(cm.group(1)) if cm else int(linked or 0)
        vm = (
            (re.search(spec.vote_pattern, seg_html) or re.search(spec.vote_pattern, inner))
            if spec.vote_pattern
            else None
        )
        votes = int(vm.group(1).replace(",", "")) if vm else None
        title = normalize_title(_COMMENT_RE.sub("", title_raw)).strip()
        if len(title) < 4:
            continue
        url = urljoin(base_url, key) if spec.canonical_url else g[0][1].split("#", 1)[0]
        posted = parse_time(seg, now) or (parse_time(strip_html(inner), now) if tm else None)
        posted = posted or now - timedelta(minutes=1)
        if key not in out:
            out[key] = BoardPost(title=title, url=url, comments=comments, posted_at=posted, votes=votes)
    return list(out.values())


# ---- 글 상세: 본문·댓글·공감 ---------------------------------------------------

MAX_VOTES = 100_000  # 글 하나의 공감 수로는 말이 안 되는 숫자(글 번호·조회수를 잘못 읽은 것)
COMMENTISH = re.compile(r"(comment|reply|cmt|memo|댓글)", re.I)
_NAVISH = re.compile(
    r"(^|[-_ ])(nav|menu|header|footer|side|aside|gnb|lnb|banner|login|best|popular|related|list)", re.I
)
_VOTEISH = re.compile(r"(like|symph|vote|recom|good|sympathy|thumb|추천|공감)", re.I)
_NEGVOTE = re.compile(r"(nogood|dislike|unlike|bad|down|비추)", re.I)
_CONTENTISH = re.compile(r"(content|text|txt|view|body|cont|memo)", re.I)
_VOTE_TEXT_RE = re.compile(r"(?:추천|공감|좋아요|♥|❤)\s*[:：]?\s*(\d[\d,]*)")
_NUM_RE = re.compile(r"^\s*\+?(\d[\d,]{0,8})\s*$")
_JUNK_LINE_RE = re.compile(
    r"^(\d{2,4}[.\-/]\d{1,2}[.\-/]\d{1,2}.*|\d{1,2}:\d{2}(:\d{2})?|\d+\s*(분|시간|일)\s*전|"
    r"(추천|공감|비추천|신고|답글|댓글|삭제|수정|좋아요|공유|IP|ip)\s*\d*|\d+|[\d.]+\.\*+\.?\d*)$"
)


def _attr_text(n: Node) -> str:
    return " ".join([n.id, *n.classes])


def _num_in(n: Node) -> int | None:
    if _NEGVOTE.search(_attr_text(n)) or "비추" in n.text():
        return None
    t = n.text()
    m = _NUM_RE.match(t) or _VOTE_TEXT_RE.search(t)
    return int(m.group(1).replace(",", "")) if m else None


def _vote_of(n: Node, selector: str | None) -> int | None:
    """댓글 안의 공감 수(선택자 → class 이름 → '추천 12' 글자 순으로 찾는다)."""
    cands = n.select(selector) if selector else [x for x in n.nodes() if _VOTEISH.search(_attr_text(x))]
    for c in cands:
        v = _num_in(c)
        if v is not None:
            return v
    m = _VOTE_TEXT_RE.search(n.text())
    return int(m.group(1).replace(",", "")) if m else None


def _post_votes(root: Node, spec: CommunitySpec, skip: set[int], body: Node) -> int | None:
    """글의 공감 수: 댓글·메뉴·인기글 목록 밖에서, 본문에 가장 가까운 공감 요소."""

    def outside(n: Node) -> bool:
        chain = [n, *n.ancestors()]
        return not any(id(a) in skip or _NAVISH.search(_attr_text(a)) for a in chain)

    body_chain = {id(a): a.depth() for a in [body, *body.ancestors()]}
    bd = body.depth()

    def distance(n: Node) -> int:
        lca = next(a for a in [n, *n.ancestors()] if id(a) in body_chain)
        return n.depth() - body_chain[id(lca)] + bd - body_chain[id(lca)]

    heuristic = [x for x in root.nodes() if _VOTEISH.search(_attr_text(x))]
    # vote_selector는 댓글 공감용으로만 맞춘 사이트도 있어, 글 밖에서 못 찾으면 class 이름으로 다시 찾는다
    for cands in ([*root.select(spec.vote_selector)] if spec.vote_selector else [], heuristic):
        found = [
            (distance(c), i, v) for i, c in enumerate(cands) if outside(c) and (v := _num_in(c)) is not None
        ]
        if found:
            return min(found)[2]
    m = _VOTE_TEXT_RE.search(root.text(skip | {id(body)}))
    return int(m.group(1).replace(",", "")) if m else None


def _comment_nodes(root: Node, spec: CommunitySpec) -> list[Node]:
    if spec.comment_selector:
        return root.select(spec.comment_selector)
    groups: dict[tuple[str, str], list[Node]] = {}
    for n in root.nodes():
        if COMMENTISH.search(_attr_text(n)):
            groups.setdefault((n.tag, n.attrs.get("class", "") or "#" + n.id), []).append(n)
    best: tuple[tuple[int, int], list[Node]] | None = None
    for ns in groups.values():
        texts = [x.text() for x in ns]
        wordy = [t for t in texts if re.search(r"[가-힣A-Za-z]{2}", t)]
        avg = sum(len(t) for t in texts) / len(ns)
        if len(wordy) * 2 < len(ns) or not 2 <= avg <= 1200:
            continue
        if any(a in ns for x in ns for a in x.ancestors()):  # 서로 안에 든 요소(목록 상자 등)
            continue
        key = (len(ns), -min(x.depth() for x in ns))
        if best is None or key > best[0]:
            best = (key, ns)
    if best is None:
        return []
    if best[0][0] == 1:  # 댓글 상자 하나만 잡혔으면 그 안의 반복 요소(li 등)가 댓글
        rows = _repeated_children(best[1][0])
        if len(rows) >= 2:
            return rows
        if len(best[1][0].text()) > 600:
            return []
    return best[1]


def _repeated_children(box: Node) -> list[Node]:
    """상자 안에서 같은 깊이로 가장 많이 반복되는 요소들."""
    groups: dict[tuple[str, int], list[Node]] = {}
    base = box.depth()
    for n in box.nodes():
        if n.tag in ("li", "div", "article", "tr", "dl", "section") and n.text():
            groups.setdefault((n.tag, n.depth() - base), []).append(n)
    cands = [g for g in groups.values() if len(g) >= 2]
    return max(cands, key=lambda g: (len(g), -g[0].depth()), default=[])


def _comment_text(n: Node, skip: set[int] | None = None) -> str:
    skip = skip or set()
    inner = [x for x in n.nodes() if _CONTENTISH.search(_attr_text(x)) and not _VOTEISH.search(_attr_text(x))]
    inner = [
        x for x in inner if not any(id(a) in skip for a in [x, *x.ancestors()]) and len(x.text(skip)) >= 2
    ]
    if inner:
        text = max(inner, key=lambda x: len(x.text(skip))).text(skip)
        return " ".join(text.split())
    lines = [ln for ln in n.text(skip).split("\n") if not _JUNK_LINE_RE.match(ln.strip())]
    if len(lines) > 1:  # 첫 줄의 짧은 띄어쓰기 없는 말은 닉네임으로 보고 뺀다
        lines = [ln for i, ln in enumerate(lines) if not (i == 0 and len(ln) <= 12 and " " not in ln)]
    return " ".join(" ".join(lines).split()).strip(" /|:·-")


def _comment_row(n: Node, comment_ids: set[int]) -> Node:
    """댓글 하나를 감싼 가장 바깥 요소(다른 댓글은 들어 있지 않은)."""
    row = n
    for a in n.ancestors():
        if any(id(x) in comment_ids and x is not n for x in a.nodes()):
            break
        row = a
    return row


_CHROME_TAGS = {
    "a",
    "nav",
    "header",
    "footer",
    "aside",
    "form",
    "head",
    "title",
    "button",
    "select",
    "h1",
    "h2",
    "h3",
}


def _chrome(root: Node, skip: set[int]) -> set[int]:
    """본문이 아닌 요소(머리말·메뉴·제목·버튼·링크·댓글)의 id 집합."""
    out = set(skip)
    for n in root.nodes():
        if n.tag in _CHROME_TAGS or _NAVISH.search(_attr_text(n)):
            out.add(id(n))
    return out


def _main_block(root: Node, chrome: set[int]) -> Node:
    """본문 상자: 머리말·메뉴·링크·댓글을 뺀 글이 가장 많이 모인 가장 깊은 요소."""
    sizes: dict[int, int] = {}

    def size(n: Node) -> int:
        if id(n) in sizes:
            return sizes[id(n)]
        total = 0
        if id(n) not in chrome:
            for c in n.children:
                total += len(c.strip()) if isinstance(c, str) else size(c)
        sizes[id(n)] = total
        return total

    cur = root
    while True:
        kids = [c for c in cur.children if isinstance(c, Node)]
        if not kids:
            return cur
        big = max(kids, key=size)
        if size(big) < 0.7 * max(size(cur), 1):
            return cur
        cur = big


def parse_post(html_text: str, spec: CommunitySpec) -> PostDetail:
    """글 상세 HTML → 본문·공감 수·댓글(공감 많은 순)."""
    root = parse_html(html_text)
    cnodes = _comment_nodes(root, spec)
    skip = {id(n) for n in cnodes}
    for n in cnodes:  # 댓글 목록을 감싼 상자도 본문 후보에서 뺀다
        skip |= {id(a) for a in n.ancestors() if COMMENTISH.search(_attr_text(a))}
    chrome = _chrome(root, skip)
    body_node = root.select_one(spec.body_selector) if spec.body_selector else None
    body_node = body_node or _main_block(root, chrome)
    body = " ".join(body_node.text(chrome - {id(body_node)}).split())
    comments: list[Comment] = []
    for n in cnodes:
        strip = (
            {id(x) for x in n.select(spec.comment_strip_selector)} if spec.comment_strip_selector else set()
        )
        text = _comment_text(n, strip)
        votes = None
        vm = re.search(spec.comment_vote_regex, text) if spec.comment_vote_regex else None
        if vm:
            votes = int(vm.group(1).replace(",", ""))
            text = (text[: vm.start()] + text[vm.end() :]).strip(" /|:·-")
        if len(text) < 2:
            continue
        votes = votes if votes is not None else _vote_of(n, spec.vote_selector)
        if votes is None:  # 공감 버튼이 댓글 내용 옆(같은 댓글 줄)에 있는 사이트
            row = _comment_row(n, skip)
            votes = _vote_of(row, spec.vote_selector) if row is not n else None
        if any(c.text == text[:300] for c in comments):  # 베스트 댓글이 본 목록에도 다시 나오는 사이트
            continue
        comments.append(Comment(text=text[:300], votes=votes or 0))
    votes = _post_votes(root, spec, skip, body_node)
    if votes is not None and votes > MAX_VOTES:  # 글 번호·조회수를 잘못 읽은 것
        votes = None
    order = {id(c): i for i, c in enumerate(comments)}
    comments.sort(key=lambda c: (-c.votes, order[id(c)]))
    return PostDetail(body=body[:2000], votes=votes, comments=comments)


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


def explain_fetch_error(e: Exception) -> str:
    """게시판 접속 실패 → 쉬운 설명."""
    status = getattr(getattr(e, "response", None), "status_code", None) or getattr(e, "status", None)
    if status == 403:
        return (
            "사이트가 프로그램 접속을 막았습니다(403, 보안 검사·해외 접속 차단 등) — "
            "이 컴퓨터에서 계속 막히면 이 게시판은 끄세요(설정 → 커뮤니티 게시판)"
        )
    if status == 404:
        return "게시판 주소가 없습니다(404) — list_url 확인"
    if status in (429, 503):
        return f"사이트가 잠시 접속을 제한했습니다({status}) — 나중에 다시"
    return f"{type(e).__name__}: {e}"


_BOARD_WORDS = re.compile(r"(정치|시사|토론|이슈|사회)")


def board_hints(html_text: str, base_url: str, limit: int = 6) -> list[tuple[str, str]]:
    """목록에서 글을 못 찾았을 때: 페이지 안의 '정치·시사' 게시판 링크(이름, 주소) — 게시판 id 찾기용."""
    out: list[tuple[str, str]] = []
    for m in _A_RE.finditer(html_text):
        text = strip_html(m.group(2)).strip()
        href = urljoin(base_url, htmllib.unescape(m.group(1)))
        if text and len(text) <= 20 and _BOARD_WORDS.search(text) and all(h != href for _t, h in out):
            out.append((text, href))
        if len(out) >= limit:
            break
    return out


def post_heat(s: Signal) -> float:
    """글 하나의 반응 크기(댓글 + 공감/2)."""
    return int(s.metric.get("comments", 0)) + int(s.metric.get("votes") or 0) / 2


def apply_detail(sig: Signal, d: PostDetail, now: datetime) -> None:
    """글 상세를 신호에 넣는다(본문 앞부분 = summary, 댓글은 공감 많은 순 15개)."""
    sig.summary = d.body[:600]
    if d.votes is not None and sig.metric.get("votes") is None:
        sig.metric["votes"] = d.votes
    sig.metric["top_comments"] = [{"t": c.text[:200], "v": c.votes} for c in d.comments[:15]]
    sig.metric["comments_read"] = len(d.comments)
    sig.metric["detail_at"] = now.isoformat()
    sig.metric["detail_comments"] = int(sig.metric.get("comments", 0))


class CommunityCollector(Collector):
    """설정된 커뮤니티 게시판을 읽어 Signal(kind='community')로. 반응 큰 글은 본문·댓글까지."""

    name = "community"

    def __init__(
        self,
        specs: list[CommunitySpec],
        client: httpx.AsyncClient,
        extra_politics: list[str] | None = None,
        need_detail: Callable[[Signal], bool] | None = None,
    ) -> None:
        self.specs = [s for s in specs if s.enabled]
        self.client = client
        self.extra = extra_politics or []
        self.robots = RobotsCache(client)
        self.need_detail = need_detail or (lambda _s: True)
        self.site_errors: dict[str, str] = {}
        self.site_counts: dict[str, int] = {}
        self.detail_counts: dict[str, int] = {}

    async def fetch_site(self, spec: CommunitySpec, since: datetime) -> list[Signal]:
        """게시판 하나(실패 격리)."""
        now = now_kst()
        out: list[Signal] = []
        seen: set[str] = set()  # 여러 쪽(옆 칸 인기글 목록 포함)에 같은 글이 다시 나와도 한 번만
        for page in range(1, spec.pages + 1):
            url = spec.page_url(page)
            if not await self.robots.allowed(url):
                self.site_errors[spec.name] = "robots.txt가 수집을 허용하지 않음 — 건너뜀"
                log.warning("[%s] robots.txt 금지: %s", spec.name, url)
                return out
            try:
                resp = await request(self.client, "GET", url)
            except Exception as e:  # 게시판 단위 실패 격리
                self.site_errors[spec.name] = explain_fetch_error(e)
                log.warning("[%s] 목록 실패: %s", spec.name, e)
                return out
            posts = parse_board(decode_html(resp), spec, url, now)
            if page == 1 and not posts:  # 글 주소 패턴이 사이트와 안 맞을 때(개편·잘못된 게시판 id)
                self.site_errors[spec.name] = (
                    "목록에서 글을 하나도 못 찾음 — post_pattern 확인(topicradar probe)"
                )
                return out
            for post in posts:
                if post.posted_at < since or post.url in seen:
                    continue
                seen.add(post.url)
                if spec.politics_filter and not is_political(post.title, self.extra):
                    continue
                metric: dict = {"site": spec.name, "comments": post.comments}
                if post.votes is not None:
                    metric["votes"] = post.votes
                out.append(
                    Signal(
                        source=f"community:{spec.name}",
                        kind="community",
                        fetched_at=now,
                        published_at=post.posted_at,
                        title=post.title,
                        url=post.url,
                        summary="",
                        metric=metric,
                    )
                )
            if page < spec.pages:
                await pause(1.0)
        self.site_counts[spec.name] = len(out)
        await self.fetch_details(spec, out, now)
        return out

    async def fetch_details(self, spec: CommunitySpec, sigs: list[Signal], now: datetime) -> None:
        """반응 큰 글부터 detail_posts개의 본문·댓글을 읽는다(이미 읽었고 반응이 그대로면 건너뜀)."""
        if spec.detail_posts <= 0:
            return
        picked = [s for s in sorted(sigs, key=lambda s: -post_heat(s)) if self.need_detail(s)]
        n = 0
        for sig in picked[: spec.detail_posts]:
            if not await self.robots.allowed(sig.url):
                continue
            await pause(1.0)
            try:
                resp = await request(self.client, "GET", sig.url)
            except Exception as e:  # 글 하나 실패는 건너뛴다
                log.warning("[%s] 글 상세 실패: %s (%s)", spec.name, sig.url, e)
                continue
            apply_detail(sig, parse_post(decode_html(resp), spec), now)
            n += 1
        self.detail_counts[spec.name] = n

    async def fetch(self, since: datetime) -> list[Signal]:
        """모든 게시판(사이트끼리는 동시에, 사이트 안에서는 순차)."""
        import asyncio

        chunks = await asyncio.gather(*(self.fetch_site(s, since) for s in self.specs))
        return [s for c in chunks for s in c]
