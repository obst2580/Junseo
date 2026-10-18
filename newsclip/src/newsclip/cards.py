"""건강 채널 수치 카드: 장면 사이를 빠르게 지나가는 표·차트 컷(세로 쇼츠 사진 칸 1080x810 PNG).

사진·영상 장면 사이에 1~2초 끼워 숫자를 '한눈에' 보이게 하는 게 목적이라, 카드 하나에 숫자 하나 또는 항목 4개까지만
싣고 글자는 크게 쓴다. 작업자가 장면 대사를 읽고 queries.json의 "cards"(장면 ID → 카드 내용)에 쓰면, 그 장면의 첫 컷이
카드가 되고 이어서 그 장면의 사진·영상이 나온다(카드가 장면 전체를 차지하지 않는다).
- 색: 흰 카드, 검은 글씨, 강조는 하늘색 하나(채널 상단 제목과 같은 색). 주의는 주황, 위험은 빨강(신호등처럼).
- 사진 칸 아랫부분은 자막 자리라 검정으로 이어진다 — 내용은 위쪽 content_h 안에만 그린다.

레이아웃(layout):
- number   kicker, value, unit, label, note, level(ok|warn|bad)   — 기준값·숫자 하나("130/80 mmHg")
- bars     title, unit, items[{label, value, text}] (2~4개), hi(강조할 항목 번호 0부터) — 가로 막대 비교
- table    title, cols[] (2~3칸), rows[[…]] (2~4줄), hi(강조할 줄 번호) — 정상·주의·위험 기준표
- compare  title, left{label, value}, right{label, value}, note       — 전후·두 무리 비교
"""

from __future__ import annotations

import re
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

from newsclip.slides import Painter

W, H = 1080, 810  # 세로 쇼츠 사진 칸(newsclip.vertical.BOX)
CONTENT_H = 600  # 내용을 그릴 기본 높이(아래는 자막 자리) — 디자인마다 render_cards가 정한다
PAD = 64
INK = (17, 17, 17)
SUB = (110, 116, 128)
SKY = (0, 156, 222)
SKY_BG = (225, 244, 252)
GRID = (232, 236, 241)
CARD = (255, 255, 255)
LEVEL = {"ok": SKY, "warn": (245, 140, 20), "bad": (226, 52, 52)}
LAYOUTS = ("number", "bars", "table", "compare")
MAX_ITEMS = 4
CARD_GROW = 1.4  # 내용을 키울 수 있는 최대 배율
EDGE = 48  # 키운 내용과 카드 가장자리 사이


def _num(v: object) -> float | None:
    """'130', '6.5%', '1,200' → 숫자(막대 길이용). 못 읽으면 None."""
    m = re.search(r"-?\d[\d,]*\.?\d*", str(v))
    if not m:
        return None
    try:
        return float(m.group(0).replace(",", ""))
    except ValueError:
        return None


class CardPainter(Painter):
    """폰트 묶음(Pretendard)으로 카드 하나를 그린다."""

    def __init__(self, fonts: dict[str, str], h: int = H, content_h: int = CONTENT_H) -> None:
        super().__init__(fonts)
        self.h, self.content_h = h, content_h
        self.focus = (0.5, 0.4)  # 카드에서 다가갈 자리(가로·세로 비율) — 강조한 줄·숫자

    def card(self, spec: dict, out: Path) -> Path:
        layout = str(spec.get("layout") or "number")
        if layout not in LAYOUTS:
            layout = "number"
        self.focus_px: float | None = None
        work = Image.new("RGB", (W, self.content_h), CARD)
        getattr(self, f"_c_{layout}")(ImageDraw.Draw(work), spec)
        # 그린 내용만 잘라 내용 자리(위쪽 content_h)에 꽉 차게 키워(많아야 CARD_GROW배) 가운데에 놓는다 — 사진 칸이
        # 큰 디자인에서도 숫자가 작아 보이지 않게
        used = ImageChops.difference(work, Image.new("RGB", work.size, CARD)).getbbox() or (
            0,
            0,
            W,
            self.content_h,
        )
        x0, y0, x1, y1 = used
        cw, ch = max(1, x1 - x0), max(1, y1 - y0)
        k = min(CARD_GROW, (W - 2 * EDGE) / cw, (self.content_h - 2 * EDGE) / ch)
        part = work.crop(used).resize((round(cw * k), round(ch * k)), Image.Resampling.LANCZOS)
        ox, oy = (W - part.width) // 2, (self.content_h - part.height) // 2
        img = Image.new("RGB", (W, self.h), CARD)
        img.paste(part, (ox, oy))
        fy = (self.focus_px if self.focus_px is not None else (y0 + y1) / 2 - 0) - y0
        self.focus = (0.5, round(min(0.9, max(0.1, (oy + fy * k) / self.h)), 3))
        out.parent.mkdir(parents=True, exist_ok=True)
        img.save(out, "PNG")
        return out

    # ---- 공통 ----
    def _head(self, d: ImageDraw.ImageDraw, text: str, y: int = 48) -> int:
        """위쪽 제목(검정, 하늘색 막대). 다음 y를 돌려준다."""
        if not text:
            return y
        size, lines = self.fit(str(text), "ExtraBold", 74, W - 2 * PAD - 28, 2, 48)
        d.rounded_rectangle((PAD, y + 8, PAD + 12, y + 8 + size * len(lines) + 4), radius=6, fill=SKY)
        for ln in lines:
            d.text((PAD + 32, y), ln, font=self.font("ExtraBold", size), fill=INK)
            y += round(size * 1.18)
        return y + 24

    def _center(self, d: ImageDraw.ImageDraw, text: str, y: int, weight: str, size: int, fill) -> int:  # noqa: ANN001
        f = self.font(weight, size)
        d.text((W / 2, y), str(text), font=f, fill=fill, anchor="mt")
        return y + round(size * 1.2)

    # ---- 레이아웃 ----
    def _c_number(self, d: ImageDraw.ImageDraw, s: dict) -> None:
        y = 56
        if s.get("kicker"):
            y = self._center(d, s["kicker"], y, "Bold", 52, SUB)
        color = LEVEL.get(str(s.get("level") or "ok"), SKY)
        value, unit = str(s.get("value", "")), str(s.get("unit", ""))
        size, _ = self.fit(value, "ExtraBold", 200, W - 2 * PAD - (160 if unit else 0), 1, 90)
        fv, fu = self.font("ExtraBold", size), self.font("Bold", max(44, size // 3))
        wv, wu = d.textlength(value, font=fv), (d.textlength(" " + unit, font=fu) if unit else 0)
        x = (W - wv - wu) / 2
        base = y + 20 + size
        self.focus_px = base - size / 2
        d.text((x, base), value, font=fv, fill=color, anchor="ls")
        if unit:
            d.text((x + wv, base), " " + unit, font=fu, fill=SUB, anchor="ls")
        y = base + 36
        if s.get("label"):
            size2, lines = self.fit(str(s["label"]), "ExtraBold", 66, W - 2 * PAD, 2, 44)
            for ln in lines:
                y = self._center(d, ln, y, "ExtraBold", size2, INK)
        if s.get("note") and y < self.content_h - 50:
            self._center(d, s["note"], y + 6, "Bold", 42, SUB)

    def _c_bars(self, d: ImageDraw.ImageDraw, s: dict) -> None:
        y = self._head(d, s.get("title", ""))
        items = [i for i in (s.get("items") or []) if isinstance(i, dict)][:MAX_ITEMS]
        if not items:
            return
        hi = s.get("hi")
        nums = [_num(i.get("value")) or 0.0 for i in items]
        top = max(nums) or 1.0
        row = min(118, (self.content_h - y) // len(items))
        label_w = 300
        bar_x, bar_w = PAD + label_w, W - 2 * PAD - label_w - 170
        unit = str(s.get("unit", ""))
        for k, (it, v) in enumerate(zip(items, nums, strict=True)):
            on = hi == k
            cy = y + row // 2
            if on:
                self.focus_px = cy
            size, lines = self.fit(str(it.get("label", "")), "Bold", 46, label_w - 20, 1, 30)
            d.text(
                (PAD, cy),
                lines[0],
                font=self.font("ExtraBold" if on else "Bold", size),
                fill=INK,
                anchor="lm",
            )
            length = max(12, round(bar_w * v / top))
            h = round(row * 0.56)
            d.rounded_rectangle((bar_x, cy - h // 2, bar_x + bar_w, cy + h // 2), radius=h // 2, fill=GRID)
            d.rounded_rectangle(
                (bar_x, cy - h // 2, bar_x + length, cy + h // 2),
                radius=h // 2,
                fill=SKY if on or hi is None else (150, 206, 232),
            )
            txt = str(it.get("text") or f"{it.get('value', '')}{unit}")
            d.text(
                (bar_x + length + 16, cy),
                txt,
                font=self.font("ExtraBold", 50),
                fill=SKY if on else INK,
                anchor="lm",
            )
            y += row

    def _c_table(self, d: ImageDraw.ImageDraw, s: dict) -> None:
        y = self._head(d, s.get("title", ""))
        cols = [str(c) for c in (s.get("cols") or [])][:3]
        rows = [
            [str(c) for c in r][: max(1, len(cols) or 3)]
            for r in (s.get("rows") or [])
            if isinstance(r, list)
        ][:MAX_ITEMS]
        if not rows:
            return
        n = max(len(cols), max(len(r) for r in rows))
        width = W - 2 * PAD
        col_w = [width * (0.42 if n == 3 else 0.5)] + [width * (0.58 if n == 3 else 0.5) / max(1, n - 1)] * (
            n - 1
        )
        hi = s.get("hi")
        head_h = 84 if cols else 0
        row_h = min(116, (self.content_h - y - head_h) // len(rows))
        if cols:
            d.rounded_rectangle((PAD, y, W - PAD, y + head_h), radius=18, fill=INK)
            x = PAD
            for c, w in zip(cols, col_w, strict=False):
                d.text(
                    (x + w / 2, y + head_h / 2),
                    c,
                    font=self.font("Bold", 40),
                    fill=(255, 255, 255),
                    anchor="mm",
                )
                x += w
            y += head_h + 8
        for k, r in enumerate(rows):
            on = hi == k
            if on:
                self.focus_px = y + row_h / 2
                d.rounded_rectangle(
                    (PAD, y + 4, W - PAD, y + row_h - 4), radius=18, fill=SKY_BG, outline=SKY, width=4
                )
            else:
                d.line((PAD + 16, y + row_h, W - PAD - 16, y + row_h), fill=GRID, width=3)
            x = PAD
            for j, (cell, w) in enumerate(zip(r, col_w, strict=False)):
                size, lines = self.fit(
                    cell, "ExtraBold" if (on or j == 0) else "Bold", 60, round(w) - 24, 1, 30
                )
                fill = SKY if on and j > 0 else INK
                d.text(
                    (x + w / 2, y + row_h / 2),
                    lines[0],
                    font=self.font("ExtraBold" if on or j == 0 else "Bold", size),
                    fill=fill,
                    anchor="mm",
                )
                x += w
            y += row_h

    def _c_compare(self, d: ImageDraw.ImageDraw, s: dict) -> None:
        y = self._head(d, s.get("title", ""))
        left, right = s.get("left") or {}, s.get("right") or {}
        gap = 36
        bw = (W - 2 * PAD - gap) // 2
        bh = min(330, self.content_h - y - (70 if s.get("note") else 10))
        for k, side in enumerate((left, right)):
            x0 = PAD + k * (bw + gap)
            on = k == 1
            d.rounded_rectangle(
                (x0, y, x0 + bw, y + bh),
                radius=28,
                fill=SKY_BG if on else (245, 246, 248),
                outline=SKY if on else GRID,
                width=5,
            )
            size, lines = self.fit(str(side.get("label", "")), "Bold", 46, bw - 40, 1, 30)
            d.text((x0 + bw / 2, y + 34), lines[0], font=self.font("Bold", size), fill=SUB, anchor="mt")
            vsize, vl = self.fit(str(side.get("value", "")), "ExtraBold", 120, bw - 40, 1, 56)
            d.text(
                (x0 + bw / 2, y + bh / 2 + 30),
                vl[0],
                font=self.font("ExtraBold", vsize),
                fill=SKY if on else INK,
                anchor="mm",
            )
        d.text((W / 2, y + bh / 2), "→", font=self.font("ExtraBold", 64), fill=SKY, anchor="mm")
        self.focus_px = y + bh / 2
        if s.get("note"):
            self._center(d, s["note"], y + bh + 20, "Bold", 42, SUB)


def render_cards(
    specs: dict, fonts: dict[str, str], out_dir: Path, size: tuple[int, int], style: str
) -> dict[str, tuple[Path, tuple[float, float]]]:
    """{장면 ID: 카드 내용} → {장면 ID: (세로 화면 한 장 JPEG, 다가갈 자리)}. 카드는 디자인의 사진 칸 크기로 그리고
    사진과 같은 바탕에 얹는다. 내용은 자막 자리 위에만 그린다. 다가갈 자리(사진 칸 안 가로·세로 비율)는 강조한
    줄·숫자 — 쇼츠 렌더가 카드 컷에서 그쪽으로 다가간다. 바로 앞 장면에 카드가 있으면 그 장면은 건너뛴다(카드가
    이어지면 지나가는 컷이 아니라 슬라이드쇼가 된다)."""
    from newsclip.render import SUB_TEXT_Y
    from newsclip.vertical import box_of, compose_photo, style_of

    st = style_of(style)
    x0, y0, x1, y1 = box_of(st)
    content_h = min(y1 - y0 - 40, SUB_TEXT_Y + st.sub_shift - y0 - 20)
    painter = CardPainter(fonts, h=y1 - y0, content_h=content_h)
    made: dict[str, tuple[Path, tuple[float, float]]] = {}
    prev = None
    for sid in sorted(specs, key=_scene_no):
        spec = specs[sid]
        if not isinstance(spec, dict):
            continue
        no = _scene_no(sid)
        if prev is not None and no == prev + 1:
            continue
        png = painter.card(spec, out_dir / "original" / f"{sid}_card.png")
        with Image.open(png) as im:
            frame = compose_photo(im.convert("RGB"), size, style)
        dest = out_dir / f"{sid}_card.jpg"
        frame.save(dest, "JPEG", quality=92)
        made[sid] = (dest, painter.focus)
        prev = no
    return made


def _scene_no(sid: str) -> int:
    m = re.search(r"\d+", sid)
    return int(m.group(0)) if m else 0
