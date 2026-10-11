"""topicradar CLI: collect / slot / pick / made / history / daily."""

from __future__ import annotations

import asyncio
import logging
from datetime import date, datetime
from pathlib import Path

import typer
from rich.console import Console
from rich.table import Table

from studio_core.config import KST, load_channel, load_settings, now_kst
from studio_core.llm import get_llm
from topicradar import pipeline
from topicradar.models import SlotName

app = typer.Typer(add_completion=False, help="주제 선정 + 대본 초안 (아침 프리뷰 / 저녁 리뷰 / 주간 심층)")
console = Console()
SLOTS = ("morning", "evening", "weekly")


def _setup() -> tuple:
    logging.basicConfig(level=logging.WARNING, format="%(levelname)s %(name)s: %(message)s")
    settings = load_settings()
    cfg = load_channel(settings)
    return settings, cfg, pipeline.open_store(settings)


def _parse_since(since: str | None) -> datetime | None:
    if not since:
        return None
    dt = datetime.fromisoformat(since)
    return dt.replace(tzinfo=KST) if dt.tzinfo is None else dt


def _print_collect(report: object) -> None:
    t = Table(title="수집 결과")
    t.add_column("수집기")
    t.add_column("건수/상태")
    for k, v in sorted(report.counts.items()):  # type: ignore[attr-defined]
        t.add_row("새로 저장" if k == "_new" else k, str(v))
    for k, v in report.skipped.items():  # type: ignore[attr-defined]
        t.add_row(k, f"[yellow]건너뜀[/yellow] {v}")
    for k, v in report.errors.items():  # type: ignore[attr-defined]
        t.add_row(k, f"[red]실패[/red] {v}")
    console.print(t)


def _print_slot(res: pipeline.SlotResult) -> None:
    p = res.plan
    t = Table(title=f"{p.plan_id} 편성안")
    for col in ("순서", "역할", "주제", "카테고리", "총점", "추세", "예상/회수"):
        t.add_column(col)
    for it in p.items:
        extra = (
            it.expected or ""
            if it.role == "오늘의포인트"
            else (it.hit or "")
            if it.role == "포인트회수"
            else ""
        )
        t.add_row(
            str(it.order),
            it.role,
            it.topic.name[:28],
            it.topic.category,
            f"{it.topic.total:.1f}",
            it.topic.trend,
            extra[:30],
        )
    console.print(t)
    console.print(f"제목: {' / '.join(p.titles)}  ·  썸네일: {p.thumbnail_text}")
    console.print(f"→ {res.project}/report.md, script.txt")
    for w in res.warnings:
        console.print(f"[yellow]⚠ {w}[/yellow]")


@app.command()
def collect(
    since: str = typer.Option(None, help="ISO 시각(기본: 마지막 수집 1시간 전, 없으면 24시간 전)"),
) -> None:
    """RSS·네이버·유튜브·일정 수집."""
    settings, cfg, store = _setup()
    report = asyncio.run(pipeline.run_collect(settings, cfg, store, _parse_since(since)))
    _print_collect(report)


@app.command()
def slot(
    name: str = typer.Argument(..., help="morning | evening | weekly"),
    day: str = typer.Option(None, "--date", help="YYYY-MM-DD (주간 기본: 이번/다가오는 일요일)"),
    notify: bool = typer.Option(False, "--notify", help="Slack 전송"),
) -> None:
    """슬롯 편성안·대본 생성 → projects/날짜_슬롯/."""
    if name not in SLOTS:
        raise typer.BadParameter(f"{name}: morning|evening|weekly")
    settings, cfg, store = _setup()
    s: SlotName = name  # type: ignore[assignment]
    d = date.fromisoformat(day) if day else pipeline.default_day(s)
    res = pipeline.run_slot(settings, cfg, store, s, d, get_llm(settings))
    _print_slot(res)
    if notify and asyncio.run(pipeline.send_slack(cfg, res)):
        console.print("Slack 전송 완료")


@app.command()
def pick(
    project: Path = typer.Option(..., "--project", help="projects/YYYY-MM-DD_slot"),
    replace: tuple[int, str] = typer.Option(..., "--replace", help="<order> <topic_id>"),
) -> None:
    """아이템 교체 후 대본 재생성."""
    settings, cfg, store = _setup()
    res = pipeline.run_pick(settings, cfg, store, project, replace[0], replace[1], get_llm(settings))
    _print_slot(res)


@app.command()
def made(
    project: Path = typer.Option(..., "--project"),
    video_id: str = typer.Option(..., "--video-id"),
    title: str = typer.Option(None, "--title", help="실제 업로드 제목(기본: 제목 1안)"),
) -> None:
    """업로드 기록, covered_in 갱신, past_titles 추가."""
    settings, _cfg, store = _setup()
    row = asyncio.run(pipeline.run_made(settings, store, project, video_id, title))
    console.print(f"기록: {row.video_id} · {row.plan_id} · 예측 {row.predicted_total} · '{row.title}'")


@app.command()
def history(weeks: int = typer.Option(4, "--weeks")) -> None:
    """예측 vs 조회수, 제목 패턴·카테고리 성과, 포인트 적중률, 가중치 제안."""
    settings, cfg, store = _setup()
    text = asyncio.run(pipeline.run_history(store, cfg, weeks))
    out = settings.data_dir / "history.md"
    out.write_text(text, encoding="utf-8")
    console.print(text)
    console.print(f"→ {out}")


@app.command()
def daily(
    at: str = typer.Option(None, "--at", help="시각 강제(ISO, 기본 현재 KST)"),
    notify: bool = typer.Option(False, "--notify"),
) -> None:
    """요일·시각에 따라 collect → slot (05:30 morning, 12:00 collect, 17:00 evening, 토 17:00 weekly)."""
    settings, cfg, store = _setup()
    now = _parse_since(at) or now_kst()
    llm = get_llm(settings)
    for action, s, d in pipeline.daily_actions(now):
        if action == "collect":
            _print_collect(asyncio.run(pipeline.run_collect(settings, cfg, store, None)))
        elif s is not None and d is not None:
            res = pipeline.run_slot(settings, cfg, store, s, d, llm)
            _print_slot(res)
            if notify:
                asyncio.run(pipeline.send_slack(cfg, res))


if __name__ == "__main__":
    app()
