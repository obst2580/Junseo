"""주제판: 지금 커뮤니티에서 뜨는 주제(100% 커뮤니티 반응) + 10분 묶음 추천, 그리고 판에서 영상 계획 만들기.

- 판 구간: 지금부터 board.window_hours(기본 24시간) 전까지의 신호를 주제로 묶고 커뮤니티 반응도로 줄 세운다.
- 판에 오르는 주제: 구간 안 커뮤니티 글이 board.min_posts(기본 2) 이상, 또는 글 하나라도 반응도 50 이상
  (댓글 70개쯤, 공감 40개쯤)인 주제.
- 주제마다 커뮤니티 의견(채널이 대신 말할 주장)을 정리하고, 같이 다루기 좋은 주제 2~3개를 '10분 묶음'으로 추천한다.
- 영상: 판에서 1개를 골라 3분 단독, 2~3개를 골라 10분 묶음. 기사는 대본의 사실 근거로만 붙는다.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from datetime import datetime, timedelta

from studio_core.config import ChannelConfig
from studio_core.llm import ClaudeLLM, LLMError
from studio_core.text import idf_table, short_name, tokenize
from topicradar.cluster import GENERIC_TOKENS, IDF_PRIOR, cluster_signals, signal_tokens
from topicradar.db import Store
from topicradar.models import (
    FORMATS,
    Board,
    BoardTopic,
    Bundle,
    Opinion,
    RiskFlag,
    Topic,
    VideoItem,
    VideoPlan,
    target_chars,
)
from topicradar.opinion import LLM_TOPICS, heuristic_opinion, llm_opinions
from topicradar.planner import fill_plan_text, link_sentence
from topicradar.risk import plan_risks
from topicradar.score import heat_stats, score_topics


def shorts_target(cfg: ChannelConfig, fmt: str) -> tuple[int, int]:
    """채널 설정대로 대본 글자 수 목표 — script.shorts_seconds가 있으면 쇼츠는 그 길이(끝 인사 몫 빼고)."""
    sc = cfg.script
    return target_chars(fmt, sc.chars_per_minute, sc.shorts_seconds, sc.shorts_outro or sc.outro)


log = logging.getLogger("topicradar.board")

SOLO_SCORE = 50.0  # 글이 하나뿐이어도 반응도가 이만큼이면 판에 올린다
REL_MIN = 3.0  # 묶음으로 추천할 최소 연관도
LEAD_WEIGHT = 1.3  # 10분 묶음에서 첫 주제의 분량 가중


@dataclass
class BoardContext:
    """판 만들기에 필요한 것."""

    store: Store
    cfg: ChannelConfig
    llm: ClaudeLLM | None = None
    notes: list[str] = field(default_factory=list)


# ---- 주제 준비(군집 + 점수) ------------------------------------------------


def _prev_counts(store: Store, topics: list[Topic], start: datetime, end: datetime) -> dict[str, int]:
    """직전 같은 길이 구간의 주제별 커뮤니티 글 수(배정된 글 + 핵심어가 겹치는 미배정 글)."""
    span = end - start
    p_start, p_end = start - span, start
    prev = store.signals_between(p_start, p_end, kinds={"community"})
    assigned = store.assigned_map([s.id for s in prev])
    toks = [signal_tokens(s) for s in prev if s.id not in assigned]
    idf = idf_table(toks + [set(t.keywords) for t in topics])
    out: dict[str, int] = {}
    for t in topics:
        n = len(t.buzz(p_start, p_end))
        kw = set(t.keywords)
        for st in toks:
            shared = st & kw
            if len(shared) >= 2 and sum(idf.get(x, 3.0) for x in shared) >= 3.5:
                n += 1
        out[t.id] = n
    return out


def prepare_topics(ctx: BoardContext, start: datetime, end: datetime) -> list[Topic]:
    """구간 신호를 주제로 묶고 커뮤니티 반응도를 매겨 저장한다."""
    signals = ctx.store.signals_between(start, end)
    existing = ctx.store.topics_since(start - timedelta(days=3))
    assigned = ctx.store.assigned_map([s.id for s in signals])
    topics = cluster_signals(
        signals,
        existing,
        end.date(),
        ctx.cfg.channel.categories,
        llm=ctx.llm,
        assigned=assigned,
        generic=set(ctx.cfg.clustering.generic_tokens),
    )
    topics = score_topics(
        topics, window_start=start, window_end=end, prev_counts=_prev_counts(ctx.store, topics, start, end)
    )
    for t in topics:
        ctx.store.save_topic(t)
    return topics


def is_hot(t: Topic, start: datetime, end: datetime, min_posts: int) -> bool:
    """판에 올릴 만큼 커뮤니티 반응이 있는가(점수는 score_topics가 먼저 매긴다)."""
    buzz = t.buzz(start, end)
    return bool(buzz) and (len(buzz) >= min_posts or t.total >= SOLO_SCORE)


# ---- 10분 묶음 추천(휴리스틱) ------------------------------------------------


def _doc(t: Topic) -> set[str]:
    return set(t.keywords) | set(tokenize(t.name)) | {x for s in t.signals for x in signal_tokens(s)}


def heuristic_bundles(topics: list[Topic], generic: set[str] | None = None, limit: int = 3) -> list[Bundle]:
    """상위 주제끼리 고유한 말(인물·기관·사건명)이 겹치면 묶는다. 정당명 같은 일반어만으로는 묶지 않는다."""
    top = topics[:LLM_TOPICS]
    gen = GENERIC_TOKENS | (generic or set())
    docs = {t.id: _doc(t) for t in top}
    idf = idf_table(list(docs.values()), prior=IDF_PRIOR)

    def shared(a: Topic, b: Topic) -> list[str]:
        both = (docs[a.id] & docs[b.id]) - gen
        return sorted((x for x in both if len(x) >= 2), key=lambda x: (-idf.get(x, 0.0), x))

    def rel(a: Topic, b: Topic) -> float:
        return sum(idf.get(x, 0.0) for x in shared(a, b)[:5])

    used: set[str] = set()
    out: list[Bundle] = []
    for anchor in top:
        if anchor.id in used:
            continue
        cands = sorted(
            ((rel(anchor, t), t) for t in top if t.id != anchor.id and t.id not in used), key=lambda x: -x[0]
        )
        partners = [t for r, t in cands if r >= REL_MIN][:2]
        if not partners:
            continue
        words = list(dict.fromkeys(w for p in partners for w in shared(anchor, p)[:2]))[:2]
        names = [short_name(t.name, 14) for t in [anchor, *partners]]
        out.append(Bundle(topic_ids=[anchor.id, *(p.id for p in partners)], link=link_sentence(names, words)))
        used |= {anchor.id, *(p.id for p in partners)}
        if len(out) >= limit:
            break
    return out


# ---- 판 만들기 ----------------------------------------------------------------


def build_board(ctx: BoardContext, now: datetime) -> Board:
    """지금 뜨는 주제판."""
    bcfg = ctx.cfg.board
    start = now - timedelta(hours=bcfg.window_hours)
    end = now + timedelta(minutes=1)
    topics = prepare_topics(ctx, start, end)
    hot = [t for t in topics if is_hot(t, start, end, bcfg.min_posts)][: bcfg.size]
    notes: list[str] = []
    quiet = sum(1 for t in topics if t.buzz(start, end) and t not in hot)
    if quiet:
        notes.append(
            f"반응이 작은 화제 {quiet}건(글 {bcfg.min_posts}개 미만·반응도 {SOLO_SCORE:.0f} 미만)은 판에서 뺐습니다"
        )
    news_only = sum(1 for t in topics if not t.buzz(start, end) and t.news_signals(start, end))
    if news_only:
        notes.append(f"기사는 있지만 커뮤니티 반응이 없는 주제 {news_only}건은 판에 올리지 않습니다")
    opinions = {t.id: heuristic_opinion(t, start, end) for t in hot}
    bundles: list[Bundle] = []
    mode = "offline"
    if ctx.llm is not None and hot:
        try:
            got, bundles = llm_opinions(ctx.llm, hot[:LLM_TOPICS], start, end)
            opinions.update(got)
            mode = "claude"
        except LLMError as e:
            log.warning("Claude 의견 정리 실패 → 휴리스틱으로 대신: %s", e)
            notes.insert(0, f"Claude 의견 정리 실패 → 공감 많은 글을 그대로 옮김: {e}")
    if not bundles:
        bundles = heuristic_bundles(hot, set(ctx.cfg.clustering.generic_tokens))
    no_news = [t for t in hot if not t.news_signals()]
    if no_news:
        notes.append(f"기사가 아직 없는 주제 {len(no_news)}건 — 대본에서 사실 부분은 #TODO로 남습니다")
    board = Board(
        id=now.strftime("%Y-%m-%d_%H%M%S"),
        created_at=now,
        window_start=start,
        window_end=end,
        topics=[
            BoardTopic(
                topic_id=t.id,
                name=t.name,
                category=t.category,
                score=t.total,
                reason=t.scores["heat"].reason if "heat" in t.scores else "",
                trend=t.trend,
                opinion=opinions[t.id],
                articles=len(t.news_signals()),
                links=t.top_links(2),
                covered_in=list(t.covered_in),
            )
            for t in hot
        ],
        bundles=bundles,
        notes=notes,
        llm_mode=mode,  # type: ignore[arg-type]
    )
    ctx.store.save_board(board)
    return board


# ---- 판 → 영상 계획 -------------------------------------------------------------


def allocate_seconds(items: list[VideoItem], minutes: float) -> None:
    """형식 길이를 주제에 나눈다(10분 묶음은 첫 주제를 조금 더 길게)."""
    weights = [LEAD_WEIGHT if i == 0 and len(items) > 1 else 1.0 for i in range(len(items))]
    total = sum(weights) or 1.0
    for it, w in zip(items, weights, strict=True):
        it.est_seconds = int(round(minutes * 60 * w / total / 5.0) * 5)


def make_plan(
    ctx: BoardContext,
    board: Board,
    topic_ids: list[str],
    fmt: str,
    plan_id: str,
    now: datetime,
    *,
    opinions: dict[str, Opinion] | None = None,
    titles: list[str] | None = None,
    thumbnail_text: str = "",
    link: str | None = None,
) -> VideoPlan:
    """판에서 고른 주제로 영상 계획(제목·고리·리스크까지).

    opinions·titles·thumbnail_text·link: 바깥(claude.ai 대본실)에서 정한 값. 비어 있는 것만 여기서 채운다.
    """
    if fmt not in FORMATS:
        raise ValueError("형식은 long(10분 묶음) | short(3분 단독) | shorts(세로 쇼츠)")
    spec = FORMATS[fmt]
    ids = list(dict.fromkeys(topic_ids))
    if len(ids) != len(topic_ids):
        raise ValueError("같은 주제를 두 번 골랐습니다")
    if not spec.min_topics <= len(ids) <= spec.max_topics:
        raise ValueError(
            f"{spec.name}는 주제를 하나만 골라 주세요"
            if spec.max_topics == 1
            else f"10분 묶음은 연관 주제 {spec.min_topics}~{spec.max_topics}개를 골라 주세요"
        )
    items: list[VideoItem] = []
    for n, tid in enumerate(ids, start=1):
        bt = board.get(tid)
        topic = ctx.store.get_topic(tid)
        if bt is None or topic is None:
            raise KeyError(f"주제 {tid}가 이 판에 없습니다")
        items.append(VideoItem(topic=topic, order=n, opinion=(opinions or {}).get(tid, bt.opinion)))
    allocate_seconds(items, spec.minutes)
    bundle = next((b for b in board.bundles if set(b.topic_ids) == set(ids)), None)
    if fmt != "long":
        link = None
    elif not link and bundle:
        link = bundle.link
    plan = VideoPlan(
        id=plan_id,
        format=fmt,  # type: ignore[arg-type]
        created_at=now,
        board_id=board.id,
        items=items,
        link=link,
        titles=[t for t in (titles or []) if t][:3],
        thumbnail_text=thumbnail_text,
        target_chars=shorts_target(ctx.cfg, fmt),
        window_start=board.window_start,
        window_end=board.window_end,
    )
    fill_plan_text(plan, ctx.llm, set(ctx.cfg.clustering.generic_tokens))
    flags: list[RiskFlag] = []
    for it in items:
        if not it.topic.news_signals():
            flags.append(
                RiskFlag(
                    kind="구성",
                    topic_id=it.topic.id,
                    message=f"'{short_name(it.topic.name, 20)}' 기사 없음 — 커뮤니티 글만으로는 사실을 단정할 수 없어 대본의 사실 부분은 #TODO로 남습니다",
                )
            )
        posts, _c, _v = heat_stats(it.topic.buzz())
        if posts == 0:
            flags.append(
                RiskFlag(kind="구성", topic_id=it.topic.id, message="커뮤니티 글 없음 — 주장 근거가 없습니다")
            )
    plan.risk_flags = flags + plan_risks(plan)
    if ctx.llm is not None:
        plan.llm_mode = "claude"
    return plan
