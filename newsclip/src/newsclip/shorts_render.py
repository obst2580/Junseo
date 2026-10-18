"""세로 쇼츠 프로젝트 + 음성 파일 → 완성 mp4.

프로젝트의 장면(사진·영상 구간·출처)과 대본은 그대로 쓰고, 자막은 받은 음성에 맞춰 새로 나눈다.
효과음(화제 전환·질문)은 장면 문장에서 찾고, 배경음악은 있을 때만 깐다.
"""

from __future__ import annotations

import hashlib
import json
import math
import re
import subprocess
from dataclasses import replace
from pathlib import Path

from PIL import Image

from newsclip.autosub import SUB_LINE, AudioInfo, align, analyze, script_phrases, subtitle_cues
from newsclip.models import Scene
from newsclip.render import (
    EMPH_GREEN,
    EMPH_RED,
    EMPH_YELLOW,
    RED,
    YELLOW,
    CueArt,
    Overlay,
    RenderPlan,
    Shot,
    fit_sub_px,
    is_hot,
    render,
    render_cue,
    render_emph,
)
from newsclip.sfx import ensure_sfx, sfx_cues
from newsclip.srt import parse_srt
from newsclip.vertical import BOX

SIZE = (1080, 1920)
HOOK_SEC = 3.5  # 훅: 이 시각 전에 시작하는 첫 문장 자막을 짧게 잘라 빠르게 보여 준다
HOOK_CHUNK = 6  # 훅 한 번에 보이는 글자 수(공백 빼고)
HOOK_PX = 140  # 훅 자막 글자 크기(px, 폭이 넘으면 자동으로 줄어든다)
HOOK_CENTER_Y = (BOX[1] + BOX[3]) // 2  # 훅 자막은 사진 칸 한가운데에 띄운다
# 컷 편집: 자막이 바뀌는 때에 맞춰 사진을 1.5~2초마다 바꾼다(같은 사진은 넓게 → 가깝게 끊어 쓴다)
SHOT_SEC = 2.0  # 한 컷 목표 길이
SHOT_MIN = 1.2  # 이보다 짧은 컷은 만들지 않는다
SHOT_MAX = 3.2  # 자막 경계가 없어도 이보다 길면 나눈다
PUNCH = 1.32  # 가깝게 다시 쓸 때 확대 배율
PUSH = 0.06  # 한 컷 동안 천천히 다가가는 정도
FOCUS = (0.5, 0.33)  # 확대 기준점(사진 칸 위쪽 가운데 — 보통 얼굴 자리)
XFADE_SEC = 0.16  # 화제가 바뀌는 장면에서 빠른 확대 전환
EMPH_MAX = 12  # 강조 자막(색 글자)이 될 수 있는 자막 길이(공백 빼고)
EMPH_GAP = 4.0  # 강조 자막 사이 최소 간격(초) — 너무 자주 나오면 강조가 아니다
# 센 말 → 빨강, 따옴표로 옮긴 말(직접 인용) → 노랑, 핵심 인물·키워드 → 초록
STRONG = re.compile(
    "폭로|막말|논란|의혹|충격|비판|비난|반발|구속|탄핵|거짓|위반|폭동|망언|파문|저격|고발|기소|경고|분노|격돌|최악"
    "|배신|자멸|민낯|죽이|막장|몰락|폭망|박살"
)
QUOTE = re.compile("[\"“”‘’']")


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


def cut_pieces(
    start: float, end: float, cuts: list[float], target: float = SHOT_SEC, min_len: float = SHOT_MIN
) -> list[tuple[float, float]]:
    """장면 구간을 자막 경계(cuts)에서 target초 안팎 컷으로 나눈다. 경계가 없어 SHOT_MAX보다 긴 컷은 고르게 나눈다."""
    out: list[tuple[float, float]] = []
    cur = start
    inner = sorted(c for c in cuts if start < c < end - min_len)
    for k, c in enumerate(inner):
        if c - cur < min_len:
            continue
        nxt = next((n for n in inner[k + 1 :] if end - n >= min_len), end)
        if abs(c - cur - target) <= abs(nxt - cur - target):  # 목표 길이에 더 가까운 경계에서 자른다
            out.append((cur, c))
            cur = c
    out.append((cur, end))
    fixed: list[tuple[float, float]] = []
    for a, b in out:
        fixed += _pieces(a, b, SHOT_MAX)
    return fixed


def shots_for(
    scenes: list[dict],
    asset_dir: Path,
    total: float,
    videos: dict[str, Path],
    max_shot_sec: float | None = None,
    cuts: list[float] | None = None,
    xfade_at: list[float] = (),
) -> list[Shot]:
    """장면 → 화면 조각. 장면 사이 틈은 앞 조각을 늘려 메운다. videos[장면 id]가 있으면 그 장면은 영상(길이 안)으로.
    cuts(자막이 바뀌는 시각)를 주면 컷 편집: 그 경계에서 SHOT_SEC초 안팎으로 끊고, 같은 사진은 넓게 → 가깝게
    (PUNCH배) 번갈아 쓰며, 화제가 바뀌는 장면이나 xfade_at(넘기는 효과음 시각)에서 시작하는 장면의 첫 컷은
    빠른 확대 전환으로 넘어간다.
    cuts가 없으면 max_shot_sec보다 길게 나오지 않도록만 나눈다(같은 장면의 다른 사진을 번갈아 쓴다)."""
    shots: list[Shot] = []
    count = 0  # 줌 방향을 조각 순서대로 번갈아 쓰려고 센다(나누지 않으면 장면 번호와 같다)
    topic = None
    for sc in sorted(scenes, key=lambda s: s["start"]):
        start, end = float(sc["start"]), float(sc["end"])
        new_topic = topic is not None and sc.get("topic") != topic
        topic = sc.get("topic")
        clip = videos.get(sc["id"])
        if clip is not None:
            count += 1
            span = min(end - start, probe_duration(clip))
            shots.append(Shot(clip, "video", start, start + span, in_sec=0.0))
            if start + span < end:
                zoom = (1.0, 1.04) if count % 2 else (1.04, 1.0)
                shots.append(Shot(asset_dir / sc["chosen"], "image", start + span, end, zoom=zoom))
            continue
        pool = _photo_pool(sc, asset_dir) or [asset_dir / sc["chosen"]]
        if cuts is not None:
            for i, (a, b) in enumerate(cut_pieces(start, end, cuts)):
                photo = pool[(i // 2) % len(pool)]
                tight = i % 2 == 1  # 넓게 → 가깝게 → (다음 사진) 넓게 → 가깝게
                z = PUNCH if tight else 1.0
                turn = new_topic or any(abs(start - t) < 0.05 for t in xfade_at)
                xf = XFADE_SEC if (i == 0 and turn and start > 0) else 0.0
                shots.append(Shot(photo, "image", a, b, zoom=(z, z + PUSH), focus=FOCUS, xfade=xf))
            continue
        for i, (a, b) in enumerate(_pieces(start, end, max_shot_sec)):
            count += 1
            zoom = (
                (1.0, 1.04) if count % 2 else (1.04, 1.0)
            )  # 홀수 조각 줌인, 짝수 줌아웃(fcpxml과 같은 규칙)
            photo = pool[i % len(pool)]
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


def hook_chunks(text: str, limit: int = HOOK_CHUNK) -> list[str]:
    """훅 자막 한 줄 → 짧은 조각들(낱말을 이어 붙이되 공백 빼고 limit자 안쪽). 긴 낱말은 혼자 한 조각."""
    out: list[str] = []
    cur = ""
    for word in text.replace("\n", " ").split():
        cand = f"{cur} {word}".strip()
        if cur and len(cand.replace(" ", "")) > limit:
            out.append(cur)
            cur = word
        else:
            cur = cand
    if cur:
        out.append(cur)
    return out


_SENT_END = re.compile(r"[.!?…][\"'”’」』)]*$")


def emph_color(text: str, keywords: set[str]) -> tuple[int, int, int] | None:
    """짧은 자막 중 강조할 것의 색: 센 말 → 빨강, 직접 인용 → 노랑, 핵심 키워드(숫자 말고) → 초록, 아니면 None."""
    core = re.sub(r"\s+", "", text)
    if not core or len(core) > EMPH_MAX:
        return None
    if STRONG.search(core):
        return EMPH_RED
    if QUOTE.search(core):
        return EMPH_YELLOW
    if any(is_hot(w, keywords) and not is_hot(w, set()) for w in text.split()):
        return EMPH_GREEN
    return None


def pick_emphasis(cues: list, keywords: set[str], start: int = 0) -> dict[int, tuple[int, int, int]]:
    """강조 자막 고르기: 빨강 → 노랑 → 초록 순으로, 이미 고른 강조와 EMPH_GAP초 안에 붙지 않는 것만.
    반환: {자막 번호: 색}. start 앞(훅)은 고르지 않는다."""
    order = (EMPH_RED, EMPH_YELLOW, EMPH_GREEN)
    cands = [(k, emph_color(c.text, keywords)) for k, c in enumerate(cues) if k >= start]
    cands = sorted(
        ((k, col) for k, col in cands if col is not None), key=lambda kc: (order.index(kc[1]), kc[0])
    )
    picked: dict[int, tuple[int, int, int]] = {}
    for k, col in cands:
        if all(abs(cues[k].start - cues[j].start) >= EMPH_GAP for j in picked):
            picked[k] = col
    return picked


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
    # 훅: 첫 문장(HOOK_SEC 전에 시작하는 자막, 문장이 끝나는 자막까지)은 짧게 잘라 빠르게 이어 보여 준다
    hook_n = 0
    for cue in lines:
        if cue.start >= HOOK_SEC:
            break
        hook_n += 1
        if _SENT_END.search(cue.text.strip()):
            break
    px = fit_sub_px([cue.text for cue in lines[hook_n:]] or [""], size)
    arts = []
    emph = pick_emphasis(lines, keywords, start=hook_n)
    for k, cue in enumerate(lines):
        if k < hook_n:
            pieces = hook_chunks(cue.text)
            weights = [max(1, len(p.replace(" ", ""))) for p in pieces]
            at = cue.start
            for j, (piece, wt) in enumerate(zip(pieces, weights, strict=True)):
                span = (cue.end - cue.start) * wt / sum(weights)
                hook = render_cue(
                    piece,
                    asset_dir / "subtitles" / f"hook_{k:02d}_{j:02d}.png",
                    size,
                    keywords=keywords,
                    px=HOOK_PX,
                    center_y=HOOK_CENTER_Y,
                    text_color=YELLOW,
                    hot_color=RED,
                )
                if hook is not None:
                    arts.append(_timed(hook, at, at + span))
                at += span
            continue
        if k in emph:
            art = render_emph(cue.text, asset_dir / "subtitles" / f"emph_{k:03d}.png", size, color=emph[k])
        else:
            art = render_cue(
                cue.text, asset_dir / "subtitles" / f"cue_{k:03d}.png", size, keywords=keywords, px=px
            )
        if art is not None:
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
    # 핵심어: 호출자가 준 말 + 첫 장면 검색어의 말(훅에서 빨간색으로 강조)
    first = min(scenes, key=lambda s: float(s["start"]))
    # 검색어는 사진을 찾는 말이라 다 강조하면 빨강·노랑이 넘친다 — 첫 낱말(보통 인물 이름) 하나만 핵심어로
    query_words = {w for w in str(first.get("query", "")).split()[:1] if len(w) >= 2}
    cues, _spans, _info = subtitles_for(
        project["script"], audio, SIZE, asset_dir, set(keywords) | query_words, srt=srt, words=words
    )
    cues = [c for c in cues if c.start < total]
    # 컷 편집: 자막이 바뀌는 때(훅 조각 포함)에 맞춰 사진을 바꾼다. max_shot_sec를 주면 예전 방식(길이로만 나눔)
    cut_at = None if max_shot_sec else sorted({round(c.start, 3) for c in cues if c.start > 0})
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
    sounds = [cue for cue in sfx_cues(scene_models) if cue.at < total]
    sfx = [(cue.at, sfx_dir[cue.name]) for cue in sounds]
    # 넘기는 효과음(whoosh)이 나는 장면은 화면도 빠른 확대 전환으로 넘긴다
    whoosh = [cue.at for cue in sounds if cue.name == "whoosh"]
    shots = shots_for(scenes, asset_dir, total, videos or {}, max_shot_sec, cuts=cut_at, xfade_at=whoosh)
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
        photo_box=BOX,
        photo_fade=_photo_fade(asset_dir),
    )


def _photo_fade(asset_dir: Path) -> Path | None:
    """사진 칸 아래를 바탕 검정으로 잇는 그림(확대해도 사진 아래 끝이 늘 부드럽게 사라지게). 디자인에 없으면 None."""
    from newsclip.vertical import _fade_mask, style_of

    st = style_of("default")
    if not st.photo_fade:
        return None
    x0, y0, x1, y1 = BOX
    mask = _fade_mask((x1 - x0, y1 - y0), st.photo_fade)
    img = Image.new("RGBA", mask.size, (0, 0, 0, 0))
    img.putalpha(mask.point(lambda v: 255 - v))
    out = asset_dir / "subtitles" / "photo_fade.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out, "PNG")
    return out


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
