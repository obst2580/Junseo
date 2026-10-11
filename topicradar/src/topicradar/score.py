"""주제 점수(0~100 가중합). 세부 점수마다 근거 한 문장을 저장한다.

1. 관심도 40%: 윈도우 기사 수 + 상승률(절대량보다 상승률에 더 큰 비중)
2. 공백도 20%: 경쟁 채널이 최근 24시간에 다뤘으면 감점, 아무도 안 다뤘고 관심도도 낮으면 감점
3. 지속성 10%: Claude 판정(오프라인은 휴리스틱)
4. 채널 적합도 30%: Claude 판정 + 코드 보정(최근 7일 내 다룸 감점, 시리즈 연속 가점)
카테고리 균형(경제 ≤ 절반)은 slots.py가 편성 단계에서 적용한다.
수익화 안전도는 점수에 넣지 않는다(risk.py가 표시만).
"""

from __future__ import annotations

import json
import math
import re
from dataclasses import dataclass
from datetime import date, datetime, timedelta

from pydantic import BaseModel

from studio_core.config import KST
from studio_core.llm import ClaudeLLM, LLMError
from topicradar import prompts
from topicradar.models import POLITICS_CORE, ScoreDetail, Topic

DEFAULT_WEIGHTS = {"interest": 40.0, "gap": 20.0, "persistence": 10.0, "fit": 30.0}
SCORE_LABELS = {"interest": "관심도", "gap": "공백도", "persistence": "지속성", "fit": "적합도"}

_INSTITUTIONAL = re.compile(
    r"(법안|법률|개정|제도|정책|예산|개혁|시행령|재판|수사|협상|선거|국정감사|특검|입법|헌법)"
)
_HAPPENING = re.compile(r"(막말|설전|말실수|해프닝|공방|신경전|발언 논란|말다툼|비아냥)")
_CAUSE = re.compile(r"(때문|이유|배경|책임|탓|반발|여파|원인)")
_POLITICAL = re.compile(r"(국회|정부|대통령|법안|여당|야당|정책|예산|장관|국무회의|당정|입법|원내|정당|의원)")
_AGENDA = re.compile(
    r"(노동권|플랫폼노동|공공의료|지역의료|검찰개혁|언론자유|기후|불평등|민생|돌봄|노동자|약자|차별)"
)


def clamp(x: float, lo: float = 0.0, hi: float = 100.0) -> float:
    """범위 제한."""
    return max(lo, min(hi, x))


@dataclass
class InterestResult:
    """관심도 계산 결과."""

    score: float
    rise: float
    trend: str
    reason: str


def interest_score(
    n_window: int,
    n_prev: int,
    trend_ratio: float | None = None,
    saturation: int = 25,
    engagement: int = 0,
    label: str = "윈도우 기사",
) -> InterestResult:
    """관심도: 0.4×양(건수 + 댓글/20) 점수 + 0.6×상승률 점수."""
    volume = n_window + engagement / 20.0
    count = 100.0 * min(1.0, math.log1p(volume) / math.log1p(saturation))
    rise = trend_ratio if trend_ratio is not None else (n_window - n_prev) / max(n_prev, 1)
    rise = max(-1.0, min(3.0, rise))
    rise_score = clamp(50.0 + 25.0 * rise)
    score = round(0.4 * count + 0.6 * rise_score, 1)
    trend = "↑" if rise >= 0.3 else "↓" if rise <= -0.3 else "→"
    basis = "검색 추이" if trend_ratio is not None else f"직전 동일 구간 {n_prev}건 대비"
    eng = f"(댓글 {engagement})" if engagement else ""
    return InterestResult(score, rise, trend, f"{label} {n_window}건{eng}, {basis} {rise:+.0%}")


def gap_score(competitor_hits: int, interest: float, has_competitors: bool = True) -> ScoreDetail:
    """공백도."""
    suffix = "" if has_competitors else " (경쟁 채널 미설정)"
    if competitor_hits >= 1:
        s = clamp(60.0 - 20.0 * (competitor_hits - 1))
        return ScoreDetail(score=s, reason=f"경쟁 채널이 최근 24시간에 {competitor_hits}편 다룸{suffix}")
    if interest >= 40.0:
        return ScoreDetail(score=100.0, reason=f"경쟁 채널 미다룸, 관심도 {interest:.0f}로 충분{suffix}")
    return ScoreDetail(
        score=round(30.0 + interest, 1), reason=f"아무도 안 다뤘지만 관심도도 낮음({interest:.0f}){suffix}"
    )


def _text(t: Topic) -> str:
    return " ".join([t.name, *(s.text for s in t.signals if s.kind not in ("competitor", "community"))])


def heuristic_judge(t: Topic) -> tuple[ScoreDetail, ScoreDetail]:
    """지속성·적합도 휴리스틱(오프라인)."""
    text = _text(t)
    inst = bool(_INSTITUTIONAL.search(text))
    # 주제명 자체가 설전·막말이면 국감 같은 제도 맥락이 있어도 해프닝으로 본다.
    happening = bool(_HAPPENING.search(t.name)) or (bool(_HAPPENING.search(text)) and not inst)
    days = {
        s.published_at.astimezone(KST).date() for s in t.signals if s.kind not in ("competitor", "community")
    }
    has_next = t.scheduled_at is not None or t.follow_up is not None

    p, p_why = 50.0, []
    if has_next:
        p += 20
        p_why.append("후속 일정 있음")
    if inst and not happening:
        p += 15
        p_why.append("제도·정책 사안")
    if len(days) >= 2:
        p += 10
        p_why.append(f"{len(days)}일째 보도")
    if happening:
        p -= 30
        p_why.append("단발 해프닝 성격")
    persistence = ScoreDetail(score=clamp(p), reason=", ".join(p_why) or "흐름 판단 근거 부족")

    f, f_why = 50.0, []
    if t.category in POLITICS_CORE:
        f += 15
        f_why.append(f"정치 핵심({t.category})")
    if has_next:
        f += 10
        f_why.append("'다음'이 예측 가능")
    if _CAUSE.search(text):
        f += 5
        f_why.append("원인 설명 가능")
    if _AGENDA.search(text):
        f += 10
        f_why.append("진보 의제와 직결")
    if happening:
        f -= 20
        f_why.append("단순 발언·설전")
    buzz = t.buzz()
    if len(buzz) >= 3:
        f += 10
        f_why.append(f"진보 커뮤니티 화제({len(buzz)}건)")
    if t.category in ("경제", "노동", "복지") and not _POLITICAL.search(text):
        f -= 20
        f_why.append("정치 쟁점으로 번지지 않음")
    fit = ScoreDetail(score=clamp(f), reason=", ".join(f_why) or "특이점 없음")
    return persistence, fit


class LLMJudgement(BaseModel):
    """Claude 판정 항목."""

    topic_id: str
    persistence: float
    persistence_reason: str
    fit: float
    fit_reason: str


class LLMJudgeResult(BaseModel):
    """Claude 판정 결과."""

    judgements: list[LLMJudgement]


def llm_judge(llm: ClaudeLLM, topics: list[Topic]) -> dict[str, tuple[ScoreDetail, ScoreDetail]]:
    """지속성·적합도를 한 번의 배치 호출로 판정."""
    rows = []
    for t in topics:
        sigs = sorted(
            (s for s in t.signals if s.kind not in ("competitor", "community")),
            key=lambda s: s.published_at,
            reverse=True,
        )
        buzz = sorted(t.buzz(), key=lambda s: -int(s.metric.get("comments", 0)))
        rows.append(
            {
                "topic_id": t.id,
                "name": t.name,
                "category": t.category,
                "kind": t.kind,
                "scheduled_at": t.scheduled_at.isoformat() if t.scheduled_at else None,
                "follow_up": t.follow_up,
                "articles": [{"title": s.title, "summary": s.summary[:160]} for s in sigs[:4]],
                "community_buzz": {
                    "posts": len(buzz),
                    "comments": sum(int(s.metric.get("comments", 0)) for s in buzz),
                    "titles": [f"[{s.metric.get('site', '')}] {s.title}" for s in buzz[:5]],
                },
            }
        )
    system = prompts.IDENTITY + "\n" + prompts.JUDGE_TASK
    res = llm.json("judge", system, "주제 목록:\n" + json.dumps(rows, ensure_ascii=False), LLMJudgeResult)
    assert isinstance(res, LLMJudgeResult)
    out = {}
    for j in res.judgements:
        out[j.topic_id] = (
            ScoreDetail(score=clamp(j.persistence), reason=j.persistence_reason),
            ScoreDetail(score=clamp(j.fit), reason=j.fit_reason),
        )
    return out


@dataclass
class Coverage:
    """최근 다룬 기록 한 건."""

    plan_id: str
    day: date
    window_end: datetime | None


def coverage_adjust(t: Topic, fit: ScoreDetail, coverage: list[Coverage], target_day: date) -> ScoreDetail:
    """최근 7일 내 다룬 주제: 새 전개 없으면 −25, 새 전개 있으면 시리즈 연속 +10."""
    recent = [c for c in coverage if 0 <= (target_day - c.day).days <= 7]
    if not recent:
        return fit
    last = max(recent, key=lambda c: (c.day, c.window_end or datetime.min.replace(tzinfo=KST)))
    cutoff = last.window_end or datetime.combine(last.day, datetime.min.time(), tzinfo=KST)
    new_sigs = [s for s in t.signals if s.kind != "competitor" and s.published_at >= cutoff]
    scheduled_today = t.scheduled_at is not None and t.scheduled_at.date() == target_day
    label = last.plan_id
    if len(new_sigs) >= 2 or scheduled_today:
        return ScoreDetail(
            score=clamp(fit.score + 10), reason=f"{fit.reason} / 시리즈 연속({label} 이후 새 전개)"
        )
    return ScoreDetail(
        score=clamp(fit.score - 25), reason=f"{fit.reason} / 최근 7일 내 다룸({label}), 새 전개 없음"
    )


def total_score(scores: dict[str, ScoreDetail], weights: dict[str, float] | None = None) -> float:
    """가중합."""
    w = weights or DEFAULT_WEIGHTS
    norm = sum(w.values()) or 100.0
    return round(sum(scores[k].score * w.get(k, 0.0) for k in scores) / norm, 1)


def score_topics(
    topics: list[Topic],
    *,
    window_start: datetime,
    window_end: datetime,
    prev_counts: dict[str, int],
    target_day: date,
    coverage: dict[str, list[Coverage]] | None = None,
    weights: dict[str, float] | None = None,
    has_competitors: bool = True,
    llm: ClaudeLLM | None = None,
    trend_ratios: dict[str, float] | None = None,
    community_mode: bool = False,
) -> list[Topic]:
    """주제마다 scores·total·trend를 채운다(제자리 갱신 후 반환).

    community_mode: 커뮤니티 정치 게시판 데이터가 있으면 관심도를 커뮤니티 반응(글 수·댓글·상승률)으로 잰다.
    prev_counts는 같은 기준(커뮤니티 모드면 커뮤니티 글 수)이어야 한다.
    """
    judged: dict[str, tuple[ScoreDetail, ScoreDetail]] = {}
    if llm is not None and topics:
        try:
            judged = llm_judge(llm, topics)
        except LLMError:
            judged = {}
    coverage = coverage or {}
    trend_ratios = trend_ratios or {}
    gap_from = window_end - timedelta(hours=24)
    for t in topics:
        if community_mode:
            buzz = t.buzz(window_start, window_end)
            ir = interest_score(
                len(buzz),
                prev_counts.get(t.id, 0),
                trend_ratios.get(t.id),
                saturation=15,
                engagement=sum(int(s.metric.get("comments", 0)) for s in buzz),
                label="커뮤니티 글",
            )
            ir.reason += f" · 기사 {len(t.news_signals(window_start, window_end))}건"
        else:
            n_window = len(t.window_signals(window_start, window_end))
            ir = interest_score(n_window, prev_counts.get(t.id, 0), trend_ratios.get(t.id))
        comp = [s for s in t.signals if s.kind == "competitor" and gap_from <= s.published_at <= window_end]
        t.competitor_hits = len(comp)
        persistence, fit = judged.get(t.id) or heuristic_judge(t)
        fit = coverage_adjust(t, fit, coverage.get(t.id, []), target_day)
        t.scores = {
            "interest": ScoreDetail(score=ir.score, reason=ir.reason),
            "gap": gap_score(len(comp), ir.score, has_competitors),
            "persistence": persistence,
            "fit": fit,
        }
        t.trend = ir.trend  # type: ignore[assignment]
        t.total = total_score(t.scores, weights)
    return sorted(topics, key=lambda t: -t.total)
