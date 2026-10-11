"""명령 구현(CLI는 얇게, 로직은 여기)."""

from __future__ import annotations

import asyncio
import json
import statistics
from dataclasses import dataclass
from datetime import date, datetime, time, timedelta
from pathlib import Path

from studio_core.config import KST, ChannelConfig, Settings, append_past_title, now_kst
from studio_core.http import make_client
from studio_core.llm import ClaudeLLM
from topicradar.collectors.base import Collector, CollectorDisabled, CollectReport, collect_all
from topicradar.collectors.naver_news import NaverNewsCollector
from topicradar.collectors.overseas import OverseasCollector
from topicradar.collectors.rss import RssCollector
from topicradar.collectors.schedule import ManualScheduleCollector
from topicradar.collectors.youtube import YouTubeCollector, video_stats
from topicradar.db import Store, UploadRow
from topicradar.history import history_report
from topicradar.models import SlotName, SlotPlan
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
    new = store.upsert_signals(report.signals)
    report.counts["_new"] = new
    store.set_meta("last_collect", now_kst().isoformat())
    return report


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


def write_project(settings: Settings, store: Store, plan: SlotPlan, llm: ClaudeLLM | None) -> SlotResult:
    """대본 생성·점검 후 report.md / script.txt / plan.json 저장."""
    proj = project_dir(settings, plan.plan_id)
    script = draft_script(plan, llm)
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
    settings: Settings, cfg: ChannelConfig, store: Store, slot: SlotName, day: date, llm: ClaudeLLM | None
) -> SlotResult:
    """slot: 편성 + 대본 + 리포트."""
    plan = build_plan(SlotContext(store=store, cfg=cfg, llm=llm), slot, day)
    return write_project(settings, store, plan, llm)


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
    return write_project(settings, store, plan, llm)


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
    return history_report(store, cfg.scoring.weights, weeks, now_kst())


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
