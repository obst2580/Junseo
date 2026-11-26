"""newsclip CLI: run / review / xml / search."""

from __future__ import annotations

import asyncio
import logging
from pathlib import Path

import typer
from rich.console import Console
from rich.table import Table

from newsclip import pipeline
from studio_core.config import load_channel, load_settings
from studio_core.llm import get_llm

app = typer.Typer(add_completion=False, help="대본·자막 → 이미지·영상 자동 수집 → 프리미어용 FCP XML")
console = Console()


def _setup() -> tuple:
    logging.basicConfig(level=logging.WARNING, format="%(levelname)s %(name)s: %(message)s")
    settings = load_settings()
    return settings, load_channel(settings)


def _size(value: str) -> tuple[int, int]:
    w, h = value.lower().split("x")
    return int(w), int(h)


@app.command()
def run(
    project: Path = typer.Option(..., "--project", help="projects/YYYY-MM-DD_slot"),
    per_scene: int = typer.Option(3, "--per-scene", help="장면당 다운로드 후보 수"),
    video_max_sec: float = typer.Option(20.0, "--video-max-sec", help="영상 클립 1개 최대 길이(초)"),
    clip: bool = typer.Option(False, "--clip", help="open_clip 유사도 가산(uv sync --extra clip)"),
    fps: int = typer.Option(30, "--fps"),
    size: str = typer.Option("1920x1080", "--size"),
) -> None:
    """장면 분할 → 검색 → 순위 → 다운로드 → assets.json → timeline.xml."""
    settings, cfg = _setup()
    sset, rep = asyncio.run(
        pipeline.run_project(
            settings,
            cfg,
            project,
            per_scene=per_scene,
            video_max_sec=video_max_sec,
            use_clip=clip,
            llm=get_llm(settings),
            fps=fps,
            size=_size(size),
        )
    )
    t = Table(title=f"{project.name} 장면")
    for col in ("장면", "시간", "주제", "종류", "키워드", "선택", "출처"):
        t.add_column(col)
    for sc in sset.scenes:
        ch = sc.chosen
        t.add_row(
            sc.id,
            f"{sc.start_sec:.1f}-{sc.end_sec:.1f}",
            sc.topic[:14],
            sc.kind,
            ", ".join(sc.keywords_ko)[:24],
            ch.source if ch else "[red]없음[/red]",
            (ch.attribution_text if ch else "")[:30],
        )
    console.print(t)
    console.print(
        f"장면 {rep.scenes}개 · 자료 확보 {rep.with_asset}개 · 다운로드 {rep.downloaded}개 · 소스별 선택 {rep.by_source}"
    )
    for name, why in rep.skipped_sources.items():
        console.print(f"[yellow]건너뛴 소스 {name}: {why}[/yellow]")
    for e in rep.errors[:10]:
        console.print(f"[yellow]⚠ {e}[/yellow]")
    if rep.xml_errors:
        console.print("[red]timeline.xml 검사 실패:[/red] " + "; ".join(rep.xml_errors[:5]))
        raise typer.Exit(1)
    console.print(f"→ {project}/scenes.json, assets.json, timeline.xml (검사 통과)")


@app.command()
def xml(
    project: Path = typer.Option(..., "--project"),
    fps: int = typer.Option(30, "--fps"),
    size: str = typer.Option("1920x1080", "--size"),
    zoom: float = typer.Option(1.04, "--zoom"),
    audio: str = typer.Option(None, "--audio", help="프로젝트 폴더 기준 내레이션 파일(예: narration.wav)"),
    video_max_sec: float = typer.Option(20.0, "--video-max-sec"),
) -> None:
    """scenes.json → timeline.xml 재생성."""
    _setup()
    sset = pipeline.load_scenes(project)
    a = (project / audio) if audio else None
    errs = pipeline.write_xml(
        project, sset, fps=fps, size=_size(size), zoom=zoom, audio=a, video_max_sec=video_max_sec
    )
    if errs:
        console.print("[red]검사 실패:[/red] " + "; ".join(errs[:5]))
        raise typer.Exit(1)
    n_seg = sum(1 for sc in sset.scenes if sc.chosen and not sc.skipped)
    console.print(
        f"→ {project}/timeline.xml ({fps}fps, {size}, zoom {zoom}, 장면 {n_seg}/{len(sset.scenes)}, 검사 통과)"
    )


@app.command()
def review(
    project: Path = typer.Option(..., "--project"),
    port: int = typer.Option(8765, "--port"),
) -> None:
    """localhost:8765 검토 UI(후보 선택·건너뛰기·영상 시작 초 조정 → 저장 시 XML 재생성)."""
    import uvicorn

    from newsclip.review.app import create_app

    _setup()
    console.print(f"http://localhost:{port} — Ctrl+C로 종료")
    uvicorn.run(create_app(project.resolve()), host="127.0.0.1", port=port, log_level="warning")


@app.command()
def search(
    query: str = typer.Argument(...),
    kind: str = typer.Option("photo", "--kind", help="photo | video | broll"),
) -> None:
    """소스별 상위 5개 표(디버그)."""
    settings, cfg = _setup()
    res = asyncio.run(pipeline.search_debug(settings, cfg, query, kind))
    for name, items in res.items():
        if isinstance(items, str):
            console.print(f"[yellow]{name}: {items}[/yellow]")
            continue
        t = Table(title=name)
        for col in ("점수", "제목", "출처", "크기", "근거"):
            t.add_column(col)
        for a in items:
            size_txt = f"{a.width}x{a.height}" if a.width else "-"
            t.add_row(
                f"{a.score:.0f}",
                a.title[:30],
                (a.author_or_channel or a.source_domain)[:20],
                size_txt,
                ", ".join(a.score_notes)[:40],
            )
        console.print(t)


if __name__ == "__main__":
    app()
