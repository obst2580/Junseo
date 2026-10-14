"""세로 쇼츠 화면(1080x1920) 배치.

휴대폰 쇼츠 화면은 위·아래·오른쪽에 유튜브 버튼과 글자가 덮인다. 그래서 내용은 가운데 띠에 모은다.
- HEAD(위쪽): 영상 내내 떠 있는 상단 제목(썸네일 문구) — 흰 글씨 + 검은 테두리, 둘째 줄은 노랑.
- BOX(가운데): 사진 칸(4:3). 보통 가로 사진(16:9·3:2)은 칸에 꽉 차게 양옆만 조금 잘라 크게 보이고, 아주 넓거나
  세로인 사진은 자르지 않고 칸 안에 넣는다. 화면 전체 바탕은 같은 사진을 흐리고 어둡게 깐다(검은 빈 곳 없이).
- BOX 아래: 대본 자막 자리(프리미어 캡션도 이 높이에 두면 쇼츠 버튼에 가리지 않는다).

채널마다 디자인이 다르다(channel.yaml shorts.style): default(정치는푸르다) = 흰·노랑 글씨 + 검은 테두리, 검게 흐린 바탕 /
navy_red(자유뉴스365) = 남색·빨강 띠 위 흰 글씨(블랙한산스), 사진 칸 위아래는 검은색(흐린 사진 바탕 없이).
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageOps

from studio_core.fonts import korean_font_path

SIZE = (1080, 1920)
HEAD = (60, 220, 1020, 520)  # 상단 제목 자리(x0, y0, x1, y1)
BOX = (0, 560, 1080, 1370)  # 사진 칸(4:3)
SUB_TOP = 1400  # 대본 자막 윗선(BOX 바로 아래)
CROP_MAX = 1.9  # 이보다 넓은 사진(파노라마)은 자르지 않고 넣는다
SUB_LINE = 14  # 세로 화면 자막 한 줄 글자 수(가로는 20)
HEAD_LINE_COLORS = ((255, 255, 255), (255, 222, 64))  # 첫 줄 흰색, 둘째 줄 노랑
STROKE = (12, 12, 12)
NAVY, RED = (11, 31, 75), (215, 20, 26)
FONT_CDN = "https://cdn.jsdelivr.net/gh/google/fonts@main/ofl/"


@dataclass(frozen=True)
class Style:
    """세로 쇼츠 디자인 한 벌(상단 제목·바탕·출처 표시)."""

    name: str
    tint: tuple[int, int, int] = (0, 0, 0)  # 흐린 바탕에 섞는 색
    tint_amount: float = 0.5
    solid_bg: tuple[int, int, int] | None = None  # 있으면 흐린 사진 대신 이 색 한 가지로 바탕을 칠한다
    font_file: str = ""  # 상단 제목 폰트(비우면 Pretendard ExtraBold) — FONT_CDN에서 한 번 받아 둔다
    font_path: str = ""  # FONT_CDN 안의 경로
    bands: tuple[tuple[int, int, int], ...] = ()  # 줄마다 띠 색(비우면 띠 없이 검은 테두리 글씨)
    text_colors: tuple[tuple[int, int, int], ...] = HEAD_LINE_COLORS
    caption_box: tuple[int, int, int, int] = (0, 0, 0, 150)  # 출처 표시 상자 색

    @property
    def font_url(self) -> str:
        return FONT_CDN + self.font_path if self.font_path else ""


STYLES: dict[str, Style] = {
    "default": Style("default"),
    "navy_red": Style(
        "navy_red",
        solid_bg=(0, 0, 0),  # 사진 칸 위아래는 검은색
        font_file="BlackHanSans-Regular.ttf",
        font_path="blackhansans/BlackHanSans-Regular.ttf",  # 블랙한산스(OFL)
        bands=(NAVY, RED),
        text_colors=((255, 255, 255), (255, 255, 255)),
        caption_box=(*NAVY, 200),
    ),
}


def style_of(name: str | None) -> Style:
    """이름 → 디자인(모르는 이름이면 기본)."""
    return STYLES.get(name or "default", STYLES["default"])


def is_vertical(size: tuple[int, int]) -> bool:
    return size[1] > size[0]


def compose_photo(im: Image.Image, size: tuple[int, int] = SIZE, style: str = "default") -> Image.Image:
    """사진 → 세로 화면 한 장: 바탕(흐리고 어둡게 깐 같은 사진, 또는 디자인의 단색) + 가운데 사진 칸의 사진."""
    st = style_of(style)
    w, h = size
    rgb = im.convert("RGB")
    if st.solid_bg is not None:
        bg = Image.new("RGB", size, st.solid_bg)
    else:
        small = ImageOps.fit(
            rgb, (w // 4, h // 4), method=Image.Resampling.BILINEAR
        )  # 작게 흐려서 키우면 빠르다
        bg = small.filter(ImageFilter.GaussianBlur(10)).resize(size, Image.Resampling.BILINEAR)
        bg = Image.blend(bg, Image.new("RGB", size, st.tint), st.tint_amount)
    x0, y0, x1, y1 = _box(size)
    bw, bh = x1 - x0, y1 - y0
    if 1.0 <= rgb.width / rgb.height <= CROP_MAX:  # 16:9까지는 양옆만 조금 잘라 칸을 채운다
        photo = ImageOps.fit(rgb, (bw, bh), method=Image.Resampling.LANCZOS, centering=(0.5, 0.4))
    else:
        photo = ImageOps.contain(rgb, (bw, bh), method=Image.Resampling.LANCZOS)
    bg.paste(photo, (x0 + (x1 - x0 - photo.width) // 2, y0 + (y1 - y0 - photo.height) // 2))
    return bg


def _box(size: tuple[int, int]) -> tuple[int, int, int, int]:
    sx, sy = size[0] / SIZE[0], size[1] / SIZE[1]
    return round(BOX[0] * sx), round(BOX[1] * sy), round(BOX[2] * sx), round(BOX[3] * sy)


def box_top(size: tuple[int, int]) -> int:
    return _box(size)[1]


def sub_top(size: tuple[int, int]) -> int:
    return round(SUB_TOP * size[1] / SIZE[1])


def split_head(text: str) -> list[str]:
    """상단 제목 → 한두 줄. 7자 이하면 한 줄, 길면 가운데에 가까운 띄어쓰기에서(없으면 글자 가운데에서) 나눈다.
    한 글자 낱말 바로 뒤('등 돌린'의 '등')에서는 되도록 자르지 않는다."""
    text = " ".join(str(text).split())
    if len(text) <= 7:
        return [text] if text else []
    spaces = [i for i, ch in enumerate(text) if ch == " "]
    mid = len(text) / 2

    def cost(i: int) -> float:
        return abs(i - mid) + (2 if len(text[:i].rsplit(" ", 1)[-1]) == 1 else 0)

    cut = min(spaces, key=cost) if spaces else round(mid)
    a, b = text[:cut].strip(), text[cut:].strip()
    return [x for x in (a, b) if x]


_NOTDEF = "\U0010fffd"  # 어느 폰트에도 없는 글자 — 없는 글자가 그려지는 네모 상자와 비교한다


def _has_glyph(font: ImageFont.FreeTypeFont | ImageFont.ImageFont, ch: str) -> bool:
    """폰트에 그 글자가 있나. Pretendard에는 한자(與·野·美)가 없어 네모 상자로 그려진다."""
    if ch.isspace():
        return True
    try:
        m, box = font.getmask(ch), font.getmask(_NOTDEF)
    except Exception:  # 그릴 수 없는 글자
        return False
    return (m.size, bytes(m)) != (box.size, bytes(box))


def _runs(text: str, font, fallback) -> list[tuple[str, object]]:  # noqa: ANN001
    """글자마다 쓸 폰트(없는 글자는 fallback)로 묶은 조각들."""
    out: list[tuple[str, object]] = []
    for ch in text:
        f = font if fallback is None or _has_glyph(font, ch) else fallback
        if out and out[-1][1] is f:
            out[-1] = (out[-1][0] + ch, f)
        else:
            out.append((ch, f))
    return out


def _load(path: str | None, size: int):  # noqa: ANN202
    return ImageFont.truetype(path, size) if path else ImageFont.load_default(size=size)


def render_headline(
    text: str, out: Path, size: tuple[int, int] = SIZE, font_path: str | None = None, style: str = "default"
) -> Path:
    """상단 제목 PNG(투명 바탕). 줄마다 가운데 정렬. 기본 디자인은 검은 테두리 글씨, 띠 디자인(navy_red)은
    줄마다 색 띠 위에 흰 글씨 — 어떤 사진 위에서도 읽힌다. font_path에 없는 글자(한자 與·野 등)는 설치된 한글 폰트로."""
    st = style_of(style)
    w, h = size
    s = w / SIZE[0]
    img = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    lines = split_head(text)
    x0, y0, x1, y1 = (round(v * s) for v in HEAD)
    path = font_path or korean_font_path()
    spare = korean_font_path()
    pad = round(34 * s) if st.bands else 0  # 띠 안쪽 여백(좌우)
    gap = round(14 * s) if st.bands else 0  # 띠 사이
    fsize = round(124 * s)
    while True:
        font = _load(path, fsize)
        fallback = _load(spare, fsize) if spare and spare != path else None
        runs = [_runs(ln, font, fallback) for ln in lines]
        widths = [sum(d.textlength(t, font=f) for t, f in r) for r in runs]
        lh = round(fsize * (1.32 if st.bands else 1.22))
        total = lh * len(lines) + gap * max(0, len(lines) - 1)
        if (max(widths, default=0) + 2 * pad <= x1 - x0 and total <= y1 - y0) or fsize <= round(60 * s):
            break
        fsize -= round(6 * s) or 1
    y = y0 + (y1 - y0 - total) // 2
    for i, (r, width) in enumerate(zip(runs, widths, strict=True)):
        k = min(i, 1) if len(lines) > 1 else 0
        color = st.text_colors[min(k, len(st.text_colors) - 1)]
        x = (w - width) / 2
        if st.bands:
            d.rounded_rectangle(
                (x - pad, y, x + width + pad, y + lh),
                radius=round(14 * s),
                fill=st.bands[min(k, len(st.bands) - 1)],
            )
        for t, f in r:
            d.text(
                (x, y + lh // 2),
                t,
                font=f,
                fill=color,
                anchor="lm",
                stroke_width=0 if st.bands else max(4, round(fsize * 0.08)),
                stroke_fill=STROKE,
            )
            if f is not font:  # 대신 쓴 폰트는 보통 굵기라 같은 색 테두리로 한 번 더 그려 굵게 맞춘다
                d.text(
                    (x, y + lh // 2),
                    t,
                    font=f,
                    fill=color,
                    anchor="lm",
                    stroke_width=max(1, round(fsize * 0.03)),
                    stroke_fill=color,
                )
            x += d.textlength(t, font=f)
        y += lh + gap
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out, "PNG")
    return out
