"""자막 PNG(투명 배경) — Pillow 렌더.

- 출처 표시: 화면 왼쪽 위 작은 글씨(아래쪽은 대본 자막 자리). 세로 쇼츠는 사진 칸 왼쪽 위(위쪽은 상단 제목 자리)
- 대본 자막: 화면 아래 가운데, 흰 글씨 + 반투명 검은 상자. 세로 쇼츠는 사진 칸 바로 아래(쇼츠 버튼에 안 가리게)
"""

from __future__ import annotations

import logging
from pathlib import Path

from PIL import Image, ImageDraw

from newsclip.vertical import box_top, is_vertical, style_of, sub_top
from studio_core.fonts import korean_font_path, load_font

log = logging.getLogger("newsclip.caption")


def render_caption(
    text: str, out: Path, size: tuple[int, int] = (1920, 1080), font_size: int = 26, style: str = "default"
) -> Path:
    """출처 문구 PNG를 만든다(이미 같은 내용이면 재사용)."""
    if korean_font_path() is None:
        log.warning("한글 폰트를 찾지 못했습니다. STUDIO_FONT로 지정하세요(자막이 깨질 수 있음).")
    w, h = size
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    font = load_font(font_size)
    pad_x, pad_y, margin = 14, 8, int(min(w, h) * 0.045)
    box = draw.textbbox((0, 0), text, font=font)
    tw, th = box[2] - box[0], box[3] - box[1]
    x0, y0 = margin, (box_top(size) + margin if is_vertical(size) else margin)
    y1 = y0 + th + 2 * pad_y
    draw.rounded_rectangle((x0, y0, x0 + tw + 2 * pad_x, y1), radius=8, fill=style_of(style).caption_box)
    draw.text((x0 + pad_x - box[0], y0 + pad_y - box[1]), text, font=font, fill=(255, 255, 255, 235))
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out, "PNG")
    return out


def render_subtitle(text: str, out: Path, size: tuple[int, int] = (1920, 1080), font_size: int = 54) -> Path:
    """대본 자막 한 줄 PNG(화면 아래 가운데)."""
    if korean_font_path() is None:
        log.warning("한글 폰트를 찾지 못했습니다. STUDIO_FONT로 지정하세요(자막이 깨질 수 있음).")
    w, h = size
    vertical = is_vertical(size)
    scale = min(w, h) / 1080 * (1.2 if vertical else 1.0)  # 세로는 폭이 좁아 한 줄이 짧은 대신 조금 크게
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    font = load_font(round(font_size * scale))
    pad_x, pad_y = round(28 * scale), round(14 * scale)
    box = draw.textbbox((0, 0), text, font=font)
    tw, th = box[2] - box[0], box[3] - box[1]
    if vertical:
        y0 = sub_top(size)
        y1 = y0 + th + 2 * pad_y
    else:
        y1 = h - round(h * 0.075)
        y0 = y1 - th - 2 * pad_y
    x0 = (w - tw) // 2 - pad_x
    draw.rounded_rectangle((x0, y0, x0 + tw + 2 * pad_x, y1), radius=round(12 * scale), fill=(0, 0, 0, 170))
    draw.text((x0 + pad_x - box[0], y0 + pad_y - box[1]), text, font=font, fill=(255, 255, 255, 255))
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out, "PNG")
    return out
