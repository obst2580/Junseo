"""화면 서버가 쓰는 기능 모음(FastAPI와 분리해 테스트하기 쉽게 둔다).

기존 CLI와 같은 파이프라인 함수(topicradar.pipeline, newsclip.pipeline)를 그대로 부른다.
"""

from __future__ import annotations

import asyncio
import json
import re
import shutil
from datetime import date, datetime, time, timedelta
from pathlib import Path
from typing import Any

from studio_app.jobs import Say
from studio_core.config import (
    KST,
    Settings,
    load_channel,
    now_kst,
    save_channel_text,
    set_channel_value,
    set_community_enabled,
)
from studio_core.envfile import mask, read_env, set_env
from studio_core.llm import auth_marker, get_llm
from topicradar import pipeline as tr
from topicradar.db import Store
from topicradar.models import SlotPlan
from topicradar.prompts import TONES
from topicradar.report import SLOT_KO, buzz_cell, render_report
from topicradar.script import validate_script

PROJECT_RE = re.compile(r"^(\d{4}-\d{2}-\d{2})_(morning|evening|weekly)$")
SLOT_ORDER = {"morning": 0, "evening": 1, "weekly": 2}
UPLOAD_NAMES = {"narration.srt", "narration.wav"}
AUTO_MARK = "narration.srt.auto"  # 자막을 음성에서 자동으로 맞췄다는 표시(결과 요약 JSON)
WEEKDAYS = "월화수목금토일"
WEIGHT_KEYS = ("interest", "gap", "persistence", "fit")
COLLECT_FRESH = timedelta(minutes=20)  # 이보다 최근에 수집했으면 편성 전에 다시 수집하지 않는다

KEYS: list[dict[str, Any]] = [
    {
        "key": "ANTHROPIC_API_KEY",
        "label": "Claude API 키",
        "required": True,
        "why": "주제 판단·대본(입담 말투)·장면 나누기. 없으면 기사 문장을 옮긴 골격 대본만 나옵니다.",
        "how": "Claude Console → API Keys → Create Key(만료는 '없음' 또는 90일 이상 권장) · Billing에서 월 사용 한도 설정",
        "url": "https://console.anthropic.com/settings/keys",
    },
    {
        "key": "NAVER_CLIENT_ID",
        "label": "네이버 Client ID",
        "required": True,
        "why": "뉴스 검색(근거 기사)과 이미지 검색(1순위 자료). 지금은 무료(검색 합산 월 77만 5천 건까지).",
        "how": "네이버 클라우드 콘솔 → Services → Application Service → NAVER API HUB → 이용 신청 → 애플리케이션 등록(검색 API 선택). 개발자센터 신규 발급은 2026-07-31 종료",
        "url": "https://console.ncloud.com/",
    },
    {
        "key": "NAVER_CLIENT_SECRET",
        "label": "네이버 Client Secret",
        "required": True,
        "why": "위 애플리케이션의 Secret. 예전 개발자센터 키도 그대로 쓸 수 있습니다(2027-06-30까지).",
        "how": "NAVER API HUB 애플리케이션 상세의 Client Secret",
        "url": "https://console.ncloud.com/",
    },
    {
        "key": "YOUTUBE_API_KEY",
        "label": "YouTube API 키",
        "required": True,
        "why": "경쟁 채널이 다룬 주제, 내 영상 조회수, 영상 자료 검색.",
        "how": "Google Cloud Console → YouTube Data API v3 사용 → 사용자 인증 정보 → API 키",
        "url": "https://console.cloud.google.com/apis/library/youtube.googleapis.com",
    },
    {
        "key": "GOOGLE_API_KEY",
        "label": "Google 검색 API 키 (선택)",
        "required": False,
        "why": "기존 키가 있을 때만. 신규 가입이 막혔고 2027-01-01 종료 예정이라 없으면 네이버 이미지가 1순위.",
        "how": "Google Cloud Console → Custom Search API",
        "url": "https://console.cloud.google.com/apis/library/customsearch.googleapis.com",
    },
    {
        "key": "GOOGLE_CSE_ID",
        "label": "Google 검색엔진 ID (선택)",
        "required": False,
        "why": "위 키와 함께 쓰는 검색엔진 ID(cx).",
        "how": "Programmable Search Engine → 이미지 검색 켜기",
        "url": "https://programmablesearchengine.google.com/",
    },
    {
        "key": "STUDIO_CONTACT",
        "label": "연락처 이메일 (선택)",
        "required": False,
        "why": "Wikimedia 이미지 요청 예절(User-Agent)에 들어갑니다.",
        "how": "본인 이메일",
        "url": "",
    },
]


# ---- 공통 ---------------------------------------------------------------------------------------


def open_store(settings: Settings) -> Store:
    """요청·작업마다 새로 연다(스레드 안전)."""
    return Store(settings.db_path)


def project_path(settings: Settings, pid: str) -> Path:
    """프로젝트 폴더(이름 형식 검사)."""
    if not PROJECT_RE.match(pid):
        raise ValueError(f"프로젝트 이름 형식이 아닙니다: {pid}")
    path = settings.projects_dir / pid
    if not path.is_dir():
        raise FileNotFoundError(f"프로젝트 {pid} 없음")
    return path


def load_plan(project: Path) -> SlotPlan:
    """plan.json."""
    return SlotPlan.model_validate_json((project / "plan.json").read_text(encoding="utf-8"))


def label_for(day: date, slot: str) -> str:
    """'10월 8일(목) 저녁 리뷰'."""
    return f"{day.month}월 {day.day}일({WEEKDAYS[day.weekday()]}) {SLOT_KO.get(slot, slot)}"


def suggest_slot(now: datetime) -> str | None:
    """지금 만들 만한 슬롯."""
    t = now.timetz().replace(tzinfo=None)
    if time(3, 0) <= t < time(10, 0):
        return "morning"
    if time(15, 0) <= t < time(23, 0):
        return "weekly" if now.weekday() == 5 and t >= time(19, 0) else "evening"
    return None


def default_date(slot: str, now: datetime | None = None) -> date:
    """슬롯 기본 날짜(주간은 이번/다가오는 일요일)."""
    return tr.default_day(slot, now)  # type: ignore[arg-type]


# ---- 상태·프로젝트 ---------------------------------------------------------------------------------


def status(settings: Settings) -> dict[str, Any]:
    """홈 화면 상단: 시각, Claude 연결 여부, 처음 설정 점검, 추천 슬롯."""
    now = now_kst()
    cfg = load_channel(settings)
    missing = [k["label"] for k in KEYS if k["required"] and not settings.env(k["key"])]
    with open_store(settings) as store:
        last = store.get_meta("last_collect")
    setup: list[str] = []
    rejected = auth_marker(settings).exists() and bool(settings.env("ANTHROPIC_API_KEY"))
    if rejected:
        setup.append(
            "Claude API 키가 거부됐습니다(만료·삭제됐거나 잘못된 키) — 설정에서 새 키를 넣으세요. "
            "그동안 대본은 오프라인 초안으로 나옵니다"
        )
    if missing:
        setup.append("API 키 입력: " + ", ".join(missing))
    if not shutil.which("ffmpeg"):
        setup.append(
            "ffmpeg 설치(영상 자료 구간 자르기용) — 맥: brew install ffmpeg / 윈도우: winget install Gyan.FFmpeg"
        )
    if not cfg.channel.competitors:
        setup.append("경쟁 채널 ID 입력(공백도 점수용)")
    if cfg.communities and not (settings.data_dir / "probe.json").exists():
        setup.append("커뮤니티 게시판 점검(설정 → 게시판 점검)")
    llm = (
        "offline"
        if settings.env("STUDIO_LLM").lower() == "offline" or not settings.env("ANTHROPIC_API_KEY")
        else "rejected"
        if rejected
        else "claude"
    )
    return {
        "now": now.isoformat(),
        "now_label": f"{now.month}월 {now.day}일({WEEKDAYS[now.weekday()]}) {now:%H:%M}",
        "llm": llm,
        "last_collect": last,
        "last_collect_label": _when(last),
        "setup": setup,
        "suggest": suggest_slot(now),
        "defaults": {s: default_date(s, now).isoformat() for s in ("morning", "evening", "weekly")},
        "root": str(settings.root),
    }


def _when(iso: str | None) -> str:
    """ISO 시각 → '10월 8일 17:00'(KST, 브라우저 시간대와 무관)."""
    if not iso:
        return ""
    dt = datetime.fromisoformat(iso).astimezone(KST)
    return f"{dt.month}월 {dt.day}일 {dt:%H:%M}"


def _uploads(store: Store) -> dict[str, Any]:
    return {u.plan_id: u for u in store.uploads()}


def _read_json(path: Path) -> dict[str, Any] | None:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None


def project_summary(settings: Settings, pid: str, uploads: dict[str, Any]) -> dict[str, Any]:
    """목록 한 줄."""
    m = PROJECT_RE.match(pid)
    assert m
    day, slot = date.fromisoformat(m.group(1)), m.group(2)
    d = settings.projects_dir / pid
    title = ""
    if (d / "plan.json").exists():
        try:
            plan = load_plan(d)
            title = plan.titles[0] if plan.titles else ""
        except ValueError:
            title = ""
    up = uploads.get(pid)
    steps = {
        "plan": (d / "script.txt").exists(),
        "audio": (d / "narration.srt").exists(),
        "assets": (d / "scenes.json").exists(),
        "xml": (d / "timeline.xml").exists(),
        "upload": up is not None,
    }
    return {
        "id": pid,
        "date": day.isoformat(),
        "slot": slot,
        "label": label_for(day, slot),
        "title": title,
        "steps": steps,
        "has_wav": (d / "narration.wav").exists(),
        "srt_auto": (d / AUTO_MARK).exists(),
        "autosub": _read_json(d / AUTO_MARK),
        "video_id": up.video_id if up else None,
        "uploaded_title": up.title if up else None,
    }


def list_projects(settings: Settings) -> list[dict[str, Any]]:
    """최근 순."""
    if not settings.projects_dir.exists():
        return []
    names = [p.name for p in settings.projects_dir.iterdir() if p.is_dir() and PROJECT_RE.match(p.name)]
    names.sort(key=lambda n: (n.split("_")[0], SLOT_ORDER[n.split("_")[1]]), reverse=True)
    with open_store(settings) as store:
        ups = _uploads(store)
    return [project_summary(settings, n, ups) for n in names]


def _item_dict(plan: SlotPlan, it: Any) -> dict[str, Any]:
    t = it.topic
    return {
        "order": it.order,
        "role": it.role,
        "est_seconds": it.est_seconds,
        "topic": {
            "id": t.id,
            "name": t.name,
            "category": t.category,
            "total": round(t.total, 1),
            "trend": t.trend,
            "kind": t.kind,
            "scores": {k: {"score": round(v.score), "reason": v.reason} for k, v in t.scores.items()},
        },
        "buzz": buzz_cell(t),
        "links": t.top_links(2),
        "expected": it.expected,
        "impact": it.impact,
        "actual": it.actual,
        "hit": it.hit,
        "hit_reason": it.hit_reason,
        "chain": it.chain,
        "note": it.note,
        "risks": [f.message for f in plan.risk_flags if f.topic_id == t.id],
    }


def project_detail(settings: Settings, pid: str) -> dict[str, Any]:
    """프로젝트 화면 전체."""
    d = project_path(settings, pid)
    with open_store(settings) as store:
        summary = project_summary(settings, pid, _uploads(store))
    out: dict[str, Any] = {"summary": summary, "plan": None, "items": [], "script": "", "warnings": []}
    if not (d / "plan.json").exists():
        return out
    plan = load_plan(d)
    script = (d / "script.txt").read_text(encoding="utf-8") if (d / "script.txt").exists() else ""
    out.update(
        plan={
            "titles": plan.titles,
            "thumbnail_text": plan.thumbnail_text,
            "chains": [c.model_dump() for c in plan.chains],
            "excluded": plan.excluded,
            "risks": [{"kind": f.kind, "message": f.message} for f in plan.risk_flags if f.topic_id is None],
            "window": (
                f"{plan.window_start:%m-%d %H:%M} ~ {plan.window_end:%m-%d %H:%M}"
                if plan.window_start and plan.window_end
                else ""
            ),
            "llm_mode": plan.llm_mode,
        },
        items=[_item_dict(plan, it) for it in plan.items],
        script=script,
        warnings=validate_script(script, plan) if script else [],
    )
    return out


def save_script(settings: Settings, pid: str, text: str) -> list[str]:
    """대본 저장 → 점검 → report.md 갱신."""
    d = project_path(settings, pid)
    text = text.replace("\r\n", "\n")
    if not text.endswith("\n"):
        text += "\n"
    path = d / "script.txt"
    path.write_text(text, encoding="utf-8")
    plan = load_plan(d)
    warnings = validate_script(text, plan)
    rel = path.relative_to(settings.root) if path.is_relative_to(settings.root) else path
    (d / "report.md").write_text(render_report(plan, rel, warnings), encoding="utf-8")
    return warnings


def replace_candidates(settings: Settings, pid: str, order: int, limit: int = 20) -> list[dict[str, Any]]:
    """교체 후보: 이 편성 윈도우 앞뒤로 보도된 주제 중 아직 편성 안 된 것, 총점 순."""
    d = project_path(settings, pid)
    plan = load_plan(d)
    if not any(i.order == order for i in plan.items):
        raise KeyError(f"순서 {order} 항목 없음")
    used = plan.topic_ids()
    start = plan.window_start or datetime.combine(plan.date, time(0), tzinfo=KST)
    since = start - timedelta(days=7 if plan.slot == "weekly" else 2)
    with open_store(settings) as store:
        topics = [t for t in store.topics_since(since) if t.id not in used and t.news_signals()]
    topics.sort(key=lambda t: -t.total)
    return [
        {
            "id": t.id,
            "name": t.name,
            "category": t.category,
            "total": round(t.total, 1),
            "buzz": buzz_cell(t),
            "last_seen": f"{t.last_seen:%m-%d %H:%M}",
            "articles": len(t.news_signals()),
        }
        for t in topics[:limit]
    ]


_YT_RE = re.compile(r"(?:v=|youtu\.be/|shorts/|live/|embed/)([A-Za-z0-9_-]{11})")


def parse_video_id(text: str) -> str:
    """유튜브 주소 또는 ID → ID."""
    text = text.strip()
    m = _YT_RE.search(text)
    if m:
        return m.group(1)
    if re.fullmatch(r"[A-Za-z0-9_-]{11}", text):
        return text
    raise ValueError("유튜브 영상 주소나 11자리 영상 ID를 넣어 주세요")


# ---- 설정 -------------------------------------------------------------------------------------


def settings_info(settings: Settings) -> dict[str, Any]:
    """설정 화면."""
    env_file = read_env(settings.root / ".env")
    keys = []
    for k in KEYS:
        value = settings.env(k["key"]) or env_file.get(k["key"], "")
        keys.append({**k, "set": bool(value), "masked": mask(value)})
    cfg = load_channel(settings)
    probe_path = settings.data_dir / "probe.json"
    probe = json.loads(probe_path.read_text(encoding="utf-8")) if probe_path.exists() else None
    if probe:
        probe["checked_label"] = _when(probe.get("checked_at"))
    return {
        "keys": keys,
        "channel": {
            "youtube_channel_id": cfg.channel.youtube_channel_id,
            "competitors": cfg.channel.competitors,
            "slack_webhook": cfg.channel.slack_webhook,
            "tone": cfg.script.tone,
            "weights": {k: round(v) for k, v in cfg.scoring.weights.items()},
            "selection_mode": cfg.selection.mode,
        },
        "tones": [{"name": n, "rules": r} for n, r in TONES.items()],
        "communities": [
            {
                "name": c.name,
                "enabled": c.enabled,
                "list_url": c.list_url,
                "note": c.note,
                "politics_filter": c.politics_filter,
            }
            for c in cfg.communities
        ],
        "probe": probe,
        "raw": settings.channel_path.read_text(encoding="utf-8") if settings.channel_path.exists() else "",
    }


def save_keys(settings: Settings, values: dict[str, str]) -> None:
    """.env 저장(알려진 키만)."""
    allowed = {k["key"] for k in KEYS}
    bad = set(values) - allowed
    if bad:
        raise ValueError(f"알 수 없는 키: {', '.join(sorted(bad))}")
    set_env(settings.root / ".env", values, template=settings.root / ".env.example")
    if values.get("ANTHROPIC_API_KEY"):
        auth_marker(settings).unlink(missing_ok=True)


def save_channel(settings: Settings, data: dict[str, Any]) -> None:
    """채널 기본 설정(줄 단위 수정)."""
    if "tone" in data and data["tone"] not in TONES:
        raise ValueError(f"말투는 {', '.join(TONES)} 중 하나")
    for key in ("youtube_channel_id", "slack_webhook"):
        if key in data:
            set_channel_value(settings, "channel", key, str(data[key]).strip())
    if "competitors" in data:
        raw = data["competitors"]
        items = raw if isinstance(raw, list) else re.split(r"[\s,]+", str(raw))
        set_channel_value(settings, "channel", "competitors", [x.strip() for x in items if x.strip()])
    if "tone" in data:
        set_channel_value(settings, "script", "tone", data["tone"])
    if "selection_mode" in data:
        if data["selection_mode"] not in ("hottest", "balanced"):
            raise ValueError("선정 방식은 hottest | balanced")
        set_channel_value(settings, "selection", "mode", data["selection_mode"])
    if "weights" in data:
        w = {k: float(v) for k, v in data["weights"].items()}
        if set(w) != set(WEIGHT_KEYS) or any(not 0 <= v <= 100 for v in w.values()) or sum(w.values()) <= 0:
            raise ValueError("가중치는 관심도·공백도·지속성·적합도 네 개, 각각 0~100")
        set_channel_value(
            settings, "scoring", "weights", {k: int(w[k]) if w[k].is_integer() else w[k] for k in WEIGHT_KEYS}
        )


def toggle_community(settings: Settings, name: str, enabled: bool) -> None:
    """게시판 켜기·끄기."""
    set_community_enabled(settings, name, enabled)


def save_raw(settings: Settings, text: str) -> None:
    """channel.yaml 전체(검사 후 저장)."""
    save_channel_text(settings, text.replace("\r\n", "\n"))


# ---- 작업(백그라운드) ------------------------------------------------------------------------------


def job_collect(settings: Settings, say: Say, since: datetime | None = None) -> dict[str, Any]:
    """수집."""
    cfg = load_channel(settings)
    say("커뮤니티 게시판·뉴스·경쟁 채널 수집 중…")
    with open_store(settings) as store:
        rep = asyncio.run(tr.run_collect(settings, cfg, store, since))
    for k, v in sorted(rep.counts.items()):
        if k != "_new":
            say(f"  {k}: {v}건")
    for k, v in {**rep.skipped, **rep.errors}.items():
        say(f"⚠️ {k}: {v}")
    if rep.derived_queries:
        say("커뮤니티 화제 → 뉴스 검색어: " + ", ".join(rep.derived_queries))
    return {
        "counts": rep.counts,
        "skipped": rep.skipped,
        "errors": rep.errors,
        "derived_queries": rep.derived_queries,
        "new": rep.counts.get("_new", 0),
    }


def _collect_if_stale(settings: Settings, say: Say) -> None:
    with open_store(settings) as store:
        last = store.get_meta("last_collect")
    if last and now_kst() - datetime.fromisoformat(last) < COLLECT_FRESH:
        say(f"최근 수집({datetime.fromisoformat(last):%H:%M}) 결과를 씁니다")
        return
    job_collect(settings, say)


def job_slot(settings: Settings, say: Say, slot: str, day: date, collect: bool = True) -> dict[str, Any]:
    """편성 만들기(필요하면 먼저 수집) → 대본 초안 → (Slack 설정 시) 알림."""
    if collect:
        _collect_if_stale(settings, say)
    cfg = load_channel(settings)
    llm = get_llm(settings)
    say(f"{label_for(day, slot)} 편성 계산 중… ({'Claude' if llm else '오프라인 휴리스틱'})")
    with open_store(settings) as store:
        res = tr.run_slot(settings, cfg, store, slot, day, llm)  # type: ignore[arg-type]
    say(f"대본 초안 작성 완료 — 점검 {len(res.warnings)}건")
    if cfg.channel.slack_webhook:
        try:
            if asyncio.run(tr.send_slack(cfg, res)):
                say("Slack 전송 완료")
        except Exception as e:  # 알림 실패는 편성 결과에 영향 없음
            say(f"⚠️ Slack 전송 실패: {e}")
    return {"project": res.plan.plan_id, "warnings": res.warnings}


def job_pick(settings: Settings, say: Say, pid: str, order: int, topic_id: str) -> dict[str, Any]:
    """항목 교체 후 대본 다시 쓰기."""
    d = project_path(settings, pid)
    cfg = load_channel(settings)
    with open_store(settings) as store:
        res = tr.run_pick(settings, cfg, store, d, order, topic_id, get_llm(settings))
    return {"project": pid, "warnings": res.warnings}


def job_rewrite(settings: Settings, say: Say, pid: str) -> dict[str, Any]:
    """같은 편성으로 대본만 다시 쓰기(키를 넣은 뒤 Claude 대본으로 바꿀 때)."""
    d = project_path(settings, pid)
    cfg = load_channel(settings)
    with open_store(settings) as store:
        plan = tr.plan_from_project(store, d)
        res = tr.write_project(settings, store, plan, get_llm(settings), cfg.script.tone)
    return {"project": pid, "warnings": res.warnings}


def job_newsclip(settings: Settings, say: Say, pid: str) -> dict[str, Any]:
    """자료 수집: 장면 분할 → 검색 → 다운로드 → timeline.xml."""
    from newsclip import pipeline as nc

    d = project_path(settings, pid)
    if not (d / "narration.srt").exists():
        raise FileNotFoundError("자막이 없습니다 — 음성·자막 단계에서 음성 파일(또는 SRT)을 올려 주세요")
    cfg = load_channel(settings)
    say("장면 나누기 → 사진·영상 검색 → 다운로드(장면 수에 따라 수 분)…")
    sset, rep = asyncio.run(nc.run_project(settings, cfg, d, llm=get_llm(settings)))
    for k, v in rep.skipped_sources.items():
        say(f"⚠️ {k}: {v}")
    for e in rep.errors:
        say(f"⚠️ {e}")
    say(f"장면 {rep.scenes}개 · 자료 확보 {rep.with_asset}개 · 다운로드 {rep.downloaded}개")
    return {
        "project": pid,
        "scenes": rep.scenes,
        "with_asset": rep.with_asset,
        "downloaded": rep.downloaded,
        "by_source": rep.by_source,
        "xml_errors": rep.xml_errors,
        "missing": [sc.id for sc in sset.scenes if not sc.chosen],
    }


def job_made(settings: Settings, say: Say, pid: str, video: str, title: str | None) -> dict[str, Any]:
    """업로드 기록."""
    d = project_path(settings, pid)
    vid = parse_video_id(video)
    with open_store(settings) as store:
        row = asyncio.run(tr.run_made(settings, store, d, vid, title or None))
    return {"project": pid, "video_id": row.video_id, "title": row.title}


def job_history(settings: Settings, say: Say, weeks: int) -> dict[str, Any]:
    """성과 분석."""
    cfg = load_channel(settings)
    with open_store(settings) as store:
        text = asyncio.run(tr.run_history(store, cfg, weeks))
    return {"markdown": text}


def job_probe(settings: Settings, say: Say, name: str | None = None) -> dict[str, Any]:
    """커뮤니티 게시판 점검 → data/probe.json."""
    cfg = load_channel(settings)
    results = asyncio.run(tr.probe_communities(cfg, name))
    out = [
        {
            "name": r.name,
            "url": r.url,
            "allowed": r.allowed,
            "posts": len(r.posts),
            "kept": r.kept,
            "error": r.error,
            "samples": [
                {"at": f"{p.posted_at:%m-%d %H:%M}", "comments": p.comments, "title": p.title}
                for p in r.posts[:5]
            ],
            "ok": r.allowed and not r.error and bool(r.posts),
        }
        for r in results
    ]
    for r in out:
        say(
            f"{r['name']}: "
            + (
                "정상"
                if r["ok"]
                else r["error"] or ("robots.txt 금지" if not r["allowed"] else "글을 못 찾음")
            )
        )
    data = {"checked_at": now_kst().isoformat(), "results": out}
    if name is None:
        (settings.data_dir / "probe.json").write_text(
            json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8"
        )
    return data


def job_daily(settings: Settings, say: Say, now: datetime) -> dict[str, Any]:
    """자동 실행: 시각별 수집·편성(CLI daily와 같은 규칙)."""
    made: list[str] = []
    for action, slot, day in tr.daily_actions(now):
        if action == "collect":
            job_collect(settings, say)
        elif slot and day:
            made.append(job_slot(settings, say, slot, day, collect=False)["project"])
    return {"projects": made}


# ---- AI 음성(타입캐스트 웹 등) -------------------------------------------------------------------


def narration_info(settings: Settings, pid: str) -> dict[str, Any]:
    """타입캐스트에 붙여넣을 글(읽는 문장만, 단락 정리)과 글자 수·남은 확인 주석 수."""
    from newsclip.autosub import narration_text

    d = project_path(settings, pid)
    script = (d / "script.txt").read_text(encoding="utf-8") if (d / "script.txt").exists() else ""
    text = narration_text(script)
    todo = sum(1 for ln in script.splitlines() if re.match(r"\s*#\s*(TODO|확인)", ln))
    return {"text": text, "chars": len(text.replace("\n", "")), "todo": todo}


def save_audio(settings: Settings, pid: str, tmp: Path, filename: str) -> dict[str, Any]:
    """받은 음성 파일 → narration.wav(필요하면 변환) → 자막 자동 생성."""
    from newsclip.autosub import AUDIO_EXTS, to_wav

    d = project_path(settings, pid)
    ext = Path(filename).suffix.lower()
    if ext not in AUDIO_EXTS:
        tmp.unlink(missing_ok=True)
        raise ValueError(f"음성 파일만 올릴 수 있습니다({', '.join(sorted(AUDIO_EXTS))})")
    for old in d.glob("narration_source.*"):
        old.unlink()
    src = d / f"narration_source{ext}"
    tmp.replace(src)
    wav = d / "narration.wav"
    if ext == ".wav":
        shutil.copyfile(src, wav)
    else:
        to_wav(src, wav)
    return make_autosub(settings, pid)


def make_autosub(settings: Settings, pid: str) -> dict[str, Any]:
    """대본 + narration.wav → narration.srt(쉼에 맞춤)."""
    from newsclip.autosub import make_srt, script_sentences
    from newsclip.srt import write_srt

    d = project_path(settings, pid)
    if not (d / "narration.wav").exists():
        raise FileNotFoundError("음성 파일이 없습니다 — 먼저 음성을 올려 주세요")
    script = (d / "script.txt").read_text(encoding="utf-8")
    cues, info, matched = make_srt(script, d / "narration.wav")
    if not cues:
        raise ValueError("대본에 읽을 문장이 없습니다")
    (d / "narration.srt").write_text(write_srt(cues), encoding="utf-8")
    sentences = len(script_sentences(script))
    result = {
        "cues": len(cues),
        "duration": round(info.duration, 1),
        "sentences": sentences,
        "matched": matched,
        "boundaries": max(0, sentences - 1),
        "pauses": len(info.silences),
    }
    (d / AUTO_MARK).write_text(json.dumps(result, ensure_ascii=False), encoding="utf-8")
    return result
