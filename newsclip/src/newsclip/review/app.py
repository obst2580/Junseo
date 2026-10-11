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


def create_app(project: Path) -> FastAPI:
    """프로젝트 하나를 검토하는 앱."""
    app = FastAPI(title="newsclip review")
    state: dict[str, object] = {"sset": pipeline.load_scenes(project), "dirty_clips": set()}

    def sset() -> SceneSet:
        return state["sset"]  # type: ignore[return-value]

    def scene(scene_id: str):  # noqa: ANN202
        for sc in sset().scenes:
            if sc.id == scene_id:
                return sc
        raise HTTPException(404, f"장면 {scene_id} 없음")

    app.mount("/files", StaticFiles(directory=str(project)), name="files")

    @app.get("/", response_class=HTMLResponse)
    def index() -> str:
        return INDEX.read_text(encoding="utf-8")

    @app.get("/api/scenes")
    def scenes() -> dict:
        data = sset().model_dump(mode="json")
        for sc in data["scenes"]:
            for a in sc["candidates"]:
                lp = a.get("local_path")
                a["preview"] = f"/files/{Path(lp).resolve().relative_to(project)}" if lp else None
        return data

    @app.post("/api/scenes/{scene_id}/choose")
    def choose(scene_id: str, body: ChooseBody) -> dict:
        sc = scene(scene_id)
        a = next((c for c in sc.candidates if c.id == body.asset_id), None)
        if a is None or not a.local_path:
            raise HTTPException(400, "다운로드된 후보만 선택할 수 있습니다")
        sc.chosen = a
        sc.skipped = False
        return {"ok": True}

    @app.post("/api/scenes/{scene_id}/skip")
    def skip(scene_id: str, body: SkipBody) -> dict:
        scene(scene_id).skipped = body.skipped
        return {"ok": True}

    @app.post("/api/scenes/{scene_id}/clip_start")
    def clip_start(scene_id: str, body: ClipStartBody) -> dict:
        sc = scene(scene_id)
        a = next((c for c in sc.candidates if c.id == body.asset_id), None)
        if a is None or a.media != "video":
            raise HTTPException(400, "영상 후보가 아닙니다")
        a.clip_start_sec = max(0.0, body.sec)
        state["dirty_clips"].add((scene_id, a.id))  # type: ignore[union-attr]
        return {"ok": True}

    @app.post("/api/save")
    async def save() -> dict:
        registry = DedupeRegistry()
        sem = asyncio.Semaphore(5)
        for scene_id, aid in list(state["dirty_clips"]):  # type: ignore[union-attr]
            sc = scene(scene_id)
            idx = next(i for i, c in enumerate(sc.candidates) if c.id == aid)
            a = sc.candidates[idx]
            dest = Path(a.local_path) if a.local_path else project / "assets" / f"{sc.id}_v{idx + 1}.mp4"
            new = await download_video(a, dest, sc.duration, 20.0, registry, sem)
            if new is not None:
                sc.candidates[idx] = new
                if sc.chosen and sc.chosen.id == aid:
                    sc.chosen = new
        state["dirty_clips"] = set()
        pipeline.save_scenes(project, sset())
        audio = project / "narration.wav"
        errs = pipeline.write_xml(project, sset(), audio=audio if audio.exists() else None)
        return {"ok": not errs, "errors": errs}

    return app
