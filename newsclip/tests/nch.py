"""newsclip 테스트 도우미."""

from __future__ import annotations

from pathlib import Path

from newsclip.models import Asset, Scene, SceneSet, asset_id

ROOT = Path(__file__).resolve().parents[2]
FIX = ROOT / "fixtures" / "demo"


def asset(source: str = "naver_image", url: str = "https://img.example.com/a.jpg", **kw) -> Asset:
    """테스트용 자료."""
    base = dict(
        source=source, id=asset_id(source, url), url=url, download_url=url, source_domain="example.com"
    )
    base.update(kw)
    return Asset(**base)


def scene(sid: str, start: float, end: float, **kw) -> Scene:
    """테스트용 장면."""
    base = dict(
        id=sid,
        start_sec=start,
        end_sec=end,
        text="국회 본회의",
        topic="국회",
        keywords_ko=["국회", "본회의"],
        keywords_en=["National Assembly Korea"],
        kind="photo",
    )
    base.update(kw)
    return Scene(**base)


def sset(scenes: list[Scene]) -> SceneSet:
    """테스트용 SceneSet."""
    return SceneSet(project="2026-10-08_evening", scenes=scenes)
