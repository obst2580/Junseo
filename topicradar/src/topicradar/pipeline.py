"""명령 구현(CLI는 얇게, 로직은 여기)."""

from __future__ import annotations

import asyncio
import json
import re
import statistics
from dataclasses import dataclass, field
from datetime import date, datetime, time, timedelta
from pathlib import Path

from studio_core.config import KST, ChannelConfig, Settings, append_past_title, now_kst
from studio_core.http import make_client
from studio_core.llm import ClaudeLLM
from topicradar.collectors.base import Collector, CollectorDisabled, CollectReport, collect_all
from topicradar.collectors.community import CommunityCollector
from topicradar.collectors.naver_news import NaverNewsCollector
from topicradar.collectors.overseas import OverseasCollector
from topicradar.collectors.rss import RssCollector
from topicradar.collectors.schedule import ManualScheduleCollector
from topicradar.collectors.youtube import YouTubeCollector, video_stats
from topicradar.db import Store, UploadRow
from topicradar.history import history_report
from topicradar.models import Signal, SlotName, SlotPlan
from topicradar.report import notify_slack, render_report, slack_text
from topicradar.script import draft_script, validate_script
from topicradar.slots import SlotContext, build_plan, replace_item


def open_store(settings: Settings) -> Store:
    """data/studio.db."""
    return Store(settings.db_path)


def build_collectors(settings: Settings, cfg: ChannelConfig, client: object) -> list[Collector]:
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
        cols.append(CommunityCollector(cfg.communities, client, cfg.clustering.generic_tokens))  # type: ignore[arg-type]
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
        report = await collect_all(build_collectors(settings, cfg, client), since)
        # 2단계: 커뮤니티에서 뜨는 화제로 뉴스 검색어를 만들어 근거 기사를 확보한다.
        queries = derive_queries(
            [s for s in report.signals if s.kind == "community"],
            cfg.selection.derived_queries,
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


async def probe_communities(cfg: ChannelConfig, name: str | None = None) -> list[ProbeResult]:
    """설정된 게시판 목록 1쪽을 읽어 패턴이 맞는지 보여 준다(저장하지 않음)."""
    from topicradar.collectors.community import RobotsCache, is_political, parse_board

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
                    posts = parse_board(resp.text, spec, url, now_kst())
                    res.posts = posts
                    res.kept = sum(
                        1
                        for p in posts
                        if not spec.politics_filter or is_political(p.title, cfg.clustering.generic_tokens)
                    )
                except Exception as e:  # 진단용
                    res.error = f"{type(e).__name__}: {e}"
            out.append(res)
    return out


def project_dir(settings: Settings, plan_id: str) -> Path:
    """projects/<날짜_슬롯>/."""
    d = settings.projects_dir / plan_id
    d.mkdir(parents=True, exist_ok=True)
    return d


@dataclass
class SlotResult:
    """slot 실행 결과."""

    plan: SlotPlan
    project: Path
    warnings: list[str]


def write_project(
    settings: Settings, store: Store, plan: SlotPlan, llm: ClaudeLLM | None, tone: str = "확신형+입담형"
) -> SlotResult:
    """대본 생성·점검 후 report.md / script.txt / plan.json 저장."""
    proj = project_dir(settings, plan.plan_id)
    script = draft_script(plan, llm, tone)
    warnings = validate_script(script, plan)
    script_path = proj / "script.txt"
    script_path.write_text(script, encoding="utf-8")
    rel = script_path.relative_to(settings.root) if script_path.is_relative_to(settings.root) else script_path
    (proj / "report.md").write_text(render_report(plan, rel, warnings), encoding="utf-8")
    (proj / "plan.json").write_text(
        json.dumps(plan.model_dump(mode="json"), ensure_ascii=False, indent=2), encoding="utf-8"
    )
    store.save_plan(plan, str(proj))
    return SlotResult(plan, proj, warnings)


def default_day(slot: SlotName, now: datetime | None = None) -> date:
    """슬롯 기본 날짜(주간은 이번/다가오는 일요일)."""
    today = (now or now_kst()).date()
    if slot == "weekly":
        return today + timedelta(days=(6 - today.weekday()) % 7)
    return today


def run_slot(
    settings: Settings,
    cfg: ChannelConfig,
    store: Store,
    slot: SlotName,
    day: date,
    llm: ClaudeLLM | None,
    draft: bool = False,
) -> SlotResult:
    """slot: 편성 + 대본 + 리포트. draft=True면 추천 편성(주제 후보)만 저장하고 대본은 만들지 않는다."""
    ctx = SlotContext(store=store, cfg=cfg, llm=llm)
    if not draft:
        return write_project(settings, store, build_plan(ctx, slot, day), llm, cfg.script.tone)
    plan = build_plan(ctx, slot, day, fill_text=False)
    proj = project_dir(settings, plan.plan_id)
    for name in ("script.txt", "report.md"):  # 다시 만들면 예전 대본은 옆에 남겨 둔다
        if (proj / name).exists():
            (proj / name).replace(proj / f"{Path(name).stem}.prev{Path(name).suffix}")
    (proj / "plan.json").write_text(
        json.dumps(plan.model_dump(mode="json"), ensure_ascii=False, indent=2), encoding="utf-8"
    )
    store.save_plan(plan, str(proj))
    return SlotResult(plan, proj, [])


def run_finalize(
    settings: Settings,
    cfg: ChannelConfig,
    store: Store,
    project: Path,
    picks: list[tuple[str, str]],
    llm: ClaudeLLM | None,
) -> SlotResult:
    """고른 주제로 편성 확정 → 예상·회수·제목 → 대본 + 리포트."""
    from topicradar.slots import finalize_plan

    plan = plan_from_project(store, project)
    plan = finalize_plan(SlotContext(store=store, cfg=cfg, llm=llm), plan, picks)
    return write_project(settings, store, plan, llm, cfg.script.tone)


def plan_from_project(store: Store, project: Path) -> SlotPlan:
    """폴더명(YYYY-MM-DD_slot)으로 편성안 조회."""
    name = project.name
    day_s, slot = name.split("_", 1)
    plan = store.get_plan(date.fromisoformat(day_s), slot)
    if plan is None:
        raise FileNotFoundError(f"{name} 편성안이 DB에 없습니다(slot을 먼저 실행)")
    return plan


def run_pick(
    settings: Settings,
    cfg: ChannelConfig,
    store: Store,
    project: Path,
    order: int,
    topic_id: str,
    llm: ClaudeLLM | None,
) -> SlotResult:
    """pick: 항목 교체 후 대본 재생성."""
    plan = plan_from_project(store, project)
    topic = store.get_topic(topic_id)
    if topic is None:
        raise KeyError(f"주제 {topic_id} 없음")
    plan = replace_item(SlotContext(store=store, cfg=cfg, llm=llm), plan, order, topic)
    return write_project(settings, store, plan, llm, cfg.script.tone)


async def run_made(
    settings: Settings, store: Store, project: Path, video_id: str, title: str | None = None
) -> UploadRow:
    """made: 업로드 기록, covered_in 갱신, past_titles 추가, (키 있으면) 통계 조회."""
    plan = plan_from_project(store, project)
    for it in plan.items:
        store.mark_covered(it.topic.id, plan.plan_id)
    final_title = title or (plan.titles[0] if plan.titles else plan.plan_id)
    append_past_title(settings, final_title)
    totals = [it.topic.total for it in plan.items if it.role != "포인트회수"] or [0.0]
    upload_time = {"morning": time(7, 0), "evening": time(19, 0), "weekly": time(10, 0)}[plan.slot]
    row = UploadRow(
        video_id=video_id,
        plan_id=plan.plan_id,
        title=final_title,
        uploaded_at=datetime.combine(plan.date, upload_time, tzinfo=KST),
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


async def run_history(store: Store, cfg: ChannelConfig, weeks: int) -> str:
    """history: 통계 갱신 후 분석."""
    await refresh_stats(store)
    return history_report(store, cfg.scoring.weights, weeks, now_kst(), cfg.selection.mode)


async def send_slack(cfg: ChannelConfig, result: SlotResult) -> bool:
    """--notify."""
    if not cfg.channel.slack_webhook:
        return False
    async with make_client() as client:
        await notify_slack(client, cfg.channel.slack_webhook, slack_text(result.plan, result.project))
    return True


def daily_actions(now: datetime) -> list[tuple[str, SlotName | None, date | None]]:
    """daily: 시각별 동작. 05:30 morning, 12:00 collect만, 17:00 evening(토요일은 weekly 추가)."""
    t = now.timetz().replace(tzinfo=None)
    today = now.date()
    acts: list[tuple[str, SlotName | None, date | None]] = [("collect", None, None)]
    if time(5, 0) <= t < time(6, 30):
        acts.append(("slot", "morning", today))
    elif time(16, 30) <= t < time(18, 0):
        acts.append(("slot", "evening", today))
        if today.weekday() == 5:
            acts.append(("slot", "weekly", today + timedelta(days=1)))
    return acts


def run_async(coro: object) -> object:
    """동기 진입점에서 코루틴 실행."""
    return asyncio.run(coro)  # type: ignore[arg-type]
