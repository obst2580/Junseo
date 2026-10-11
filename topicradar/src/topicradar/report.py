"""리포트(마크다운) + Slack 전송."""

from __future__ import annotations

from collections import Counter
from pathlib import Path

import httpx

from studio_core.http import request
from topicradar.models import Board, Topic, VideoPlan
from topicradar.score import SCORE_LABELS, heat_stats


def _cell(text: str | None) -> str:
    return (text or "").replace("|", "／").replace("\n", " ")


def buzz_cell(t: Topic) -> str:
    """커뮤니티 반응 요약: 사이트별 글 수 + 댓글·공감 합."""
    buzz = t.buzz()
    if not buzz:
        return "-"
    by_site = Counter(str(s.metric.get("site", "?")) for s in buzz)
    _posts, comments, votes = heat_stats(buzz)
    tail = f"댓글 {comments}" + (f" · 공감 {votes}" if votes else "")
    return "·".join(f"{k}{v}" for k, v in by_site.most_common()) + f" ({tail})"


def render_report(plan: VideoPlan, script_path: Path, warnings: list[str]) -> str:
    """영상 계획 리포트 마크다운."""
    p = plan
    out = [f"# {p.created_at:%Y-%m-%d %H:%M} {p.spec.name}", ""]
    if p.window_start and p.window_end:
        out.append(f"- 주제판 구간: {p.window_start:%m-%d %H:%M} ~ {p.window_end:%m-%d %H:%M} (KST)")
    out.append(f"- 판정 엔진: {'Claude' if p.llm_mode == 'claude' else '오프라인 휴리스틱(키 없음)'}")
    out.append(f"- 대본 목표: {p.target_chars[0]:,}~{p.target_chars[1]:,}자")
    if p.link:
        out.append(f"- 주제를 잇는 고리: {p.link}")
    out += [
        "",
        "| 순서 | 주제 | 카테고리 | 반응도 | 커뮤니티 반응 | 커뮤니티 주장 | 분량 | 근거 기사 | 리스크 |",
        "|---|---|---|---|---|---|---|---|---|",
    ]
    flags_by_topic: dict[str, list[str]] = {}
    for f in p.risk_flags:
        if f.topic_id:
            flags_by_topic.setdefault(f.topic_id, []).append(f.kind)
    for it in p.items:
        t = it.topic
        links = " ".join(f"[{n + 1}]({u})" for n, u in enumerate(t.top_links(2))) or "기사 없음"
        risk = ", ".join(sorted(set(flags_by_topic.get(t.id, [])))) or "-"
        stance = _cell(it.opinion.stance) if it.opinion else "-"
        out.append(
            f"| {it.order} | {_cell(t.name)} `{t.id}` | {t.category} | **{t.total:.0f}** {t.trend} "
            f"| {buzz_cell(t)} | {stance} | {it.est_seconds}초 | {links} | {risk} |"
        )
    out += ["", "## 커뮤니티 의견", ""]
    for it in p.items:
        op = it.opinion
        out.append(f"### {it.order}. {it.topic.name}")
        for k, v in it.topic.scores.items():
            out.append(f"- {SCORE_LABELS.get(k, k)} {v.score:.0f}: {v.reason}")
        if op is None:
            out += ["- 의견 정리 없음", ""]
            continue
        out.append(f"- 주장: **{op.stance}** ({'Claude 정리' if op.by == 'claude' else '공감 많은 글'})")
        out += [f"  - {x}" for x in op.points]
        if op.minority:
            out.append(f"- 소수 의견: {op.minority}")
        if op.questions:
            out.append("- 궁금해하는 것: " + " / ".join(op.questions))
        if op.samples:
            out.append("- 대표 글·댓글:")
            out += [
                f"  - [{s.site} {'글' if s.kind == 'post' else '댓글'} · 공감 {s.votes}] {_cell(s.text)[:80]}"
                for s in op.samples[:6]
            ]
        out.append("")
    out += ["## 제목 3안", ""]
    out += [f"{n + 1}. {t}" for n, t in enumerate(p.titles)]
    out += ["", f"**썸네일 문구:** {p.thumbnail_text} ({len(p.thumbnail_text)}자)", ""]
    if p.risk_flags:
        out += ["## 리스크(표시 전용)", ""]
        for f in p.risk_flags:
            out.append(f"- [{f.kind}] {f.message}")
        out.append("")
    if p.notes:
        out += ["## 메모", ""] + [f"- {n}" for n in p.notes] + [""]
    out += ["## 대본", "", f"- 경로: `{script_path}`"]
    if warnings:
        out += ["- 점검:"] + [f"  - ⚠️ {w}" for w in warnings]
    else:
        out.append("- 점검: 규칙 위반 없음")
    return "\n".join(out) + "\n"


def board_text(board: Board, limit: int = 8) -> str:
    """Slack·CLI용 주제판 요약."""
    lines = [f"*주제판 {board.created_at:%m-%d %H:%M}* — 지금 커뮤니티에서 뜨는 주제"]
    for n, t in enumerate(board.topics[:limit], start=1):
        lines.append(f"{n}. {t.name} ({t.score:.0f}, {t.opinion.heat_label}) — {t.opinion.stance}")
    for b in board.bundles:
        names = [board.get(i).name for i in b.topic_ids if board.get(i)]  # type: ignore[union-attr]
        lines.append(f"10분 묶음 추천: {' + '.join(names)} — {b.link}")
    return "\n".join(lines)


def slack_text(plan: VideoPlan, project_dir: Path) -> str:
    """Slack 요약 문구."""
    lines = [f"*{plan.spec.name}* — 제목 1안: {plan.titles[0] if plan.titles else '-'}"]
    for it in plan.items:
        stance = f" → {it.opinion.stance}" if it.opinion else ""
        lines.append(f"{it.order}. {it.topic.name} ({it.topic.total:.0f}){stance}")
    lines.append(f"폴더: `{project_dir}`")
    return "\n".join(lines)


async def notify_slack(client: httpx.AsyncClient, webhook: str, text: str) -> None:
    """Slack Incoming Webhook 전송."""
    await request(client, "POST", webhook, json={"text": text})
