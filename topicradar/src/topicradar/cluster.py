"""Signal → Topic 묶기.

Claude가 있으면 한 번의 배치 호출로 묶고, 없으면 IDF 가중 토큰 겹침 기반의
결정적 휴리스틱으로 묶는다. 어느 경로든 이미 주제에 배정된 신호는 그 배정을
유지하고(주간 슬롯이 재군집하지 않도록), 새 묶음은 기존 주제와 겹치면 병합한다.
"""

from __future__ import annotations

import hashlib
import json
import re
from collections import Counter
from collections.abc import Iterable
from datetime import date, datetime, time, timedelta
from typing import Literal

from pydantic import BaseModel

from studio_core.config import KST
from studio_core.llm import ClaudeLLM, LLMError
from studio_core.text import idf_table, split_sentences, tokenize
from topicradar import prompts
from topicradar.models import Signal, Topic, TopicKind

CATEGORY_LEXICON: dict[str, list[str]] = {
    "국회": [
        "국회",
        "본회의",
        "상임위",
        "법사위",
        "법제사법위",
        "법안",
        "의원",
        "원내",
        "여야",
        "입법",
        "표결",
        "필리버스터",
        "국정감사",
        "국감",
        "교섭단체",
        "국회의장",
        "상정",
        "가결",
        "부결",
        "의결",
    ],
    "정부": [
        "대통령",
        "대통령실",
        "총리",
        "장관",
        "국무회의",
        "정부",
        "부처",
        "행정",
        "시행령",
        "차관",
        "청와대",
    ],
    "사법": [
        "법원",
        "재판",
        "판결",
        "선고",
        "검찰",
        "기소",
        "수사",
        "영장",
        "헌법재판소",
        "헌재",
        "대법원",
        "공판",
        "구속",
        "압수수색",
        "혐의",
        "특검",
        "재판부",
        "1심",
        "항소",
    ],
    "선거": [
        "선거",
        "여론조사",
        "지지율",
        "공천",
        "후보",
        "전당대회",
        "경선",
        "선관위",
        "득표",
        "투표",
        "출마",
    ],
    "외교안보": [
        "북한",
        "외교",
        "안보",
        "국방",
        "한미",
        "한일",
        "한중",
        "미사일",
        "동맹",
        "합참",
        "정상회담",
        "안보리",
        "외교부",
        "국방부",
    ],
    "국제": [
        "미국",
        "중국",
        "일본",
        "러시아",
        "유럽",
        "백악관",
        "우크라이나",
        "중동",
        "이스라엘",
        "하원",
        "상원",
        "world",
        "president",
        "congress",
        "minister",
        "election",
    ],
    "경제": [
        "경제",
        "금리",
        "환율",
        "증시",
        "코스피",
        "물가",
        "부동산",
        "주가",
        "수출",
        "성장률",
        "한은",
        "한국은행",
        "세제",
        "관세",
        "반도체",
        "원화",
        "달러",
        "기준금리",
        "market",
        "stocks",
        "inflation",
    ],
    "노동": [
        "노동",
        "노조",
        "파업",
        "임금",
        "최저임금",
        "고용",
        "해고",
        "근로",
        "노동자",
        "플랫폼노동",
        "택배",
    ],
    "복지": [
        "복지",
        "연금",
        "건강보험",
        "의료",
        "돌봄",
        "저출생",
        "기초생활",
        "보육",
        "장애인",
        "지역의료",
        "공공의료",
    ],
}

_FUTURE_VERB = (
    r"(예정|열린다|연다|개최한다|개최할|진행한다|진행할|표결한다|표결할|표결에 부친다|선고한다|선고할|"
    r"발표한다|발표할|상정한다|상정할|처리한다|처리할|앞두고|할 계획|가질 예정|열릴|잡혔|잡혀|부쳐진다|상정된다)"
)
# 제목형 일정: "…8일 본회의 표결", "…8일 1심 선고" (명사로 끝나는 예고 제목)
_TITLE_SCHEDULE_RE = re.compile(
    r"(?P<when>오늘|내일|모레|(?P<day>\d{1,2})일)\s*(?:(?P<ampm>오전|오후)\s*(?P<hour>\d{1,2})시(?:\s*(?P<min>\d{1,2})분)?)?"
    r"[^.…]{0,20}?(표결|선고|상정|본회의|국무회의|발표|투표|청문회|개최)\s*(?:…|$)"
)
_SCHEDULE_RE = re.compile(
    r"(?P<when>오늘|이날|내일|모레|(?P<day>\d{1,2})일)\s*(?:\((?:월|화|수|목|금|토|일)\)\s*)?"
    r"(?:(?P<ampm>오전|오후)\s*(?P<hour>\d{1,2})시(?:\s*(?P<min>\d{1,2})분)?)?[^.]{0,50}?" + _FUTURE_VERB
)
_FOLLOW_RE = re.compile(r"(예정|앞두고|전망|방침|계획|다음 달|다음 주|내주|내달|이달 중|후속|수순)")
_ANNOUNCE_RE = re.compile(r"(발표|밝혔다|공개|내놨다|확정|의결)")


# ---- 공통 유틸 ------------------------------------------------------------


def topic_id_for(name: str, day: date) -> str:
    """주제 ID."""
    return f"t{day:%m%d}-{hashlib.sha1(name.encode('utf-8')).hexdigest()[:6]}"


_INSTITUTION_WORDS = {
    "국회",
    "국감",
    "법사위",
    "본회의",
    "국무회의",
    "상임위",
    "국방위",
    "정무위",
    "기재위",
    "대법원",
    "헌재",
}


def _norm_token(t: str) -> str:
    """'국감서'·'법사위서'처럼 기관명 뒤에 붙은 축약 조사 '서'를 뗀다."""
    if len(t) >= 3 and t.endswith("서") and t[:-1] in _INSTITUTION_WORDS:
        return t[:-1]
    return t


def signal_tokens(sig: Signal) -> set[str]:
    """군집용 토큰(제목 + 요약 첫 문장 + 키워드)."""
    first = split_sentences(sig.summary)[:1]
    raw = set(tokenize(sig.title)) | set(tokenize(" ".join(first))) | {k for k in sig.keywords if len(k) >= 2}
    return {_norm_token(t) for t in raw}


def categorize(texts: Iterable[str], hints: Iterable[str] = (), categories: list[str] | None = None) -> str:
    """어휘 사전으로 카테고리 판정(정치 카테고리 우선 동점 처리)."""
    order = categories or list(CATEGORY_LEXICON)
    score: Counter[str] = Counter()
    for i, text in enumerate(texts):
        weight = 2 if i == 0 else 1
        low = text.lower()
        for cat, words in CATEGORY_LEXICON.items():
            score[cat] += weight * sum(1 for w in words if w in low)
    for h in hints:
        if h in CATEGORY_LEXICON:
            score[h] += 2
    if not score or max(score.values()) == 0:
        return order[0] if order else "정부"
    best = max(score.values())
    for c in order:
        if score.get(c, 0) == best:
            return c
    return max(score, key=lambda c: score[c])


def _match_to_dt(m: re.Match[str], base: date) -> tuple[datetime, bool] | None:
    when = m.group("when")
    if when in ("오늘", "이날"):
        d = base
    elif when == "내일":
        d = base + timedelta(days=1)
    elif when == "모레":
        d = base + timedelta(days=2)
    else:
        day = int(m.group("day"))
        if not 1 <= day <= 31:
            return None
        month, year = base.month, base.year
        if day < base.day - 10:  # 다음 달로 넘어가는 일정
            month, year = (1, year + 1) if month == 12 else (month + 1, year)
        try:
            d = date(year, month, day)
        except ValueError:
            return None
    has_time = bool(m.group("hour"))
    hh = int(m.group("hour")) if has_time else 0
    if m.group("ampm") == "오후" and hh < 12:
        hh += 12
    mm = int(m.group("min")) if m.group("min") else 0
    return datetime.combine(d, time(min(hh, 23), min(mm, 59)), tzinfo=KST), has_time


def find_schedule(sig: Signal) -> datetime | None:
    """신호 속 '앞으로의 일정' 시각(KST). 같은 날짜면 시각이 적힌 언급을 우선한다."""
    if sig.kind == "schedule" and sig.metric.get("scheduled_at"):
        return datetime.fromisoformat(sig.metric["scheduled_at"]).astimezone(KST)
    base = sig.published_at.astimezone(KST).date()
    found: list[tuple[datetime, bool]] = []
    for text, pattern in (
        (sig.title, _SCHEDULE_RE),
        (sig.title, _TITLE_SCHEDULE_RE),
        (sig.summary, _SCHEDULE_RE),
    ):
        m = pattern.search(text)
        if m:
            got = _match_to_dt(m, base)
            if got is not None:
                found.append(got)
    if not found:
        return None
    first_day = found[0][0].date()
    timed = [dt for dt, has_time in found if has_time and dt.date() == first_day]
    return timed[0] if timed else found[0][0]


def find_follow_up(signals: list[Signal]) -> str | None:
    """다음 수순 한 문장(최신 기사 우선)."""
    for sig in sorted(signals, key=lambda s: s.published_at, reverse=True):
        for sent in split_sentences(sig.summary):
            if _FOLLOW_RE.search(sent) and len(sent) <= 120:
                return sent
    return None


def infer_kind(signals: list[Signal], scheduled_at: datetime | None) -> TopicKind:
    """주제 성격 추정(커뮤니티 글은 성격 판정에서 뺀다)."""
    content = [s for s in signals if s.kind not in ("competitor", "community")]
    if not content:
        return "event"
    n = len(content)
    if sum(s.kind == "overseas" for s in content) * 2 >= n:
        return "overseas"
    press = sum(s.kind == "press" for s in content)
    dawn = sum(1 for s in content if s.published_at.astimezone(KST).hour < 6 and _ANNOUNCE_RE.search(s.text))
    if press * 2 >= n or dawn * 2 > n:
        return "announcement"
    if scheduled_at is not None:
        return "scheduled"
    return "event"


def latest_schedule(signals: list[Signal]) -> datetime | None:
    """가장 최근에 보도된 일정 언급."""
    best: tuple[datetime, datetime] | None = None
    for s in signals:
        at = find_schedule(s)
        if at is None:
            continue
        key = (s.published_at, at)
        if best is None or key > best:
            best = key
    return best[1] if best else None


def window_title(t: Topic, start: datetime | None, end: datetime | None) -> str:
    """슬롯에서 보여줄 이름: 윈도우 신호 중 가장 중심적인 한국어 제목(없으면 주제명)."""
    sigs = [s for s in t.signals if s.kind not in ("competitor", "schedule", "community")]
    if start and end:
        sigs = [s for s in sigs if start <= s.published_at < end]
    korean = [s for s in sigs if re.search(r"[가-힣]", s.title)]
    if not korean:
        return t.name
    return _group_name(korean)


def has_korean(t: Topic) -> bool:
    """한국어 신호가 하나라도 있는가(오프라인 대본은 번역을 못 한다)."""
    return any(re.search(r"[가-힣]", s.title) for s in t.signals if s.kind not in ("competitor", "community"))


def refresh_topic(t: Topic) -> Topic:
    """신호 목록에서 파생 필드(기간·일정·다음 수순·성격)를 다시 계산한다."""
    content = [s for s in t.signals if s.kind != "competitor"]
    if content:
        t.first_seen = min(t.first_seen, min(s.published_at for s in content))
        t.last_seen = max(t.last_seen, max(s.published_at for s in content))
    facts = [s for s in content if s.kind != "community"]  # 일정·다음 수순은 뉴스·일정에서만
    t.scheduled_at = latest_schedule(facts) or t.scheduled_at
    t.follow_up = find_follow_up(facts) or t.follow_up
    t.kind = infer_kind(content, t.scheduled_at)
    return t


# ---- 휴리스틱 군집 --------------------------------------------------------

# 정치 기사 어디에나 나오는 말: 연결 근거로는 약하게(최대 0.5)만 친다.
GENERIC_TOKENS: set[str] = {
    "국회",
    "국정감사",
    "국감",
    "본회의",
    "정부",
    "대통령",
    "대통령실",
    "여야",
    "여당",
    "야당",
    "의원",
    "정당",
    "장관",
    "법안",
    "개정안",
    "상정",
    "의결",
    "표결",
    "발표",
    "정책",
    "예산",
    "검찰",
    "법원",
    "재판",
    "위원회",
    "국무회의",
    "상임위",
    "국정",
    "감사",
    "여론",
    "통과",
    "가결",
    "반대",
    "찬성",
    "회의",
    "후속",
    "조치",
    "착수",
    "심사",
    "진행",
    "마련",
    "강화",
    "확대",
    "촉구",
    "제기",
    "예고",
    "대응",
    "입법",
    "제출",
    "결정",
    "원내대표",
    "대변인",
    "지도부",
    "공방",
    "비판",
    "주장",
    "논란",
    "전체회의",
    "처리",
    "추진",
    "검토",
    "요구",
    "더불어민주당",
    "민주당",
    "국민의힘",
    "조국혁신당",
    "개혁신당",
    "진보당",
    "정의당",
    "미국",
    "한국",
    "중국",
}


IDF_PRIOR = 50  # 작은 윈도우에서도 '희귀어 2개 공유'가 연결 근거가 되도록


def _weight(tok: str, idf: dict[str, float], generic: set[str]) -> float:
    w = idf.get(tok, 3.0)
    return min(w, 0.5) if tok in generic else w


def _linked(
    a: set[str], b: set[str], idf: dict[str, float], generic: set[str], min_weight: float = 3.0
) -> bool:
    """두 신호가 같은 주제인가: 공유 토큰 2개 이상, 비일반 토큰 1개 이상, IDF 가중합 ≥ min_weight."""
    shared = a & b
    if len(shared) < 2 or not (shared - generic):
        return False
    return sum(_weight(t, idf, generic) for t in shared) >= min_weight


def heuristic_groups(
    signals: list[Signal],
    existing: list[Topic] | None = None,
    generic: set[str] | None = None,
) -> tuple[list[list[Signal]], dict[int, str]]:
    """IDF 가중 겹침 단일연결 군집.

    기존 주제의 신호도 같은 그래프에 넣어 IDF를 넓게 잡고, 새 신호 묶음이 기존 신호와
    연결되면 그 주제 ID를 돌려준다(반환: 새 신호 묶음 목록, {묶음 index: 기존 topic_id}).
    """
    generic = GENERIC_TOKENS | (generic or set())
    existing = existing or []
    old_sigs: list[tuple[Signal, str]] = [
        (s, t.id) for t in existing for s in t.signals if s.kind != "competitor"
    ]
    toks = [signal_tokens(s) for s in signals]
    old_toks = [signal_tokens(s) for s, _ in old_sigs]
    idf = idf_table(toks + old_toks, prior=IDF_PRIOR)
    parent = list(range(len(signals)))

    def find(i: int) -> int:
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i

    for i in range(len(signals)):
        for j in range(i + 1, len(signals)):
            if _linked(toks[i], toks[j], idf, generic):
                parent[find(i)] = find(j)
    buckets: dict[int, list[int]] = {}
    for i in range(len(signals)):
        buckets.setdefault(find(i), []).append(i)
    groups = sorted(buckets.values(), key=lambda g: min(signals[i].published_at for i in g))
    matches: dict[int, str] = {}
    for gi, members in enumerate(groups):
        votes: Counter[str] = Counter()
        for i in members:
            for (_, tid), ot in zip(old_sigs, old_toks, strict=True):
                if _linked(toks[i], ot, idf, generic):
                    votes[tid] += 1
        if votes:
            matches[gi] = votes.most_common(1)[0][0]
    return [[signals[i] for i in g] for g in groups], matches


def _group_name(group: list[Signal]) -> str:
    # 주제명은 뉴스 제목에서(커뮤니티 제목은 말투가 섞여 이름으로 쓰지 않는다. 커뮤니티 글뿐이면 예외)
    group = [s for s in group if s.kind != "community"] or group
    toks = [signal_tokens(s) for s in group]
    prio = {"schedule": 0, "domestic": 1, "press": 2, "overseas": 3}

    def centrality(i: int) -> tuple[float, int]:
        return (sum(len(toks[i] & toks[j]) for j in range(len(group)) if j != i), -prio.get(group[i].kind, 5))

    best = max(range(len(group)), key=centrality)
    name = group[best].title
    return name if len(name) <= 40 else name[:39] + "…"


def _group_keywords(group: list[Signal], idf: dict[str, float], k: int = 8) -> list[str]:
    cnt: Counter[str] = Counter()
    for s in group:
        cnt.update(signal_tokens(s))
    ranked = sorted(cnt, key=lambda t: (t in GENERIC_TOKENS, -(cnt[t] * idf.get(t, 1.0)), t))
    return ranked[:k]


def attach_competitors(topics: list[Topic], competitor_signals: list[Signal]) -> None:
    """경쟁 채널 영상을 가장 많이 연결되는 주제에 붙인다(새 주제는 만들지 않음)."""
    if not topics or not competitor_signals:
        return
    docs = [
        set(t.keywords) | set(tokenize(t.name)) | {x for s in t.signals for x in signal_tokens(s)}
        for t in topics
    ]
    idf = idf_table(docs + [signal_tokens(s) for s in competitor_signals], prior=IDF_PRIOR)
    for sig in competitor_signals:
        st = signal_tokens(sig)
        best: tuple[float, Topic] | None = None
        for d, t in zip(docs, topics, strict=True):
            shared = st & d
            w = sum(_weight(x, idf, GENERIC_TOKENS) for x in shared)
            distinct = shared - GENERIC_TOKENS
            strong = len(distinct) >= 2 or (len(shared) >= 2 and distinct and w >= 2.5)
            if strong and (best is None or w > best[0]):
                best = (w, t)
        if best is not None and all(s.id != sig.id for s in best[1].signals):
            best[1].signals.append(sig)


def heuristic_cluster(
    signals: list[Signal],
    existing: list[Topic],
    day: date,
    categories: list[str] | None = None,
    generic: set[str] | None = None,
) -> list[Topic]:
    """휴리스틱 경로."""
    groups, matches = heuristic_groups(signals, existing, generic)
    idf = idf_table(
        [signal_tokens(s) for s in signals] + [signal_tokens(s) for t in existing for s in t.signals]
    )
    by_id = {t.id: t for t in existing}
    touched: dict[str, Topic] = {}
    for gi, g in enumerate(groups):
        if gi in matches and matches[gi] in by_id:
            t = touched.get(matches[gi]) or by_id[matches[gi]]
            known = {s.id for s in t.signals}
            t.signals.extend(s for s in g if s.id not in known)
            kw = _group_keywords([s for s in t.signals if s.kind != "competitor"], idf)
            t.keywords = list(dict.fromkeys([*t.keywords[:4], *kw]))[:10]
            touched[t.id] = t
            continue
        name = _group_name(g)
        hints = [s.metric.get("category") or s.metric.get("category_hint") for s in g]
        t = Topic(
            id=topic_id_for(name, day),
            name=name,
            keywords=_group_keywords(g, idf),
            # 카테고리는 뉴스 기준(커뮤니티 제목의 말투·감정어가 섞이지 않게)
            category=categorize(
                [name, *(s.text for s in ([x for x in g if x.kind != "community"] or g))],
                [h for h in hints if h],
                categories,
            ),
            signals=list(g),
            first_seen=min(s.published_at for s in g),
            last_seen=max(s.published_at for s in g),
        )
        touched[t.id] = t
    return [refresh_topic(t) for t in touched.values()]


# ---- Claude 경로 ---------------------------------------------------------


class LLMTopic(BaseModel):
    """Claude 군집 결과 항목."""

    existing_id: str | None
    name: str
    keywords: list[str]
    category: str
    signal_ids: list[str]
    competitor_ids: list[str]
    kind: Literal["overseas", "announcement", "scheduled", "event"]
    scheduled_at: str | None
    follow_up: str | None


class LLMCluster(BaseModel):
    """Claude 군집 결과."""

    topics: list[LLMTopic]


def _llm_prompt(
    signals: list[Signal], competitors: list[Signal], existing: list[Topic], categories: list[str]
) -> str:
    def row(s: Signal) -> dict:
        return {
            "id": s.id,
            "kind": s.kind,
            "time": s.published_at.astimezone(KST).strftime("%Y-%m-%d %H:%M"),
            "title": s.title,
            "summary": s.summary[:220],
        }

    payload = {
        "categories": categories,
        "existing_topics": [{"id": t.id, "name": t.name, "keywords": t.keywords[:8]} for t in existing],
        "signals": [row(s) for s in signals],
        "competitor_videos": [row(s) for s in competitors],
    }
    return "다음 신호를 주제로 묶어라.\n" + json.dumps(payload, ensure_ascii=False)


def llm_cluster(
    llm: ClaudeLLM,
    signals: list[Signal],
    competitors: list[Signal],
    existing: list[Topic],
    day: date,
    categories: list[str],
) -> list[Topic]:
    """Claude 경로. 응답에서 빠진 신호는 휴리스틱으로 보충한다."""
    system = prompts.IDENTITY + "\n" + prompts.CLUSTER_TASK
    result = llm.json("cluster", system, _llm_prompt(signals, competitors, existing, categories), LLMCluster)
    assert isinstance(result, LLMCluster)
    by_sig = {s.id: s for s in signals}
    by_comp = {s.id: s for s in competitors}
    by_existing = {t.id: t for t in existing}
    used: set[str] = set()
    touched: dict[str, Topic] = {}
    for lt in result.topics:
        sigs = [by_sig[i] for i in lt.signal_ids if i in by_sig and i not in used]
        if not sigs:
            continue
        used.update(s.id for s in sigs)
        base = by_existing.get(lt.existing_id or "") or touched.get(lt.existing_id or "")
        if base is None:
            base = Topic(
                id=topic_id_for(lt.name, day),
                name=lt.name,
                keywords=lt.keywords[:10],
                category=lt.category
                if lt.category in categories
                else categorize([lt.name], categories=categories),
                signals=[],
                first_seen=min(s.published_at for s in sigs),
                last_seen=max(s.published_at for s in sigs),
            )
        known = {s.id for s in base.signals}
        base.signals.extend(s for s in sigs if s.id not in known)
        base.signals.extend(by_comp[i] for i in lt.competitor_ids if i in by_comp)
        base = refresh_topic(base)
        if lt.scheduled_at:
            try:
                at = datetime.fromisoformat(lt.scheduled_at)
                base.scheduled_at = at.replace(tzinfo=KST) if at.tzinfo is None else at.astimezone(KST)
            except ValueError:
                pass
        base.kind = lt.kind
        base.follow_up = lt.follow_up or base.follow_up
        touched[base.id] = base
    leftovers = [s for s in signals if s.id not in used]
    if leftovers:
        for t in heuristic_cluster(leftovers, [*existing, *touched.values()], day, categories):
            touched[t.id] = t
    return list(touched.values())


# ---- 진입점 ---------------------------------------------------------------


def cluster_signals(
    signals: list[Signal],
    existing: list[Topic],
    day: date,
    categories: list[str],
    llm: ClaudeLLM | None = None,
    assigned: dict[str, str] | None = None,
    generic: set[str] | None = None,
) -> list[Topic]:
    """윈도우 신호를 주제로 묶어 '이번에 건드린 주제' 목록을 돌려준다.

    assigned: 이미 주제가 정해진 신호 {signal_id: topic_id}. 그 배정은 유지한다.
    """
    assigned = assigned or {}
    content = [s for s in signals if s.kind != "competitor"]
    competitors = [s for s in signals if s.kind == "competitor"]
    by_existing = {t.id: t for t in existing}
    touched: dict[str, Topic] = {}
    fresh: list[Signal] = []
    for s in content:
        tid = assigned.get(s.id)
        if tid and tid in by_existing:
            t = touched.setdefault(tid, by_existing[tid])
            if all(x.id != s.id for x in t.signals):
                t.signals.append(s)
        else:
            fresh.append(s)
    if fresh:
        new_topics: list[Topic]
        if llm is not None:
            try:
                new_topics = llm_cluster(llm, fresh, competitors, existing, day, categories)
                competitors = []  # Claude가 이미 매칭함
            except LLMError:
                new_topics = heuristic_cluster(fresh, existing, day, categories, generic)
        else:
            new_topics = heuristic_cluster(fresh, existing, day, categories, generic)
        for t in new_topics:
            touched[t.id] = t
    topics = list(touched.values())
    attach_competitors(topics, competitors)
    return topics
