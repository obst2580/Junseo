"""슬롯별 선정 규칙(아침 프리뷰 / 저녁 리뷰 / 주간 심층).

- 아침: 윈도우 전날 17:00~당일 05:30 + 오늘 일정.
  해외 1~2 → 새벽 발표 0~2 → 오늘 볼 포인트 3(오늘 예정 중 최고점, 각각 expected).
  전날 저녁에 다룬 주제는 다시 정리하지 않는다(오늘 후속 일정이 있으면 포인트로만 허용).
- 저녁: 윈도우 당일 05:30~17:00.
  헤드라인 1 → 서브 2~4 → 아침 포인트 회수(전부, actual·hit) → 내일 쟁점 1.
  아침 포인트가 아닌 아침 항목은 새 전개(신호 2건 이상)가 있을 때만 다시 다룬다.
- 주간: 윈도우 월 00:00~토 17:00, 이번 주 편성안 전부가 입력.
  인과 사슬 1~2개(줄기 + 서브), 단발 사건은 사슬에 연결될 때만, 다음 주 전망으로 마무리.
- 공통: 같은 슬롯에서 경제 카테고리가 절반을 넘지 않게 교체한다.
"""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass, field
from datetime import date, datetime, time, timedelta

from studio_core.config import KST, ChannelConfig
from studio_core.llm import ClaudeLLM
from studio_core.text import idf_table
from topicradar.cluster import cluster_signals, find_schedule, has_korean, signal_tokens
from topicradar.db import Store
from topicradar.models import ECON_CATEGORY, RiskFlag, Role, SlotItem, SlotName, SlotPlan, Topic
from topicradar.planner import fill_plan_text
from topicradar.risk import plan_risks
from topicradar.score import Coverage, score_topics

TARGET_SECONDS = {"morning": 420, "evening": 660, "weekly": 1050}
ROLE_WEIGHT: dict[str, float] = {
    "헤드라인": 1.6,
    "서브": 1.0,
    "오늘의포인트": 1.1,
    "포인트회수": 0.8,
    "내일쟁점": 0.7,
    "줄기": 2.0,
}
MIN_SUB_TOTAL = 40.0


def window_for(slot: SlotName, day: date) -> tuple[datetime, datetime]:
    """슬롯 윈도우 [start, end)."""
    if slot == "morning":
        return (
            datetime.combine(day - timedelta(days=1), time(17, 0), tzinfo=KST),
            datetime.combine(day, time(5, 30), tzinfo=KST),
        )
    if slot == "evening":
        return datetime.combine(day, time(5, 30), tzinfo=KST), datetime.combine(day, time(17, 0), tzinfo=KST)
    monday = day - timedelta(days=day.weekday())
    return datetime.combine(monday, time(0, 0), tzinfo=KST), datetime.combine(
        monday + timedelta(days=5), time(17, 0), tzinfo=KST
    )


def _sched_on(t: Topic, day: date) -> bool:
    return t.scheduled_at is not None and t.scheduled_at.date() == day


def allocate_seconds(items: list[SlotItem], slot: SlotName) -> None:
    """역할 가중치로 예상 길이 배분."""
    total_w = sum(ROLE_WEIGHT[i.role] for i in items) or 1.0
    for i in items:
        i.est_seconds = int(round(TARGET_SECONDS[slot] * ROLE_WEIGHT[i.role] / total_w / 5.0) * 5)


def balance_categories(
    items: list[SlotItem],
    pool_for: Callable[[SlotItem], list[Topic]],
    replaceable: set[str],
    max_ratio: float = 0.5,
) -> list[str]:
    """경제 카테고리가 절반을 넘으면 최저점 경제 항목을 비경제 후보로 교체(없으면 제거)."""
    notes: list[str] = []
    used = {i.topic.id for i in items}
    while True:
        econ = [i for i in items if i.topic.category == ECON_CATEGORY]
        if len(econ) <= len(items) * max_ratio:
            return notes
        victims = sorted((i for i in econ if i.role in replaceable), key=lambda i: i.topic.total)
        if not victims:
            return notes
        victim = victims[0]
        repl = next(
            (t for t in pool_for(victim) if t.category != ECON_CATEGORY and t.id not in used),
            None,
        )
        if repl is None:
            items.remove(victim)
            notes.append(f"카테고리 균형: 경제 '{victim.topic.name}' 제외(대체 후보 없음)")
        else:
            notes.append(f"카테고리 균형: 경제 '{victim.topic.name}' → '{repl.name}' 교체")
            victim.topic = repl
            victim.note = "카테고리 균형으로 교체 투입"
            used.add(repl.id)


def _renumber(items: list[SlotItem]) -> list[SlotItem]:
    for n, it in enumerate(items, start=1):
        it.order = n
    return items


@dataclass
class SlotContext:
    """슬롯 실행에 필요한 것."""

    store: Store
    cfg: ChannelConfig
    llm: ClaudeLLM | None = None
    notes: list[str] = field(default_factory=list)


# ---- 주제 준비(군집 + 점수) ----------------------------------------------


def _prev_counts(store: Store, topics: list[Topic], start: datetime, end: datetime) -> dict[str, int]:
    """직전 동일 길이 윈도우의 주제별 신호 수(배정 + 미배정 토큰 매칭)."""
    span = end - start
    p_start, p_end = start - span, start
    prev = [s for s in store.signals_between(p_start, p_end) if s.kind != "competitor"]
    assigned = store.assigned_map([s.id for s in prev])
    unassigned = [s for s in prev if s.id not in assigned]
    toks = [signal_tokens(s) for s in unassigned]
    idf = idf_table(toks + [set(t.keywords) for t in topics])
    out: dict[str, int] = {}
    for t in topics:
        n = len(t.window_signals(p_start, p_end))
        kw = set(t.keywords)
        for st in toks:
            shared = st & kw
            if len(shared) >= 2 and sum(idf.get(x, 3.0) for x in shared) >= 3.5:
                n += 1
        out[t.id] = n
    return out


def _coverage(
    store: Store, topics: list[Topic], day: date, current_plan_id: str
) -> dict[str, list[Coverage]]:
    plans = [p for p in store.plans_between(day - timedelta(days=7), day) if p.plan_id != current_plan_id]
    out: dict[str, list[Coverage]] = {}
    ids = {t.id for t in topics}
    for p in plans:
        for it in p.items:
            if it.topic.id in ids:
                out.setdefault(it.topic.id, []).append(Coverage(p.plan_id, p.date, p.window_end))
    for t in topics:
        for pid in t.covered_in:
            if pid == current_plan_id or any(c.plan_id == pid for c in out.get(t.id, [])):
                continue
            d = date.fromisoformat(pid.split("_")[0])
            slot = pid.split("_")[1] if "_" in pid else "evening"
            out.setdefault(t.id, []).append(
                Coverage(pid, d, window_for(slot, d)[1] if slot in TARGET_SECONDS else None)
            )  # type: ignore[arg-type]
    return out


def prepare_topics(ctx: SlotContext, slot: SlotName, day: date) -> tuple[list[Topic], datetime, datetime]:
    """윈도우 신호를 군집하고 점수를 매겨 저장한다."""
    start, end = window_for(slot, day)
    signals = ctx.store.signals_between(start, end)
    comp = ctx.store.signals_between(
        end - timedelta(hours=24), end + timedelta(hours=2), kinds={"competitor"}
    )
    known = {s.id for s in signals}
    signals += [s for s in comp if s.id not in known]
    known |= {s.id for s in comp}
    # 윈도우 이전 3일 기사 중 '오늘 이후 일정'을 예고한 미배정 기사도 함께 묶는다(일정 근거 보강).
    lookback = ctx.store.signals_between(start - timedelta(days=3), start)
    lb_assigned = ctx.store.assigned_map([s.id for s in lookback])
    for s in lookback:
        at = find_schedule(s)
        if s.id not in known and s.id not in lb_assigned and at is not None and at.date() >= day:
            signals.append(s)
    existing = ctx.store.topics_since(start - timedelta(days=7))
    assigned = ctx.store.assigned_map([s.id for s in signals])
    topics = cluster_signals(
        signals,
        existing,
        day,
        ctx.cfg.channel.categories,
        llm=ctx.llm,
        assigned=assigned,
        generic=set(ctx.cfg.clustering.generic_tokens),
    )
    if slot == "weekly":
        week_ids = {it.topic.id for p in ctx.store.plans_between(start.date(), end.date()) for it in p.items}
        have = {t.id for t in topics}
        for tid in week_ids - have:
            t = ctx.store.get_topic(tid)
            if t is not None:
                topics.append(t)
    plan_id = f"{day.isoformat()}_{slot}"
    topics = score_topics(
        topics,
        window_start=start,
        window_end=end,
        prev_counts=_prev_counts(ctx.store, topics, start, end),
        target_day=day,
        coverage={} if slot == "weekly" else _coverage(ctx.store, topics, day, plan_id),
        weights=ctx.cfg.scoring.weights,
        has_competitors=bool(ctx.cfg.channel.competitors),
        llm=ctx.llm,
    )
    for t in topics:
        ctx.store.save_topic(t)
    return topics, start, end


# ---- 슬롯별 선정 ----------------------------------------------------------


def select_morning(
    topics: list[Topic],
    day: date,
    start: datetime,
    end: datetime,
    prev_evening_ids: set[str],
    allow_foreign: bool = True,
) -> tuple[list[SlotItem], list[str], list[RiskFlag]]:
    """아침 편성. allow_foreign=False(오프라인)면 한국어 기사가 없는 해외 주제는 제외한다."""
    notes: list[str] = []
    flags: list[RiskFlag] = []
    eligible: list[Topic] = []
    for t in topics:
        in_window = bool(t.window_signals(start, end))
        today = _sched_on(t, day)
        if not in_window and not today:
            continue
        if not allow_foreign and not has_korean(t):
            notes.append(f"'{t.name}': 영문 기사뿐 — 오프라인 모드는 번역 불가로 제외(Claude 경로에선 포함)")
            continue
        if t.id in prev_evening_ids and not today:
            notes.append(f"'{t.name}': 전날 저녁에 다룸, 오늘 후속 일정 없음 → 제외")
            continue
        eligible.append(t)
    by_total = sorted(eligible, key=lambda t: -t.total)
    sched = [t for t in by_total if _sched_on(t, day)]
    overseas = [t for t in by_total if t.kind == "overseas" and not _sched_on(t, day)]
    announce = [
        t
        for t in by_total
        if t.kind == "announcement" and not _sched_on(t, day) and t.id not in prev_evening_ids
    ]
    picks_overseas = overseas[:1] + [t for t in overseas[1:2] if t.total >= 45]
    picks_announce = [t for t in announce if t.total >= 45][:2]
    points = sched[:3]
    if len(points) < 3:
        used = {t.id for t in [*picks_overseas, *picks_announce, *points]}
        fillers = [
            t
            for t in by_total
            if t.id not in used and t.follow_up and t.kind != "overseas" and t.id not in prev_evening_ids
        ]
        if fillers:
            notes.append(f"오늘 예정 일정 {len(points)}개 → 후속 수순이 있는 주제로 포인트 보충")
        points += fillers[: 3 - len(points)]
    if len(points) < 3:
        flags.append(
            RiskFlag(kind="구성", message=f"오늘 볼 포인트가 {len(points)}개뿐 — 일정 YAML 보강 필요")
        )
    if not picks_overseas:
        flags.append(RiskFlag(kind="구성", message="해외 헤드라인 없음 — 첫 문장 규칙(해외 헤드라인) 확인"))
    items: list[SlotItem] = []
    for n, t in enumerate(picks_overseas):
        items.append(SlotItem(topic=t, order=0, role="헤드라인" if n == 0 else "서브"))
    items += [SlotItem(topic=t, order=0, role="서브") for t in picks_announce]
    items += [SlotItem(topic=t, order=0, role="오늘의포인트") for t in points]
    pools: dict[str, list[Topic]] = {"overseas": overseas, "announce": announce, "points": sched}

    def pool_for(it: SlotItem) -> list[Topic]:
        if it.role == "오늘의포인트":
            return pools["points"]
        return pools["overseas"] if it.topic.kind == "overseas" else pools["announce"]

    notes += balance_categories(items, pool_for, replaceable={"서브", "오늘의포인트", "헤드라인"})
    return _renumber(items), notes, flags


def select_evening(
    topics: list[Topic],
    day: date,
    start: datetime,
    end: datetime,
    morning: SlotPlan | None,
    store: Store,
) -> tuple[list[SlotItem], list[str], list[RiskFlag]]:
    """저녁 편성."""
    notes: list[str] = []
    flags: list[RiskFlag] = []
    by_id = {t.id: t for t in topics}
    point_items = morning.items_by_role("오늘의포인트") if morning else []
    point_ids = {i.topic.id for i in point_items}
    morning_other = (morning.topic_ids() - point_ids) if morning else set()
    events: list[Topic] = []
    for t in sorted(topics, key=lambda t: -t.total):
        n = len(t.window_signals(start, end))
        if n == 0 or t.id in point_ids:
            continue
        if t.id in morning_other and n < 2:
            notes.append(f"'{t.name}': 아침에 다룸, 새 전개 부족(신호 {n}건) → 제외")
            continue
        events.append(t)
    items: list[SlotItem] = []
    if events:
        items.append(SlotItem(topic=events[0], order=0, role="헤드라인"))
        subs = [t for t in events[1:] if t.total >= MIN_SUB_TOTAL][:4]
        if len(subs) < 2:
            subs = events[1:3]
        items += [SlotItem(topic=t, order=0, role="서브") for t in subs]
    else:
        flags.append(RiskFlag(kind="구성", message="오늘 윈도우에 새 사건 없음"))
    notes += balance_categories(items, lambda _it: events, replaceable={"서브", "헤드라인"})
    # 아침 포인트 회수(결과 없으면 pending) — 반드시 전부 포함
    if not morning:
        flags.append(RiskFlag(kind="구성", message="오늘 아침 편성안이 없어 포인트 회수를 건너뜀"))
    for pi in point_items:
        fresh = by_id.get(pi.topic.id) or store.get_topic(pi.topic.id) or pi.topic
        items.append(
            SlotItem(topic=fresh, order=0, role="포인트회수", expected=pi.expected, impact=pi.impact)
        )
    # 내일 쟁점
    used = {i.topic.id for i in items}
    tomorrow = day + timedelta(days=1)
    pool = {t.id: t for t in topics}
    for t in store.topics_since(start - timedelta(days=3)):
        pool.setdefault(t.id, t)
    cands = sorted(
        (t for t in pool.values() if t.id not in used and _sched_on(t, tomorrow)), key=lambda t: -t.total
    )
    if not cands:
        cands = sorted(
            (
                t
                for t in pool.values()
                if t.id not in used and t.follow_up and t.last_seen >= start - timedelta(days=2)
            ),
            key=lambda t: (-(t.last_seen.date().toordinal()), -t.total),
        )
        if cands:
            notes.append("내일 일정이 잡힌 주제가 없어 후속 수순이 있는 최근 주제로 내일 쟁점 선정")
    if cands:
        items.append(SlotItem(topic=cands[0], order=0, role="내일쟁점"))
    else:
        flags.append(RiskFlag(kind="구성", message="내일 쟁점 후보 없음"))
    # 슬롯 전체 기준으로도 경제 ≤ 절반(본문 기준은 위에서 이미 적용)
    notes += balance_categories(items, lambda _it: events, replaceable={"서브"})
    return _renumber(items), notes, flags


def select_weekly(
    topics: list[Topic], day: date, end: datetime, chains_from: Callable[[list[Topic]], list]
) -> tuple[list[SlotItem], list, list[str], list[RiskFlag]]:
    """주간 편성: 사슬 1~2개 + 다음 주 전망."""
    notes: list[str] = []
    flags: list[RiskFlag] = []
    chains = chains_from(topics)
    by_id = {t.id: t for t in topics}
    items: list[SlotItem] = []
    used: set[str] = set()
    for ch in chains:
        members = [by_id[i] for i in ch.topic_ids if i in by_id and i not in used]
        if not members:
            continue
        anchor = max(
            members,
            key=lambda t: (t.scores["persistence"].score if "persistence" in t.scores else 0, t.total),
        )
        for t in sorted(members, key=lambda t: t.first_seen):
            role: Role = "줄기" if t.id == anchor.id else "서브"
            items.append(SlotItem(topic=t, order=0, role=role, chain=ch.title))
            used.add(t.id)
    dropped = [
        t.name for t in topics if t.id not in used and len({s.published_at.date() for s in t.signals}) <= 1
    ]
    if dropped:
        notes.append(f"사슬에 연결되지 않은 단발 사건 {len(dropped)}건 제외")
    next_mon = day + timedelta(days=(7 - day.weekday()) % 7 or 7)
    next_week = [
        t
        for t in topics
        if t.id not in used
        and t.scheduled_at is not None
        and next_mon <= t.scheduled_at.date() < next_mon + timedelta(days=7)
    ]
    if not next_week:
        next_week = [
            t
            for t in topics
            if t.id in used and t.scheduled_at is not None and t.scheduled_at.date() >= next_mon
        ]
    if not next_week:
        next_week = [t for t in sorted(topics, key=lambda t: -t.total) if t.follow_up and t.id not in used][
            :1
        ]
    for t in sorted(next_week, key=lambda t: -t.total)[:2]:
        items.append(SlotItem(topic=t, order=0, role="내일쟁점", note="다음 주 전망"))
    notes += balance_categories(items, lambda _it: [], replaceable={"서브"})
    if not chains:
        flags.append(RiskFlag(kind="구성", message="인과 사슬을 만들 지속성 높은 주제가 부족"))
    return _renumber(items), chains, notes, flags


# ---- 진입점 ---------------------------------------------------------------


def build_plan(ctx: SlotContext, slot: SlotName, day: date) -> SlotPlan:
    """슬롯 편성안 생성(점수·선정·문장 요소·리스크)."""
    topics, start, end = prepare_topics(ctx, slot, day)
    plan = SlotPlan(date=day, slot=slot, window_start=start, window_end=end)
    if slot == "morning":
        prev = ctx.store.get_plan(day - timedelta(days=1), "evening")
        items, notes, flags = select_morning(
            topics, day, start, end, prev.topic_ids() if prev else set(), allow_foreign=ctx.llm is not None
        )
        plan.items = items
    elif slot == "evening":
        items, notes, flags = select_evening(
            topics, day, start, end, ctx.store.get_plan(day, "morning"), ctx.store
        )
        plan.items = items
    else:
        from topicradar.planner import heuristic_chains, llm_plan_text

        def chains_from(ts: list[Topic]) -> list:
            if ctx.llm is not None:
                try:
                    res = llm_plan_text(ctx.llm, SlotPlan(date=day, slot="weekly"), ts)
                    if res.chains:
                        from topicradar.models import Chain

                        return [
                            Chain(title=c.title, summary=c.summary, topic_ids=c.topic_ids)
                            for c in res.chains[:2]
                        ]
                except Exception:  # Claude 실패 시 휴리스틱
                    pass
            return heuristic_chains(ts, generic=set(ctx.cfg.clustering.generic_tokens))

        items, chains, notes, flags = select_weekly(topics, day, end, chains_from)
        plan.items, plan.chains = items, chains
    allocate_seconds(plan.items, slot)
    fill_plan_text(plan, ctx.llm, topics if slot == "weekly" else None)
    plan.excluded = notes
    plan.risk_flags = [*flags, *plan_risks(plan)]
    if ctx.llm is not None:
        plan.llm_mode = "claude"
    return plan


def replace_item(ctx: SlotContext, plan: SlotPlan, order: int, topic: Topic) -> SlotPlan:
    """pick: 항목 교체 후 문장 요소를 다시 채운다(역할 유지)."""
    target = next((i for i in plan.items if i.order == order), None)
    if target is None:
        raise ValueError(f"order {order} 항목이 없습니다")
    target.topic = topic
    target.expected = target.impact = target.actual = target.hit = target.hit_reason = None  # type: ignore[assignment]
    target.note = "수동 교체(pick)"
    if target.role == "포인트회수":
        morning = ctx.store.get_plan(plan.date, "morning")
        src = next((i for i in (morning.items if morning else []) if i.topic.id == topic.id), None)
        if src is not None:
            target.expected, target.impact = src.expected, src.impact
    plan.titles, plan.thumbnail_text = [], ""
    fill_plan_text(plan, ctx.llm)
    plan.risk_flags = [f for f in plan.risk_flags if f.kind == "구성"] + plan_risks(plan)
    return plan
