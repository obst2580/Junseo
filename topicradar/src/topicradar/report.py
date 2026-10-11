"""리포트(마크다운) + Slack 전송."""

from __future__ import annotations

from collections import Counter
from pathlib import Path

import httpx

from studio_core.http import request
from topicradar.models import SlotPlan, Topic
from topicradar.score import SCORE_LABELS

SLOT_KO = {"morning": "아침 프리뷰", "evening": "저녁 리뷰", "weekly": "주간 심층"}
HIT_KO = {"hit": "✅ hit", "miss": "❌ miss", "pending": "⏳ pending"}


def _cell(text: str | None) -> str:
    return (text or "").replace("|", "／").replace("\n", " ")


def _detail(plan_item_scores: dict) -> str:
    parts = []
    for k in ("interest", "gap", "persistence", "fit"):
        if k in plan_item_scores:
            parts.append(f"{SCORE_LABELS[k][:2]}{plan_item_scores[k].score:.0f}")
    return " ".join(parts)


def buzz_cell(t: Topic) -> str:
    """커뮤니티 반응 요약: 사이트별 글 수 + 댓글 합(근거 링크로는 쓰지 않음)."""
    buzz = t.buzz()
    if not buzz:
        return "-"
    by_site = Counter(str(s.metric.get("site", "?")) for s in buzz)
    comments = sum(int(s.metric.get("comments", 0)) for s in buzz)
    return "·".join(f"{k}{v}" for k, v in by_site.most_common()) + f" (댓글 {comments})"


def render_report(plan: SlotPlan, script_path: Path, warnings: list[str]) -> str:
    """편성 리포트 마크다운."""
    p = plan
    out = [f"# {p.date.isoformat()} {SLOT_KO[p.slot]} 편성안", ""]
    if p.window_start and p.window_end:
        out.append(f"- 윈도우: {p.window_start:%m-%d %H:%M} ~ {p.window_end:%m-%d %H:%M} (KST)")
    out.append(f"- 판정 엔진: {'Claude' if p.llm_mode == 'claude' else '오프라인 휴리스틱(키 없음)'}")
    cats = Counter(i.topic.category for i in p.items)
    out.append("- 카테고리 분포: " + ", ".join(f"{c} {n}" for c, n in cats.most_common()))
    out.append("")
    third = (
        "expected / 달라지는 것" if p.slot == "morning" else "actual / hit" if p.slot == "evening" else "사슬"
    )
    out.append(
        f"| 순서 | 역할 | 주제 | 카테고리 | 총점(세부) | 추세 | 커뮤니티 반응 | {third} | 근거 링크 | 리스크 |"
    )
    out.append("|---|---|---|---|---|---|---|---|---|---|")
    flags_by_topic: dict[str, list[str]] = {}
    for f in p.risk_flags:
        if f.topic_id:
            flags_by_topic.setdefault(f.topic_id, []).append(f.kind)
    for it in p.items:
        t = it.topic
        if it.role == "오늘의포인트":
            col = f"{_cell(it.expected) or '(근거 없음)'} / {_cell(it.impact) or '-'}"
        elif it.role == "포인트회수":
            col = f"{_cell(it.actual) or '(결과 없음)'} / {HIT_KO.get(it.hit or '', '-')}"
        elif it.chain:
            col = _cell(it.chain)
        else:
            col = _cell(it.note) or "-"
        links = " ".join(f"[{n + 1}]({u})" for n, u in enumerate(t.top_links(2))) or "-"
        risk = ", ".join(sorted(set(flags_by_topic.get(t.id, [])))) or "-"
        out.append(
            f"| {it.order} | {it.role} | {_cell(t.name)} `{t.id}` | {t.category} | **{t.total:.1f}** ({_detail(t.scores)}) "
            f"| {t.trend} | {buzz_cell(t)} | {col} | {links} | {risk} |"
        )
    out += ["", "## 점수 근거", ""]
    for it in p.items:
        t = it.topic
        out.append(f"- **{it.order}. {t.name}**")
        for k, v in t.scores.items():
            out.append(f"  - {SCORE_LABELS.get(k, k)} {v.score:.0f}: {v.reason}")
        if it.hit_reason:
            out.append(f"  - 판정 근거: {it.hit_reason}")
    if p.chains:
        out += ["", "## 인과 사슬", ""]
        for c in p.chains:
            out.append(f"- **{c.title}** — {c.summary}")
    out += ["", "## 제목 3안", ""]
    out += [f"{n + 1}. {t}" for n, t in enumerate(p.titles)]
    out += ["", f"**썸네일 문구:** {p.thumbnail_text} ({len(p.thumbnail_text)}자)", ""]
    if p.risk_flags:
        out += ["## 리스크(표시 전용)", ""]
        for f in p.risk_flags:
            out.append(f"- [{f.kind}] {f.message}")
        out.append("")
    if p.excluded:
        out += ["## 제외·조정 메모", ""] + [f"- {n}" for n in p.excluded] + [""]
    out += ["## 대본", "", f"- 경로: `{script_path}`"]
    if warnings:
        out += ["- 점검:"] + [f"  - ⚠️ {w}" for w in warnings]
    else:
        out.append("- 점검: 규칙 위반 없음")
    return "\n".join(out) + "\n"


def slack_text(plan: SlotPlan, project_dir: Path) -> str:
    """Slack 요약 문구."""
    lines = [
        f"*{plan.date.isoformat()} {SLOT_KO[plan.slot]}* — 제목 1안: {plan.titles[0] if plan.titles else '-'}"
    ]
    for it in plan.items:
        extra = ""
        if it.role == "오늘의포인트" and it.expected:
            extra = f" → 예상: {it.expected}"
        if it.role == "포인트회수":
            extra = f" → {HIT_KO.get(it.hit or '', '-')}"
        lines.append(f"{it.order}. [{it.role}] {it.topic.name} ({it.topic.total:.0f}){extra}")
    lines.append(f"폴더: `{project_dir}`")
    return "\n".join(lines)


async def notify_slack(client: httpx.AsyncClient, webhook: str, text: str) -> None:
    """Slack Incoming Webhook 전송."""
    await request(client, "POST", webhook, json={"text": text})
