"""studio 화면 서버(FastAPI). 브라우저 화면 하나로 편성 → 대본 → 자막 → 자료 → XML → 업로드 기록까지.

이 컴퓨터에서만 연다(127.0.0.1). 오래 걸리는 일은 JobRunner가 한 번에 하나씩 돌린다.
"""

from __future__ import annotations

import os
import subprocess
import sys
from datetime import date, datetime
from pathlib import Path
from typing import Any

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import HTMLResponse, JSONResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel

from newsclip.review.app import ReviewSession
from studio_app import services as sv
from studio_app.auto import AutoRunner
from studio_app.jobs import Busy, JobRunner
from studio_core.config import KST, Settings

INDEX = Path(__file__).with_name("static") / "index.html"
ALLOWED_HOSTS = {"127.0.0.1", "localhost", "[::1]", "testserver"}


class SlotBody(BaseModel):
    """편성 만들기."""

    slot: str
    date: str | None = None
    collect: bool = True
    draft: bool = True  # 주제 후보까지만(대본은 고른 뒤)


class CollectBody(BaseModel):
    """수집."""

    since: str | None = None


class ScriptBody(BaseModel):
    """대본 저장."""

    text: str


class FinalizePick(BaseModel):
    """고른 주제 하나."""

    topic_id: str
    role: str


class FinalizeBody(BaseModel):
    """주제 고르기 결과(화면 순서대로)."""

    items: list[FinalizePick]


class PickBody(BaseModel):
    """주제 교체."""

    order: int
    topic_id: str


class MadeBody(BaseModel):
    """업로드 기록."""

    video: str
    title: str | None = None


class ChooseBody(BaseModel):
    """자료 후보 선택."""

    asset_id: str


class SkipBody(BaseModel):
    """장면 건너뛰기."""

    skipped: bool


class ClipBody(BaseModel):
    """영상 시작 초."""

    asset_id: str
    sec: float


class KeysBody(BaseModel):
    """API 키."""

    values: dict[str, str]


class ChannelBody(BaseModel):
    """채널 기본 설정."""

    youtube_channel_id: str | None = None
    competitors: list[str] | str | None = None
    slack_webhook: str | None = None
    tone: str | None = None
    weights: dict[str, float] | None = None
    selection_mode: str | None = None


class CommunityBody(BaseModel):
    """게시판 켜기·끄기."""

    name: str
    enabled: bool


class RawBody(BaseModel):
    """channel.yaml 전체."""

    text: str


class ToggleBody(BaseModel):
    """자동 실행."""

    enabled: bool


class HistoryBody(BaseModel):
    """성과 분석."""

    weeks: int = 4


class ProbeBody(BaseModel):
    """게시판 점검."""

    name: str | None = None


def _parse_dt(value: str | None) -> datetime | None:
    if not value:
        return None
    dt = datetime.fromisoformat(value)
    return dt.replace(tzinfo=KST) if dt.tzinfo is None else dt


def open_folder(path: Path) -> None:
    """탐색기·Finder로 폴더 열기."""
    if sys.platform == "darwin":
        subprocess.Popen(["open", str(path)])
    elif os.name == "nt":
        os.startfile(str(path))  # type: ignore[attr-defined]  # noqa: S606
    else:
        subprocess.Popen(["xdg-open", str(path)])


def create_app(settings: Settings, *, inline_jobs: bool = False, auto: bool = True) -> FastAPI:
    """화면 앱. inline_jobs=True면 작업을 요청 안에서 바로 끝낸다(테스트용)."""
    app = FastAPI(title="studio")

    @app.middleware("http")
    async def local_only(request: Request, call_next: Any) -> Any:
        # 다른 웹사이트가 이 컴퓨터의 화면 서버로 요청을 보내지 못하게(DNS 리바인딩·교차 사이트 요청 차단):
        # 주소는 127.0.0.1/localhost만, 바꾸는 요청은 화면이 붙이는 X-Studio 헤더가 있어야 한다.
        host = request.headers.get("host", "").rsplit(":", 1)[0]
        if host not in ALLOWED_HOSTS:
            return JSONResponse({"error": "이 컴퓨터(127.0.0.1)에서만 열 수 있습니다"}, status_code=403)
        if request.method not in ("GET", "HEAD") and request.headers.get("x-studio") != "1":
            return JSONResponse({"error": "studio 화면에서만 바꿀 수 있습니다"}, status_code=403)
        return await call_next(request)

    runner = JobRunner(inline=inline_jobs)
    reviews: dict[str, tuple[float, ReviewSession]] = {}
    settings.ensure_dirs()

    def start(kind: str, label: str, fn: Any, **params: Any) -> dict[str, Any]:
        try:
            job = runner.start(kind, label, fn, settings, **params)
        except Busy as e:
            raise HTTPException(409, str(e)) from e
        return job.as_dict()

    def auto_trigger(now: datetime) -> bool:
        if runner.busy():
            return False
        runner.start("daily", f"자동 실행 {now:%H:%M}", sv.job_daily, settings, now=now)
        return True

    autorun = AutoRunner(settings.data_dir / "app.json", auto_trigger)
    app.state.runner = runner
    app.state.autorun = autorun
    if auto:
        autorun.start()

    def guard(fn: Any, *args: Any) -> Any:
        try:
            return fn(*args)
        except FileNotFoundError as e:
            raise HTTPException(404, str(e)) from e
        except (ValueError, KeyError) as e:
            raise HTTPException(400, str(e).strip("'\"")) from e

    def review(pid: str) -> ReviewSession:
        d = guard(sv.project_path, settings, pid)
        sj = d / "scenes.json"
        if not sj.exists():
            raise HTTPException(404, "아직 자료 수집 전입니다")
        mtime = sj.stat().st_mtime
        cached = reviews.get(pid)
        if cached is None or cached[0] != mtime:
            cached = (mtime, ReviewSession(d))
            reviews[pid] = cached
        return cached[1]

    app.mount("/files", StaticFiles(directory=str(settings.projects_dir)), name="files")

    @app.get("/", response_class=HTMLResponse)
    def index() -> str:
        return INDEX.read_text(encoding="utf-8")

    # ---- 상태·작업 ------------------------------------------------------------------------------
    @app.get("/api/status")
    def status() -> dict:
        return {**sv.status(settings), "auto": autorun.info(), "busy": runner.busy()}

    @app.get("/api/jobs/current")
    def current_job() -> dict:
        job = runner.current
        return {"job": job.as_dict() if job else None}

    @app.post("/api/jobs/cancel")
    def cancel_job() -> dict:
        job = runner.cancel()
        if job is None:
            raise HTTPException(409, "중지할 작업이 없습니다")
        return job.as_dict()

    @app.post("/api/jobs/collect")
    def collect(body: CollectBody) -> dict:
        since = _parse_dt(body.since)
        return start("collect", "뉴스·커뮤니티 수집", sv.job_collect, since=since)

    @app.post("/api/jobs/slot")
    def slot(body: SlotBody) -> dict:
        if body.slot not in sv.SLOT_ORDER:
            raise HTTPException(400, "slot은 morning | evening | weekly")
        day = date.fromisoformat(body.date) if body.date else sv.default_date(body.slot)
        return start(
            "slot",
            f"{sv.label_for(day, body.slot)} 편성",
            sv.job_slot,
            slot=body.slot,
            day=day,
            collect=body.collect,
            draft=body.draft,
        )

    @app.post("/api/jobs/history")
    def history(body: HistoryBody) -> dict:
        return start("history", "성과 분석", sv.job_history, weeks=max(1, min(body.weeks, 26)))

    @app.post("/api/jobs/probe")
    def probe(body: ProbeBody) -> dict:
        return start("probe", "커뮤니티 게시판 점검", sv.job_probe, name=body.name)

    # ---- 프로젝트 -------------------------------------------------------------------------------
    @app.get("/api/projects")
    def projects() -> dict:
        return {"projects": sv.list_projects(settings)}

    @app.get("/api/projects/{pid}")
    def project(pid: str) -> dict:
        return guard(sv.project_detail, settings, pid)

    @app.put("/api/projects/{pid}/script")
    def save_script(pid: str, body: ScriptBody) -> dict:
        return {"warnings": guard(sv.save_script, settings, pid, body.text)}

    @app.post("/api/projects/{pid}/rewrite")
    def rewrite(pid: str) -> dict:
        guard(sv.project_path, settings, pid)
        return start("rewrite", "대본 다시 쓰기", sv.job_rewrite, pid=pid)

    @app.post("/api/projects/{pid}/finalize")
    def finalize(pid: str, body: FinalizeBody) -> dict:
        guard(sv.project_path, settings, pid)
        if not body.items:
            raise HTTPException(400, "주제를 하나 이상 골라 주세요")
        picks = [[p.topic_id, p.role] for p in body.items]
        return start("finalize", "대본 만들기", sv.job_finalize, pid=pid, picks=picks)

    @app.get("/api/projects/{pid}/candidates")
    def candidates(pid: str, order: int) -> dict:
        return {"candidates": guard(sv.replace_candidates, settings, pid, order)}

    @app.post("/api/projects/{pid}/pick")
    def pick(pid: str, body: PickBody) -> dict:
        guard(sv.project_path, settings, pid)
        return start(
            "pick",
            f"{body.order}번 주제 교체",
            sv.job_pick,
            pid=pid,
            order=body.order,
            topic_id=body.topic_id,
        )

    @app.put("/api/projects/{pid}/upload/{name}")
    async def upload(pid: str, name: str, request: Request) -> dict:
        d = guard(sv.project_path, settings, pid)
        if name not in sv.UPLOAD_NAMES:
            raise HTTPException(400, f"올릴 수 있는 파일: {', '.join(sorted(sv.UPLOAD_NAMES))}")
        tmp = d / f".{name}.part"
        size = 0
        with tmp.open("wb") as f:
            async for chunk in request.stream():
                size += len(chunk)
                f.write(chunk)
        if size == 0:
            tmp.unlink(missing_ok=True)
            raise HTTPException(400, "빈 파일입니다")
        if name.endswith(".srt"):
            from newsclip.srt import parse_srt

            text = tmp.read_bytes().decode("utf-8-sig", errors="replace")
            try:
                cues = parse_srt(text)
            except Exception as e:  # 형식 오류를 그대로 보여 준다
                tmp.unlink(missing_ok=True)
                raise HTTPException(400, f"SRT 형식 오류: {e}") from e
            if not cues:
                tmp.unlink(missing_ok=True)
                raise HTTPException(400, "SRT에서 자막을 하나도 못 읽었습니다")
            tmp.write_text(text, encoding="utf-8")
        tmp.replace(d / name)
        if name.endswith(".srt"):
            (d / sv.AUTO_MARK).unlink(missing_ok=True)  # 직접 만든 자막을 올리면 자동 자막 표시 해제
        return {"ok": True, "name": name, "bytes": size}

    @app.get("/api/projects/{pid}/narration-text")
    def narration_text(pid: str) -> dict:
        return guard(sv.narration_info, settings, pid)

    @app.put("/api/projects/{pid}/upload-audio")
    async def upload_audio(pid: str, filename: str, request: Request) -> dict:
        d = guard(sv.project_path, settings, pid)
        tmp = d / ".narration_upload.part"
        size = 0
        with tmp.open("wb") as f:
            async for chunk in request.stream():
                size += len(chunk)
                f.write(chunk)
        if size == 0:
            tmp.unlink(missing_ok=True)
            raise HTTPException(400, "빈 파일입니다")
        try:
            return {"ok": True, **sv.save_audio(settings, pid, tmp, filename)}
        except (ValueError, FileNotFoundError) as e:
            raise HTTPException(400, str(e)) from e
        except Exception as e:  # ffmpeg 변환 실패 등
            raise HTTPException(500, f"음성 처리 실패: {e}") from e

    @app.post("/api/projects/{pid}/autosub")
    def autosub(pid: str) -> dict:
        return {"ok": True, **guard(sv.make_autosub, settings, pid)}

    @app.post("/api/projects/{pid}/newsclip")
    def newsclip_run(pid: str) -> dict:
        guard(sv.project_path, settings, pid)
        reviews.pop(pid, None)
        return start("newsclip", "자료 수집", sv.job_newsclip, pid=pid)

    @app.get("/api/projects/{pid}/scenes")
    def scenes(pid: str) -> dict:
        return review(pid).scenes_json(files_prefix=f"/files/{pid}")

    @app.post("/api/projects/{pid}/scenes/{sid}/choose")
    def choose(pid: str, sid: str, body: ChooseBody) -> dict:
        review(pid).choose(sid, body.asset_id)
        return {"ok": True}

    @app.post("/api/projects/{pid}/scenes/{sid}/skip")
    def skip(pid: str, sid: str, body: SkipBody) -> dict:
        review(pid).skip(sid, body.skipped)
        return {"ok": True}

    @app.post("/api/projects/{pid}/scenes/{sid}/clip_start")
    def clip_start(pid: str, sid: str, body: ClipBody) -> dict:
        review(pid).clip_start(sid, body.asset_id, body.sec)
        return {"ok": True}

    @app.post("/api/projects/{pid}/scenes/save")
    async def save_scenes(pid: str) -> dict:
        sess = review(pid)
        res = await sess.save()
        reviews[pid] = ((sv.project_path(settings, pid) / "scenes.json").stat().st_mtime, sess)
        return res

    @app.post("/api/projects/{pid}/made")
    def made(pid: str, body: MadeBody) -> dict:
        guard(sv.project_path, settings, pid)
        guard(sv.parse_video_id, body.video)
        return start("made", "업로드 기록", sv.job_made, pid=pid, video=body.video, title=body.title)

    @app.post("/api/projects/{pid}/open-folder")
    def open_project_folder(pid: str) -> dict:
        d = guard(sv.project_path, settings, pid)
        try:
            open_folder(d)
        except OSError as e:
            raise HTTPException(500, f"폴더를 열지 못했습니다: {e}") from e
        return {"ok": True, "path": str(d)}

    # ---- 설정 ----------------------------------------------------------------------------------
    @app.get("/api/settings")
    def get_settings() -> dict:
        return {**sv.settings_info(settings), "auto": autorun.info()}

    @app.put("/api/settings/keys")
    def put_keys(body: KeysBody) -> dict:
        guard(sv.save_keys, settings, body.values)
        return {"ok": True}

    @app.put("/api/settings/channel")
    def put_channel(body: ChannelBody) -> dict:
        guard(sv.save_channel, settings, body.model_dump(exclude_none=True))
        return {"ok": True}

    @app.put("/api/settings/community")
    def put_community(body: CommunityBody) -> dict:
        guard(sv.toggle_community, settings, body.name, body.enabled)
        return {"ok": True}

    @app.put("/api/settings/raw")
    def put_raw(body: RawBody) -> dict:
        guard(sv.save_raw, settings, body.text)
        return {"ok": True}

    @app.put("/api/settings/auto")
    def put_auto(body: ToggleBody) -> dict:
        return autorun.set_enabled(body.enabled)

    @app.exception_handler(HTTPException)
    async def http_error(_request: Request, exc: HTTPException) -> JSONResponse:
        return JSONResponse({"error": exc.detail}, status_code=exc.status_code)

    return app
