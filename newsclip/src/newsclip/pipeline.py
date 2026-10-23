"""newsclip run / xml / search 구현."""

from __future__ import annotations

import asyncio
import hashlib
import json
import logging
from collections.abc import Callable
from dataclasses import dataclass, field
from pathlib import Path

import httpx

from newsclip.download import (
    DedupeRegistry,
    doc_like,
    download_image,
    download_stock_video,
    download_video,
    text_heavy,
)
from newsclip.fcpxml import build_xmeml, validate_xmeml
from newsclip.license_log import STOCK_SOURCES, attribution_text, write_assets_json, write_credits
from newsclip.models import Asset, Scene, SceneSet, asset_id
from newsclip.rank import asset_key, clip_scorer_or_none, rank, route_for
from newsclip.scenes import build_scenes
from newsclip.slides import Painter, ensure_fonts, fetch_font
from newsclip.sources.base import Budget, SearchCache, Source
from newsclip.sources.google_image import GoogleImageSource
from newsclip.sources.naver_image import NaverImageSource
from newsclip.sources.pexels import PexelsSource
from newsclip.sources.pixabay import PixabaySource
from newsclip.sources.wikimedia import WikimediaSource
from newsclip.sources.youtube_video import YouTubeVideoSource, is_blocked, thumb_video_id, video_channels
from newsclip.srt import parse_srt
from newsclip.vertical import is_vertical, local_font, render_headline, style_of
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
    borrowed: int = 0  # 사진을 못 받아 앞뒤 장면 사진을 이어 쓴 장면 수
    dropped_thumbs: int = 0  # 뺀 유튜브 썸네일 + 받고 보니 영상 썸네일(큰 제목 글씨)이라 뺀 사진 수
    dropped_docs: int = 0  # 받고 보니 문서·표·화면 캡처(흰 바탕 잔글씨)라 뺀 사진 수(뉴스 채널·세로 쇼츠)


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
    if cfg.images.sources:  # 무료 소재 사이트만(건강 채널) — 설정 순서가 곧 찾는 순서
        per_day = {"pexels": cfg.budget.pexels_per_day, "pixabay": cfg.budget.pixabay_per_day}
        made: dict[str, Source] = {}
        for name in cfg.images.sources:
            site, _, media = name.partition("_")
            cls = {"pexels": PexelsSource, "pixabay": PixabaySource}.get(site)
            if cls is None or media not in ("image", "video"):
                raise ValueError(
                    f"모르는 소재 사이트: {name} (pexels_video·pexels_image·pixabay_video·pixabay_image)"
                )
            budget.limits[name] = per_day[site]
            made[name] = cls(client, budget, cache, media=media, orientation=cfg.images.orientation)
        return made
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
        if isinstance(item, dict) and item.get("other_day"):
            sc.other_day = True
        if video:
            sc.kind = "video"
        if per_topic.get(sc.topic, "").strip():
            sc.video_query = per_topic[sc.topic].strip()


def query_for(scene: Scene, source: str) -> str:
    """소스별 검색어. Wikimedia는 영어, 유튜브는 주제 검색어,
    사진은 주제 대표 말 + 그 장면에만 두드러진 말(장면마다 다른 사진이 나오게)."""
    if source == "wikimedia":
        return " ".join(scene.keywords_en[:2]) or " ".join(scene.keywords_ko[:2])
    if source in STOCK_SOURCES:  # 소재 사이트는 영어 검색이 정확하다 — 작업자가 쓴 영어 검색어 그대로
        return scene.query or " ".join(scene.keywords_en[:3]) or " ".join(scene.keywords_ko[:2])
    if source == "youtube_video":
        return topic_query(scene)
    if scene.query:
        return scene.query
    anchor = scene.topic_keywords[:1]
    own = [k for k in scene.keywords_ko if k not in anchor][:1]
    return " ".join([*anchor, *own]) or " ".join(scene.keywords_ko[:2])


# 사람이 나오는 장면인지 가리는 영어 검색어 낱말(소재 사이트 검색어는 영어)
PEOPLE_WORDS = {
    "woman", "women", "man", "men", "senior", "seniors", "elderly", "older", "aged", "people", "person",
    "couple", "family", "doctor", "nurse", "patient", "girl", "boy", "child", "parents", "grandmother",
    "grandfather", "friends", "adult", "mother", "father", "lady", "guy", "worker",
    "pharmacist", "customer", "staff", "wife", "husband", "mom", "dad", "grandma", "grandpa", "teacher", "student",
}  # fmt: skip


def people_query(query: str, hint: str) -> str | None:
    """사람이 나오는 검색어면 hint를 앞에 붙인 검색어(이미 들어 있거나 사람 장면이 아니면 None)."""
    words = query.lower().split()
    if not hint or hint.lower() in words or not PEOPLE_WORDS & set(words):
        return None
    return f"{hint} {query}"


async def gather_candidates(
    scene: Scene,
    sources: dict[str, Source],
    report: RunReport,
    want: int,
    want_videos: int = 0,
    people_hint: str = "",
) -> list[Asset]:
    """우선순위대로 검색해 후보를 모은다(사진이 want장, 영상이 want_videos개 모이면 멈춤, 키 없는 소스는 경고 후 건너뜀).
    무료 소재 사이트만 쓰는 채널은 설정 순서(images.sources)가 곧 찾는 순서다.
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

    def enough() -> bool:
        return images() >= want and sum(1 for a in out if a.media == "video") >= want_videos

    route = [n for n in route_for(scene) if n in sources] or list(sources)
    for name in route:
        if enough():
            break
        q = query_for(scene, name)
        # 사람 장면은 people_hint(예: asian)를 붙여 먼저 찾고, 모자라면 원래 검색어로 채운다
        hinted = people_query(q, people_hint) if name in STOCK_SOURCES else None
        if hinted:
            await search(name, hinted)
            # 이 사이트의 자료(영상 또는 사진)가 hint 검색어로 넉넉하면 원래 검색어 결과는 섞지 않는다
            media = "video" if name.endswith("_video") else "image"
            if sum(1 for a in out if a.media == media) >= (want_videos if media == "video" else want):
                continue
        await search(name, q)
    fallback = topic_query(scene)
    for name in route:
        if enough() or name in (
            "youtube_video",
            "wikimedia",
            *STOCK_SOURCES,
        ):  # 소재 사이트에 한국어 주제어는 헛검색
            continue
        if fallback and fallback != query_for(scene, name) and name not in report.skipped_sources:
            await search(name, fallback)
    return out


async def keep_news_thumbs(
    client: httpx.AsyncClient, found: list[list[Asset]], allowed: list[str], report: RunReport
) -> list[list[Asset]]:
    """사진 후보에 섞인 유튜브 썸네일은 뉴스 공식 채널(allowed) 것만 남긴다. 채널은 YouTube API로 확인하고,
    확인하지 못한 썸네일(키 없음·조회 실패·목록에 없는 채널)은 뺀다. 남긴 썸네일은 출처에 채널 이름을 적는다."""
    ids = {
        a.id: vid for cands in found for a in cands if a.media == "image" and (vid := thumb_video_id(a.url))
    }
    if not ids:
        return found
    channels: dict[str, str] = {}
    if allowed:
        try:
            channels = await video_channels(client, list(ids.values()))
        except Exception as e:  # 키 없음·조회 실패 → 썸네일을 모두 뺀다
            report.errors.append(f"유튜브 썸네일 채널 확인 실패(썸네일은 뺌): {type(e).__name__}: {e}")
    out, dropped = [], 0
    for cands in found:
        keep = []
        for a in cands:
            vid = ids.get(a.id)
            if vid is None:
                keep.append(a)
                continue
            ch = channels.get(vid, "")
            if ch and is_blocked(ch, allowed):  # is_blocked = 목록과 이름이 맞는지(정규화 후 포함)
                keep.append(a.model_copy(update={"author_or_channel": ch, "source_domain": f"YouTube {ch}"}))
            else:
                dropped += 1
        out.append(keep)
    report.dropped_thumbs = dropped
    return out


def _date_label(project_id: str) -> str:
    """'2026-10-13_1338_short' → '10월 13일(화)'."""
    import datetime as _dt

    try:
        d = _dt.date.fromisoformat(project_id[:10])
    except ValueError:
        return ""
    return f"{d.month}월 {d.day}일({'월화수목금토일'[d.weekday()]})"


def render_slides(
    scenes: list[Scene], slides: dict, fonts: dict[str, str], assets_dir: Path, channel: str, project_id: str
) -> int:
    """slides = {"slides": {장면 ID: 슬라이드 내용}, "source": "자료: …"} → 장면마다 그림.
    내용이 없는 장면은 앞 장면 슬라이드를 그대로 쓴다(맨 앞이 비면 첫 슬라이드). 그대로 쓴 장면 수를 돌려준다."""
    specs = slides.get("slides") or {}
    painter = Painter(fonts)
    date = _date_label(project_id)
    source = str(slides.get("source", ""))
    made: dict[str, Asset] = {}
    for sc in scenes:
        spec = specs.get(sc.id)
        if not isinstance(spec, dict):
            continue
        out = painter.render(
            spec, assets_dir / f"{sc.id}_slide.png", channel=channel, date=date, source=source
        )
        made[sc.id] = Asset(
            source="slide",
            id=asset_id("slide", f"{project_id}/{sc.id}"),
            title=str(spec.get("title") or spec.get("label") or spec.get("quote") or sc.topic)[:80],
            url=f"slide:{sc.id}",
            source_page_url="",
            source_domain="",
            download_url="",
            width=1920,
            height=1080,
            local_path=str(out),
            media="image",
            attribution_text="",
        )
    held, last = 0, None
    first = next(iter(made.values()), None)
    for sc in scenes:
        a = made.get(sc.id)
        if a is None:
            a = last or first
            held += a is not None
        if a is not None:
            sc.candidates = [a]
            sc.chosen = a
            last = a
    return held


MAX_TRIES = 8  # 장면마다 받아 볼 사진 후보 수(앞 후보가 막히면 다음으로)
STOCK_VIDEOS = (
    1  # 장면마다 받을 무료 소재 영상 수(나머지 컷은 사진 — 장면이 2~5초라 영상 하나 + 사진 하나면 충분)
)
STOCK_TRIES = 3  # 받아 볼 영상 후보 수


def add_cards(
    scenes: list[Scene],
    specs: dict,
    fonts: dict[str, str],
    assets_dir: Path,
    size: tuple[int, int],
    style: str,
    project_id: str,
) -> int:
    """수치 카드(newsclip.cards)를 그 장면의 첫 컷으로 넣는다 — 이어서 장면의 사진·영상이 나온다. 넣은 카드 수."""
    from newsclip.cards import render_cards

    made = render_cards(specs, fonts, assets_dir, size, style)
    for sc in scenes:
        if sc.id not in made:
            continue
        path, focus = made[sc.id]
        spec = specs.get(sc.id) or {}
        card = Asset(
            source="card",
            id=asset_id("card", f"{project_id}/{sc.id}"),
            title=str(spec.get("title") or spec.get("label") or spec.get("kicker") or sc.topic)[:80],
            url=f"card:{sc.id}",
            width=size[0],
            height=size[1],
            local_path=str(path),
            media="image",
            focus=focus,
        )
        sc.candidates = [card, *sc.candidates]
        sc.chosen = card
    return len(made)


def borrow_empty(scenes: list[Scene]) -> int:
    """사진을 하나도 못 받은 장면은 바로 앞(없으면 뒤) 장면 사진을 이어 쓴다 — timeline에 빈 곳(검은 화면)이
    생기지 않게. 이어 쓴 장면 수를 돌려준다(Scene.borrowed에 빌려 온 장면 ID)."""
    n = 0
    for i, sc in enumerate(scenes):
        if sc.chosen is not None:
            continue
        near = [*reversed(scenes[:i]), *scenes[i + 1 :]]
        src = next((x for x in near if x.chosen is not None and not x.borrowed), None)
        if src is not None:
            sc.chosen = src.chosen
            sc.borrowed = src.id
            n += 1
    return n


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
    slides: dict | None = None,
    headline: str = "",
    style: str = "default",
) -> tuple[SceneSet, RunReport]:
    """newsclip run 전체. videos=False면 영상은 받지 않고 후보(링크)로만 남긴다(유튜브 받기가 막힌 곳).
    slides(증시 채널)가 있으면 사진을 찾지 않고 장면마다 슬라이드 그림을 그려 쓴다(newsclip.slides).
    size가 세로(1080x1920, 쇼츠)면 사진을 세로 화면에 맞춰 넣고, headline(상단 제목)을 맨 위 트랙에 깐다."""
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
        if not cfg.images.sources:  # 소재 사이트만 쓰는 채널은 구글을 찾지 않는다
            GoogleImageSource.check_ready(None)  # type: ignore[arg-type]
    except Unavailable as e:
        report.skipped_sources["google_image"] = f"{e} → 네이버 이미지가 1순위"
    clip_scorer: Callable | None = clip_scorer_or_none() if use_clip else None
    if use_clip and clip_scorer is None:
        report.errors.append("--clip: open_clip_torch 미설치(uv sync --extra clip) — 유사도 가산 생략")
    assets_dir = project / "assets"
    registry = DedupeRegistry()
    sem = asyncio.Semaphore(5)
    head_png: Path | None = None
    fonts: dict[str, str] = {}
    if slides is not None or (headline.strip() and is_vertical(size)):
        async with make_client() as client:
            fonts = await ensure_fonts(client, settings.data_dir / "fonts")
    if headline.strip() and is_vertical(size):
        st = style_of(style)
        head_font = local_font(st) or fonts.get("ExtraBold")  # 정치는푸르다: 210 매머드빵(패키지에 둠)
        if st.font_url and not local_font(
            st
        ):  # 채널 디자인 폰트(자유뉴스365: 블랙한산스) — 못 받으면 Pretendard
            async with make_client() as client:
                head_font = (
                    await fetch_font(client, settings.data_dir / "fonts", st.font_file, st.font_url)
                    or head_font
                )
        digest = hashlib.sha1(f"{st.name}:{headline.strip()}".encode()).hexdigest()[:8]
        head_png = render_headline(
            headline.strip(), project / "overlays" / f"headline_{digest}.png", size, head_font, style=st.name
        )
    if slides is not None:
        report.skipped_sources.clear()
        report.borrowed = render_slides(scenes, slides, fonts, assets_dir, cfg.channel.name, project.name)
        return _finish(project, sset, report, cfg, fps, size, video_max_sec, None, head_png, style)
    async with make_client() as client:
        sources = make_sources(settings, cfg, client)
        stock_video = any(n.endswith("_video") and n in STOCK_SOURCES for n in sources)
        found = await asyncio.gather(
            *(
                gather_candidates(
                    sc,
                    sources,
                    report,
                    want=per_scene * 3,
                    want_videos=3 if stock_video else 0,
                    people_hint=cfg.images.people_hint,
                )
                for sc in scenes
            )
        )
        found = await keep_news_thumbs(client, found, cfg.images.youtube_thumb_channels, report)
        prev_key: str | None = None
        used: dict[str, int] = {}  # 앞 장면들이 이미 고른 자료(같은 사진 되풀이 감점)
        story = " ".join(sc.text for sc in scenes)  # 사진 제목의 인물이 대본에 있나
        # 작업자가 사진을 보고 뺀 것(queries.json "drop": 사진 주소 또는 제목 일부) — 다시 돌리면 다음 후보로
        drop = tuple(str(d) for d in (queries or {}).get("drop") or [] if str(d).strip())
        for sc, cands in zip(scenes, found, strict=True):
            ranked = rank(sc, cands, cfg, prev_key, used=used, story=story, drop=drop)
            for a in ranked:
                a.attribution_text = attribution_text(a)
            sc.candidates = ranked
            if ranked:
                prev_key = asset_key(ranked[0])
            for media in ("image", "video"):  # 장면의 첫 사진·첫 영상 후보를 '썼음'으로
                top = next((a for a in ranked if a.media == media), None)
                if top is not None:
                    used[top.id] = used.get(top.id, 0) + 1

        news = not cfg.images.sources  # 뉴스 사진을 찾는 채널만 영상 썸네일을 거른다(널스윤서는 소재 사이트)

        async def fetch(sc: Scene) -> None:
            """순위대로 받되, 받기에 실패하면(막힌 주소·문서 파일 등) 다음 후보로 — 사진 per_scene장이 모이거나
            후보 MAX_TRIES개를 써 볼 때까지. 영상은 videos일 때 앞쪽 후보만 받아 본다."""
            got: dict[str, Asset] = {}
            n = 0

            def dest(ext: str) -> Path:
                nonlocal n
                n += 1
                return assets_dir / f"{sc.id}_{n}.{ext}"

            # 무료 소재 영상(Pexels·Pixabay): 장면마다 STOCK_VIDEOS개를 순위대로 받아 본다(실패하면 다음 후보)
            stock = [a for a in sc.candidates if a.media == "video" and a.source in STOCK_SOURCES]
            for a in stock[:STOCK_TRIES]:
                if sum(1 for x in got.values() if x.media == "video") >= STOCK_VIDEOS:
                    break
                res = await download_stock_video(
                    client, a, dest("mp4"), sc.duration, registry, size, sem, style
                )
                if res is not None:
                    got[res.id] = res
            if videos:
                vids = [
                    a
                    for a in sc.candidates[:per_scene]
                    if a.media == "video" and a.source not in STOCK_SOURCES
                ]
                res = await asyncio.gather(
                    *(download_video(a, dest("mp4"), sc.duration, video_max_sec, registry, sem) for a in vids)
                )
                got.update({a.id: a for a in res if a is not None})
            want = max(1, per_scene - len(got))
            images = [a for a in sc.candidates if a.media == "image"][:MAX_TRIES]
            ok, i = 0, 0
            while ok < want and i < len(images):
                chunk = images[i : i + want - ok]
                i += len(chunk)
                res = await asyncio.gather(
                    *(download_image(client, a, dest("jpg"), registry, size, sem, style) for a in chunk)
                )
                for a in res:
                    if a is None:
                        continue
                    if news and text_heavy(a):  # 큰 제목 글씨를 박은 영상 썸네일은 쓰지 않고 다음 후보로
                        report.dropped_thumbs += 1
                        continue
                    if (news or size[1] > size[0]) and doc_like(
                        a
                    ):  # 문서·표·화면 캡처는 화면에서 글씨가 안 읽힌다
                        report.dropped_docs += 1
                        continue
                    got[a.id] = a
                    ok += 1
            sc.candidates = [got.get(a.id, a) for a in sc.candidates]
            sc.chosen = next((a for a in sc.candidates if a.local_path), None)

        await asyncio.gather(*(fetch(sc) for sc in scenes))
        if queries and isinstance(queries.get("cards"), dict) and queries["cards"]:
            if not fonts:
                fonts = await ensure_fonts(client, settings.data_dir / "fonts")
            add_cards(scenes, queries["cards"], fonts, assets_dir, size, style, project.name)
        report.borrowed = borrow_empty(scenes)
    return _finish(project, sset, report, cfg, fps, size, video_max_sec, clip_scorer, head_png, style)


def _finish(
    project: Path,
    sset: SceneSet,
    report: RunReport,
    cfg: ChannelConfig,
    fps: int,
    size: tuple[int, int],
    video_max_sec: float,
    clip_scorer: Callable | None,
    headline: Path | None = None,
    style: str = "default",
) -> tuple[SceneSet, RunReport]:
    """집계·scenes.json·assets.json·timeline.xml."""
    scenes = sset.scenes
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
    write_credits(project / "credits.txt", sset)
    audio = project / "narration.wav"
    report.xml_errors = write_xml(
        project,
        sset,
        fps=fps,
        size=size,
        audio=audio if audio.exists() else None,
        video_max_sec=video_max_sec,
        options=cfg.timeline,
        headline=headline,
        style=style,
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
    headline: Path | None = None,
    style: str = "default",
    **extra,  # noqa: ANN003 - 롱폼 편집 트랙(build_xmeml의 overlays·sfx_list·bgm·markers)
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
        headline=headline,
        style=style,
        **extra,
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
