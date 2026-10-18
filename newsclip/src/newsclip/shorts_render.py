"""세로 쇼츠 프로젝트 + 음성 파일 → 완성 mp4.

프로젝트의 장면(사진·영상 구간·출처)과 대본은 그대로 쓰고, 자막은 받은 음성에 맞춰 새로 나눈다.
효과음(화제 전환·질문)은 장면 문장에서 찾고, 배경음악은 있을 때만 깐다.
"""

from __future__ import annotations

import hashlib
import json
import subprocess
from dataclasses import replace
from pathlib import Path

from newsclip.autosub import SUB_LINE, AudioInfo, align, analyze, script_phrases, subtitle_cues
from newsclip.models import Scene
from newsclip.render import RED, YELLOW, CueArt, Overlay, RenderPlan, Shot, fit_sub_px, render, render_cue
from newsclip.sfx import ensure_sfx, sfx_cues
from newsclip.srt import parse_srt

SIZE = (1080, 1920)
HOOK_SEC = 1.5  # 첫 문장을 크게 띄우는 시간(초)
HOOK_PX = 124  # 훅 자막 글자 크기(px, 폭이 넘으면 자동으로 줄어든다)
HOOK_CENTER_Y = 965  # 훅 자막 가운데 위치(사진 칸 560~1370의 가운데)


def probe_duration(path: Path) -> float:
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "json", str(path)],
        capture_output=True,
        text=True,
        check=True,
    ).stdout
    return float(json.loads(out)["format"]["duration"])


def caption_name(attribution: str) -> str:
    """출처 표시 PNG 이름(newsclip.fcpxml과 같은 규칙: 기본 디자인 문구의 sha1 앞 8자리)."""
    return f"captions/cap_{hashlib.sha1(attribution.encode()).hexdigest()[:8]}.png"


def shots_for(scenes: list[dict], asset_dir: Path, total: float, videos: dict[str, Path]) -> list[Shot]:
    """장면 → 화면 조각. 장면 사이 틈은 앞 조각을 늘려 메운다. videos[장면 id]가 있으면 그 장면은 영상(길이 안)으로."""
    shots: list[Shot] = []
    for n, sc in enumerate(sorted(scenes, key=lambda s: s["start"]), start=1):
        start, end = float(sc["start"]), float(sc["end"])
        photo = asset_dir / sc["chosen"]
        zoom = (1.0, 1.04) if n % 2 else (1.04, 1.0)  # 홀수 장면 줌인, 짝수 줌아웃(fcpxml과 같은 규칙)
        clip = videos.get(sc["id"])
        if clip is not None:
            span = min(end - start, probe_duration(clip))
            shots.append(Shot(clip, "video", start, start + span, in_sec=0.0))
            if start + span < end:
                shots.append(Shot(photo, "image", start + span, end, zoom=zoom))
        else:
            shots.append(Shot(photo, "image", start, end, zoom=zoom))
    if shots:
        shots[0] = replace(shots[0], start=0.0)
        shots[-1] = replace(shots[-1], end=total)
    filled: list[Shot] = []
    for shot in shots:
        if filled and filled[-1].end < shot.start:
            filled[-1] = replace(filled[-1], end=shot.start)
        filled.append(shot)
    return [s for s in filled if s.end > s.start]


def wrap_hook(text: str, limit: int = 8) -> str:
    """훅 자막은 크게 띄우므로 한 줄을 공백 뺀 8글자 안쪽으로 다시 나눈다."""
    lines: list[str] = []
    cur = ""
    for word in text.replace("\n", " ").split():
        cand = f"{cur} {word}".strip()
        if cur and len(cand.replace(" ", "")) > limit:
            lines.append(cur)
            cur = word
        else:
            cur = cand
    if cur:
        lines.append(cur)
    return "\n".join(lines)


def subtitles_for(
    script: str,
    audio: Path,
    size: tuple[int, int],
    asset_dir: Path,
    keywords: set[str],
    srt: Path | None = None,
) -> tuple[list[CueArt], list[tuple[float, float]], AudioInfo]:
    """대본 + 음성 → 자막 그림 목록(음성 쉼에 맞춘 한 줄씩)."""
    info = analyze(audio)
    if srt is not None:
        # 받은 자막(SRT)을 그대로 쓴다: 제작실에서 본 자막과 같은 시간
        lines = parse_srt(srt.read_text(encoding="utf-8"))
    else:
        cues, _matched = align(script_phrases(script), info)
        lines = subtitle_cues(cues, limit=SUB_LINE, info=info)
    px = fit_sub_px([cue.text for cue in lines], size)
    arts = []
    for k, cue in enumerate(lines):
        art = render_cue(
            cue.text, asset_dir / "subtitles" / f"cue_{k:03d}.png", size, keywords=keywords, px=px
        )
        if art is None:
            continue
        if k == 0 and cue.start < HOOK_SEC:
            # 첫 문장은 첫 1.5초 동안 화면 가운데에 크게 띄운 뒤, 아래 자막으로 이어 간다
            hook_end = min(HOOK_SEC, cue.end)
            hook = render_cue(
                wrap_hook(cue.text),
                asset_dir / "subtitles" / "hook.png",
                size,
                keywords=keywords,
                px=HOOK_PX,
                center_y=HOOK_CENTER_Y,
                text_color=YELLOW,
                hot_color=RED,
            )
            arts.append(_timed(hook, cue.start, hook_end))
            if cue.end > hook_end:
                arts.append(_timed(art, hook_end, cue.end))
            continue
        arts.append(_timed(art, cue.start, cue.end))
    return arts, [(cue.start, cue.end) for cue in lines], info


def _timed(art: CueArt, start: float, end: float) -> CueArt:
    return replace(art, start=start, end=end)


def build_plan(
    project: dict,
    asset_dir: Path,
    audio: Path,
    *,
    srt: Path | None = None,
    bgm: Path | None = None,
    videos: dict[str, Path] | None = None,
    keywords: set[str] = frozenset(),
) -> RenderPlan:
    """프로젝트 dict(script, thumb, scenes) + 자료 폴더 + 음성 → 렌더 계획."""
    from newsclip.autosub import wav_duration

    total = wav_duration(audio)
    scenes = project["scenes"]
    shots = shots_for(scenes, asset_dir, total, videos or {})
    # 핵심어: 호출자가 준 말 + 첫 장면 검색어의 말(훅에서 빨간색으로 강조)
    first = min(scenes, key=lambda s: float(s["start"]))
    query_words = {w for w in str(first.get("query", "")).split() if len(w) >= 2}
    cues, _spans, _info = subtitles_for(
        project["script"], audio, SIZE, asset_dir, set(keywords) | query_words
    )
    cues = [c for c in cues if c.start < total]
    caps = [
        Overlay(asset_dir / caption_name(sc["attribution"]), float(sc["start"]), float(sc["end"]))
        for sc in scenes
        if sc.get("attribution") and (asset_dir / caption_name(sc["attribution"])).exists()
    ]
    head = next(iter(sorted((asset_dir / "overlays").glob("headline_*.png"))), None)
    scene_models = [
        Scene(
            id=sc["id"],
            start_sec=float(sc["start"]),
            end_sec=float(sc["end"]),
            text=sc["text"],
            topic=sc["topic"],
        )
        for sc in scenes
    ]
    sfx_dir = ensure_sfx(asset_dir)
    sfx = [(cue.at, sfx_dir[cue.name]) for cue in sfx_cues(scene_models) if cue.at < total]
    return RenderPlan(
        shots=shots,
        total=total,
        narration=audio,
        size=SIZE,
        headline=head,
        captions=caps,
        cues=cues,
        sfx=sfx,
        bgm=bgm,
    )


def render_short(
    project: dict,
    asset_dir: Path,
    audio: Path,
    out: Path,
    *,
    srt: Path | None = None,
    bgm: Path | None = None,
    videos: dict[str, Path] | None = None,
    keywords: set[str] = frozenset(),
) -> Path:
    plan = build_plan(project, asset_dir, audio, srt=srt, bgm=bgm, videos=videos, keywords=keywords)
    return render(plan, out)
