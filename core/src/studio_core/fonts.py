"""한글 폰트 탐색(출처 자막 PNG, 시연용 이미지 렌더링)."""

from __future__ import annotations

import os
from functools import lru_cache
from pathlib import Path

from PIL import ImageFont

CANDIDATES = [
    # macOS
    "/System/Library/Fonts/AppleSDGothicNeo.ttc",
    "/Library/Fonts/NanumGothic.ttf",
    "/System/Library/Fonts/Supplemental/AppleGothic.ttf",
    # Windows
    "C:/Windows/Fonts/malgun.ttf",
    "C:/Windows/Fonts/NanumGothic.ttf",
    # Linux
    "/usr/share/fonts/truetype/nanum/NanumGothic.ttf",
    "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
    "/usr/share/fonts/noto-cjk/NotoSansCJK-Regular.ttc",
    "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
]


@lru_cache(maxsize=1)
def korean_font_path() -> str | None:
    """사용 가능한 한글 폰트 경로. STUDIO_FONT가 있으면 우선."""
    env = os.environ.get("STUDIO_FONT")
    if env and Path(env).exists():
        return env
    for c in CANDIDATES:
        if Path(c).exists():
            return c
    return None


def load_font(size: int) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    """한글 폰트를 불러온다. 없으면 Pillow 기본 폰트(한글 깨짐 경고는 호출 측에서)."""
    path = korean_font_path()
    if path:
        return ImageFont.truetype(path, size=size)
    return ImageFont.load_default(size=size)
