"""topicradar CLI: collect / board / video / rewrite / made / history / probe."""

from __future__ import annotations

import asyncio
import logging
from datetime import datetime
from pathlib import Path

import typer
from rich.console import Console
from rich.table import Table

from studio_core.config import KST, load_channel, load_settings
from studio_core.llm import get_llm
from topicradar import pipeline
from topicradar.models import FORMATS, Board
from topicradar.report import board_text

app = typer.Typer(
    add_completion=False, help="커뮤니티 반응으로 주제 고르기 + 대본 초안 (10분 묶음 / 3분 단독)"
)
console = Console()


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
    if getattr(report, "derived_queries", None):
        console.print("커뮤니티 화제 → 뉴스 검색어: " + ", ".join(report.derived_queries))  # type: ignore[attr-defined]


def _print_board(board: Board) -> None:
    t = Table(
        title=f"주제판 {board.id} (구간 {board.window_start:%m-%d %H:%M} ~ {board.window_end:%m-%d %H:%M})"
    )
    for col in ("#", "주제 ID", "주제", "반응도", "반응", "커뮤니티 주장", "기사"):
        t.add_column(col)
    for n, bt in enumerate(board.topics, start=1):
        t.add_row(
            str(n),
            bt.topic_id,
            bt.name[:24],
            f"{bt.score:.0f}{bt.trend}",
            bt.opinion.heat_label,
            bt.opinion.stance[:34],
            str(bt.articles),
        )
    console.print(t)
    for b in board.bundles:
        console.print(f"10분 묶음 추천: [bold]{' '.join(b.topic_ids)}[/bold] — {b.link}")
    for note in board.notes:
        console.print(f"[dim]{note}[/dim]")


def _print_video(res: pipeline.VideoResult) -> None:
    p = res.plan
    t = Table(title=f"{p.id} {p.spec.name}")
    for col in ("순서", "주제", "반응도", "분량", "커뮤니티 주장"):
        t.add_column(col)
    for it in p.items:
        t.add_row(
            str(it.order),
            it.topic.name[:28],
            f"{it.topic.total:.0f}",
            f"{it.est_seconds}초",
            (it.opinion.stance if it.opinion else "")[:40],
        )
    console.print(t)
    if p.link:
        console.print(f"고리: {p.link}")
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
def board(
    notify: bool = typer.Option(False, "--notify", help="Slack 전송"),
) -> None:
    """주제판: 지금 커뮤니티에서 뜨는 주제(반응도 순) + 커뮤니티 의견 + 10분 묶음 추천."""
    settings, cfg, store = _setup()
    b = pipeline.run_board(cfg, store, get_llm(settings))
    _print_board(b)
    if notify and asyncio.run(pipeline.send_slack(cfg, board_text(b))):
        console.print("Slack 전송 완료")


@app.command()
def video(
    fmt: str = typer.Option(..., "--format", help="long(10분 묶음) | short(3분 단독)"),
    topics: str = typer.Option(None, "--topics", help="주제 ID(쉼표로): 3분은 1개, 10분은 2~3개"),
    pick: str = typer.Option(None, "--pick", help="판의 순위(쉼표로), 예: 1 또는 1,3"),
    bundle: int = typer.Option(None, "--bundle", help="판의 10분 묶음 추천 번호(1부터)"),
    board_id: str = typer.Option(None, "--board", help="주제판 ID(기본: 가장 최근 판)"),
) -> None:
    """판에서 고른 주제로 대본·리포트 → projects/<영상 ID>/."""
    if fmt not in FORMATS:
        raise typer.BadParameter(f"{fmt}: long | short")
    settings, cfg, store = _setup()
    b = store.get_board(board_id) if board_id else store.latest_board()
    if b is None:
        raise typer.BadParameter("주제판이 없습니다 — 먼저 board를 실행하세요")
    if topics:
        ids = [x.strip() for x in topics.split(",") if x.strip()]
    elif pick:
        ids = [b.topics[int(x) - 1].topic_id for x in pick.split(",") if x.strip()]
    elif bundle:
        if not 1 <= bundle <= len(b.bundles):
            raise typer.BadParameter(f"묶음 추천은 {len(b.bundles)}개입니다")
        ids = b.bundles[bundle - 1].topic_ids
    else:
        raise typer.BadParameter("--topics, --pick, --bundle 중 하나로 주제를 고르세요")
    res = pipeline.run_video(settings, cfg, store, b.id, ids, fmt, get_llm(settings))
    _print_video(res)


@app.command()
def rewrite(project: Path = typer.Option(..., "--project", help="projects/<영상 ID>")) -> None:
    """같은 계획으로 대본만 다시 쓰기."""
    settings, cfg, store = _setup()
    _print_video(pipeline.run_rewrite(settings, cfg, store, project, get_llm(settings)))


@app.command()
def made(
    project: Path = typer.Option(..., "--project"),
    video_id: str = typer.Option(..., "--video-id"),
    title: str = typer.Option(None, "--title", help="실제 업로드 제목(기본: 제목 1안)"),
) -> None:
    """업로드 기록, covered_in 갱신, past_titles 추가."""
    settings, _cfg, store = _setup()
    row = asyncio.run(pipeline.run_made(settings, store, project, video_id, title))
    console.print(f"기록: {row.video_id} · {row.plan_id} · 반응도 {row.predicted_total} · '{row.title}'")


@app.command()
def history(weeks: int = typer.Option(4, "--weeks")) -> None:
    """커뮤니티 반응도 vs 조회수, 10분·3분 형식별·카테고리별 성과."""
    settings, _cfg, store = _setup()
    text = asyncio.run(pipeline.run_history(store, weeks))
    out = settings.data_dir / "history.md"
    out.write_text(text, encoding="utf-8")
    console.print(text)
    console.print(f"→ {out}")


@app.command()
def probe(name: str = typer.Option(None, "--name", help="커뮤니티 이름(기본: 전부)")) -> None:
    """커뮤니티 게시판 파싱 점검: robots.txt 허용 여부, 잡힌 글 수, 정치 필터 통과 수, 예시 5개."""
    _settings, cfg, _store = _setup()
    if not cfg.communities:
        console.print("[yellow]channel.yaml에 communities가 없습니다.[/yellow]")
        return
    for r in asyncio.run(pipeline.probe_communities(cfg, name)):
        head = f"[bold]{r.name}[/bold] {r.url}"
        if not r.allowed:
            console.print(f"{head}\n  [red]robots.txt가 수집을 허용하지 않음 — 이 게시판은 쓰지 마세요[/red]")
            continue
        if r.error:
            console.print(f"{head}\n  [red]실패[/red] {r.error}")
            continue
        console.print(f"{head}\n  글 {len(r.posts)}개 인식 · 정치 필터 통과 {r.kept}개")
        if not r.posts:
            console.print(
                "  [yellow]글을 하나도 못 찾았습니다 → post_pattern을 실제 글 주소 형식에 맞게 고치세요[/yellow]"
            )
        t = Table(show_header=True)
        for col in ("시각", "댓글", "추천", "제목", "주소"):
            t.add_column(col)
        for p in r.posts[:5]:
            t.add_row(
                f"{p.posted_at:%m-%d %H:%M}",
                str(p.comments),
                "-" if p.votes is None else str(p.votes),
                p.title[:40],
                p.url[-40:],
            )
        console.print(t)
        if r.detail_error:
            console.print(f"  글 상세 [red]실패[/red] {r.detail_error}")
        elif r.detail_url:
            console.print(
                f"  글 상세: 본문 {r.body_chars}자 · 댓글 {r.comments_found}개(공감 읽힘 {r.comment_votes}) · "
                f"글 공감 {'-' if r.post_votes is None else r.post_votes}  ({r.detail_url})"
            )
            for v, text in r.comment_samples:
                console.print(f"    [{v}] {text}")


if __name__ == "__main__":
    app()
