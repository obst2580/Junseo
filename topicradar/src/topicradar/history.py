"""history: 예측 점수 vs 실제 조회수, 제목 패턴·카테고리별 성과, 포인트 적중률 → 가중치 조정 제안."""

from __future__ import annotations

import math
import statistics
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import datetime, timedelta

from topicradar.db import Store, UploadRow
from topicradar.models import SlotPlan
from topicradar.score import DEFAULT_WEIGHTS, SCORE_LABELS

TITLE_PATTERNS = [
    ("왜", "왜"),
    ("다음", "다음"),
    ("결국", "결국"),
    ("이유", "이유"),
    ("생기는 일", "~하면 생기는 일"),
]


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


def title_pattern(title: str) -> str:
    """제목 패턴 분류."""
    for marker, label in TITLE_PATTERNS:
        if marker in title:
            return label
    return "기타"


@dataclass
class Row:
    """업로드 1건 분석 행."""

    upload: UploadRow
    plan: SlotPlan | None
    rel_views: float


def _rows(store: Store, uploads: list[UploadRow]) -> list[Row]:
    by_slot: dict[str, list[int]] = defaultdict(list)
    for u in uploads:
        if u.views is not None:
            by_slot[u.plan_id.split("_")[-1]].append(u.views)
    med = {k: statistics.median(v) or 1 for k, v in by_slot.items()}
    out = []
    for u in uploads:
        if u.views is None:
            continue
        day, slot = u.plan_id.split("_")
        plan = store.get_plan(datetime.fromisoformat(day).date(), slot)
        out.append(Row(u, plan, u.views / med[slot]))
    return out


def _component_means(plan: SlotPlan) -> dict[str, float]:
    acc: dict[str, list[float]] = defaultdict(list)
    for it in plan.items:
        for k, v in it.topic.scores.items():
            acc[k].append(v.score)
    return {k: statistics.fmean(v) for k, v in acc.items() if v}


def suggest_weights(
    rows: list[Row], weights: dict[str, float], min_n: int = 5
) -> tuple[dict[str, float] | None, str]:
    """세부 점수와 상대 조회수의 상관으로 ±5 조정 제안."""
    usable = [r for r in rows if r.plan is not None]
    if len(usable) < min_n:
        return None, f"업로드 {len(usable)}건 — {min_n}건 이상부터 가중치 조정을 제안합니다."
    corr: dict[str, float] = {}
    for k in DEFAULT_WEIGHTS:
        xs = [_component_means(r.plan).get(k, 0.0) for r in usable if r.plan]
        c = pearson(xs, [r.rel_views for r in usable])
        if c is not None:
            corr[k] = c
    if len(corr) < 2:
        return None, "세부 점수 분산이 없어 제안 불가"
    up = max(corr, key=lambda k: corr[k])
    down = min(corr, key=lambda k: corr[k])
    new = dict(weights)
    if up == down or corr[up] - corr[down] < 0.2:
        return None, "세부 점수 간 상관 차이가 작아(0.2 미만) 현행 유지 권고"
    step = min(5.0, new[down] - 5.0)
    if step <= 0:
        return None, f"{SCORE_LABELS[down]} 가중치가 이미 하한(5)"
    new[up] += step
    new[down] -= step
    why = ", ".join(f"{SCORE_LABELS[k]} r={v:+.2f}" for k, v in sorted(corr.items(), key=lambda x: -x[1]))
    return new, f"{SCORE_LABELS[up]} +{step:.0f}, {SCORE_LABELS[down]} −{step:.0f} 제안 ({why})"


def hit_rates(plans: list[SlotPlan]) -> dict[str, Counter[str]]:
    """ISO 주차별 포인트 적중 집계."""
    out: dict[str, Counter[str]] = defaultdict(Counter)
    for p in plans:
        if p.slot != "evening":
            continue
        y, w, _ = p.date.isocalendar()
        for it in p.items_by_role("포인트회수"):
            out[f"{y}-W{w:02d}"][it.hit or "pending"] += 1
    return out


def history_report(
    store: Store, weights: dict[str, float], weeks: int, now: datetime, mode: str = "balanced"
) -> str:
    """history 마크다운."""
    since = now - timedelta(weeks=weeks)
    rows = _rows(store, store.uploads(since))
    out = [
        f"# 최근 {weeks}주 성과 분석",
        "",
        f"- 분석 업로드: {len(rows)}건 (조회수는 슬롯별 중앙값 대비 상대값)",
        "",
    ]
    xs = [r.upload.predicted_total for r in rows]
    c = pearson(xs, [r.rel_views for r in rows])
    out.append("## 예측 점수 vs 실제 조회수")
    out.append(f"- 상관계수 r = {c:+.2f}" if c is not None else "- 표본 부족(3건 미만) 또는 분산 0")
    out += ["", "| 업로드 | 슬롯 | 예측 총점 | 조회수 | 상대 |", "|---|---|---|---|---|"]
    for r in rows:
        out.append(
            f"| {r.upload.video_id} | {r.upload.plan_id} | {r.upload.predicted_total:.1f} | {r.upload.views:,} | {r.rel_views:.2f} |"
        )
    out += ["", "## 제목 패턴별 성과", "", "| 패턴 | 건수 | 평균 상대 조회수 |", "|---|---|---|"]
    by_pat: dict[str, list[float]] = defaultdict(list)
    for r in rows:
        by_pat[title_pattern(r.upload.title)].append(r.rel_views)
    for pat, vals in sorted(by_pat.items(), key=lambda x: -statistics.fmean(x[1])):
        out.append(f"| {pat} | {len(vals)} | {statistics.fmean(vals):.2f} |")
    out += [
        "",
        "## 카테고리별 성과(첫 항목 기준)",
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
        "## 아침 포인트 적중률(주차별)",
        "",
        "| 주차 | hit | miss | pending | 적중률(판정분) |",
        "|---|---|---|---|---|",
    ]
    plans = store.plans_between(since.date(), now.date())
    for wk, cnt in sorted(hit_rates(plans).items()):
        judged = cnt["hit"] + cnt["miss"]
        rate = f"{cnt['hit'] / judged:.0%}" if judged else "-"
        out.append(f"| {wk} | {cnt['hit']} | {cnt['miss']} | {cnt['pending']} | {rate} |")
    if mode == "hottest":
        out += [
            "",
            "## 주제 선정 방식",
            "",
            "- 가장 이슈인 순(이슈도 = 커뮤니티 반응 60% + 보도량 40% + 급상승·다른 채널 가점, 중복 감점).",
            "- 위 '예측 점수 vs 실제 조회수' 상관이 이슈도와 조회수의 관계입니다. 가중치는 '균형' 방식에서만 씁니다.",
        ]
        return "\n".join(out) + "\n"
    new, why = suggest_weights(rows, weights)
    out += [
        "",
        "## 가중치 조정 제안",
        "",
        "- 현행: " + ", ".join(f"{SCORE_LABELS[k]} {v:.0f}" for k, v in weights.items()),
    ]
    out.append(f"- {why}")
    if new:
        out.append("- 제안: " + ", ".join(f"{SCORE_LABELS[k]} {v:.0f}" for k, v in new.items()))
        out.append("- 적용하려면 channel.yaml의 scoring.weights를 수정하세요.")
    return "\n".join(out) + "\n"
