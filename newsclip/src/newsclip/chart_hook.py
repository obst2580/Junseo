"""널스윤서 첫 문장 훅: 간호 차트판(클립보드)에 손글씨(210 여행일기 Bold)로 써지는 짧은 영상(mp4, 소리 없음).

첫 문장을 두 줄로 나눠 차트판 종이에 크게 쓴다. 낱말은 목소리가 그 말을 읽는 때에 맞춰 왼쪽부터 써지고(starts),
핵심 말(key)은 빨간 글씨로 쓴 뒤 빨간 펜 동그라미를 친다. 차트판은 처음에 아래에서 올라와 자리 잡고, 핵심 말이
들리는 순간 그 말 쪽으로 빠르게 당기고(펀치인 — 살짝 지나쳤다 제자리, 줌 흐림, 휙 소리), 끝 0.2초는 흐려지며 다음 컷으로 넘어간다. 화면 위쪽 제목 띠·바탕은 사진과 같은 디자인
(vertical.plain_background)이고, 차트판은 사진 칸 안에 그린다.
"""

from __future__ import annotations

import math
import random
import subprocess
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

from newsclip.render import QUOTE_FONT

FPS = 30
PEN = (12, 18, 40)  # 거의 검정에 가까운 남색 펜
RED = (214, 40, 40)
DESK = (206, 222, 232)  # 책상(옅은 하늘빛 회색)
BOARD = (126, 84, 52)  # 클립보드(갈색)
PAPER = (252, 252, 249)
RULE = (214, 222, 232)  # 종이 줄
CLIP, CLIP_IN, CLIP_HOLE = (178, 182, 188), (150, 154, 160), (120, 124, 130)
CB_W, CB_H = 940, 1180  # 클립보드 그림 크기(사진 칸 폭 1080 기준)
PAPER_BOX = (40, 110, 900, 1150)
TEXT_X, TEXT_W = 96, 724  # 글씨 왼쪽 끝·최대 폭(클립보드 그림 안)
LINE_Y = (420, 660)  # 두 줄의 글자 바닥선(클립보드 그림 안)
HAND_PX = 160  # 손글씨 시작 크기(폭이 넘으면 줄인다)
TILT = -1.5  # 차트판 기울기(도)
WRITE_SEC = 0.18  # 낱말 하나가 써지는 시간
CIRCLE_SEC = 0.3  # 동그라미가 그려지는 시간
RISE_SEC = 0.3  # 차트판이 올라오는 시간
RISE_PX = 110
PUSH = 0.05  # 영상 내내 다가가는 정도
PUNCH_ZOOM = 1.5  # 핵심 말 펀치인: 이만큼 당긴다
PUNCH_SEC = 0.28  # 당기는 데 걸리는 시간(살짝 지나쳤다 제자리까지)
PUNCH_OVER = 1.8  # 지나치는 정도(ease-out-back 세기)
ZOOM_TRAIL = 0.12  # 줌 흐림: 겹치는 화면들이 덜 당긴 정도
BLUR_SEC = 0.2  # 끝에서 흐려지는 시간
BLUR_MAX = 14


def _ease_out_back(p: float, s: float = PUNCH_OVER) -> float:
    """0→1, 끝 무렵 살짝 1을 넘었다가 1로(펀치인 손맛)."""
    p -= 1
    return 1 + (s + 1) * p**3 + s * p**2


def split_lines(text: str, n: int = 2) -> list[str]:
    """첫 문장 → 많아야 n줄. 띄어쓰기 중 두 줄 길이가 가장 비슷해지는 곳에서 나눈다(짧으면 한 줄)."""
    text = " ".join(str(text).replace("\n", " ").split()).strip(" \"“”‘’'")
    words = text.split(" ")
    if n < 2 or len(words) < 2 or len(text.replace(" ", "")) <= 7:
        return [text] if text else []
    best = min(
        range(1, len(words)),
        key=lambda k: abs(len("".join(words[:k])) - len("".join(words[k:]))),
    )
    return [" ".join(words[:best]), " ".join(words[best:])]


@dataclass(frozen=True)
class Word:
    text: str
    x: float
    line: int
    width: float
    red: bool


def layout(
    lines: list[str], key: str | None, font_path: str = str(QUOTE_FONT)
) -> tuple[ImageFont.FreeTypeFont, list[Word]]:
    """줄들 → 글꼴 크기(폭에 맞춤)와 낱말 자리. key에 든 낱말은 빨강."""
    probe = ImageDraw.Draw(Image.new("RGB", (1, 1)))
    px = HAND_PX
    while True:
        font = ImageFont.truetype(font_path, px)
        widest = max((probe.textlength(ln, font=font) for ln in lines), default=0)
        if widest + (40 if key else 0) <= TEXT_W or px <= 60:
            break
        px -= 4
    space = probe.textlength(" ", font=font)
    key_span = _key_span(" ".join(lines), key)
    out: list[Word] = []
    pos = 0  # 문장 안 글자 자리(줄 사이 공백 하나 포함)
    for li, ln in enumerate(lines):
        x = TEXT_X
        for k, w in enumerate(ln.split(" ")):
            if k:
                x += space
                pos += 1
            width = probe.textlength(w, font=font)
            red = key_span is not None and pos < key_span[1] and pos + len(w) > key_span[0]
            out.append(Word(w, x, li, width, red))
            x += width
            pos += len(w)
        pos += 1
    return font, out


def _key_span(text: str, key: str | None) -> tuple[int, int] | None:
    if not key or key not in text:
        return None
    at = text.index(key)
    return at, at + len(key)


class ChartHook:
    """차트판 훅 한 편(정지된 층은 한 번만 그려 두고 프레임마다 글씨·동그라미·움직임만 바꾼다)."""

    def __init__(
        self,
        lines: list[str],
        key: str | None,
        starts: list[float],
        duration: float,
        size: tuple[int, int],
        style: str = "health",
    ) -> None:
        from newsclip.vertical import _box, plain_background, style_of

        st = style_of(style)
        self.size = size
        self.box = _box(size, st)
        bg = plain_background(size, st)
        self.bg = bg if bg is not None else Image.new("RGB", size, (0, 0, 0))
        self.duration = duration
        self.font, self.words = layout(lines, key)
        self.starts = (list(starts) + [duration] * len(self.words))[: len(self.words)]
        reds = [i for i, w in enumerate(self.words) if w.red]
        self.circle_at = (self.starts[reds[-1]] + WRITE_SEC) if reds else None
        self.punch_at = self.starts[reds[0]] if reds and reds[0] > 0 else None  # 핵심 말 앞까지 쓰고 줌 컷
        self.board = self._board()
        self.ink = self._ink()
        self.desk = self._desk()
        self.shadow = self._shadow()

    # ---- 정지된 층 ----
    def _desk(self) -> Image.Image:
        bw, bh = self.box[2] - self.box[0], self.box[3] - self.box[1]
        desk = Image.new("RGB", (bw, bh), DESK)
        d = ImageDraw.Draw(desk)
        rnd = random.Random(3)
        for y in range(0, bh, 6):  # 결이 살짝 보이는 책상
            d.line((0, y, bw, y), fill=tuple(c + rnd.randint(-4, 4) for c in DESK))
        return desk

    def _board(self) -> Image.Image:
        cb = Image.new("RGBA", (CB_W, CB_H), (0, 0, 0, 0))
        c = ImageDraw.Draw(cb)
        c.rounded_rectangle((0, 40, CB_W, CB_H), radius=36, fill=BOARD)
        c.rounded_rectangle(PAPER_BOX, radius=6, fill=PAPER)
        c.rounded_rectangle((320, 0, 620, 120), radius=24, fill=CLIP)
        c.rounded_rectangle((350, 20, 590, 100), radius=18, fill=CLIP_IN)
        c.ellipse((445, 30, 495, 80), fill=CLIP_HOLE)
        lines = {w.line for w in self.words}
        for li in sorted(lines):
            ly = LINE_Y[min(li, len(LINE_Y) - 1)] + 30
            c.line((80, ly, 860, ly), fill=RULE, width=2)
        return cb

    def _ink(self) -> Image.Image:
        """글씨 전부(써지는 만큼만 가려서 붙인다)."""
        ink = Image.new("RGBA", (CB_W, CB_H), (0, 0, 0, 0))
        d = ImageDraw.Draw(ink)
        for w in self.words:
            d.text((w.x, self._base(w)), w.text, font=self.font, fill=RED if w.red else PEN, anchor="ls")
        return ink

    def _shadow(self) -> Image.Image:
        rot = self.board.rotate(TILT, resample=Image.BICUBIC, expand=True)
        sh = Image.new("RGBA", rot.size, (0, 0, 0, 0))
        sh.paste((0, 0, 0, 110), mask=rot.getchannel("A"))
        return sh.filter(ImageFilter.GaussianBlur(18))

    def _base(self, w: Word) -> float:
        return (
            LINE_Y[min(w.line, len(LINE_Y) - 1)] if len({x.line for x in self.words}) > 1 else sum(LINE_Y) / 2
        )

    # ---- 프레임 ----
    def frame(self, t: float) -> Image.Image:
        cb = self.board.copy()
        mask = Image.new("L", (CB_W, CB_H), 0)
        md = ImageDraw.Draw(mask)
        asc = self.font.size * 1.1
        for w, s in zip(self.words, self.starts, strict=True):
            frac = min(1.0, max(0.0, (t - s) / WRITE_SEC))
            if frac > 0:
                base = self._base(w)
                md.rectangle(
                    (w.x - 8, base - asc, w.x + (w.width + 8) * frac, base + self.font.size * 0.35), fill=255
                )
        cb.paste(self.ink, (0, 0), Image.composite(self.ink.getchannel("A"), mask, mask))
        if self.circle_at is not None and t > self.circle_at:
            self._circle(cb, min(1.0, (t - self.circle_at) / CIRCLE_SEC))
        rot = cb.rotate(TILT, resample=Image.BICUBIC, expand=True)
        rise = 1 - min(1.0, t / RISE_SEC)  # 남은 올라오기(1 → 0), 끝에서 부드럽게 멈춘다
        oy = -30 + round(RISE_PX * rise**2)
        desk = self.desk.convert("RGBA")
        ox = (desk.width - rot.width) // 2
        desk.alpha_composite(self.shadow, (ox + 14, oy + 22))
        desk.alpha_composite(rot, (ox, oy))
        box_img = box_img_src = desk.convert("RGB")
        bw, bh = box_img.size
        k = 1 + PUSH * min(1.0, t / max(self.duration, 0.01))  # 아주 살짝 다가간다(글씨 가운데 쪽으로)
        cx, cy = bw * 0.5, bh * 0.42
        moving = 0.0  # 줌이 움직이는 세기(0~1) — 움직이는 동안 줌 흐림
        if self.punch_at is not None and t >= self.punch_at:
            # 핵심 말이 들리는 순간 그 말 쪽으로 빠르게 당긴다(살짝 지나쳤다 제자리 — 펀치인), 그 뒤로도 아주 살짝 다가간다
            p = min(1.0, (t - self.punch_at) / PUNCH_SEC)
            span = max(self.duration - self.punch_at, 0.01)
            k1 = PUNCH_ZOOM + PUSH * min(1.0, (t - self.punch_at) / span)
            k = k + (k1 - k) * _ease_out_back(p)
            kx, ky = self._key_center(ox, oy)
            e = 1 - (1 - p) ** 3
            cx, cy = cx + (kx - cx) * e, cy + (ky - cy) * e
            moving = 1 - p
        if k > 1.0005:
            box_img = self._zoom(box_img, k, cx, cy)
            if moving > 0.05:  # 줌 흐림: 조금 덜 당긴 화면들을 겹쳐 안쪽으로 빨려 드는 느낌
                trail = [
                    self._zoom(box_img_src, k * (1 - ZOOM_TRAIL * moving * j / 3), cx, cy) for j in (1, 2, 3)
                ]
                for j, im in enumerate(trail, start=1):
                    box_img = Image.blend(box_img, im, 0.5 / (j + 1))
        left = self.duration - t
        if left < BLUR_SEC:
            box_img = box_img.filter(ImageFilter.GaussianBlur(BLUR_MAX * (1 - left / BLUR_SEC)))
        out = self.bg.copy()
        out.paste(box_img, self.box[:2])
        return out

    @staticmethod
    def _zoom(img: Image.Image, k: float, cx: float, cy: float) -> Image.Image:
        """(cx, cy)를 가운데 쪽에 두고 k배 당긴 화면(가장자리 밖은 잘리지 않게 붙잡는다)."""
        bw, bh = img.size
        zw, zh = round(bw * k), round(bh * k)
        if zw <= bw or zh <= bh:
            return img
        big = img.resize((zw, zh), Image.BICUBIC)
        left = round(min(max(cx * k - bw / 2, 0), zw - bw))
        top = round(min(max(cy * k - bh / 2, 0), zh - bh))
        return big.crop((left, top, left + bw, top + bh))

    def _key_center(self, ox: int, oy: int) -> tuple[float, float]:
        """핵심 말 가운데의 사진 칸 안 자리(차트판이 자리 잡은 뒤 기준, 기울기는 작아서 무시)."""
        reds = [w for w in self.words if w.red]
        x = (min(w.x for w in reds) + max(w.x + w.width for w in reds)) / 2
        y = self._base(reds[0]) - self.font.size * 0.35
        rot_dx = (self.shadow.width - CB_W) / 2  # 회전하며 늘어난 만큼
        rot_dy = (self.shadow.height - CB_H) / 2
        return ox + rot_dx + x, oy + rot_dy + y + self.font.size * 0.6  # 둘째 줄도 조금 보이게 살짝 아래로

    def _circle(self, cb: Image.Image, p: float) -> None:
        reds = [w for w in self.words if w.red]
        x1 = min(w.x for w in reds)
        x2 = max(w.x + w.width for w in reds)
        base = self._base(reds[0])
        size = self.font.size
        d = ImageDraw.Draw(cb)
        for j in range(2):  # 펜으로 두 번 돌린 동그라미
            box = (x1 - 24 + j * 4, base - size * 0.95 + j * 4, x2 + 22 - j * 3, base + 28 - j * 3)
            d.arc(
                box,
                start=200 + j * 12,
                end=200 + j * 12 + 360 * p * (1.0 if j == 0 else 0.92),
                fill=RED,
                width=7,
            )

    def write(self, out: Path) -> Path:
        """mp4로 쓴다(소리 없음)."""
        n = max(1, math.ceil(self.duration * FPS))
        W, H = self.size
        out.parent.mkdir(parents=True, exist_ok=True)
        cmd = [
            "ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}",
            "-r", str(FPS), "-i", "-", "-an", "-c:v", "libx264", "-preset", "veryfast", "-crf", "18",
            "-pix_fmt", "yuv420p", "-movflags", "+faststart", str(out),
        ]  # fmt: skip
        proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, stderr=subprocess.PIPE)
        assert proc.stdin is not None
        try:
            for i in range(n):
                proc.stdin.write(self.frame(i / FPS).tobytes())
        finally:
            proc.stdin.close()
        if proc.wait() != 0:
            raise RuntimeError(
                f"차트판 훅 영상 만들기 실패: {proc.stderr.read().decode()[-400:] if proc.stderr else ''}"
            )
        return out


def make_chart_clip(
    text: str,
    key: str | None,
    starts: list[float],
    duration: float,
    out: Path,
    size: tuple[int, int],
    style: str = "health",
    lines: list[str] | None = None,
) -> tuple[Path, float | None, float | None]:
    """첫 문장 → 차트판 훅 mp4. starts는 낱말마다 써지기 시작하는 때(lines를 띄어쓰기로 나눈 순서). 돌려주는 값:
    (파일, 펀치인 시작 때, 동그라미가 그려지기 시작하는 때 — 효과음 자리, 핵심 말이 없으면 None)."""
    ls = lines or split_lines(text)
    hook = ChartHook(ls, key, starts, duration, size, style)
    return hook.write(out), hook.punch_at, hook.circle_at
