"""주제 점수(0~100) — 100% 커뮤니티 반응.

반응도 = 100 × log1p(글 수 + 댓글/20 + 공감/10) / log1p(30)   (판 구간 안의 커뮤니티 글)
가점: 직전 같은 길이 구간보다 글이 늘었으면 최대 +10(급상승).
기사 수·경쟁 채널은 점수에 넣지 않는다(화면에 표시만 — 기사는 대본의 사실 근거).
"""

from __future__ import annotations

import math
from datetime import datetime

from topicradar.models import ScoreDetail, Signal, Topic

SCORE_LABELS = {"heat": "반응도"}
SATURATION = 30.0  # 글 30개 상당(댓글 20개 = 글 1개, 공감 10개 = 글 1개)이면 100점


def clamp(x: float, lo: float = 0.0, hi: float = 100.0) -> float:
    """범위 제한."""
    return max(lo, min(hi, x))


def heat_stats(buzz: list[Signal]) -> tuple[int, int, int]:
    """(글 수, 댓글 합, 공감 합)."""
    comments = sum(int(s.metric.get("comments", 0)) for s in buzz)
    votes = sum(int(s.metric.get("votes") or 0) for s in buzz)
    return len(buzz), comments, votes


def community_score(t: Topic, start: datetime, end: datetime, prev_posts: int) -> tuple[ScoreDetail, float]:
    """반응도와 상승률. 사유 문장에 근거 숫자를 모두 적는다."""
    posts, comments, votes = heat_stats(t.buzz(start, end))
    volume = posts + comments / 20.0 + votes / 10.0
    base = 100.0 * min(1.0, math.log1p(volume) / math.log1p(SATURATION))
    rise = max(-1.0, min(3.0, (posts - prev_posts) / max(prev_posts, 1)))
    bonus = 10.0 * max(0.0, min(rise, 2.0)) / 2.0 if posts >= 2 else 0.0
    parts = [f"커뮤니티 글 {posts}건", f"댓글 {comments:,}"]
    if votes:
        parts.append(f"공감 {votes:,}")
    if bonus:
        parts.append(f"직전 대비 {rise:+.0%}")
    return ScoreDetail(score=round(clamp(base + bonus), 1), reason=" · ".join(parts)), rise


def score_topics(
    topics: list[Topic], *, window_start: datetime, window_end: datetime, prev_counts: dict[str, int]
) -> list[Topic]:
    """주제마다 scores·total·trend를 채우고 반응도 순으로 돌려준다."""
    for t in topics:
        detail, rise = community_score(t, window_start, window_end, prev_counts.get(t.id, 0))
        t.scores = {"heat": detail}
        t.total = detail.score
        t.trend = "↑" if rise >= 0.3 else "↓" if rise <= -0.3 else "→"  # type: ignore[assignment]
    return sorted(topics, key=lambda t: -t.total)
