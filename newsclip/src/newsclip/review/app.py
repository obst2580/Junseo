"""검토 UI(FastAPI + 단일 HTML, localhost:8765).

장면별 텍스트·타임코드·후보 썸네일 3개·출처를 보고, 클릭으로 chosen 변경, 건너뛰기,
유튜브 후보의 시작 초 조정을 한다. 저장하면 시작 초가 바뀐 영상은 구간을 다시 받고
scenes.json 갱신 + timeline.xml 재생성.
"""

from __future__ import annotations

import asyncio
from pathlib import Path

from fastapi import FastAPI, HTTPException
from fastapi.responses import HTMLResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel

from newsclip import pipeline
from newsclip.download import DedupeRegistry, download_video
from newsclip.models import SceneSet

INDEX = Path(__file__).with_name("index.html")


class ChooseBody(BaseModel):
    """후보 선택."""

    asset_id: str


class SkipBody(BaseModel):
    """건너뛰기."""

    skipped: bool


class ClipStartBody(BaseModel):
    """영상 시작 초."""

    asset_id: str
    sec: float


class ReviewSession:
    """프로젝트 하나의 검토 상태(선택·건너뛰기·영상 시작 초). 검토 UI와 studio 화면이 같이 쓴다."""

    def __init__(self, project: Path) -> None:
        self.project = project
        self.sset: SceneSet = pipeline.load_scenes(project)
        self.dirty_clips: set[tuple[str, str]] = set()

    def scene(self, scene_id: str):  # noqa: ANN201
        """장면 조회(없으면 404)."""
        for sc in self.sset.scenes:
            if sc.id == scene_id:
                return sc
        raise HTTPException(404, f"장면 {scene_id} 없음")

    def scenes_json(self, files_prefix: str = "/files") -> dict:
        """장면 목록 + 후보 미리보기 주소."""
        data = self.sset.model_dump(mode="json")
        root = self.project.resolve()
        for sc in data["scenes"]:
            for a in sc["candidates"]:
                lp = a.get("local_path")
                a["preview"] = (
                    f"{files_prefix}/{Path(lp).resolve().relative_to(root).as_posix()}" if lp else None
                )
        return data

    def choose(self, scene_id: str, asset_id: str) -> None:
        """후보 선택."""
        sc = self.scene(scene_id)
        a = next((c for c in sc.candidates if c.id == asset_id), None)
        if a is None or not a.local_path:
            raise HTTPException(400, "다운로드된 후보만 선택할 수 있습니다")
        sc.chosen = a
        sc.skipped = False

    def skip(self, scene_id: str, skipped: bool) -> None:
        """건너뛰기."""
        self.scene(scene_id).skipped = skipped

    def clip_start(self, scene_id: str, asset_id: str, sec: float) -> None:
        """영상 시작 초(저장할 때 구간을 다시 받는다)."""
        sc = self.scene(scene_id)
        a = next((c for c in sc.candidates if c.id == asset_id), None)
        if a is None or a.media != "video":
            raise HTTPException(400, "영상 후보가 아닙니다")
        a.clip_start_sec = max(0.0, sec)
        self.dirty_clips.add((scene_id, a.id))

    async def save(self) -> dict:
        """바뀐 영상 구간 재다운로드 → scenes.json 저장 → timeline.xml 재생성."""
        registry = DedupeRegistry()
        sem = asyncio.Semaphore(5)
        for scene_id, aid in list(self.dirty_clips):
            sc = self.scene(scene_id)
            idx = next(i for i, c in enumerate(sc.candidates) if c.id == aid)
            a = sc.candidates[idx]
            dest = Path(a.local_path) if a.local_path else self.project / "assets" / f"{sc.id}_v{idx + 1}.mp4"
            new = await download_video(a, dest, sc.duration, 20.0, registry, sem)
            if new is not None:
                sc.candidates[idx] = new
                if sc.chosen and sc.chosen.id == aid:
                    sc.chosen = new
        self.dirty_clips = set()
        pipeline.save_scenes(self.project, self.sset)
        audio = self.project / "narration.wav"
        errs = pipeline.write_xml(self.project, self.sset, audio=audio if audio.exists() else None)
        return {"ok": not errs, "errors": errs}


def create_app(project: Path) -> FastAPI:
    """프로젝트 하나를 검토하는 앱."""
    app = FastAPI(title="newsclip review")
    session = ReviewSession(project)
    app.mount("/files", StaticFiles(directory=str(project)), name="files")

    @app.get("/", response_class=HTMLResponse)
    def index() -> str:
        return INDEX.read_text(encoding="utf-8")

    @app.get("/api/scenes")
    def scenes() -> dict:
        return session.scenes_json()

    @app.post("/api/scenes/{scene_id}/choose")
    def choose(scene_id: str, body: ChooseBody) -> dict:
        session.choose(scene_id, body.asset_id)
        return {"ok": True}

    @app.post("/api/scenes/{scene_id}/skip")
    def skip(scene_id: str, body: SkipBody) -> dict:
        session.skip(scene_id, body.skipped)
        return {"ok": True}

    @app.post("/api/scenes/{scene_id}/clip_start")
    def clip_start(scene_id: str, body: ClipStartBody) -> dict:
        session.clip_start(scene_id, body.asset_id, body.sec)
        return {"ok": True}

    @app.post("/api/save")
    async def save() -> dict:
        return await session.save()

    return app
