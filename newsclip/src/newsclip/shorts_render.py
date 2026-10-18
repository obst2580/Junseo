"""세로 쇼츠 프로젝트 + 음성 파일 → 완성 mp4.

프로젝트의 장면(사진·영상 구간·출처)과 대본은 그대로 쓰고, 자막은 받은 음성에 맞춰 새로 나눈다.
효과음(화제 전환·질문)은 장면 문장에서 찾고, 배경음악은 있을 때만 깐다.
"""

from __future__ import annotations

import hashlib
import json
import math
import subprocess
from dataclasses import replace
from pathlib import Path

from PIL import Image

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


def _photo_pool(sc: dict, asset_dir: Path) -> list[Path]:
    """장면의 사진 목록: 골라 쓴 사진 먼저, 그다음 같은 장면의 다른 후보 사진."""
    names = [sc["chosen"]] + [n for n in sc.get("files", []) if n != sc["chosen"] and n.startswith("assets/")]
    return [asset_dir / n for n in names if (asset_dir / n).exists()]


def _pieces(start: float, end: float, max_sec: float | None) -> list[tuple[float, float]]:
    """장면을 max_sec 이하 조각으로 나눈다(사진 간격 줄이기). max_sec가 없으면 그대로."""
    if not max_sec or end - start <= max_sec:
        return [(start, end)]
    k = math.ceil((end - start) / max_sec)
    step = (end - start) / k
    return [(start + i * step, start + (i + 1) * step) for i in range(k)]


def shots_for(
    scenes: list[dict],
    asset_dir: Path,
    total: float,
    videos: dict[str, Path],
    max_shot_sec: float | None = None,
) -> list[Shot]:
    """장면 → 화면 조각. 장면 사이 틈은 앞 조각을 늘려 메운다. videos[장면 id]가 있으면 그 장면은 영상(길이 안)으로.
    max_shot_sec를 주면 사진 한 장이 그보다 길게 나오지 않도록 장면을 나눈다(같은 장면의 다른 사진을 번갈아 쓴다)."""
    shots: list[Shot] = []
    count = 0  # 줌 방향을 조각 순서대로 번갈아 쓰려고 센다(나누지 않으면 장면 번호와 같다)
    for sc in sorted(scenes, key=lambda s: s["start"]):
        start, end = float(sc["start"]), float(sc["end"])
        clip = videos.get(sc["id"])
        if clip is not None:
            count += 1
            span = min(end - start, probe_duration(clip))
            shots.append(Shot(clip, "video", start, start + span, in_sec=0.0))
            if start + span < end:
                zoom = (1.0, 1.04) if count % 2 else (1.04, 1.0)
                shots.append(Shot(asset_dir / sc["chosen"], "image", start + span, end, zoom=zoom))
            continue
        pool = _photo_pool(sc, asset_dir)
        for i, (a, b) in enumerate(_pieces(start, end, max_shot_sec)):
            count += 1
            zoom = (
                (1.0, 1.04) if count % 2 else (1.04, 1.0)
            )  # 홀수 조각 줌인, 짝수 줌아웃(fcpxml과 같은 규칙)
            photo = pool[i % len(pool)] if pool else asset_dir / sc["chosen"]
            shots.append(Shot(photo, "image", a, b, zoom=zoom))
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
    words: list | None = None,
) -> tuple[list[CueArt], list[tuple[float, float]], AudioInfo]:
    """대본 + 음성 → 자막 그림 목록(음성 쉼에 맞춘 한 줄씩). words(타입캐스트 낱말 시각)가 있으면 그 시각에 맞춘다."""
    info = analyze(audio)
    if srt is not None:
        # 받은 자막(SRT)을 그대로 쓴다: 제작실에서 본 자막과 같은 시간
        lines = parse_srt(srt.read_text(encoding="utf-8"))
    else:
        cues, _matched = align(script_phrases(script), info, words=words)
        lines = subtitle_cues(cues, limit=SUB_LINE, info=info, words=words)
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
    max_shot_sec: float | None = None,
    words: list | None = None,
) -> RenderPlan:
    """프로젝트 dict(script, thumb, scenes) + 자료 폴더 + 음성 → 렌더 계획."""
    from newsclip.autosub import wav_duration

    total = wav_duration(audio)
    scenes = project["scenes"]
    shots = shots_for(scenes, asset_dir, total, videos or {}, max_shot_sec)
    # 핵심어: 호출자가 준 말 + 첫 장면 검색어의 말(훅에서 빨간색으로 강조)
    first = min(scenes, key=lambda s: float(s["start"]))
    query_words = {w for w in str(first.get("query", "")).split() if len(w) >= 2}
    cues, _spans, _info = subtitles_for(
        project["script"], audio, SIZE, asset_dir, set(keywords) | query_words, srt=srt, words=words
    )
    cues = [c for c in cues if c.start < total]
    caps = []
    for k, sc in enumerate(scenes):
        src = asset_dir / caption_name(sc["attribution"]) if sc.get("attribution") else None
        if src is None or not src.exists():
            continue
        # 출처 그림은 전체 화면 크기라서, 글자가 있는 부분만 잘라 쓴다(렌더 속도)
        img = Image.open(src).convert("RGBA")
        box = img.getchannel("A").getbbox()
        if box is None:
            continue
        cut = asset_dir / "subtitles" / f"cap_{k:03d}.png"
        cut.parent.mkdir(parents=True, exist_ok=True)
        img.crop(box).save(cut, "PNG")
        caps.append(Overlay(cut, float(sc["start"]), float(sc["end"]), x=box[0], y=box[1]))
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
    max_shot_sec: float | None = None,
    words: list | None = None,
) -> Path:
    plan = build_plan(
        project,
        asset_dir,
        audio,
        srt=srt,
        bgm=bgm,
        videos=videos,
        keywords=keywords,
        max_shot_sec=max_shot_sec,
        words=words,
    )
    return render(plan, out)


def voice_and_render(
    project: dict,
    asset_dir: Path,
    out: Path,
    *,
    voice_id: str,
    tempo: float = 1.0,
    remove_silence_ms: int | None = None,
    bgm: Path | None = None,
    max_shot_sec: float | None = None,
    keywords: set[str] = frozenset(),
    client=None,  # noqa: ANN001 - 시험용 httpx.Client
) -> Path:
    """대본을 타입캐스트로 읽고(속도·쉼 조절), 장면 시간을 새 음성에 다시 맞춘 뒤 완성 mp4를 만든다."""
    from newsclip.autosub import narration_text
    from newsclip.typecast_tts import retime_scenes, synthesize

    speech = synthesize(
        narration_text(project["script"]),
        voice_id,
        asset_dir / "narration_typecast.wav",
        tempo=tempo,
        remove_silence_ms=remove_silence_ms,
        client=client,
    )
    retimed = {**project, "scenes": retime_scenes(project["scenes"], speech.chars, speech.duration)}
    return render_short(
        retimed,
        asset_dir,
        speech.path,
        out,
        bgm=bgm,
        keywords=keywords,
        max_shot_sec=max_shot_sec,
        words=speech.words,
    )
