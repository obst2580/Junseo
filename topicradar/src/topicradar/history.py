"""history: 커뮤니티 반응도 vs 실제 조회수, 10분 묶음 vs 3분 단독, 카테고리별 성과."""

from __future__ import annotations

import math
import statistics
from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime, timedelta

from topicradar.db import Store, UploadRow
from topicradar.models import FORMATS, VideoPlan


def pearson(xs: list[float], ys: list[float]) -> float | None:
    """피어슨 상관(표본 3개 미만이거나 분산 0이면 None)."""
    if len(xs) < 3 or len(xs) != len(ys):
        return None
    mx, my = statistics.fmean(xs), statistics.fmean(ys)
    sx = math.sqrt(sum((x - mx) ** 2 for x in xs))
    sy = math.sqrt(sum((y - my) ** 2 for y in ys))
    if sx == 0 or sy == 0:
        return None
    return sum((x - mx) * (y - my) for x, y in zip(xs, ys, strict=True)) / (sx * sy)


@dataclass
class Row:
    """업로드 1건 분석 행."""

    upload: UploadRow
    plan: VideoPlan | None
    fmt: str
    rel_views: float


def _rows(store: Store, uploads: list[UploadRow]) -> list[Row]:
    plans = {u.plan_id: store.get_video(u.plan_id) for u in uploads}
    fmt_of = {pid: (p.format if p else pid.rsplit("_", 1)[-1]) for pid, p in plans.items()}
    by_fmt: dict[str, list[int]] = defaultdict(list)
    for u in uploads:
        if u.views is not None:
            by_fmt[fmt_of[u.plan_id]].append(u.views)
    med = {k: statistics.median(v) or 1 for k, v in by_fmt.items()}
    return [
        Row(u, plans[u.plan_id], fmt_of[u.plan_id], u.views / med[fmt_of[u.plan_id]])
        for u in uploads
        if u.views is not None
    ]


def history_report(store: Store, weeks: int, now: datetime) -> str:
    """history 마크다운."""
    since = now - timedelta(weeks=weeks)
    rows = _rows(store, store.uploads(since))
    out = [
        f"# 최근 {weeks}주 성과 분석",
        "",
        f"- 분석 업로드: {len(rows)}건 (상대 조회수 = 같은 형식(10분·3분) 영상의 중앙값 대비)",
        "",
        "## 형식별 성과",
        "",
        "| 형식 | 건수 | 평균 조회수 | 중앙값 |",
        "|---|---|---|---|",
    ]
    by_fmt: dict[str, list[int]] = defaultdict(list)
    for r in rows:
        by_fmt[r.fmt].append(r.upload.views or 0)
    for fmt, vals in sorted(by_fmt.items()):
        name = FORMATS[fmt].name if fmt in FORMATS else fmt
        out.append(
            f"| {name} | {len(vals)} | {statistics.fmean(vals):,.0f} | {statistics.median(vals):,.0f} |"
        )
    xs = [r.upload.predicted_total for r in rows]
    c = pearson(xs, [r.rel_views for r in rows])
    out += ["", "## 커뮤니티 반응도 vs 실제 조회수"]
    out.append(
        f"- 상관계수 r = {c:+.2f} (1에 가까울수록 '커뮤니티에서 뜨거운 주제 = 잘 되는 영상')"
        if c is not None
        else "- 표본 부족(3건 미만) 또는 분산 0"
    )
    out += ["", "| 업로드 | 형식 | 제목 | 반응도 | 조회수 | 상대 |", "|---|---|---|---|---|---|"]
    for r in rows:
        name = FORMATS[r.fmt].name if r.fmt in FORMATS else r.fmt
        out.append(
            f"| {r.upload.video_id} | {name} | {r.upload.title.replace('|', '／')} | {r.upload.predicted_total:.0f} "
            f"| {r.upload.views:,} | {r.rel_views:.2f} |"
        )
    out += [
        "",
        "## 카테고리별 성과(첫 주제 기준)",
        "",
        "| 카테고리 | 건수 | 평균 상대 조회수 |",
        "|---|---|---|",
    ]
    by_cat: dict[str, list[float]] = defaultdict(list)
    for r in rows:
        if r.plan and r.plan.items:
            by_cat[r.plan.items[0].topic.category].append(r.rel_views)
    for cat, vals in sorted(by_cat.items(), key=lambda x: -statistics.fmean(x[1])):
        out.append(f"| {cat} | {len(vals)} | {statistics.fmean(vals):.2f} |")
    out += [
        "",
        "## 주제 고르는 기준",
        "",
        "- 100% 커뮤니티 반응: 반응도 = 글 수 + 댓글/20 + 공감/10 (로그 눈금, 30이면 100점) + 급상승 가점.",
        "- 채널의 주장 = 그 주제에 대한 커뮤니티 다수 의견(공감 많은 글·댓글). 사실은 기사에서만.",
    ]
    return "\n".join(out) + "\n"
