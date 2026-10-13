"""세로 쇼츠 화면(1080x1920) 배치.

휴대폰 쇼츠 화면은 위·아래·오른쪽에 유튜브 버튼과 글자가 덮인다. 그래서 내용은 가운데 띠에 모은다.
- HEAD(위쪽): 영상 내내 떠 있는 상단 제목(썸네일 문구) — 흰 글씨 + 검은 테두리, 둘째 줄은 노랑.
- BOX(가운데): 사진 칸(4:3). 보통 가로 사진(16:9·3:2)은 칸에 꽉 차게 양옆만 조금 잘라 크게 보이고, 아주 넓거나
  세로인 사진은 자르지 않고 칸 안에 넣는다. 화면 전체 바탕은 같은 사진을 흐리고 어둡게 깐다(검은 빈 곳 없이).
- BOX 아래: 대본 자막 자리(프리미어 캡션도 이 높이에 두면 쇼츠 버튼에 가리지 않는다).
"""

from __future__ import annotations

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


def is_vertical(size: tuple[int, int]) -> bool:
    return size[1] > size[0]


def compose_photo(im: Image.Image, size: tuple[int, int] = SIZE) -> Image.Image:
    """사진 → 세로 화면 한 장: 흐리고 어둡게 깐 바탕 + 가운데 사진 칸의 사진."""
    w, h = size
    rgb = im.convert("RGB")
    small = ImageOps.fit(rgb, (w // 4, h // 4), method=Image.Resampling.BILINEAR)  # 작게 흐려서 키우면 빠르다
    bg = small.filter(ImageFilter.GaussianBlur(10)).resize(size, Image.Resampling.BILINEAR)
    bg = Image.blend(bg, Image.new("RGB", size, (0, 0, 0)), 0.5)
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
    """상단 제목 → 한두 줄. 7자 이하면 한 줄, 길면 가운데에 가까운 띄어쓰기에서(없으면 글자 가운데에서) 나눈다."""
    text = " ".join(str(text).split())
    if len(text) <= 7:
        return [text] if text else []
    spaces = [i for i, ch in enumerate(text) if ch == " "]
    mid = len(text) / 2
    cut = min(spaces, key=lambda i: abs(i - mid)) if spaces else round(mid)
    a, b = text[:cut].strip(), text[cut:].strip()
    return [x for x in (a, b) if x]


def render_headline(text: str, out: Path, size: tuple[int, int] = SIZE, font_path: str | None = None) -> Path:
    """상단 제목 PNG(투명 바탕). 줄마다 가운데 정렬, 검은 테두리로 어떤 사진 위에서도 읽힌다."""
    w, h = size
    s = w / SIZE[0]
    img = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    lines = split_head(text)
    x0, y0, x1, y1 = (round(v * s) for v in HEAD)
    path = font_path or korean_font_path()
    fsize = round(124 * s)
    while True:
        font = ImageFont.truetype(path, fsize) if path else ImageFont.load_default(size=fsize)
        widths = [d.textlength(ln, font=font) for ln in lines]
        lh = round(fsize * 1.22)
        if (max(widths, default=0) <= x1 - x0 and lh * len(lines) <= y1 - y0) or fsize <= round(60 * s):
            break
        fsize -= round(6 * s) or 1
    y = y0 + (y1 - y0 - lh * len(lines)) // 2
    for i, ln in enumerate(lines):
        color = HEAD_LINE_COLORS[min(i, 1)] if len(lines) > 1 else HEAD_LINE_COLORS[0]
        d.text(
            (w // 2, y + lh // 2),
            ln,
            font=font,
            fill=color,
            anchor="mm",
            stroke_width=max(4, round(fsize * 0.08)),
            stroke_fill=STROKE,
        )
        y += lh
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out, "PNG")
    return out
