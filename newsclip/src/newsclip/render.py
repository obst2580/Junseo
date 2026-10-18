"""세로 쇼츠 완성본(mp4) 만들기 — ffmpeg로 화면·자막·효과음·배경음악을 한 번에 합친다.

timeline.xml(프리미어용)과 같은 자리를 쓴다. 컷마다 사진(사진 칸 안만 천천히 다가가기, 가깝게 끊어 쓰면 크게
확대)이나 영상(블러 배경 위에 맞춤), 컷 사이 바로 컷 또는 확대 펀치 전환, 자막(검은 테두리 + 그림자, 핵심어 노랑,
살짝 커지며 올라오는 등장), 짧은 핵심 자막은 색 글자로 흐린 데서 또렷하게, 맨 위 상단 제목, 출처 표시,
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
SUB_STROKE = 0.06  # 자막 글자 검은 테두리 두께(글자 크기 대비, 0이면 없음)

# 강조 자막: 짧은 핵심 자막 한 줄을 통째로 색 글자(위 밝고 아래 진한 그러데이션 + 흰 안쪽 테두리 + 진한 바깥 테두리
# + 같은 색 번짐)로 크게 띄운다. 흐린 그림에서 또렷해지며 나타난다.
EMPH_FONT = Path(__file__).parent / "fonts" / "210MammothPpang-R.TTF"
QUOTE_FONT = (
    Path(__file__).parent / "fonts" / "210Yeohaengilgi-B.TTF"
)  # 직접 인용 강조: 손글씨(그 사람이 한 말)
EMPH_PX = 128
EMPH_GREEN = (64, 200, 72)
EMPH_RED = (228, 36, 36)
EMPH_YELLOW = (255, 196, 30)
EMPH_BLUR = (16, 7, 3)  # 등장할 때 거치는 흐림 단계(픽셀 반경) — 한 단계에 EMPH_STEP초(모두 0.1초)
EMPH_STEP = 1 / 30
PUNCH_GROW = 0.25  # 확대 펀치 전환: 앞 컷 끝은 이만큼 빠르게 다가가고, 다음 컷은 이만큼 다가간 데서 제자리로


@dataclass(frozen=True)
class Shot:
    """화면 한 조각: 사진(확대·축소) 또는 영상(구간)."""

    path: Path
    kind: str  # "image" | "video"
    start: float
    end: float
    zoom: tuple[float, float] = (1.0, 1.04)  # 사진: 확대 비율 시작·끝
    in_sec: float = 0.0  # 영상: 원본에서 시작하는 초
    focus: tuple[float, float] = (0.5, 0.5)  # 사진: 확대할 때 붙잡는 자리(사진 칸 안 가로·세로 비율)
    xfade: float = 0.0  # 앞 조각에서 이 조각으로 넘어올 때 확대 펀치 전환 길이(초, 0이면 바로 컷)


@dataclass(frozen=True)
class CueArt:
    """자막 한 줄: 투명 PNG(상자 크기) + 화면 위치 + 시간."""

    path: Path
    start: float
    end: float
    width: int
    height: int
    top: int  # 상자 윗선(화면 기준)
    intro: tuple[
        Path, ...
    ] = ()  # 강조 자막: 먼저 차례로 보여 줄 흐린 그림들(있으면 커졌다 줄기·올라오기 대신)


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
    photo_box: tuple[int, int, int, int] | None = None  # 사진 칸(x0, y0, x1, y1) — 있으면 칸 안만 확대한다
    photo_fade: Path | None = None  # 사진 칸 아래를 검정으로 잇는 그림(photo_box 자리에 얹는다)
    crf: int = 18  # 화질(낮을수록 좋고 파일이 큼). 제작실에 올릴 완성본은 크기 한도 때문에 조금 높인다
    peak: float = 0.95  # 소리 최고점(믹스 끝 리미터, 1.0 = 0 dBFS) — 디자인(vertical.Style.peak)에서 받는다


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
    stroke: float = SUB_STROKE,
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
    edge = round(font_size * stroke) if stroke and not use_box else 0
    for x, y, word, color in spots:
        draw.text((x, y), word, font=font, fill=color, stroke_width=edge, stroke_fill=(0, 0, 0, 255))
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out, "PNG")
    if center_y is not None:
        top = center_y - bh // 2  # 훅 자막: 사진 칸 가운데
    else:
        top = SUB_TEXT_Y - pad_y if h > w else round(h * 0.75)
    return CueArt(path=out, start=0.0, end=0.0, width=bw, height=bh, top=top)


def _emph_font(px: int, path: Path | None = None) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    p = path or EMPH_FONT
    return ImageFont.truetype(str(p), px) if p.exists() else _sub_font(px)


def _mix(c: tuple[int, int, int], other: tuple[int, int, int], k: float) -> tuple[int, int, int]:
    return tuple(round(a + (b - a) * k) for a, b in zip(c, other, strict=True))  # type: ignore[return-value]


def render_emph(
    text: str,
    out: Path,
    size: tuple[int, int],
    color: tuple[int, int, int] = EMPH_GREEN,
    px: int = EMPH_PX,
    font_path: Path | None = None,
    center_y: int | None = None,
) -> CueArt | None:
    """강조 자막(색 글자) PNG와 등장용 흐린 그림들(out_b0.png…)을 만든다. 자리는 보통 자막 첫 줄과 가운데를 맞춘다."""
    lines = sub_lines(text)
    if not lines:
        return None
    probe = ImageDraw.Draw(Image.new("RGBA", (1, 1)))
    fs = px
    while fs > 48:
        font = _emph_font(fs, font_path)
        widest = max(probe.textlength(ln, font=font) for ln in lines) + 2 * round(fs * 0.14)
        if widest <= SUB_MAX_W:
            break
        fs -= 2
    font = _emph_font(fs, font_path)
    outer, inner, glow = round(fs * 0.14), round(fs * 0.06), round(fs * 0.16)
    pad = outer + 2 * glow
    line_h = round(fs * 1.18)
    widths = [probe.textlength(ln, font=font) for ln in lines]
    bw = round(max(widths)) + 2 * pad
    bh = line_h * len(lines) + 2 * pad

    def mask(width: int) -> Image.Image:
        m = Image.new("L", (bw, bh), 0)
        d = ImageDraw.Draw(m)
        for row, ln in enumerate(lines):
            x = (bw - widths[row]) / 2
            d.text((x, pad + row * line_h), ln, font=font, fill=255, stroke_width=width, stroke_fill=255)
        return m

    fill_m, inner_m, outer_m = mask(0), mask(inner), mask(outer)
    dark = _mix(color, (0, 0, 0), 0.72)
    img = Image.new("RGBA", (bw, bh), (0, 0, 0, 0))
    halo = Image.new("RGBA", (bw, bh), (*color, 0))
    halo.putalpha(outer_m.filter(ImageFilter.GaussianBlur(glow)).point(lambda v: round(v * 0.9)))
    img.alpha_composite(halo)
    for m, rgb in ((outer_m, dark), (inner_m, (255, 255, 255))):
        layer = Image.new("RGBA", (bw, bh), (*rgb, 0))
        layer.putalpha(m)
        img.alpha_composite(layer)
    # 글자 속: 줄마다 위는 밝게(흰빛), 아래는 진하게
    grad = Image.new("RGBA", (bw, bh), (0, 0, 0, 0))
    gd = ImageDraw.Draw(grad)
    top_c, bot_c = _mix(color, (255, 255, 255), 0.45), _mix(color, (0, 0, 0), 0.28)
    for row in range(len(lines)):
        y0 = pad + row * line_h
        for y in range(y0, y0 + line_h):
            k = min(1.0, max(0.0, (y - y0) / (fs * 1.05)))
            gd.line([(0, y), (bw, y)], fill=(*_mix(top_c, bot_c, k), 255))
    grad.putalpha(fill_m)
    img.alpha_composite(grad)
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out, "PNG")
    intro = []
    for i, r in enumerate(EMPH_BLUR):
        b = out.with_name(f"{out.stem}_b{i}.png")
        img.convert("RGBa").filter(ImageFilter.GaussianBlur(r)).convert("RGBA").save(b, "PNG")
        intro.append(b)
    if center_y is not None:
        top = center_y - bh // 2  # 훅의 핵심(발언·행위): 사진 칸 가운데
    else:
        first_mid = SUB_TEXT_Y + round(SUB_PX * 0.6)  # 보통 자막 첫 줄 가운데
        top = first_mid - pad - round(fs * 0.6)
    return CueArt(path=out, start=0.0, end=0.0, width=bw, height=bh, top=top, intro=tuple(intro))


# ---------- ffmpeg 그래프 ----------


def _shot_chain(
    i: int,
    shot: Shot,
    size: tuple[int, int],
    box: tuple[int, int, int, int] | None = None,
    frames: int | None = None,
    tail_sec: float = 0.0,
) -> str:
    """조각 하나 → [s{i}]. frames면 딱 그 프레임 수로 자른다(컷 길이 반올림이 쌓여 영상이 밀리지 않게).
    사진은 확대 펀치 전환을 줌에 넣는다: 이 조각이 xfade로 들어오면 PUNCH_GROW만큼 다가간 데서 시작해 제자리로,
    tail_sec(다음 조각의 xfade)가 있으면 끝에서 그만큼 빠르게 다가간다."""
    W, H = size
    dur = (frames / FPS) if frames else shot.end - shot.start
    cut = f"trim=end_frame={frames}," if frames else ""
    tail = f"fps={FPS},{cut}format=yuv420p,setsar=1,setpts=PTS-STARTPTS[s{i}]"
    if shot.kind == "image":
        z0, z1 = shot.zoom
        grow = f"({z0}+({z1}-{z0})*min(t/{dur:.4f},1))"
        if shot.xfade:
            grow += f"*(1+{PUNCH_GROW}*pow(max(0,1-t/{shot.xfade:.3f}),2))"
        if tail_sec:
            grow += f"*(1+{PUNCH_GROW}*pow(max(0,(t-{dur - tail_sec:.4f})/{tail_sec:.3f}),2))"
        fx, fy = shot.focus
        if box is not None:
            # 사진 칸만 잘라 확대(붙잡는 자리 기준)한 뒤 제자리에 — 바탕(하늘색 테두리)은 그대로
            x0, y0, x1, y1 = box
            bw, bh = x1 - x0, y1 - y0
            return (
                f"[{i}:v]format=rgba,split=2[bg{i}][ph{i}];"
                f"[ph{i}]crop={bw}:{bh}:{x0}:{y0},scale=w='{bw}*{grow}':h='{bh}*{grow}':eval=frame,"
                f"crop={bw}:{bh}:x='(iw-{bw})*{fx}':y='(ih-{bh})*{fy}'[z{i}];"
                f"[bg{i}][z{i}]overlay={x0}:{y0},{tail}"
            )
        return f"[{i}:v]format=rgba,scale=w='iw*{grow}':h='ih*{grow}':eval=frame,crop={W}:{H},{tail}"
    # 영상: 같은 화면을 흐리게 깔고 가운데에 가로 맞춤으로 올린다(세로 영상이면 꽉 찬다)
    return (
        f"[{i}:v]split=2[a{i}][b{i}];"
        f"[a{i}]scale={W}:{H}:force_original_aspect_ratio=increase,crop={W}:{H},boxblur=20:5[bg{i}];"
        f"[b{i}]scale={W}:-2[fg{i}];"
        f"[bg{i}][fg{i}]overlay=(W-w)/2:(H-h)/2,{tail}"
    )


def _cue_chain(cue: CueArt) -> str:
    """자막 전환: 흐려지지 않고 바로 또렷하게 — 살짝 커졌다 제자리(POP). 아래에서 올라오는 움직임은
    overlay 위치(_cue_y)에서 준다."""
    k = f"(1+{POP_GROW}*max(0,1-(t-{cue.start:.3f})/{POP_SEC}))"
    return f",scale=w='{cue.width}*{k}':h='{cue.height}*{k}':eval=frame"


def _cue_y(cue: CueArt) -> str:
    """자막 세로 위치: 가운데를 지키며(커졌다 줄어도), 처음 SLIDE_SEC초 동안 SLIDE_PX 아래에서 올라온다."""
    return f"{cue.top}+({cue.height}-h)/2+{SLIDE_PX}*max(0,1-(t-{cue.start:.3f})/{SLIDE_SEC})"


def build_command(plan: RenderPlan, out: Path) -> list[str]:
    """RenderPlan → ffmpeg 명령(목록). 실행은 render()."""
    W, H = plan.size
    args: list[str] = ["ffmpeg", "-hide_banner", "-y", "-loglevel", "error"]
    chains: list[str] = []
    idx = 0
    for k, shot in enumerate(plan.shots):
        # 컷 길이는 프레임 단위로: 화면 시각(초×FPS)을 반올림한 경계 사이 프레임 수 — 다 더하면 전체 길이와 같다
        frames = max(1, round(shot.end * FPS) - round(shot.start * FPS))
        span = frames / FPS + 0.2
        if shot.kind == "image":
            args += ["-loop", "1", "-framerate", str(FPS), "-t", f"{span:.4f}", "-i", str(shot.path)]
        else:
            args += ["-ss", f"{shot.in_sec:.4f}", "-t", f"{span:.4f}", "-i", str(shot.path)]
        nxt = plan.shots[k + 1].xfade if k + 1 < len(plan.shots) else 0.0
        chains.append(_shot_chain(idx, shot, plan.size, plan.photo_box, frames=frames, tail_sec=nxt))
        idx += 1
    n = len(plan.shots)
    concat_in = "".join(f"[s{i}]" for i in range(n))
    chains.append(f"{concat_in}concat=n={n}:v=1:a=0[base]")
    cur = "[base]"

    def add_overlay(
        path: Path, start: float, end: float, x: str = "0", y: str = "0", chain: str = ""
    ) -> None:
        """그림 한 장을 start~end에만 얹는다. 그 구간 길이만큼만 읽어 시각을 옮겨 붙인다(60초 내내 읽지 않아
        메모리·시간을 아낀다). chain은 그림에 더 걸 필터(등장 효과 등, 시각 t는 화면 기준)."""
        nonlocal idx, cur
        # 프레임 단위로: 화면 프레임 경계에 맞춰 시작하고 딱 n프레임만(1프레임짜리 흐림 단계도 빠지지 않게).
        # 소수 오차(8.1초가 8.0999…)로 프레임을 놓치지 않게 그림은 1/4프레임 일찍 대기시키고, 표시 구간은
        # 앞뒤 반 프레임 여유를 둔다.
        f0, f1 = round(start * FPS), round(end * FPS)
        n = max(1, f1 - f0)
        args.extend(["-loop", "1", "-framerate", str(FPS), "-t", f"{(n + 0.5) / FPS:.4f}", "-i", str(path)])
        ready = (f0 - 0.25) / FPS
        chains.append(
            f"[{idx}:v]format=rgba,trim=end_frame={n},settb=AVTB,setpts=PTS-STARTPTS+{ready:.6f}/TB{chain}[o{idx}]"
        )
        first, last = (f0 - 0.5) / FPS, (f0 + n - 0.5) / FPS
        opts = f"x='{x}':y='{y}':enable='between(t,{first:.4f},{last:.4f})'"
        chains.append(f"{cur}[o{idx}]overlay={opts}[v{idx}]")
        cur = f"[v{idx}]"
        idx += 1

    if plan.photo_fade is not None and plan.photo_box is not None:
        add_overlay(plan.photo_fade, 0.0, plan.total, x=str(plan.photo_box[0]), y=str(plan.photo_box[1]))
    if plan.headline is not None:
        add_overlay(plan.headline, 0.0, plan.total)
    for cap in plan.captions:
        add_overlay(cap.path, cap.start, cap.end, x=str(cap.x), y=str(cap.y))
    for cue in plan.cues:
        if cue.intro:
            # 강조 자막: 흐린 그림들을 차례로(EMPH_STEP초씩) 보여 준 뒤 또렷한 그림 — 제자리에 그대로
            at = cue.start
            for b in cue.intro:
                nxt = min(at + EMPH_STEP, cue.end)
                add_overlay(b, at, nxt, x="(W-w)/2", y=str(cue.top))
                at = nxt
            add_overlay(cue.path, at, cue.end, x="(W-w)/2", y=str(cue.top))
            continue
        add_overlay(cue.path, cue.start, cue.end, x="(W-w)/2", y=_cue_y(cue), chain=_cue_chain(cue))
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
    # 내레이션 + 배경음악 + 효과음이 겹쳐도 찢어지지 않게 마지막에 리미터(크기 자동 키우기는 끔)
    mix.append(
        f"{''.join(mixed)}amix=inputs={count}:duration=first:normalize=0,"
        f"alimiter=limit={plan.peak}:level=false[aout]"
    )

    filt = ";".join(chains + mix)
    args += ["-filter_complex", filt, "-map", "[vout]", "-map", "[aout]", "-t", f"{plan.total:.4f}"]
    args += [
        "-r",
        str(FPS),
        "-c:v",
        "libx264",
        "-preset",
        "medium",
        "-crf",
        str(plan.crf),
        "-pix_fmt",
        "yuv420p",
    ]
    args += ["-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart", str(out)]
    return args


def render(plan: RenderPlan, out: Path) -> Path:
    """완성본 mp4를 만든다. 실패하면 ffmpeg 오류 끝부분을 담아 RuntimeError."""
    out.parent.mkdir(parents=True, exist_ok=True)
    proc = subprocess.run(build_command(plan, out), capture_output=True, text=True)
    if proc.returncode != 0:
        raise RuntimeError(f"ffmpeg 실패: {proc.stderr[-1200:]}")
    return out
