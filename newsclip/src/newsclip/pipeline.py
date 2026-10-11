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
from studio_core.config import ChannelConfig, Settings, TimelineSection, find_root, load_channel
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


def topic_query(scene: Scene) -> str:
    """같은 주제 장면이 함께 쓰는 검색어(영상 검색·사진 보충, 캐시로 한 번만 부른다)."""
    return scene.video_query or " ".join(scene.topic_keywords[:2]) or " ".join(scene.keywords_ko[:2])


def apply_queries(scenes: list[Scene], queries: dict | None) -> None:
    """장면별 검색어 적용. queries = {"scenes": {장면 ID: 검색어 또는 {"q": 검색어, "video": true}},
    "videos": {주제: 유튜브 검색어}} — 대사를 읽고 '화면에 무엇이 보여야 하나'를 정한 검색어."""
    if not queries:
        return
    per_scene, per_topic = queries.get("scenes") or {}, queries.get("videos") or {}
    for sc in scenes:
        item = per_scene.get(sc.id)
        q, video = (
            (item.get("q", ""), bool(item.get("video"))) if isinstance(item, dict) else (item or "", False)
        )
        if q.strip():
            sc.query = q.strip()
            words = [w for w in sc.query.split() if len(w) >= 2]
            sc.keywords_ko = list(dict.fromkeys([*words, *sc.keywords_ko]))[:3]
        if video:
            sc.kind = "video"
        if per_topic.get(sc.topic, "").strip():
            sc.video_query = per_topic[sc.topic].strip()


def query_for(scene: Scene, source: str) -> str:
    """소스별 검색어. Wikimedia는 영어, 유튜브는 주제 검색어,
    사진은 주제 대표 말 + 그 장면에만 두드러진 말(장면마다 다른 사진이 나오게)."""
    if source == "wikimedia":
        return " ".join(scene.keywords_en[:2]) or " ".join(scene.keywords_ko[:2])
    if source == "youtube_video":
        return topic_query(scene)
    if scene.query:
        return scene.query
    anchor = scene.topic_keywords[:1]
    own = [k for k in scene.keywords_ko if k not in anchor][:1]
    return " ".join([*anchor, *own]) or " ".join(scene.keywords_ko[:2])


async def gather_candidates(
    scene: Scene, sources: dict[str, Source], report: RunReport, want: int
) -> list[Asset]:
    """우선순위대로 검색해 후보를 모은다(사진이 충분히 모이면 멈춤, 키 없는 소스는 경고 후 건너뜀).
    영상 후보는 사진 수에 세지 않아서 영상 장면에도 사진이 같이 모인다. 장면 검색어로 사진이 모자라면
    주제 검색어로 한 번 더 찾는다."""
    out: list[Asset] = []
    seen: set[str] = set()

    async def search(name: str, query: str) -> None:
        src = sources[name]
        try:
            found = await src.search(query, scene.kind)
        except Unavailable as e:
            if name not in report.skipped_sources:
                log.warning("[%s] 건너뜀: %s", name, e)
            report.skipped_sources[name] = str(e)
            return
        except Exception as e:  # 소스 실패 격리
            report.errors.append(f"{scene.id} {name}: {type(e).__name__}: {e}")
            return
        for a in found:
            if a.id not in seen:
                seen.add(a.id)
                out.append(a)

    def images() -> int:
        return sum(1 for a in out if a.media == "image")

    route = route_for(scene)
    for name in route:
        if images() >= want:
            break
        await search(name, query_for(scene, name))
    fallback = topic_query(scene)
    for name in route:
        if images() >= want or name in ("youtube_video", "wikimedia"):
            continue
        if fallback and fallback != query_for(scene, name) and name not in report.skipped_sources:
            await search(name, fallback)
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
    videos: bool = True,
    queries: dict | None = None,
    phrases: bool = False,
) -> tuple[SceneSet, RunReport]:
    """newsclip run 전체. videos=False면 영상은 받지 않고 후보(링크)로만 남긴다(유튜브 받기가 막힌 곳)."""
    script = (project / "script.txt").read_text(encoding="utf-8")
    cues = parse_srt((project / "narration.srt").read_text(encoding="utf-8"))
    scenes = build_scenes(cues, script, llm, phrases=phrases)
    apply_queries(scenes, queries)
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
        used: dict[str, int] = {}  # 앞 장면들이 이미 고른 자료(같은 사진 되풀이 감점)
        for sc, cands in zip(scenes, found, strict=True):
            ranked = rank(sc, cands, cfg, prev_key, used=used)
            for a in ranked:
                a.attribution_text = attribution_text(a)
            sc.candidates = ranked
            if ranked:
                prev_key = asset_key(ranked[0])
            for media in ("image", "video"):  # 장면의 첫 사진·첫 영상 후보를 '썼음'으로
                top = next((a for a in ranked if a.media == media), None)
                if top is not None:
                    used[top.id] = used.get(top.id, 0) + 1

        async def fetch(sc: Scene) -> None:
            pool = sc.candidates if videos else [a for a in sc.candidates if a.media == "image"]
            top = pool[:per_scene]
            if not any(a.media == "image" for a in top):  # 영상을 못 받아도 사진 하나는 남게
                top += [a for a in pool if a.media == "image"][:1]
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
    for name in report.by_source:  # 일부 장면에서만 실패한 소스는 '못 쓴 검색'에서 뺀다
        report.skipped_sources.pop(name, None)
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
        options=cfg.timeline,
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


def timeline_options(project: Path) -> TimelineSection:
    """channel.yaml의 timeline(자막·출처 표시 여부). 없으면 기본값(자막 끔, 출처 켬)."""
    try:
        return load_channel(Settings(root=find_root(project))).timeline
    except Exception:  # 설정 파일이 깨졌어도 XML은 만든다
        return TimelineSection()


def write_xml(
    project: Path,
    sset: SceneSet,
    *,
    fps: int = 30,
    size: tuple[int, int] = (1920, 1080),
    zoom: float = 1.04,
    audio: Path | None = None,
    video_max_sec: float = 20.0,
    options: TimelineSection | None = None,
) -> list[str]:
    """timeline.xml 생성 + 재파싱 검사. 오류 목록 반환.

    자막은 기본으로 넣지 않는다(timeline.subtitles=true일 때만 narration.srt로 대본 자막 트랙).
    출처 표시(V2)는 timeline.attribution(기본 켬)을 따른다.
    """
    opts = options or timeline_options(project)
    srt = project / "narration.srt"
    subtitles = parse_srt(srt.read_text(encoding="utf-8")) if opts.subtitles and srt.exists() else None
    xml = build_xmeml(
        sset,
        project,
        fps=fps,
        size=size,
        zoom=zoom,
        audio=audio,
        video_max_sec=video_max_sec,
        subtitles=subtitles,
        attribution=opts.attribution,
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
