"""편성안의 문장 요소: 아침 포인트 expected/impact, 저녁 회수 actual/hit, 제목 3안·썸네일, 주간 사슬.

Claude가 있으면 슬롯당 한 번의 배치 호출로 만들고, 없으면 기사 문장을 그대로
뽑아 쓰는 결정적 휴리스틱으로 만든다(지어내지 않는다 — 근거가 없으면 비워 두고 표시).
"""

from __future__ import annotations

import json
import re
from collections import Counter
from datetime import datetime
from typing import Literal

from pydantic import BaseModel

from studio_core.config import KST
from studio_core.llm import ClaudeLLM, LLMError
from studio_core.text import idf_table, josa, short_name, split_sentences, to_polite, tokenize
from topicradar import prompts
from topicradar.cluster import GENERIC_TOKENS, IDF_PRIOR, signal_tokens, window_title
from topicradar.models import Chain, Signal, SlotItem, SlotPlan, Topic
from topicradar.risk import title_problems

_EXPECT_RE = re.compile(r"(전망|예상|관측|가능성|유력|확실시|점쳐|무게|기류)")
_IMPACT_STRONG = re.compile(r"(되면|하면|경우|통과하면|의결되면)")
_IMPACT_WEAK = re.compile(r"(달라|영향|적용|시행|효력|바뀌|혜택)")

PASS_WORDS = [
    "가결",
    "통과",
    "의결",
    "처리됐",
    "처리했",
    "채택",
    "승인",
    "합의",
    "타결",
    "인용",
    "확정",
    "유죄",
    "실형",
    "공포",
    "발표했",
    "강행",
]
FAIL_WORDS = [
    "부결",
    "무산",
    "연기",
    "보류",
    "철회",
    "기각",
    "결렬",
    "불발",
    "폐기",
    "미뤄",
    "미루",
    "늦춰",
    "무죄",
    "파행",
    "불참",
    "유예",
    "재지정",
]


# ---- 결과 성격 분류와 적중 판정 ---------------------------------------------


def outcome_class(text: str | None) -> Literal["pass", "fail"] | None:
    """문장의 결과 성격(진행/성사 vs 무산/연기)."""
    if not text:
        return None
    p = sum(text.count(w) for w in PASS_WORDS)
    f = sum(text.count(w) for w in FAIL_WORDS)
    if p == f:
        return None
    return "pass" if p > f else "fail"


def judge_hit(expected: str | None, actual: str | None) -> tuple[Literal["hit", "miss", "pending"], str]:
    """예상 vs 실제 → hit/miss/pending과 근거."""
    if not actual:
        return "pending", "오늘 결과 기사가 아직 없음"
    e, a = outcome_class(expected), outcome_class(actual)
    if e is None or a is None:
        return "pending", "결과는 나왔으나 예상과의 일치를 자동 판정할 수 없음 — 편집자 확인 필요"
    if e == a:
        return "hit", f"예상({'성사' if e == 'pass' else '무산·연기'})과 결과가 같음"
    return (
        "miss",
        f"예상({'성사' if e == 'pass' else '무산·연기'})과 달리 결과는 {'성사' if a == 'pass' else '무산·연기'}",
    )


def result_signals(t: Topic, start: datetime, end: datetime) -> list[Signal]:
    """회수용 결과 신호(윈도우 안, 일정 시각 이후, 결과 성격이 드러난 것 우선)."""
    after = t.scheduled_at if t.scheduled_at and start <= t.scheduled_at < end else start
    sigs = [s for s in t.news_signals(start, end) if s.published_at >= after]
    return sorted(sigs, key=lambda s: (outcome_class(s.text) is None, s.published_at))


def _outcome_sentence(s: Signal) -> str:
    """결과 성격이 드러난 첫 문장(없으면 첫 문장, 그것도 없으면 제목)."""
    sents = split_sentences(s.summary)
    for sent in sents:
        if outcome_class(sent) is not None:
            return sent
    return sents[0] if sents else s.title


def heuristic_expected(t: Topic) -> tuple[str | None, str | None]:
    """기사 속 전망 문장 → expected, 영향 문장 → impact."""
    expected = impact = None
    for s in t.signals:
        if s.kind == "schedule" and s.metric.get("expected"):
            expected = str(s.metric["expected"])
    sigs = sorted(
        (s for s in t.signals if s.kind not in ("competitor", "community")),
        key=lambda s: s.published_at,
        reverse=True,
    )
    sents = [x for s in sigs for x in split_sentences(s.summary)]
    for sent in sents:
        if expected is None and _EXPECT_RE.search(sent):
            expected = sent
    for pattern in (_IMPACT_STRONG, _IMPACT_WEAK):
        impact = impact or next((x for x in sents if pattern.search(x) and x != expected), None)
    return (to_polite(expected) if expected else None, to_polite(impact) if impact else None)


# ---- 제목·썸네일 ----------------------------------------------------------


def heuristic_titles(plan: SlotPlan) -> tuple[list[str], str]:
    """인과형 제목 3안과 10자 이내 썸네일."""
    if plan.slot == "morning":
        pts = plan.items_by_role("오늘의포인트") or plan.items
        base = short_name(pts[0].topic.name) if pts else "오늘"
        titles = [
            f"{base}, 오늘 결론 나면 생기는 일",
            f"오늘 {josa(base, '을/를')} 봐야 하는 이유",
            f"{base} 다음 수순은 결국 이것",
        ]
    elif plan.slot == "evening":
        heads = plan.items_by_role("헤드라인") or plan.items
        base = (
            short_name(window_title(heads[0].topic, plan.window_start, plan.window_end)) if heads else "오늘"
        )
        titles = [f"{base}, 진짜 이유", f"{base} 다음에 오는 것", f"결국 {josa(base, '이/가')} 바꾸는 것"]
    else:
        base = short_name(plan.chains[0].title) if plan.chains else "이번 주"
        titles = [
            f"{base}, 한 주를 꿴 진짜 이유",
            f"{base} 다음 주에 오는 것",
            f"결국 {josa(base, '으로/로')} 이어진 이유",
        ]
    titles = [t for t in titles if "감탄형" not in title_problems(t)]
    return titles[:3], thumbnail_for(base)


def thumbnail_for(base: str) -> str:
    """썸네일 문구(10자 이내)."""
    words = base.split()
    text = ""
    for w in words:
        if len(f"{text} {w}".strip()) + 3 > 10:
            break
        text = f"{text} {w}".strip()
    text = text or base[:7]
    return f"{text} 왜?"[:10]


# ---- 주간 사슬 ------------------------------------------------------------


def _persistence(t: Topic) -> float:
    """지속성: 판정 점수가 있으면 그대로, 없으면(hottest 모드) 보도된 날 수로(하루 50, 이틀 60, … 최대 100)."""
    if t.scores.get("persistence"):
        return t.scores["persistence"].score
    days = {s.published_at.date() for s in t.news_signals()}
    return min(100.0, 40.0 + 10.0 * len(days) + (10.0 if t.follow_up else 0.0))


def heuristic_chains(
    topics: list[Topic],
    max_chains: int = 2,
    generic: set[str] | None = None,
    anchor_ids: set[str] | None = None,
) -> list[Chain]:
    """지속성 높은 주제를 축으로 키워드가 이어지는 주제를 엮는다(정당명 같은 일반어만으로는 잇지 않음)."""
    if not topics:
        return []
    gen = GENERIC_TOKENS | (generic or set())
    docs = {
        t.id: set(t.keywords)
        | set(tokenize(t.name))
        | {x for sg in t.signals if sg.kind not in ("competitor", "community") for x in signal_tokens(sg)}
        for t in topics
    }
    idf = idf_table(list(docs.values()), prior=IDF_PRIOR)
    persist = {t.id: _persistence(t) for t in topics}
    # 정치 중심: 경제 주제는 사슬의 축으로는 마지막 순위(구성원으로는 들어갈 수 있음)
    pool = sorted(topics, key=lambda t: (t.category == "경제", -persist[t.id], -t.total))
    used: set[str] = set()
    chains: list[Chain] = []
    for anchor in pool:
        if len(chains) >= max_chains or anchor.id in used or persist[anchor.id] < 55:
            continue
        if anchor.category == "경제" and chains:  # 경제 사슬은 정치 사슬이 하나도 없을 때만
            continue
        if anchor_ids is not None and anchor.id not in anchor_ids:  # 커뮤니티 반응이 있는 주제만 축으로
            continue
        members = [anchor]
        for t in pool:
            if t.id == anchor.id or t.id in used:
                continue
            shared = docs[anchor.id] & docs[t.id]
            distinct = shared - gen
            if len(distinct) >= 2 and sum(idf.get(x, 0) for x in distinct) >= 3.0:
                members.append(t)
        if len(members) < 2:
            # 한 주제가 여러 날에 걸쳐 전개됐다면 그 자체가 사슬(예: 선고 예고 → 연기 → 기일 재지정)
            days = sorted(
                {
                    sg.published_at.date()
                    for sg in anchor.signals
                    if sg.kind not in ("competitor", "schedule", "community")
                }
            )
            if anchor.category != "경제" and len(days) >= 3 and persist[anchor.id] >= 70:
                used.add(anchor.id)
                firsts: list[list[str]] = []
                for d in days[:4]:
                    first = min(
                        (
                            sg
                            for sg in anchor.signals
                            if sg.published_at.date() == d
                            and sg.kind not in ("competitor", "schedule", "community")
                        ),
                        key=lambda sg: sg.published_at,
                    )
                    firsts.append(re.sub(r"[,…·\"'“”—]", " ", first.title).split())
                # 날마다 반복되는 말(주체·사건명)은 빼고 그날의 전개만 남긴다
                freq = Counter(w for ws in firsts for w in set(ws))
                steps = [
                    " ".join(w for w in ws if freq[w] * 2 <= len(firsts))[:16].strip() or " ".join(ws)[:16]
                    for ws in firsts
                ]
                chains.append(
                    Chain(title=short_name(anchor.name, 16), summary=" → ".join(steps), topic_ids=[anchor.id])
                )
            continue
        members.sort(key=lambda t: t.first_seen)
        used.update(t.id for t in members)
        steps = " → ".join(short_name(t.name, 12) for t in members[:4])
        chains.append(
            Chain(title=short_name(anchor.name, 16), summary=steps, topic_ids=[t.id for t in members])
        )
    return chains


# ---- Claude 경로 ---------------------------------------------------------


class LLMPointText(BaseModel):
    """아침 포인트 문장."""

    topic_id: str
    expected: str | None
    impact: str | None


class LLMRecallText(BaseModel):
    """저녁 회수 문장."""

    topic_id: str
    actual: str | None
    hit: Literal["hit", "miss", "pending"]
    hit_reason: str


class LLMChain(BaseModel):
    """주간 사슬."""

    title: str
    summary: str
    topic_ids: list[str]


class LLMPlanText(BaseModel):
    """편성안 문장 요소."""

    points: list[LLMPointText]
    recalls: list[LLMRecallText]
    chains: list[LLMChain]
    titles: list[str]
    thumbnail_text: str


def _articles(t: Topic, start: datetime | None = None, end: datetime | None = None, n: int = 5) -> list[dict]:
    sigs = [s for s in t.signals if s.kind not in ("competitor", "community")]
    if start and end:
        sigs = [s for s in sigs if start <= s.published_at < end] or sigs
    sigs = sorted(sigs, key=lambda s: s.published_at, reverse=True)[:n]
    return [
        {
            "time": s.published_at.astimezone(KST).strftime("%m-%d %H:%M"),
            "title": s.title,
            "summary": s.summary[:200],
        }
        for s in sigs
    ]


def llm_plan_text(llm: ClaudeLLM, plan: SlotPlan, candidates: list[Topic] | None = None) -> LLMPlanText:
    """슬롯 한 번의 배치 호출."""
    payload: dict = {"slot": plan.slot, "date": plan.date.isoformat(), "items": []}
    for it in plan.items:
        row = {"topic_id": it.topic.id, "role": it.role, "name": it.topic.name, "category": it.topic.category}
        if it.role == "포인트회수":
            row["morning_expected"] = it.expected
            row["result_articles"] = _articles(it.topic, plan.window_start, plan.window_end)
        else:
            row["articles"] = _articles(it.topic)
        payload["items"].append(row)
    if plan.slot == "weekly" and candidates:
        payload["week_topics"] = [
            {"topic_id": t.id, "name": t.name, "category": t.category, "articles": _articles(t, n=3)}
            for t in candidates
        ]
    system = prompts.IDENTITY + "\n" + prompts.PLAN_TASK
    res = llm.json("plan", system, "편성안:\n" + json.dumps(payload, ensure_ascii=False), LLMPlanText)
    assert isinstance(res, LLMPlanText)
    return res


# ---- 채우기 진입점 --------------------------------------------------------


def fill_points(items: list[SlotItem]) -> None:
    """아침 포인트 expected/impact(휴리스틱)."""
    for it in items:
        if it.role != "오늘의포인트":
            continue
        e, i = heuristic_expected(it.topic)
        it.expected = it.expected or e
        it.impact = it.impact or i
        if it.expected is None:
            it.note = "예상 근거 기사 없음 — 편집자가 expected를 채울 것"


def fill_recalls(items: list[SlotItem], start: datetime, end: datetime) -> None:
    """저녁 회수 actual/hit(휴리스틱)."""
    for it in items:
        if it.role != "포인트회수":
            continue
        res = result_signals(it.topic, start, end)
        it.actual = to_polite(_outcome_sentence(res[0])) if res else None
        it.hit, it.hit_reason = judge_hit(it.expected, it.actual)


def fill_plan_text(plan: SlotPlan, llm: ClaudeLLM | None, candidates: list[Topic] | None = None) -> None:
    """편성안 문장 요소를 채운다(제자리)."""
    start = plan.window_start or datetime.min.replace(tzinfo=KST)
    end = plan.window_end or datetime.max.replace(tzinfo=KST)
    res: LLMPlanText | None = None
    if llm is not None:
        try:
            res = llm_plan_text(llm, plan, candidates)
            plan.llm_mode = "claude"
        except LLMError:
            res = None
    if res is not None:
        pts = {p.topic_id: p for p in res.points}
        recs = {r.topic_id: r for r in res.recalls}
        for it in plan.items:
            if it.role == "오늘의포인트" and it.topic.id in pts:
                it.expected, it.impact = pts[it.topic.id].expected, pts[it.topic.id].impact
            if it.role == "포인트회수" and it.topic.id in recs:
                r = recs[it.topic.id]
                it.actual, it.hit, it.hit_reason = r.actual, r.hit, r.hit_reason
        if plan.slot == "weekly" and res.chains and not plan.chains:
            valid = {t.id for t in candidates or []}
            plan.chains = [
                Chain(title=c.title, summary=c.summary, topic_ids=[i for i in c.topic_ids if i in valid])
                for c in res.chains[:2]
            ]
        good = [t for t in res.titles if not {"날짜형", "감탄형"} & set(title_problems(t))]
        plan.titles = good[:3]
        plan.thumbnail_text = res.thumbnail_text[:10]
    # 휴리스틱 보충(LLM이 빠뜨린 부분 포함)
    fill_points(plan.items)
    if any(it.role == "포인트회수" and it.hit is None for it in plan.items):
        fill_recalls([it for it in plan.items if it.hit is None], start, end)
    if len(plan.titles) < 3 or not plan.thumbnail_text:
        titles, thumb = heuristic_titles(plan)
        plan.titles = (plan.titles + [t for t in titles if t not in plan.titles])[:3]
        plan.thumbnail_text = plan.thumbnail_text or thumb
