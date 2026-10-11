"""newsclip run / xml / search 구현."""

from __future__ import annotations

import asyncio
import json
import logging
from collections.abc import Callable
from dataclasses import dataclass, field
from pathlib import Path

import httpx

from newsclip.download import DedupeRegistry, download_image, download_video
from newsclip.fcpxml import build_xmeml, validate_xmeml
from newsclip.license_log import attribution_text, write_assets_json
from newsclip.models import Asset, Scene, SceneSet
from newsclip.rank import asset_key, clip_scorer_or_none, rank, route_for
from newsclip.scenes import build_scenes
from newsclip.sources.base import Budget, SearchCache, Source
from newsclip.sources.google_image import GoogleImageSource
from newsclip.sources.naver_image import NaverImageSource
from newsclip.sources.wikimedia import WikimediaSource
from newsclip.sources.youtube_video import YouTubeVideoSource
from newsclip.srt import parse_srt
from studio_core.config import ChannelConfig, Settings
from studio_core.http import make_client
from studio_core.keys import Unavailable
from studio_core.llm import ClaudeLLM

log = logging.getLogger("newsclip")


@dataclass
class RunReport:
    """run 결과 요약."""

    scenes: int = 0
    with_asset: int = 0
    downloaded: int = 0
    skipped_sources: dict[str, str] = field(default_factory=dict)
    errors: list[str] = field(default_factory=list)
    xml_errors: list[str] = field(default_factory=list)
    by_source: dict[str, int] = field(default_factory=dict)


def make_sources(settings: Settings, cfg: ChannelConfig, client: httpx.AsyncClient) -> dict[str, Source]:
    """소스 인스턴스(예산·캐시 공유)."""
    budget = Budget(
        settings.data_dir / "api_usage.json",
        {
            "google_image": cfg.budget.google_cse_per_day,
            "youtube_video": cfg.budget.youtube_search_per_day,
            "naver_image": cfg.budget.naver_per_day,
        },
    )
    cache = SearchCache(settings.data_dir / "search_cache.json")
    return {
        "google_image": GoogleImageSource(client, budget, cache),
        "naver_image": NaverImageSource(client, budget, cache),
        "wikimedia": WikimediaSource(client, budget, cache),
        "youtube_video": YouTubeVideoSource(client, budget, cache, blocked=cfg.videos.blocked_channels),
    }


def query_for(scene: Scene, source: str) -> str:
    """소스별 검색어(Wikimedia는 영어, 나머지는 한국어 상위 2개)."""
    if source == "wikimedia":
        return " ".join(scene.keywords_en[:2]) or " ".join(scene.keywords_ko[:2])
    return " ".join(scene.keywords_ko[:2])


async def gather_candidates(
    scene: Scene, sources: dict[str, Source], report: RunReport, want: int
) -> list[Asset]:
    """우선순위대로 검색해 후보를 모은다(충분히 모이면 멈춤, 키 없는 소스는 경고 후 건너뜀)."""
    out: list[Asset] = []
    seen: set[str] = set()
    for name in route_for(scene):
        if len(out) >= want:
            break
        src = sources[name]
        try:
            found = await src.search(query_for(scene, name), scene.kind)
        except Unavailable as e:
            if name not in report.skipped_sources:
                log.warning("[%s] 건너뜀: %s", name, e)
            report.skipped_sources[name] = str(e)
            continue
        except Exception as e:  # 소스 실패 격리
            report.errors.append(f"{scene.id} {name}: {type(e).__name__}: {e}")
            continue
        for a in found:
            if a.id not in seen:
                seen.add(a.id)
                out.append(a)
    return out


async def run_project(
    settings: Settings,
    cfg: ChannelConfig,
    project: Path,
    *,
    per_scene: int = 3,
    video_max_sec: float = 20.0,
    use_clip: bool = False,
    llm: ClaudeLLM | None = None,
    fps: int = 30,
    size: tuple[int, int] = (1920, 1080),
) -> tuple[SceneSet, RunReport]:
    """newsclip run 전체."""
    script = (project / "script.txt").read_text(encoding="utf-8")
    cues = parse_srt((project / "narration.srt").read_text(encoding="utf-8"))
    scenes = build_scenes(cues, script, llm)
    sset = SceneSet(
        project=project.name,
        fps=fps,
        width=size[0],
        height=size[1],
        scenes=scenes,
        llm_mode="claude" if llm else "offline",
    )
    report = RunReport(scenes=len(scenes))
    try:
        GoogleImageSource.check_ready(None)  # type: ignore[arg-type]
    except Unavailable as e:
        report.skipped_sources["google_image"] = f"{e} → 네이버 이미지가 1순위"
    clip_scorer: Callable | None = clip_scorer_or_none() if use_clip else None
    if use_clip and clip_scorer is None:
        report.errors.append("--clip: open_clip_torch 미설치(uv sync --extra clip) — 유사도 가산 생략")
    assets_dir = project / "assets"
    registry = DedupeRegistry()
    sem = asyncio.Semaphore(5)
    async with make_client() as client:
        sources = make_sources(settings, cfg, client)
        found = await asyncio.gather(
            *(gather_candidates(sc, sources, report, want=per_scene * 3) for sc in scenes)
        )
        prev_key: str | None = None
        for sc, cands in zip(scenes, found, strict=True):
            ranked = rank(sc, cands, cfg, prev_key)
            for a in ranked:
                a.attribution_text = attribution_text(a)
            sc.candidates = ranked
            if ranked:
                prev_key = asset_key(ranked[0])

        async def fetch(sc: Scene) -> None:
            top = sc.candidates[:per_scene]
            jobs = []
            for r, a in enumerate(top, start=1):
                if a.media == "video":
                    dest = assets_dir / f"{sc.id}_{r}.mp4"
                    jobs.append(download_video(a, dest, sc.duration, video_max_sec, registry, sem))
                else:
                    dest = assets_dir / f"{sc.id}_{r}.jpg"
                    jobs.append(download_image(client, a, dest, registry, size, sem))
            results = await asyncio.gather(*jobs)
            got = {a.id: a for a in results if a is not None}
            sc.candidates = [got.get(a.id, a) for a in sc.candidates]
            sc.chosen = next((a for a in sc.candidates if a.local_path), None)

        await asyncio.gather(*(fetch(sc) for sc in scenes))
    if clip_scorer is not None:  # 다운로드 후 로컬 이미지로 유사도 재정렬
        for sc in scenes:
            sc.candidates = rank(sc, sc.candidates, cfg, None, clip_scorer)
            sc.chosen = next((a for a in sc.candidates if a.local_path), None)
    for sc in scenes:
        for a in sc.candidates:
            if a.local_path:
                report.downloaded += 1
        if sc.chosen:
            report.with_asset += 1
            report.by_source[sc.chosen.source] = report.by_source.get(sc.chosen.source, 0) + 1
    save_scenes(project, sset)
    write_assets_json(project / "assets.json", sset)
    audio = project / "narration.wav"
    report.xml_errors = write_xml(
        project,
        sset,
        fps=fps,
        size=size,
        audio=audio if audio.exists() else None,
        video_max_sec=video_max_sec,
    )
    return sset, report


def save_scenes(project: Path, sset: SceneSet) -> None:
    """scenes.json 저장."""
    (project / "scenes.json").write_text(
        json.dumps(sset.model_dump(mode="json"), ensure_ascii=False, indent=2), encoding="utf-8"
    )


def load_scenes(project: Path) -> SceneSet:
    """scenes.json 읽기."""
    return SceneSet.model_validate_json((project / "scenes.json").read_text(encoding="utf-8"))


def write_xml(
    project: Path,
    sset: SceneSet,
    *,
    fps: int = 30,
    size: tuple[int, int] = (1920, 1080),
    zoom: float = 1.04,
    audio: Path | None = None,
    video_max_sec: float = 20.0,
) -> list[str]:
    """timeline.xml 생성 + 재파싱 검사. 오류 목록 반환. narration.srt가 있으면 V3에 대본 자막."""
    srt = project / "narration.srt"
    subtitles = parse_srt(srt.read_text(encoding="utf-8")) if srt.exists() else None
    xml = build_xmeml(
        sset,
        project,
        fps=fps,
        size=size,
        zoom=zoom,
        audio=audio,
        video_max_sec=video_max_sec,
        subtitles=subtitles,
    )
    (project / "timeline.xml").write_text(xml, encoding="utf-8")
    return validate_xmeml(xml)


async def search_debug(
    settings: Settings, cfg: ChannelConfig, query: str, kind: str
) -> dict[str, list[Asset] | str]:
    """newsclip search: 소스별 상위 5개(캐시·예산 무시)."""
    out: dict[str, list[Asset] | str] = {}
    async with make_client() as client:
        sources = make_sources(settings, cfg, client)
        for name, src in sources.items():
            src.cache = None
            src.budget = None
            try:
                found = await src.search(query, kind)  # type: ignore[arg-type]
            except Unavailable as e:
                out[name] = f"건너뜀: {e}"
                continue
            except Exception as e:  # 디버그 출력용
                out[name] = f"실패: {type(e).__name__}: {e}"
                continue
            probe = Scene(
                id="q",
                start_sec=0,
                end_sec=5,
                text=query,
                topic=query,
                keywords_ko=query.split()[:3],
                keywords_en=[],
                kind=kind,
            )  # type: ignore[arg-type]
            out[name] = rank(probe, found, cfg)[:5]
    return out
