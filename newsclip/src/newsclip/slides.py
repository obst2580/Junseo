"""증시 채널 영상 소스: 장면마다 PPT처럼 단순한 슬라이드 그림(1920x1080 PNG)을 만든다.

사진을 검색하는 대신 작업자가 장면 대사를 읽고 slides.json(장면 ID → 슬라이드 내용)을 쓰면 여기서 그린다.
- 채널 색: 위는 흰색, 아래로 갈수록 민트인 그라데이션 배경. 큰 검은 글씨, 포인트 색 하나(청록 형광펜) — 보기 편하고
  단순하게, 형광펜·번호 동그라미·체크 상자로 조금 재밌게. 카드는 흰색.
- 상승은 빨강(▲), 하락은 파랑(▼) — 국내 증시 색.
- slides.json에 없는 장면은 앞 장면 슬라이드를 그대로 쓴다(슬라이드는 2~4장면마다 바뀌면 충분).

레이아웃(layout):
- title     kicker, title, sub          — 이슈를 여는 큰 제목
- number    kicker, label, value, change, dir(up|down|flat), note — 지수·환율 같은 숫자 하나
- numbers   kicker, title, items[{label, value, change, dir}] (2~4개) — 지수 여럿·수급(외국인·기관·개인)
- bullets   kicker, title, bullets[] (1~4개) — 원인·포인트(장면마다 하나씩 늘려 쌓으면 PPT처럼 보인다)
- quote     kicker, quote, by           — 증권가·기관의 말(누가 한 말인지)
- checklist title, items[] (1~4개)       — 체크포인트
"""

from __future__ import annotations

import logging
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

from studio_core.fonts import korean_font_path

log = logging.getLogger(__name__)

W, H = 1920, 1080
INK = (17, 24, 39)
SUB = (107, 114, 128)
LINE = (178, 232, 222)
CARD = (255, 255, 255)
BG_TOP, BG_BOTTOM = (255, 255, 255), (120, 247, 228)  # 채널 색: 흰색 → 민트
MARK = (72, 228, 204)  # 형광펜·동그라미·체크 상자(민트보다 한 톤 진한 청록)
BAR = (13, 148, 136)  # 소제목 막대
UP, UP_BG = (224, 49, 49), (255, 236, 236)
DOWN, DOWN_BG = (28, 126, 214), (231, 245, 255)
FLAT, FLAT_BG = (107, 114, 128), (243, 244, 246)
FONT_URL = "https://cdn.jsdelivr.net/npm/pretendard@1.3.9/dist/public/static/Pretendard-{w}.otf"
WEIGHTS = ("Regular", "Bold", "ExtraBold")
LAYOUTS = ("title", "number", "numbers", "bullets", "quote", "checklist")


async def ensure_fonts(client: object, font_dir: Path) -> dict[str, str]:
    """Pretendard(OFL)를 한 번 받아 둔다. 못 받으면 설치된 한글 폰트로."""
    font_dir.mkdir(parents=True, exist_ok=True)
    out: dict[str, str] = {}
    for w in WEIGHTS:
        p = font_dir / f"Pretendard-{w}.otf"
        if not p.exists():
            try:
                resp = await client.get(FONT_URL.format(w=w))  # type: ignore[attr-defined]
                resp.raise_for_status()
                p.write_bytes(resp.content)
            except Exception as e:  # 폰트 받기 실패 → 설치된 폰트
                log.warning("슬라이드 폰트를 받지 못했습니다(%s): %s", w, e)
                continue
        out[w] = str(p)
    fallback = korean_font_path()
    for w in WEIGHTS:
        out.setdefault(w, out.get("Bold") or fallback or "")
    return out


async def fetch_font(client: object, font_dir: Path, filename: str, url: str) -> str | None:
    """폰트 파일 하나를 한 번 받아 둔다(쇼츠 디자인 폰트). 못 받으면 None."""
    p = font_dir / filename
    if not p.exists():
        try:
            resp = await client.get(url)  # type: ignore[attr-defined]
            resp.raise_for_status()
            font_dir.mkdir(parents=True, exist_ok=True)
            p.write_bytes(resp.content)
        except Exception as e:  # 폰트 받기 실패 → 기본 폰트
            log.warning("쇼츠 폰트를 받지 못했습니다(%s): %s", filename, e)
            return None
    return str(p)


class Painter:
    """폰트 묶음을 들고 슬라이드 하나를 그린다."""

    def __init__(self, fonts: dict[str, str]) -> None:
        self.fonts = fonts
        self._cache: dict[tuple[str, int], ImageFont.FreeTypeFont | ImageFont.ImageFont] = {}

    def font(self, weight: str, size: int) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
        key = (weight, size)
        if key not in self._cache:
            path = self.fonts.get(weight) or ""
            self._cache[key] = ImageFont.truetype(path, size) if path else ImageFont.load_default(size=size)
        return self._cache[key]

    # ---- 글자 ----
    def wrap(self, text: str, weight: str, size: int, width: int) -> list[str]:
        """픽셀 폭에 맞춰 줄바꿈(띄어쓰기 기준, 긴 낱말은 글자 단위)."""
        f = self.font(weight, size)
        lines: list[str] = []
        cur = ""
        for word in str(text).split():
            cand = f"{cur} {word}".strip()
            if f.getlength(cand) <= width:
                cur = cand
                continue
            if cur:
                lines.append(cur)
            cur = ""
            for ch in word:
                if f.getlength(cur + ch) > width and cur:
                    lines.append(cur)
                    cur = ""
                cur += ch
        if cur:
            lines.append(cur)
        return lines or [""]

    def fit(
        self, text: str, weight: str, size: int, width: int, max_lines: int, min_size: int = 40
    ) -> tuple[int, list[str]]:
        """max_lines 줄 안에 들어가는 가장 큰 글자 크기."""
        while size > min_size:
            lines = self.wrap(text, weight, size, width)
            if len(lines) <= max_lines:
                return size, lines
            size -= 6
        return min_size, self.wrap(text, weight, min_size, width)[:max_lines]

    # ---- 그리기 ----
    def render(self, spec: dict, out: Path, channel: str = "", date: str = "", source: str = "") -> Path:
        img = Image.new("RGB", (W, H), BG_TOP)
        d = ImageDraw.Draw(img)
        for yy in range(H):  # 위 흰색 → 아래 민트
            t = yy / (H - 1)
            d.line(
                (0, yy, W, yy),
                fill=tuple(round(a + (b - a) * t) for a, b in zip(BG_TOP, BG_BOTTOM, strict=True)),
            )
        self._frame(d, channel, date, source or str(spec.get("source", "")))
        layout = spec.get("layout") if spec.get("layout") in LAYOUTS else "title"
        getattr(self, f"_{layout}")(d, spec)
        out.parent.mkdir(parents=True, exist_ok=True)
        img.save(out, "PNG", optimize=True)
        return out

    def _frame(self, d: ImageDraw.ImageDraw, channel: str, date: str, source: str) -> None:
        if channel:  # 왼쪽 위 채널 이름(노란 알약)
            f = self.font("Bold", 30)
            w = int(f.getlength(channel))
            d.rounded_rectangle((90, 64, 90 + w + 48, 64 + 56), radius=28, fill=MARK)
            d.text((90 + 24, 64 + 28), channel, font=f, fill=INK, anchor="lm")
        if date:
            d.text((W - 90, 64 + 28), date, font=self.font("Regular", 30), fill=SUB, anchor="rm")
        d.line((90, H - 92, W - 90, H - 92), fill=LINE, width=3)
        if source:
            d.text((90, H - 56), source, font=self.font("Regular", 26), fill=SUB, anchor="lm")

    def _kicker(self, d: ImageDraw.ImageDraw, text: str, y: int = 190) -> int:
        if not text:
            return y
        f = self.font("Bold", 40)
        d.rounded_rectangle((90, y + 8, 102, y + 52), radius=6, fill=BAR)
        d.text((126, y + 30), str(text), font=f, fill=SUB, anchor="lm")
        return y + 90

    def _marker_text(
        self, d: ImageDraw.ImageDraw, lines: list[str], size: int, x: int, y: int, weight: str = "ExtraBold"
    ) -> int:
        """형광펜을 그은 큰 글씨(줄마다 아래쪽 절반에 노란 띠)."""
        f = self.font(weight, size)
        lh = int(size * 1.28)
        for ln in lines:
            w = f.getlength(ln)
            d.rectangle((x - 8, y + int(size * 0.62), x + w + 8, y + int(size * 1.02)), fill=MARK)
            d.text((x, y), ln, font=f, fill=INK)
            y += lh
        return y

    @staticmethod
    def _dir_colors(direction: str) -> tuple[tuple[int, int, int], tuple[int, int, int], str]:
        if direction == "up":
            return UP, UP_BG, "▲"
        if direction == "down":
            return DOWN, DOWN_BG, "▼"
        return FLAT, FLAT_BG, "–"

    def _pill(self, d: ImageDraw.ImageDraw, text: str, direction: str, x: int, y: int, size: int = 56) -> int:
        fg, bg, arrow = self._dir_colors(direction)
        label = f"{arrow} {text}".strip() if text else arrow
        f = self.font("Bold", size)
        w = int(f.getlength(label))
        h = int(size * 1.7)
        d.rounded_rectangle((x, y, x + w + size, y + h), radius=h // 2, fill=bg)
        d.text((x + size // 2, y + h // 2), label, font=f, fill=fg, anchor="lm")
        return x + w + size

    # ---- 레이아웃 ----
    def _title(self, d: ImageDraw.ImageDraw, s: dict) -> None:
        size, lines = self.fit(s.get("title", ""), "ExtraBold", 132, W - 180, 2)
        sz, sub = self.fit(s.get("sub", ""), "Regular", 56, W - 180, 2, 40) if s.get("sub") else (0, [])
        block = (
            (90 if s.get("kicker") else 0)
            + 10
            + len(lines) * int(size * 1.28)
            + (30 + len(sub) * int(sz * 1.4) if sub else 0)
        )
        y = self._kicker(d, s.get("kicker", ""), max(170, (H - block) // 2 - 20))  # 가운데쯤
        y = self._marker_text(d, lines, size, 90, y + 10)
        for ln in sub:
            d.text((90, y + 30), ln, font=self.font("Regular", sz), fill=SUB)
            y += int(sz * 1.4)

    def _number(self, d: ImageDraw.ImageDraw, s: dict) -> None:
        fg, bg, _arrow = self._dir_colors(s.get("dir", ""))
        d.ellipse((W - 640, 230, W + 120, 990), fill=bg)  # 오른쪽에 큰 색 동그라미(재미)
        y = self._kicker(d, s.get("kicker", ""))
        d.text((90, y + 20), str(s.get("label", "")), font=self.font("Bold", 72), fill=INK)
        size, val = self.fit(str(s.get("value", "")), "ExtraBold", 260, W - 760, 1, 120)
        d.text(
            (80, y + 120),
            val[0],
            font=self.font("ExtraBold", size),
            fill=fg if s.get("dir") in ("up", "down") else INK,
        )
        yy = y + 120 + int(size * 1.12)
        if s.get("change"):
            self._pill(d, str(s["change"]), s.get("dir", ""), 90, yy)
            yy += 130
        if s.get("note"):
            sz, note = self.fit(s["note"], "Bold", 56, W - 760, 2, 40)
            for ln in note:
                d.text((90, yy + 10), ln, font=self.font("Bold", sz), fill=SUB)
                yy += int(sz * 1.4)

    def _numbers(self, d: ImageDraw.ImageDraw, s: dict) -> None:
        y = self._kicker(d, s.get("kicker", ""))
        if s.get("title"):
            size, lines = self.fit(s["title"], "ExtraBold", 92, W - 180, 1, 56)
            y = self._marker_text(d, lines, size, 90, y)
        items = [i for i in (s.get("items") or []) if isinstance(i, dict)][:4] or [{"label": "", "value": ""}]
        n = len(items)
        gap = 40
        cw = (W - 180 - gap * (n - 1)) // n
        top, bottom = max(y + 50, 470), H - 150
        vs = min(
            self.fit(str(it.get("value", "")), "ExtraBold", 120, cw - 100, 1, 56)[0] for it in items
        )  # 카드끼리 같은 크기
        for k, it in enumerate(items):
            x = 90 + k * (cw + gap)
            moved = it.get("dir") in ("up", "down")
            fg, bg, _a = self._dir_colors(it.get("dir", ""))
            d.rounded_rectangle(
                (x, top, x + cw, bottom), radius=40, fill=bg if moved else CARD, outline=LINE, width=3
            )
            d.rounded_rectangle((x, top, x + cw, top + 18), radius=9, fill=fg if moved else MARK)
            d.text((x + 50, top + 70), str(it.get("label", "")), font=self.font("Bold", 56), fill=SUB)
            vl = self.wrap(str(it.get("value", "")), "ExtraBold", vs, cw - 100)
            d.text((x + 46, top + 160), vl[0], font=self.font("ExtraBold", vs), fill=fg if moved else INK)
            if it.get("change"):
                self._pill(d, str(it["change"]), it.get("dir", ""), x + 50, top + 190 + int(vs * 1.2), 44)

    def _bullets(self, d: ImageDraw.ImageDraw, s: dict) -> None:
        y = self._kicker(d, s.get("kicker", ""))
        if s.get("title"):
            size, lines = self.fit(s["title"], "ExtraBold", 100, W - 180, 2, 60)
            y = self._marker_text(d, lines, size, 90, y) + 30
        for k, b in enumerate([str(x) for x in (s.get("bullets") or [])][:4], start=1):
            r = 40
            d.ellipse((90, y + 6, 90 + 2 * r, y + 6 + 2 * r), fill=MARK)
            d.text((90 + r, y + 6 + r), str(k), font=self.font("ExtraBold", 48), fill=INK, anchor="mm")
            sz, lines = self.fit(b, "Bold", 62, W - 300, 2, 44)
            for ln in lines:
                d.text((210, y + 10), ln, font=self.font("Bold", sz), fill=INK)
                y += int(sz * 1.35)
            y += 34

    def _quote(self, d: ImageDraw.ImageDraw, s: dict) -> None:
        y = self._kicker(d, s.get("kicker", ""))
        d.text((70, y - 20), "“", font=self.font("ExtraBold", 300), fill=MARK)
        size, lines = self.fit(s.get("quote", ""), "Bold", 84, W - 360, 4, 52)
        yy = y + 170
        for ln in lines:
            d.text((200, yy), ln, font=self.font("Bold", size), fill=INK)
            yy += int(size * 1.4)
        if s.get("by"):
            d.text((200, yy + 40), f"— {s['by']}", font=self.font("Regular", 52), fill=SUB)

    def _checklist(self, d: ImageDraw.ImageDraw, s: dict) -> None:
        y = self._kicker(d, s.get("kicker", ""))
        size, lines = self.fit(s.get("title") or "체크포인트", "ExtraBold", 100, W - 180, 1, 60)
        y = self._marker_text(d, lines, size, 90, y) + 40
        for item in [str(x) for x in (s.get("items") or [])][:4]:
            d.rounded_rectangle((90, y + 8, 166, y + 84), radius=18, fill=MARK)
            d.line((106, y + 46, 124, y + 66), fill=INK, width=9)
            d.line((124, y + 66, 154, y + 22), fill=INK, width=9)
            sz, ln = self.fit(item, "Bold", 64, W - 300, 2, 44)
            for t in ln:
                d.text((210, y + 12), t, font=self.font("Bold", sz), fill=INK)
                y += int(sz * 1.35)
            y += 40
