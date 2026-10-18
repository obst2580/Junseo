"""세로 쇼츠 완성본(mp4) 만들기 — ffmpeg로 화면·자막·효과음·배경음악을 한 번에 합친다.

timeline.xml(프리미어용)과 같은 자리를 쓴다. 장면마다 사진(천천히 확대·축소)이나 영상(블러 배경 위에 맞춤),
자막 줄마다 검은 상자 + 핵심어 노란색 + 등장 효과(살짝 커졌다 줄어듦), 맨 위 상단 제목, 출처 표시,
화제 전환·질문 효과음, 배경음악(내레이션이 나올 때 자동으로 낮춤)을 넣는다.
"""

from __future__ import annotations

import re
import subprocess
from dataclasses import dataclass, field
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

from studio_core.fonts import korean_font_path, load_font

# 음성 자막 글꼴(210 모란봉 Light). 패키지 안에 같이 둔다.
SUB_FONT = Path(__file__).parent / "fonts" / "210Moranbong-Light.TTF"

FPS = 30
WHITE = (255, 255, 255, 255)
YELLOW = (255, 222, 64, 255)
RED = (255, 70, 70, 255)
BOX = (0, 0, 0, 255)  # 자막 뒤 상자: 불투명한 검은색
SUB_PX = 112  # 음성 자막 글자 크기(픽셀). 줄이 너무 넓으면 fit_sub_px가 줄인다
SUB_MAX_W = 960  # 자막 한 줄의 최대 글자 폭(화면 1080 기준 좌우 여백 60)
SUB_TEXT_Y = (
    1180  # 세로 쇼츠: 자막 첫 줄 글자 윗선 — 한 줄이든 두 줄이든 첫 줄 자리는 같다(두 줄이면 아래로 늘어남)
)
SUB_WRAP = 8  # 자막 한 줄 글자 수(공백 빼고). 넘으면 두 줄로 나눈다
SUB_BOX = False  # 자막 뒤 검은 상자. False면 상자 없이 그림자만(정치는푸르다가 고른 방식)
SUB_SHADOW = 1.0  # 상자 없을 때 글자 뒤 흐린 검은 그림자 세기(0~1)
POP_SEC = 0.15  # 자막 등장 효과 길이
POP_GROW = 0.08  # 등장할 때 처음 커지는 비율(8%)
SLIDE_PX = 28  # 자막 전환: 이만큼 아래에서 제자리로 올라오며 나타난다
SLIDE_SEC = 0.18


@dataclass(frozen=True)
class Shot:
    """화면 한 조각: 사진(확대·축소) 또는 영상(구간)."""

    path: Path
    kind: str  # "image" | "video"
    start: float
    end: float
    zoom: tuple[float, float] = (1.0, 1.04)  # 사진: 확대 비율 시작·끝
    in_sec: float = 0.0  # 영상: 원본에서 시작하는 초


@dataclass(frozen=True)
class CueArt:
    """자막 한 줄: 투명 PNG(상자 크기) + 화면 위치 + 시간."""

    path: Path
    start: float
    end: float
    width: int
    height: int
    top: int  # 상자 윗선(화면 기준)


@dataclass
class Overlay:
    """그림 한 장을 시간 구간에 얹는다(출처 표시 등). x, y는 화면에서의 위치(잘라 낸 그림 기준)."""

    path: Path
    start: float
    end: float
    x: int = 0
    y: int = 0


@dataclass
class RenderPlan:
    shots: list[Shot]
    total: float
    narration: Path
    size: tuple[int, int] = (1080, 1920)
    headline: Path | None = None
    captions: list[Overlay] = field(default_factory=list)
    cues: list[CueArt] = field(default_factory=list)
    sfx: list[tuple[float, Path]] = field(default_factory=list)  # (시작 초, 효과음 파일)
    bgm: Path | None = None
    bgm_volume: float = 0.22  # 배경음악 크기(내레이션이 없을 때 기준)


# ---------- 자막 그림 ----------


def is_hot(word: str, keywords: set[str]) -> bool:
    """강조 낱말: 숫자가 들어간 말, 핵심 키워드로 시작하는 말(조사가 붙은 '숙제는'도 '숙제')."""
    core = re.sub(r"[^0-9A-Za-z가-힣]", "", word)
    if not core:
        return False
    return any(ch.isdigit() for ch in core) or any(k and core.startswith(k) for k in keywords)


def _sub_font(px: int) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    return ImageFont.truetype(str(SUB_FONT), px) if SUB_FONT.exists() else load_font(px)


def sub_lines(text: str, limit: int = SUB_WRAP) -> list[str]:
    """자막 한 블록의 줄들. 이미 줄을 나눴으면 그대로, 한 줄이 limit자(공백 빼고)를 넘으면 가운데쯤에서 두 줄로."""
    if "\n" in text:
        return [ln.strip() for ln in text.split("\n") if ln.strip()]
    text = text.strip()
    if len(text.replace(" ", "")) <= limit:
        return [text] if text else []
    from newsclip.vertical import split_head

    return split_head(text)


def fit_sub_px(texts: list[str], size: tuple[int, int], px: int = SUB_PX) -> int:
    """자막들이 모두 SUB_MAX_W 안에 드는 가장 큰 글자 크기(px, 최소 40)."""
    probe = ImageDraw.Draw(Image.new("RGBA", (1, 1)))
    while px > 40:
        font = _sub_font(px)
        gap = probe.textlength(" ", font=font)
        widest = 0.0
        for text in texts:
            for ln in sub_lines(text):
                words = ln.split(" ")
                if ln.strip():
                    widest = max(
                        widest, sum(probe.textlength(wd, font=font) for wd in words) + gap * (len(words) - 1)
                    )
        if widest <= SUB_MAX_W:
            break
        px -= 2
    return px


def render_cue(
    text: str,
    out: Path,
    size: tuple[int, int],
    keywords: set[str] = frozenset(),
    px: int = SUB_PX,
    center_y: int | None = None,
    text_color: tuple[int, int, int, int] = WHITE,
    hot_color: tuple[int, int, int, int] = YELLOW,
    box: bool | None = None,
    shadow: float | None = None,
    max_hot: int = 1,
) -> CueArt | None:
    """자막 한 블록(줄바꿈은 \\n)을 PNG로 그린다. 핵심어는 노란색. 세로 쇼츠는 사진 칸 아랫부분에 놓는다.
    box면 뒤에 검은 상자, 아니면 shadow 세기만큼 글자 뒤에 흐린 검은 그림자를 깐다(비우면 SUB_BOX·SUB_SHADOW)."""
    lines = sub_lines(text)
    if not lines:
        return None
    if not SUB_FONT.exists() and korean_font_path() is None:
        raise RuntimeError("한글 폰트가 없어 자막을 그릴 수 없습니다(STUDIO_FONT로 지정).")
    w, h = size
    font_size = fit_sub_px([text], size, px)  # 한 줄이 화면 폭을 넘지 않게 줄인다
    font = _sub_font(font_size)
    use_box = SUB_BOX if box is None else box
    strength = SUB_SHADOW if shadow is None else shadow
    if use_box:
        pad_x, pad_y = round(28 * min(w, h) / 1080), round(14 * min(w, h) / 1080)
    else:
        pad_x = pad_y = round(font_size * 0.35)  # 그림자가 번질 자리
    probe = ImageDraw.Draw(Image.new("RGBA", (1, 1)))
    line_h = round(font_size * 1.3)
    line_w = [sum(probe.textlength(word, font=font) for word in ln.split(" ")) for ln in lines]
    gap = round(probe.textlength(" ", font=font))
    text_w = max(
        sum(probe.textlength(word, font=font) for word in ln.split(" ")) + gap * (len(ln.split(" ")) - 1)
        for ln in lines
    )
    bw = round(text_w + 2 * pad_x)
    bh = line_h * len(lines) + 2 * pad_y
    img = Image.new("RGBA", (bw, bh), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    spots: list[tuple[float, float, str, tuple]] = []  # 낱말마다 (x, y, 낱말, 색)
    used = 0  # 핵심어로 강조한 낱말 수(숫자는 세지 않음) — 한 자막에 max_hot개까지만
    for row, ln in enumerate(lines):
        x = (bw - line_w[row] - gap * (len(ln.split(" ")) - 1)) / 2
        y = pad_y + row * line_h
        for k, word in enumerate(ln.split(" ")):
            if k:
                x += gap
            hot = is_hot(word, set())  # 숫자는 늘 강조
            if not hot and used < max_hot and is_hot(word, keywords):
                hot, used = True, used + 1
            spots.append((x, y, word, hot_color if hot else text_color))
            x += draw.textlength(word, font=font)
    if use_box:
        draw.rounded_rectangle((0, 0, bw - 1, bh - 1), radius=round(12 * min(w, h) / 1080), fill=BOX)
    elif strength:
        # 상자 대신 글자 모양 그대로 번진 진한 그림자 — 밝은 사진 위에서도 글자가 읽힌다
        a = round(255 * min(1.0, strength))
        sh = Image.new("RGBA", (bw, bh), (0, 0, 0, 0))
        sd = ImageDraw.Draw(sh)
        for x, y, word, _c in spots:
            sd.text(
                (x, y + round(font_size * 0.05)),
                word,
                font=font,
                fill=(0, 0, 0, a),
                stroke_width=max(2, round(font_size * 0.08)),
                stroke_fill=(0, 0, 0, a),
            )
        blur = sh.filter(ImageFilter.GaussianBlur(font_size * 0.12))
        img.alpha_composite(blur)
        if strength > 0.5:
            img.alpha_composite(blur)  # 진하게 한 번 더
    for x, y, word, color in spots:
        draw.text((x, y), word, font=font, fill=color)
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out, "PNG")
    if center_y is not None:
        top = center_y - bh // 2  # 훅 자막: 사진 칸 가운데
    else:
        top = SUB_TEXT_Y - pad_y if h > w else round(h * 0.75)
    return CueArt(path=out, start=0.0, end=0.0, width=bw, height=bh, top=top)


# ---------- ffmpeg 그래프 ----------


def _shot_chain(i: int, shot: Shot, size: tuple[int, int]) -> str:
    W, H = size
    dur = shot.end - shot.start
    if shot.kind == "image":
        z0, z1 = shot.zoom
        grow = f"({z0}+({z1}-{z0})*min(t/{dur:.4f},1))"
        return (
            f"[{i}:v]format=rgba,scale=w='iw*{grow}':h='ih*{grow}':eval=frame,"
            f"crop={W}:{H},setsar=1,setpts=PTS-STARTPTS[s{i}]"
        )
    # 영상: 같은 화면을 흐리게 깔고 가운데에 가로 맞춤으로 올린다(세로 영상이면 꽉 찬다)
    return (
        f"[{i}:v]split=2[a{i}][b{i}];"
        f"[a{i}]scale={W}:{H}:force_original_aspect_ratio=increase,crop={W}:{H},boxblur=20:5[bg{i}];"
        f"[b{i}]scale={W}:-2[fg{i}];"
        f"[bg{i}][fg{i}]overlay=(W-w)/2:(H-h)/2,fps={FPS},format=rgba,setsar=1,setpts=PTS-STARTPTS[s{i}]"
    )


def _cue_chain(i: int, cue: CueArt) -> str:
    """자막 전환: 흐려지지 않고 바로 또렷하게 — 살짝 커졌다 제자리(POP). 아래에서 올라오는 움직임은
    overlay 위치(_cue_y)에서 준다."""
    k = f"(1+{POP_GROW}*max(0,1-(t-{cue.start:.3f})/{POP_SEC}))"
    return f"[{i}:v]format=rgba,scale=w='{cue.width}*{k}':h='{cue.height}*{k}':eval=frame[c{i}]"


def _cue_y(cue: CueArt) -> str:
    """자막 세로 위치: 가운데를 지키며(커졌다 줄어도), 처음 SLIDE_SEC초 동안 SLIDE_PX 아래에서 올라온다."""
    return f"{cue.top}+({cue.height}-h)/2+{SLIDE_PX}*max(0,1-(t-{cue.start:.3f})/{SLIDE_SEC})"


def build_command(plan: RenderPlan, out: Path) -> list[str]:
    """RenderPlan → ffmpeg 명령(목록). 실행은 render()."""
    W, H = plan.size
    args: list[str] = ["ffmpeg", "-hide_banner", "-y", "-loglevel", "error"]
    chains: list[str] = []
    idx = 0
    for shot in plan.shots:
        dur = shot.end - shot.start
        if shot.kind == "image":
            args += ["-loop", "1", "-framerate", str(FPS), "-t", f"{dur:.4f}", "-i", str(shot.path)]
        else:
            args += ["-ss", f"{shot.in_sec:.4f}", "-t", f"{dur:.4f}", "-i", str(shot.path)]
        chains.append(_shot_chain(idx, shot, plan.size))
        idx += 1
    n = len(plan.shots)
    concat_in = "".join(f"[s{i}]" for i in range(n))
    chains.append(f"{concat_in}concat=n={n}:v=1:a=0[base]")
    cur = "[base]"

    def add_overlay(
        path: Path, enable: str | None, x: str = "0", y: str = "0", src: str | None = None
    ) -> None:
        nonlocal idx, cur
        args.extend(["-loop", "1", "-t", f"{plan.total:.4f}", "-i", str(path)])
        label = f"[{idx}:v]" if src is None else src
        if src is None:
            chains.append(f"{label}format=rgba[o{idx}]")
            label = f"[o{idx}]"
        opts = f"x={x}:y={y}" + (f":enable='{enable}'" if enable else "")
        chains.append(f"{cur}{label}overlay={opts}[v{idx}]")
        cur = f"[v{idx}]"
        idx += 1

    if plan.headline is not None:
        add_overlay(plan.headline, None)
    for cap in plan.captions:
        add_overlay(cap.path, f"between(t,{cap.start:.3f},{cap.end:.3f})", x=str(cap.x), y=str(cap.y))
    for cue in plan.cues:
        args.extend(["-loop", "1", "-t", f"{plan.total:.4f}", "-i", str(cue.path)])
        chains.append(_cue_chain(idx, cue))
        x = "(W-w)/2"
        y = _cue_y(cue)
        chains.append(
            f"{cur}[c{idx}]overlay=x='{x}':y='{y}':enable='between(t,{cue.start:.3f},{cue.end:.3f})'[v{idx}]"
        )
        cur = f"[v{idx}]"
        idx += 1
    chains.append(f"{cur}format=yuv420p[vout]")

    # 음성·배경음악·효과음
    args.extend(["-i", str(plan.narration)])
    narr = idx
    idx += 1
    narr_fmt = f"[{narr}:a]aformat=sample_rates=48000:channel_layouts=mono"
    if plan.bgm is None:
        mix = [f"{narr_fmt}[nmix]"]
    else:
        mix = [f"{narr_fmt},asplit=2[nduck][nmix]"]
    mixed = ["[nmix]"]
    if plan.bgm is not None:
        args.extend(["-stream_loop", "-1", "-i", str(plan.bgm)])
        mix.append(
            f"[{idx}:a]aformat=sample_rates=48000:channel_layouts=mono,atrim=0:{plan.total:.4f},"
            f"asetpts=PTS-STARTPTS,volume={plan.bgm_volume}[bg];"
            f"[bg][nduck]sidechaincompress=threshold=0.03:ratio=8:attack=15:release=350[bgd]"
        )
        mixed.append("[bgd]")
        idx += 1
    for k, (at, path) in enumerate(plan.sfx):
        args.extend(["-i", str(path)])
        ms = max(0, round(at * 1000))
        mix.append(f"[{idx}:a]aformat=sample_rates=48000:channel_layouts=mono,adelay={ms}:all=1[f{k}]")
        mixed.append(f"[f{k}]")
        idx += 1
    count = len(mixed)
    mix.append(f"{''.join(mixed)}amix=inputs={count}:duration=first:normalize=0[aout]")

    filt = ";".join(chains + mix)
    args += ["-filter_complex", filt, "-map", "[vout]", "-map", "[aout]", "-t", f"{plan.total:.4f}"]
    args += ["-r", str(FPS), "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-pix_fmt", "yuv420p"]
    args += ["-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart", str(out)]
    return args


def render(plan: RenderPlan, out: Path) -> Path:
    """완성본 mp4를 만든다. 실패하면 ffmpeg 오류 끝부분을 담아 RuntimeError."""
    out.parent.mkdir(parents=True, exist_ok=True)
    proc = subprocess.run(build_command(plan, out), capture_output=True, text=True)
    if proc.returncode != 0:
        raise RuntimeError(f"ffmpeg 실패: {proc.stderr[-1200:]}")
    return out
