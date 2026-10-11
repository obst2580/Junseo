"""출처 자막 PNG(투명 배경, 하단 좌측 작은 글씨) — Pillow 렌더."""

from __future__ import annotations

import logging
from pathlib import Path

from PIL import Image, ImageDraw

from studio_core.fonts import korean_font_path, load_font

log = logging.getLogger("newsclip.caption")


def render_caption(text: str, out: Path, size: tuple[int, int] = (1920, 1080), font_size: int = 26) -> Path:
    """출처 문구 PNG를 만든다(이미 같은 내용이면 재사용)."""
    if korean_font_path() is None:
        log.warning("한글 폰트를 찾지 못했습니다. STUDIO_FONT로 지정하세요(자막이 깨질 수 있음).")
    w, h = size
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    font = load_font(font_size)
    pad_x, pad_y, margin = 14, 8, int(h * 0.045)
    box = draw.textbbox((0, 0), text, font=font)
    tw, th = box[2] - box[0], box[3] - box[1]
    x0, y1 = margin, h - margin
    y0 = y1 - th - 2 * pad_y
    draw.rounded_rectangle((x0, y0, x0 + tw + 2 * pad_x, y1), radius=8, fill=(0, 0, 0, 150))
    draw.text((x0 + pad_x - box[0], y0 + pad_y - box[1]), text, font=font, fill=(255, 255, 255, 235))
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out, "PNG")
    return out
