"""화면 서버가 쓰는 기능 모음(FastAPI와 분리해 테스트하기 쉽게 둔다).

기존 CLI와 같은 파이프라인 함수(topicradar.pipeline, newsclip.pipeline)를 그대로 부른다.
"""

from __future__ import annotations

import asyncio
import json
import re
import shutil
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any

from studio_app.jobs import Say
from studio_core.config import (
    ENV_OVERRIDDEN,
    KST,
    Settings,
    load_channel,
    now_kst,
    save_channel_text,
    set_channel_value,
    set_community_enabled,
)
from studio_core.envfile import mask, read_env, set_env
from studio_core.llm import auth_marker, auth_reason, check_connection, get_llm, key_problem
from topicradar import pipeline as tr
from topicradar.db import Store
from topicradar.models import FORMATS, Board, VideoPlan
from topicradar.prompts import TONES
from topicradar.report import board_text, buzz_cell, render_report
from topicradar.script import validate_script

PROJECT_RE = tr.PROJECT_RE
UPLOAD_NAMES = {"narration.srt", "narration.wav"}
AUTO_MARK = "narration.srt.auto"  # 자막을 음성에서 자동으로 맞췄다는 표시(결과 요약 JSON)
WEEKDAYS = "월화수목금토일"
COLLECT_FRESH = timedelta(minutes=20)  # 이보다 최근에 수집했으면 주제판 전에 다시 수집하지 않는다

KEYS: list[dict[str, Any]] = [
    {
        "key": "ANTHROPIC_API_KEY",
        "label": "Claude API 키",
        "required": True,
        "why": "커뮤니티 의견 정리·10분 묶음 추천·대본(입담 말투)·장면 나누기. 없으면 기사 문장과 공감 많은 글을 옮긴 골격만 나옵니다.",
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
        "why": "내 영상 조회수(성과), 영상 자료 검색, 경쟁 채널 영상 표시.",
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


def load_plan(project: Path) -> VideoPlan:
    """plan.json."""
    return VideoPlan.model_validate_json((project / "plan.json").read_text(encoding="utf-8"))


def _when(iso: str | datetime | None) -> str:
    """ISO 시각 → '10월 8일(목) 17:00'(KST, 브라우저 시간대와 무관)."""
    if not iso:
        return ""
    dt = (datetime.fromisoformat(iso) if isinstance(iso, str) else iso).astimezone(KST)
    return f"{dt.month}월 {dt.day}일({WEEKDAYS[dt.weekday()]}) {dt:%H:%M}"


def label_for(pid: str) -> str:
    """'2026-10-08_1805_long' → '10월 8일(목) 18:05 · 10분 묶음'."""
    m = re.match(r"^(\d{4}-\d{2}-\d{2})_(\d{2})(\d{2})(?:-\d+)?_(long|short)$", pid)
    if not m:
        return pid
    dt = datetime.fromisoformat(f"{m.group(1)}T{m.group(2)}:{m.group(3)}").replace(tzinfo=KST)
    return f"{_when(dt)} · {FORMATS[m.group(4)].name}"


# ---- 상태·프로젝트 ---------------------------------------------------------------------------------


def status(settings: Settings) -> dict[str, Any]:
    """화면 상단: 시각, Claude 연결 여부, 처음 설정 점검, 마지막 수집·주제판."""
    now = now_kst()
    cfg = load_channel(settings)
    missing = [k["label"] for k in KEYS if k["required"] and not settings.env(k["key"])]
    with open_store(settings) as store:
        last = store.get_meta("last_collect")
        board = store.latest_board()
    setup: list[str] = []
    rejected = auth_marker(settings).exists() and bool(settings.env("ANTHROPIC_API_KEY"))
    if rejected:
        why = auth_reason(settings) or "만료·삭제됐거나 잘못된 키"
        setup.append(
            f"Claude API 키가 거부됐습니다: {why} — 설정 → API 키에서 새 키를 넣고 [연결 확인]을 누르세요. "
            "그동안 의견 정리·대본은 오프라인 골격으로 나옵니다"
        )
    elif settings.env("ANTHROPIC_API_KEY") and key_problem(settings.env("ANTHROPIC_API_KEY")):
        setup.append(f"Claude API 키 확인: {key_problem(settings.env('ANTHROPIC_API_KEY'))}")
    if missing:
        setup.append("API 키 입력: " + ", ".join(missing))
    if not shutil.which("ffmpeg"):
        setup.append(
            "ffmpeg 설치(음성 변환·영상 자료 구간 자르기용) — 맥: brew install ffmpeg / 윈도우: winget install Gyan.FFmpeg"
        )
    if cfg.communities and not (settings.data_dir / "probe.json").exists():
        setup.append("커뮤니티 게시판 점검(설정 → 게시판 점검) — 글·본문·댓글이 제대로 읽히는지 확인")
    llm = (
        "offline"
        if settings.env("STUDIO_LLM").lower() == "offline" or not settings.env("ANTHROPIC_API_KEY")
        else "rejected"
        if rejected
        else "claude"
    )
    return {
        "now": now.isoformat(),
        "now_label": _when(now),
        "llm": llm,
        "last_collect": last,
        "last_collect_label": _when(last),
        "board_id": board.id if board else None,
        "board_label": _when(board.created_at) if board else "",
        "chars_per_minute": cfg.script.chars_per_minute,
        "setup": setup,
        "root": str(settings.root),
    }


def _uploads(store: Store) -> dict[str, Any]:
    return {u.plan_id: u for u in store.uploads()}


def _read_json(path: Path) -> dict[str, Any] | None:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None


def project_summary(settings: Settings, pid: str, uploads: dict[str, Any]) -> dict[str, Any]:
    """목록 한 줄."""
    d = settings.projects_dir / pid
    title, fmt, topics = "", pid.rsplit("_", 1)[-1], []
    if (d / "plan.json").exists():
        try:
            plan = load_plan(d)
            title = plan.titles[0] if plan.titles else ""
            fmt = plan.format
            topics = [it.topic.name for it in plan.items]
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
        "format": fmt,
        "format_name": FORMATS[fmt].name if fmt in FORMATS else fmt,
        "label": label_for(pid),
        "title": title,
        "topics": topics,
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
    names.sort(reverse=True)
    with open_store(settings) as store:
        ups = _uploads(store)
    return [project_summary(settings, n, ups) for n in names]


def _opinion_dict(op: Any) -> dict[str, Any] | None:
    if op is None:
        return None
    return {
        "stance": op.stance,
        "points": op.points,
        "minority": op.minority,
        "questions": op.questions,
        "heat": op.heat_label,
        "posts": op.posts,
        "by": op.by,
        "samples": [s.model_dump() for s in op.samples],
    }


def _item_dict(plan: VideoPlan, it: Any) -> dict[str, Any]:
    t = it.topic
    return {
        "order": it.order,
        "est_seconds": it.est_seconds,
        "topic": {
            "id": t.id,
            "name": t.name,
            "category": t.category,
            "total": round(t.total, 1),
            "trend": t.trend,
            "reason": t.scores["heat"].reason if "heat" in t.scores else "",
        },
        "buzz": buzz_cell(t),
        "articles": len(t.news_signals()),
        "links": t.top_links(2),
        "opinion": _opinion_dict(it.opinion),
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
            "format": plan.format,
            "format_name": plan.spec.name,
            "minutes": plan.spec.minutes,
            "target_chars": list(plan.target_chars),
            "link": plan.link,
            "notes": plan.notes,
            "risks": [{"kind": f.kind, "message": f.message} for f in plan.risk_flags if f.topic_id is None],
            "window": (
                f"{plan.window_start:%m-%d %H:%M} ~ {plan.window_end:%m-%d %H:%M}"
                if plan.window_start and plan.window_end
                else ""
            ),
            "llm_mode": plan.llm_mode,
            "board_id": plan.board_id,
            "script_mode": plan.script_mode,
            "script_note": plan.script_note,
        },
        items=[_item_dict(plan, it) for it in plan.items],
        script=script,
        warnings=validate_script(script, plan) if script else [],
        chars_per_minute=load_channel(settings).script.chars_per_minute,
        timeline=load_channel(settings).timeline.model_dump(),
    )
    return out


def board_info(settings: Settings) -> dict[str, Any]:
    """가장 최근 주제판(없으면 board=None)."""
    with open_store(settings) as store:
        board = store.latest_board()
    if board is None:
        return {"board": None}
    return {"board": _board_dict(board)}


def _board_dict(board: Board) -> dict[str, Any]:
    names = {t.topic_id: t.name for t in board.topics}
    age = (now_kst() - board.created_at).total_seconds() / 60
    return {
        "id": board.id,
        "created_label": _when(board.created_at),
        "age_minutes": round(age),
        "window": f"{board.window_start:%m-%d %H:%M} ~ {board.window_end:%m-%d %H:%M}",
        "window_hours": round((board.window_end - board.window_start).total_seconds() / 3600),
        "llm_mode": board.llm_mode,
        "notes": board.notes,
        "topics": [
            {
                "id": t.topic_id,
                "rank": n,
                "name": t.name,
                "category": t.category,
                "score": round(t.score),
                "reason": t.reason,
                "trend": t.trend,
                "articles": t.articles,
                "links": t.links,
                "covered": [label_for(x) for x in t.covered_in if PROJECT_RE.match(x)],
                "opinion": _opinion_dict(t.opinion),
            }
            for n, t in enumerate(board.topics, start=1)
        ],
        "bundles": [
            {"topic_ids": b.topic_ids, "names": [names.get(i, i) for i in b.topic_ids], "link": b.link}
            for b in board.bundles
        ],
    }


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
        note = ""
        if k["key"] in ENV_OVERRIDDEN:
            note = "운영체제 환경변수에 다른 값이 있어 무시하고 .env(이 화면에서 저장한 값)를 씁니다"
        keys.append({**k, "set": bool(value), "masked": mask(value), "note": note})
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
            "chars_per_minute": cfg.script.chars_per_minute,
            "window_hours": cfg.board.window_hours,
            "subtitles": cfg.timeline.subtitles,
            "attribution": cfg.timeline.attribution,
        },
        "formats": {k: {"name": v.name, "minutes": v.minutes} for k, v in FORMATS.items()},
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
    """.env 저장(알려진 키만). Claude 키는 모양부터 검사한다(관리자 키·다른 값·공백)."""
    allowed = {k["key"] for k in KEYS}
    bad = set(values) - allowed
    if bad:
        raise ValueError(f"알 수 없는 키: {', '.join(sorted(bad))}")
    values = {k: v.strip() for k, v in values.items()}
    if values.get("ANTHROPIC_API_KEY") and key_problem(values["ANTHROPIC_API_KEY"]):
        raise ValueError(key_problem(values["ANTHROPIC_API_KEY"]))
    set_env(settings.root / ".env", values, template=settings.root / ".env.example")
    if values.get("ANTHROPIC_API_KEY"):
        auth_marker(settings).unlink(missing_ok=True)


def test_claude(settings: Settings) -> dict[str, Any]:
    """설정 화면 [연결 확인]: 키로 모델 정보를 조회한다(토큰 비용 없음)."""
    if settings.env("STUDIO_LLM").lower() == "offline":
        return {
            "ok": False,
            "message": "오프라인 모드(STUDIO_LLM=offline)로 실행 중이라 Claude를 쓰지 않습니다",
        }
    return check_connection(settings)


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
    if "chars_per_minute" in data:
        cpm = int(data["chars_per_minute"])
        if not 120 <= cpm <= 600:
            raise ValueError("분당 글자 수는 120~600")
        set_channel_value(settings, "script", "chars_per_minute", cpm)
    for key in ("subtitles", "attribution"):
        if key in data:
            set_channel_value(settings, "timeline", key, bool(data[key]))
    if "window_hours" in data:
        wh = int(data["window_hours"])
        if not 3 <= wh <= 72:
            raise ValueError("주제판 구간은 3~72시간")
        set_channel_value(settings, "board", "window_hours", wh)


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
    say("커뮤니티 게시판(글·본문·댓글)·뉴스 수집 중…")
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


def job_board(settings: Settings, say: Say, collect: bool = True) -> dict[str, Any]:
    """주제판 만들기(필요하면 먼저 수집): 지금 뜨는 주제 + 커뮤니티 의견 + 10분 묶음 추천."""
    if collect:
        _collect_if_stale(settings, say)
    cfg = load_channel(settings)
    llm = get_llm(settings)
    say(f"주제 묶고 커뮤니티 의견 정리하는 중… ({'Claude' if llm else '오프라인 휴리스틱'})")
    with open_store(settings) as store:
        board = tr.run_board(cfg, store, llm)
    say(f"주제 {len(board.topics)}개 · 10분 묶음 추천 {len(board.bundles)}개 — 화면에서 골라 주세요")
    if cfg.channel.slack_webhook:
        try:
            if asyncio.run(tr.send_slack(cfg, board_text(board))):
                say("Slack 전송 완료")
        except Exception as e:  # 알림 실패는 결과에 영향 없음
            say(f"⚠️ Slack 전송 실패: {e}")
    return {"board": board.id, "topics": len(board.topics), "bundles": len(board.bundles)}


def job_video(settings: Settings, say: Say, board_id: str, topic_ids: list[str], fmt: str) -> dict[str, Any]:
    """판에서 고른 주제로 영상 대본(10분 묶음 / 3분 단독)."""
    if fmt not in FORMATS:
        raise ValueError("형식은 long(10분 묶음) | short(3분 단독)")
    cfg = load_channel(settings)
    llm = get_llm(settings)
    say(
        f"{FORMATS[fmt].name} 대본 쓰는 중(주제 {len(topic_ids)}개)… ({'Claude' if llm else '오프라인 골격'})"
    )
    with open_store(settings) as store:
        res = tr.run_video(settings, cfg, store, board_id, topic_ids, fmt, llm)
    say(f"대본 초안 작성 완료 — 점검 {len(res.warnings)}건")
    return {"project": res.plan.id, "warnings": res.warnings}


def job_rewrite(settings: Settings, say: Say, pid: str) -> dict[str, Any]:
    """같은 계획으로 대본만 다시 쓰기(키를 넣은 뒤 Claude 대본으로 바꿀 때)."""
    d = project_path(settings, pid)
    cfg = load_channel(settings)
    with open_store(settings) as store:
        res = tr.run_rewrite(settings, cfg, store, d, get_llm(settings))
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
    with open_store(settings) as store:
        text = asyncio.run(tr.run_history(store, weeks))
    return {"markdown": text}


def job_probe(settings: Settings, say: Say, name: str | None = None) -> dict[str, Any]:
    """커뮤니티 게시판 점검(목록 + 글 상세 하나) → data/probe.json."""
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
                {
                    "at": f"{p.posted_at:%m-%d %H:%M}",
                    "comments": p.comments,
                    "votes": p.votes,
                    "title": p.title,
                }
                for p in r.posts[:5]
            ],
            "detail": {
                "url": r.detail_url,
                "body_chars": r.body_chars,
                "comments": r.comments_found,
                "comment_votes": r.comment_votes,
                "post_votes": r.post_votes,
                "error": r.detail_error,
                "comment_samples": [{"votes": v, "text": t} for v, t in r.comment_samples],
            },
            "board_hints": [{"name": n, "url": u} for n, u in r.board_hints],
            "ok": r.allowed and not r.error and bool(r.posts),
        }
        for r in results
    ]
    for r in out:
        dd = r["detail"]
        detail = (
            f" · 글 상세: 본문 {dd['body_chars']}자, 댓글 {dd['comments']}개"
            if dd["url"] and not dd["error"]
            else (f" · 글 상세 실패: {dd['error']}" if dd["error"] else "")
        )
        say(
            f"{r['name']}: "
            + (
                "정상" + detail
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


def job_auto(settings: Settings, say: Say, now: datetime) -> dict[str, Any]:
    """자동 수집: 커뮤니티·뉴스를 모아 둔다(주제판이 하루 흐름을 놓치지 않게). 주제판·대본은 원할 때."""
    res = job_collect(settings, say)
    return {"collected_at": now.isoformat(), "new": res["new"]}


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
    from newsclip.autosub import narration_text

    chars = len(narration_text(script).replace("\n", ""))
    speech = max(
        info.duration - sum(b - a for a, b in info.silences if a <= 0.05 or b >= info.duration - 0.05), 1.0
    )
    result = {
        "cues": len(cues),
        "duration": round(info.duration, 1),
        "sentences": sentences,
        "matched": matched,
        "boundaries": max(0, sentences - 1),
        "pauses": len(info.silences),
        "chars": chars,
        "chars_per_minute": round(chars / (speech / 60)),  # 앞뒤 무음을 뺀 실제 읽는 속도
    }
    (d / AUTO_MARK).write_text(json.dumps(result, ensure_ascii=False), encoding="utf-8")
    return result
