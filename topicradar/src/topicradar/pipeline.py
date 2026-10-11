"""명령 구현(CLI·화면은 얇게, 로직은 여기)."""

from __future__ import annotations

import asyncio
import json
import re
import statistics
from collections.abc import Callable
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from pathlib import Path

from studio_core.config import ChannelConfig, Settings, append_past_title, now_kst
from studio_core.http import make_client
from studio_core.llm import ClaudeLLM
from topicradar import handoff
from topicradar.board import BoardContext, build_board, make_plan
from topicradar.collectors.base import Collector, CollectorDisabled, CollectReport, collect_all
from topicradar.collectors.community import CommunityCollector
from topicradar.collectors.naver_news import NaverNewsCollector
from topicradar.collectors.overseas import OverseasCollector
from topicradar.collectors.rss import RssCollector
from topicradar.collectors.schedule import ManualScheduleCollector
from topicradar.collectors.youtube import YouTubeCollector, video_stats
from topicradar.db import Store, UploadRow
from topicradar.history import history_report
from topicradar.models import Board, Signal, VideoPlan
from topicradar.report import notify_slack, render_report
from topicradar.script import validate_script, write_script


def open_store(settings: Settings) -> Store:
    """data/studio.db."""
    return Store(settings.db_path)


def needs_detail(store: Store) -> Callable[[Signal], bool]:
    """글 상세를 (다시) 읽을지: 처음 보는 글이거나, 읽은 뒤 댓글이 5개·30% 이상 늘었으면."""

    def check(sig: Signal) -> bool:
        old = store.get_signal(sig.id)
        if old is None or not old.metric.get("detail_at"):
            return True
        before = int(old.metric.get("detail_comments", 0))
        return int(sig.metric.get("comments", 0)) - before >= max(5, before * 0.3)

    return check


def build_collectors(
    settings: Settings, cfg: ChannelConfig, client: object, store: Store | None = None
) -> list[Collector]:
    """설정에 따른 수집기 목록."""
    domestic = [f for f in cfg.sources.rss if f.kind != "overseas"]
    overseas = [*cfg.sources.overseas_rss, *(f for f in cfg.sources.rss if f.kind == "overseas")]
    cols: list[Collector] = [
        RssCollector(domestic, client),  # type: ignore[arg-type]
        OverseasCollector(overseas, client),  # type: ignore[arg-type]
        ManualScheduleCollector(settings.data_dir / "schedule"),
    ]
    if cfg.sources.naver_queries:
        cols.append(NaverNewsCollector(cfg.sources.naver_queries, client))  # type: ignore[arg-type]
    if cfg.communities:
        cols.append(
            CommunityCollector(
                cfg.communities,
                client,  # type: ignore[arg-type]
                cfg.clustering.generic_tokens,
                need_detail=needs_detail(store) if store is not None else None,
            )
        )
    cols.append(YouTubeCollector(cfg.channel.competitors, client))  # type: ignore[arg-type]
    return cols


async def run_collect(
    settings: Settings, cfg: ChannelConfig, store: Store, since: datetime | None
) -> CollectReport:
    """collect: 수집 → 저장."""
    if since is None:
        last = store.get_meta("last_collect")
        since = datetime.fromisoformat(last) - timedelta(hours=1) if last else now_kst() - timedelta(hours=24)
    async with make_client() as client:
        report = await collect_all(build_collectors(settings, cfg, client, store), since)
        # 2단계: 커뮤니티에서 뜨는 화제로 뉴스 검색어를 만들어 근거 기사를 확보한다.
        queries = derive_queries(
            [s for s in report.signals if s.kind == "community"],
            cfg.board.derived_queries,
            set(cfg.clustering.generic_tokens),
            exclude=set(cfg.sources.naver_queries),
        )
        if queries:
            extra = await collect_all([NaverNewsCollector(queries, client)], since)  # type: ignore[arg-type]
            known = {s.id for s in report.signals}
            report.signals += [s for s in extra.signals if s.id not in known]
            report.counts["naver_news(커뮤니티 화제)"] = extra.counts.get("naver_news", 0)
            report.skipped.update({f"{k}(커뮤니티 화제)": v for k, v in extra.skipped.items()})
            report.errors.update({f"{k}(커뮤니티 화제)": v for k, v in extra.errors.items()})
            report.derived_queries = queries
    new = store.upsert_signals(report.signals)
    report.counts["_new"] = new
    store.set_meta("last_collect", now_kst().isoformat())
    return report


def derive_queries(
    community: list[Signal], n: int, generic: set[str], exclude: set[str] | None = None
) -> list[str]:
    """커뮤니티 제목에서 많이 겹치는 핵심어 두 개씩 묶어 뉴스 검색어를 만든다.

    첫 단어는 여러 글에 겹치는 고유한 말(정당명·'재판' 같은 일반어 제외), 둘째 단어는 일반어도 허용한다
    ("정치자금 재판"). 같은 두 단어는 순서가 달라도 한 검색어로 친다. 댓글이 많은 글일수록 가중한다.
    """
    from collections import Counter

    from studio_core.text import tokenize
    from topicradar.cluster import GENERIC_TOKENS

    if not community or n <= 0:
        return []
    stop = GENERIC_TOKENS | generic

    def nounish(t: str) -> bool:  # 어미·감탄 조각("되면", "했네요", "ㅋㅋ")은 검색어로 쓰지 않는다
        return not re.search(r"(요|다|면|네|죠|까|고|서|데|만|래|ㅋ+|ㅎ+)$", t)

    titles = [
        [t for t in dict.fromkeys(tokenize(s.title)) if t not in generic and len(t) >= 2 and nounish(t)]
        for s in community
    ]
    df: Counter[str] = Counter(t for toks in titles for t in toks)
    q: Counter[frozenset[str]] = Counter()
    text: dict[frozenset[str], str] = {}
    for s, toks in zip(community, titles, strict=True):
        top = sorted(toks, key=lambda t: (t in stop, -df[t], toks.index(t)))[:2]
        if len(top) < 2 or top[0] in stop or df[top[0]] < 2:
            continue
        key = frozenset(top)
        text.setdefault(key, " ".join(sorted(top, key=toks.index)))
        q[key] += 1 + int(s.metric.get("comments", 0)) // 20
    picked = [text[k] for k, _ in q.most_common() if text[k] not in (exclude or set())]
    return picked[:n]


@dataclass
class ProbeResult:
    """probe 결과(게시판 하나)."""

    name: str
    url: str
    allowed: bool
    posts: list = field(default_factory=list)
    kept: int = 0
    error: str = ""
    detail_url: str = ""
    body_chars: int = 0
    comments_found: int = 0
    comment_votes: int = 0  # 공감 수를 읽은 댓글 수
    post_votes: int | None = None
    detail_error: str = ""
    comment_samples: list = field(default_factory=list)
    board_hints: list = field(default_factory=list)  # 글을 못 찾았을 때 페이지의 정치·시사 게시판 링크


async def probe_communities(cfg: ChannelConfig, name: str | None = None) -> list[ProbeResult]:
    """설정된 게시판 목록 1쪽 + 댓글 가장 많은 글 하나의 상세를 읽어 패턴이 맞는지 보여 준다(저장하지 않음)."""
    from topicradar.collectors.community import (
        RobotsCache,
        board_hints,
        decode_html,
        explain_fetch_error,
        is_political,
        parse_board,
        parse_post,
    )

    out: list[ProbeResult] = []
    async with make_client() as client:
        robots = RobotsCache(client)
        for spec in cfg.communities:
            if name and spec.name != name:
                continue
            url = spec.page_url(1)
            res = ProbeResult(spec.name, url, await robots.allowed(url))
            if res.allowed:
                try:
                    resp = await client.get(url)
                    resp.raise_for_status()
                    page = decode_html(resp)
                    posts = parse_board(page, spec, url, now_kst())
                    res.posts = posts
                    if not posts:
                        res.board_hints = board_hints(page, url)
                    kept = [
                        p
                        for p in posts
                        if not spec.politics_filter or is_political(p.title, cfg.clustering.generic_tokens)
                    ]
                    res.kept = len(kept)
                    target = max(kept or posts, key=lambda p: p.comments, default=None)
                    if target is not None and await robots.allowed(target.url):
                        res.detail_url = target.url
                        try:
                            dresp = await client.get(target.url)
                            dresp.raise_for_status()
                            d = parse_post(decode_html(dresp), spec)
                            res.body_chars = len(d.body)
                            res.comments_found = len(d.comments)
                            res.comment_votes = sum(1 for c in d.comments if c.votes)
                            res.post_votes = d.votes
                            res.comment_samples = [(c.votes, c.text[:60]) for c in d.comments[:3]]
                        except Exception as e:  # 진단용
                            res.detail_error = explain_fetch_error(e)
                except Exception as e:  # 진단용
                    res.error = explain_fetch_error(e)
            out.append(res)
    return out


def project_dir(settings: Settings, plan_id: str) -> Path:
    """projects/<영상 ID>/."""
    d = settings.projects_dir / plan_id
    d.mkdir(parents=True, exist_ok=True)
    return d


PROJECT_RE = re.compile(r"^\d{4}-\d{2}-\d{2}_\d{4}(-\d+)?_(long|short)$")


def new_video_id(settings: Settings, store: Store, fmt: str, now: datetime) -> str:
    """'2026-10-11_1432_long'(같은 분에 또 만들면 '_1432-2_long')."""
    base = f"{now:%Y-%m-%d_%H%M}"
    n = 1
    while True:
        vid = f"{base}_{fmt}" if n == 1 else f"{base}-{n}_{fmt}"
        if not (settings.projects_dir / vid).exists() and store.get_video(vid) is None:
            return vid
        n += 1


@dataclass
class VideoResult:
    """영상 만들기 결과."""

    plan: VideoPlan
    project: Path
    warnings: list[str]


def write_project(
    settings: Settings,
    store: Store,
    plan: VideoPlan,
    llm: ClaudeLLM | None,
    tone: str = "확신형+입담형",
    script: str | None = None,
) -> VideoResult:
    """대본 생성·점검 후 report.md / script.txt / plan.json 저장. script: 바깥(대본실)에서 쓴 대본."""
    proj = project_dir(settings, plan.id)
    if script is None:
        script, plan.script_mode, plan.script_note = write_script(plan, llm, tone)
    else:
        plan.script_mode, plan.script_note = "claude", handoff.SCRIPT_NOTE
    warnings = validate_script(script, plan)
    script_path = proj / "script.txt"
    script_path.write_text(script, encoding="utf-8")
    rel = script_path.relative_to(settings.root) if script_path.is_relative_to(settings.root) else script_path
    (proj / "report.md").write_text(render_report(plan, rel, warnings), encoding="utf-8")
    (proj / "plan.json").write_text(
        json.dumps(plan.model_dump(mode="json"), ensure_ascii=False, indent=2), encoding="utf-8"
    )
    store.save_video(plan, str(proj))
    return VideoResult(plan, proj, warnings)


def run_board(cfg: ChannelConfig, store: Store, llm: ClaudeLLM | None, now: datetime | None = None) -> Board:
    """주제판: 지금 커뮤니티에서 뜨는 주제 + 커뮤니티 의견 + 10분 묶음 추천."""
    return build_board(BoardContext(store=store, cfg=cfg, llm=llm), now or now_kst())


def run_video(
    settings: Settings,
    cfg: ChannelConfig,
    store: Store,
    board_id: str,
    topic_ids: list[str],
    fmt: str,
    llm: ClaudeLLM | None,
    now: datetime | None = None,
) -> VideoResult:
    """판에서 고른 주제로 영상 계획 → 대본 + 리포트. 고른 주제에는 이 영상을 '다룸'으로 표시한다."""
    board = store.get_board(board_id)
    if board is None:
        raise FileNotFoundError(f"주제판 {board_id} 없음 — 주제판을 새로 만들어 주세요")
    now = now or now_kst()
    ctx = BoardContext(store=store, cfg=cfg, llm=llm)
    plan = make_plan(ctx, board, topic_ids, fmt, new_video_id(settings, store, fmt, now), now)
    res = write_project(settings, store, plan, llm, cfg.script.tone)
    for it in plan.items:
        store.mark_covered(it.topic.id, plan.id)
    return res


def run_export(cfg: ChannelConfig, store: Store, board_id: str | None = None) -> tuple[dict, str]:
    """주제판 → 대본실용 파일(내용, 파일 이름). board_id가 없으면 최신 판."""
    board = store.get_board(board_id) if board_id else store.latest_board()
    if board is None:
        raise FileNotFoundError("주제판이 없습니다 — 먼저 수집하고 주제판을 만들어 주세요")
    return handoff.export_board(cfg, store, board), handoff.export_filename(board)


def run_import(
    settings: Settings,
    cfg: ChannelConfig,
    store: Store,
    data: dict | str | bytes,
    now: datetime | None = None,
) -> VideoResult:
    """대본실에서 저장한 파일 → 영상 프로젝트. 고른 주제에는 이 영상을 '다룸'으로 표시한다."""
    sf = handoff.parse_script_file(data)
    board = store.get_board(sf.board_id)
    if board is None:
        raise FileNotFoundError(
            f"주제판 {sf.board_id}이 이 studio에 없습니다 — 같은 컴퓨터에서 내보낸 파일인지 확인해 주세요"
        )
    now = now or now_kst()
    opinions = handoff.imported_opinions(board, sf)
    plan = make_plan(
        BoardContext(store=store, cfg=cfg, llm=None),
        board,
        sf.topic_ids,
        sf.format,
        new_video_id(settings, store, sf.format, now),
        now,
        opinions=opinions,
        titles=[t.strip() for t in sf.titles if t.strip()],
        thumbnail_text=sf.thumbnail_text.strip()[: handoff.THUMB_MAX],
        link=(sf.link or "").strip() or None,
    )
    if opinions:
        plan.llm_mode = "claude"
    res = write_project(settings, store, plan, None, cfg.script.tone, script=sf.script)
    for it in plan.items:
        store.mark_covered(it.topic.id, plan.id)
    return res


def plan_from_project(store: Store, project: Path) -> VideoPlan:
    """폴더명(영상 ID)으로 영상 계획 조회(DB에 없으면 plan.json)."""
    plan = store.get_video(project.name)
    if plan is None and (project / "plan.json").exists():
        plan = VideoPlan.model_validate_json((project / "plan.json").read_text(encoding="utf-8"))
    if plan is None:
        raise FileNotFoundError(f"{project.name} 영상 계획이 없습니다")
    return plan


def run_rewrite(
    settings: Settings, cfg: ChannelConfig, store: Store, project: Path, llm: ClaudeLLM | None
) -> VideoResult:
    """같은 계획으로 대본만 다시 쓰기."""
    return write_project(settings, store, plan_from_project(store, project), llm, cfg.script.tone)


async def run_made(
    settings: Settings, store: Store, project: Path, video_id: str, title: str | None = None
) -> UploadRow:
    """made: 업로드 기록, covered_in 갱신, past_titles 추가, (키 있으면) 통계 조회."""
    plan = plan_from_project(store, project)
    for it in plan.items:
        store.mark_covered(it.topic.id, plan.id)
    final_title = title or (plan.titles[0] if plan.titles else plan.id)
    append_past_title(settings, final_title)
    totals = [it.topic.total for it in plan.items] or [0.0]
    row = UploadRow(
        video_id=video_id,
        plan_id=plan.id,
        title=final_title,
        uploaded_at=now_kst(),
        predicted_total=round(statistics.fmean(totals), 1),
    )
    store.record_upload(row)
    await refresh_stats(store, [video_id])
    return row


async def refresh_stats(store: Store, ids: list[str] | None = None) -> int:
    """업로드 영상 조회수 갱신(YOUTUBE_API_KEY 없으면 0)."""
    ids = ids or [u.video_id for u in store.uploads()]
    if not ids:
        return 0
    try:
        async with make_client() as client:
            stats = await video_stats(client, ids)
    except CollectorDisabled:
        return 0
    for vid, st in stats.items():
        store.update_stats(vid, st["views"], st.get("likes"))
    return len(stats)


async def run_history(store: Store, weeks: int) -> str:
    """history: 통계 갱신 후 분석."""
    await refresh_stats(store)
    return history_report(store, weeks, now_kst())


async def send_slack(cfg: ChannelConfig, text: str) -> bool:
    """Slack 알림(웹훅이 있을 때만)."""
    if not cfg.channel.slack_webhook:
        return False
    async with make_client() as client:
        await notify_slack(client, cfg.channel.slack_webhook, text)
    return True


def run_async(coro: object) -> object:
    """동기 진입점에서 코루틴 실행."""
    return asyncio.run(coro)  # type: ignore[arg-type]
