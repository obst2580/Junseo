"""정치는푸르다 10분 롱폼·3분 단독 편집 — 챕터·자막·카드·상단 표시를 timeline.xml 트랙으로 깐다(프리미어에서 열고 내보내기만).

- 챕터: 대본의 `## 제목 — 한 줄 요약` 헤더(장면 topic). 헤더 없는 앞부분은 콜드 오픈, `## 정리`는 정리.
  3분 단독은 주제(챕터)가 하나라 목차 카드 없이 챕터 카드부터.
- 트랙(아래 → 위): 사진·영상(V1) · 출처(V2) · 아래 그늘 · 자막(모란봉 100px, 강조는 같은 글씨에 그 말만 색) · 챕터 이름·채널 이름 ·
  카드(목차·챕터·발언·끝 화면 — 화면 전체를 덮는다)
- 소리: 음성(A1), 효과음(A2 — 챕터 휙, 발언 카드·훅 팝, 질문 띵 — 아래 자막에는 없음), 배경음악(A3, 반복해 작게)
- 유튜브 설명란 챕터(0:00 …)도 같이 만든다.
"""

from __future__ import annotations

import hashlib
import re
import subprocess
from dataclasses import dataclass, field
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageOps

from newsclip.models import Scene
from newsclip.sfx import DING, HIT, WHOOSH, ensure_sfx, sfx_cues

SIZE = (1920, 1080)
FONTS = Path(__file__).parent / "fonts"
TITLE_FONT = FONTS / "210MammothPpang-R.TTF"  # 제목·채널 이름·챕터 번호·훅
BODY_FONT = FONTS / "210Moranbong-Light.TTF"  # 자막·요약·누가/언제·챕터 이름
QUOTE_FONT = FONTS / "210Yeohaengilgi-B.TTF"  # 발언 카드 인용(손글씨)
BLUE, YELLOW, RED, WHITE = (62, 136, 200), (255, 236, 0), (228, 36, 36), (255, 255, 255)
NAVY = (8, 16, 32)
OPENING, WRAP = "오프닝", "정리"
CHAPTER_CARD_SEC = 2.2  # 챕터 카드(휙 소리와 함께)
TOC_SEC = (2.5, 6.0)  # 목차 카드 길이(그 문장 동안, 이 범위 안)
QUOTE_SEC = (2.5, 5.0)  # 발언 카드 길이(그 장면 동안, 이 범위 안)
END_SEC = 20.0  # 끝 화면(유튜브 최종 화면은 5~20초)
EMPH_SAG = 14.0  # 강조 자막: 흐름이 이만큼 늘어질 때만 하나씩(쇼츠는 8초)
# 초반 30초(콜드 오픈)는 쇼츠처럼 세게: 첫 문장은 화면 한가운데 큰 글씨(훅), 강조 자막은 5초 간격까지, 사진은 장면 안에서도
# 다른 사진으로 빨리 넘긴다(1.5초 이상 컷)
OPEN_SAG = 5.0
OPEN_CUT_MIN = 1.5
HOOK_SEC = 4.0  # 이 시각 전에 시작하는 첫 문장 자막이 훅
HOOK_MIN = 2.5  # 훅 한 줄이 떠 있는 최소 시간(조각으로 넘기지 않고 센 말 하나를 머물게)
# 첫 문장 센 말을 화면 한가운데 크게 띄울지. 끔: 첫 문장도 말한 그대로 보통 자막으로(강조 색 없이)
HOOK_CARD = False
SUB_LINE_LONG = 22  # 가로 자막 한 줄 글자 수(쇼츠보다 넓은 화면 — 줄이 덜 자주 바뀌게)
FLASH_SEC, FLASH_CHARS = (
    1.0,
    10,
)  # 이보다 짧게 스치는 짧은 줄은 앞(문장 끝 꼬리)이나 뒤(문장 머리) 줄과 합친다
HOOK_PX, HOOK_KEY_PX = 130, 170
BGM_LEVEL = 0.15  # 배경음악 크기(약 -16 dB — 10분 내내 깔려도 목소리를 덮지 않게, 들리기는 하게)
VOICE_PEAK = -1.0  # 음성 최고점(dBTP) — 넘으면 음성 트랙 크기를 낮춰 내보낼 때 찢어지지 않게
SUB_PX = 100  # 아래 자막 글자 크기(강조 줄도 같은 크기·글꼴 — 강조는 그 말의 색만 바꾼다)
SUB_BOTTOM = 1000  # 자막 아래 끝(유튜브 재생 막대 위)
ORDINALS = ["첫", "두", "세", "네", "다섯", "여섯"]
_TOC_RE = re.compile("차례로|순서대로|짚어 보|살펴보|하나씩")


@dataclass
class Chapter:
    """챕터 하나: no 0은 콜드 오픈, -1은 정리."""

    no: int
    title: str
    summary: str
    start: float
    end: float
    scenes: list[Scene] = field(default_factory=list)


@dataclass
class LongEdit:
    """timeline.xml에 더할 것: 그림 트랙들(아래 → 위), 효과음, 마커, 설명란 챕터, 올릴 파일."""

    tracks: list[list[tuple[Path, float, float]]]
    sfx: list[tuple[float, Path]]
    markers: list[tuple[float, str, str]]
    chapters: list[Chapter]
    youtube: str
    files: list[Path]
    v1: list[Scene] = field(
        default_factory=list
    )  # V1(사진·영상) 장면 — 콜드 오픈은 장면 안에서도 사진을 바꾼다


def split_heading(topic: str) -> tuple[str, str]:
    """'제목 — 한 줄 요약' → (제목, 요약). 요약이 없으면 ''."""
    parts = re.split(r"\s+[—–-]\s+", topic.strip(), maxsplit=1)
    return parts[0].strip(), (parts[1].strip() if len(parts) > 1 else "")


def chapters_of(scenes: list[Scene]) -> list[Chapter]:
    """장면 topic이 바뀌는 곳마다 챕터. 헤더 없는 앞부분('도입')은 콜드 오픈, '정리'로 시작하는 헤더는 정리."""
    out: list[Chapter] = []
    no = 0
    for sc in sorted(scenes, key=lambda s: s.start_sec):
        if out and out[-1].scenes[-1].topic == sc.topic:
            out[-1].scenes.append(sc)
            out[-1].end = sc.end_sec
            continue
        title, summary = split_heading(sc.topic)
        if sc.topic == "도입" and not out:
            ch = Chapter(0, OPENING, "", sc.start_sec, sc.end_sec)
        elif title.startswith(WRAP):
            ch = Chapter(-1, WRAP, summary, sc.start_sec, sc.end_sec)
        else:
            no += 1
            ch = Chapter(no, title, summary, sc.start_sec, sc.end_sec)
        ch.scenes.append(sc)
        out.append(ch)
    return out


def _mmss(sec: float) -> str:
    s = int(sec)
    return f"{s // 3600}:{s // 60 % 60:02d}:{s % 60:02d}" if s >= 3600 else f"{s // 60}:{s % 60:02d}"


def youtube_chapters(chapters: list[Chapter]) -> str:
    """설명란 챕터: 첫 줄은 0:00, 10초보다 짧은 챕터는 뺀다(유튜브 규칙: 3개 이상, 각 10초 이상)."""
    lines: list[str] = []
    for ch in chapters:
        if ch.end - ch.start < 10 and lines:
            continue
        name = ch.title if ch.no <= 0 else f"{ch.no}. {ch.title}"
        lines.append(f"{_mmss(0 if not lines else ch.start)} {name}")
    return "\n".join(lines) if len(lines) >= 3 else ""


# ---- 그림 -------------------------------------------------------------------------------------------


def _font(path: Path, px: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(path), px)


def _fit(d: ImageDraw.ImageDraw, text: str, path: Path, px: int, max_w: int, min_px: int = 28):
    """한 줄이 max_w에 들어가는 가장 큰 글자(px부터 줄여 간다)."""
    while px > min_px:
        f = _font(path, px)
        if max(d.textlength(ln, font=f) for ln in text.split("\n")) <= max_w:
            return f
        px -= 4
    return _font(path, min_px)


def _wrap(text: str, per_line: int) -> str:
    """공백에서 끊어 한 줄 per_line자(공백 빼고) 안쪽으로."""
    lines, cur = [], ""
    for w in text.split():
        cand = f"{cur} {w}".strip()
        if cur and len(cand.replace(" ", "")) > per_line:
            lines.append(cur)
            cur = w
        else:
            cur = cand
    return "\n".join([*lines, cur] if cur else lines)


def _center(d, xy, text, font, fill, stroke=0, spacing=14) -> None:  # noqa: ANN001
    d.multiline_text(
        xy, text, font=font, fill=fill, anchor="mm", align="center", spacing=spacing,
        stroke_width=stroke, stroke_fill=(0, 0, 0),
    )  # fmt: skip


def _cover(path: Path | None, size: tuple[int, int] = SIZE, centering=(0.5, 0.35)) -> Image.Image | None:  # noqa: ANN001
    if path is None or not path.exists():
        return None
    try:
        with Image.open(path) as im:
            return ImageOps.fit(im.convert("RGB"), size, Image.Resampling.LANCZOS, centering=centering)
    except OSError:
        return None


def _dark_bg(photo: Path | None, amount: float = 0.68, blur: int = 30) -> Image.Image:
    im = _cover(photo)
    if im is None:
        return Image.new("RGB", SIZE, NAVY)
    return Image.blend(im.filter(ImageFilter.GaussianBlur(blur)), Image.new("RGB", SIZE, NAVY), amount)


def _tag(d: ImageDraw.ImageDraw, ch: Chapter | None, channel: str) -> None:
    """왼쪽 위 지금 챕터(번호는 매머드빵, 이름은 모란봉), 오른쪽 위 채널 이름."""
    if ch is not None and ch.no != 0:
        no = str(ch.no) if ch.no > 0 else "◆"
        fn, f = _font(TITLE_FONT, 40), _font(BODY_FONT, 40)
        name = ch.title
        while name and d.textlength(name, font=f) > 860:
            name = name[:-1]
        nw, tw = d.textlength(no, font=fn), d.textlength(name, font=f)
        d.rounded_rectangle((48, 44, 48 + nw + tw + 84, 106), 12, fill=BLUE)
        d.text((76, 76), no, font=fn, fill=WHITE, anchor="lm")
        d.text((76 + nw + 18, 77), name, font=f, fill=WHITE, anchor="lm")
    d.text((SIZE[0] - 56, 75), channel, font=_font(TITLE_FONT, 44), fill=WHITE, anchor="rm",
           stroke_width=4, stroke_fill=(0, 0, 0))  # fmt: skip


def render_chrome(ch: Chapter | None, channel: str, out: Path) -> Path:
    img = Image.new("RGBA", SIZE, (0, 0, 0, 0))
    _tag(ImageDraw.Draw(img), ch, channel)
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out, "PNG")
    return out


def render_shade(out: Path, h: int = 420, alpha: int = 230) -> Path:
    """화면 아래 그늘(자막이 어느 사진 위에서도 읽히게)."""
    img = Image.new("RGBA", SIZE, (0, 0, 0, 0))
    col = Image.new("L", (1, h))
    for y in range(h):
        col.putpixel((0, y), int(alpha * (y / h) ** 1.4))
    img.paste(Image.new("RGBA", (SIZE[0], h), (0, 0, 0, 255)), (0, SIZE[1] - h), col.resize((SIZE[0], h)))
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out, "PNG")
    return out


def render_sub(
    text: str, out: Path, color: tuple[int, int, int] | None = None, mark: str | None = None
) -> Path:
    """아래 자막: 모란봉 흰 글씨. color가 있으면 강조 — 글꼴·크기는 그대로 두고 mark(따옴표 인용·센 말)만 색으로,
    mark가 없으면 줄 전체를 색으로. 아래 끝을 SUB_BOTTOM에 맞춘다."""
    img = Image.new("RGBA", SIZE, (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    text = text.strip()
    f, fill, stroke = _fit(d, text, BODY_FONT, SUB_PX, 1760), color or WHITE, 6
    box = d.multiline_textbbox((0, 0), text, font=f, spacing=14, stroke_width=stroke, align="center")
    th = box[3] - box[1]
    rows = text.split("\n")
    if color is not None and mark and any(mark in r for r in rows):
        lh = th / len(rows)  # 줄마다 가운데 맞춰, 강조할 말이 든 줄에서 그 말만 색으로
        for n, row in enumerate(rows):
            x = (SIZE[0] - d.textlength(row, font=f)) / 2
            y = SUB_BOTTOM - th + lh * (n + 0.5)
            at = row.find(mark)
            segs = (
                [(row, WHITE)]
                if at < 0
                else [(row[:at], WHITE), (mark, color), (row[at + len(mark) :], WHITE)]
            )
            for seg, col in segs:
                if seg:
                    d.text(
                        (x, y), seg, font=f, fill=col, anchor="lm", stroke_width=stroke, stroke_fill=(0, 0, 0)
                    )
                    x += d.textlength(seg, font=f)
    else:
        _center(d, (SIZE[0] // 2, SUB_BOTTOM - th // 2), text, f, fill, stroke=stroke)
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out, "PNG")
    return out


def render_chapter_card(ch: Chapter, photo: Path | None, out: Path) -> Path:
    im = _dark_bg(photo)
    d = ImageDraw.Draw(im)
    if ch.no > 0:
        label = f"{ORDINALS[ch.no - 1] if ch.no <= len(ORDINALS) else ch.no} 번째 장면"
        title = ch.title
    else:
        label, title = "마지막으로", "정리"
    d.rectangle((0, 470, SIZE[0], 476), fill=BLUE)
    _center(d, (SIZE[0] // 2, 360), label, _font(TITLE_FONT, 90), BLUE)
    # 제목은 한 줄로(120px까지 줄여 본다) — 그래도 넘치면 두 줄
    f = _fit(d, title, TITLE_FONT, 170, 1720, 120)
    if d.textlength(title, font=f) > 1720:
        title = _wrap(title, max(6, len(title.replace(" ", "")) // 2 + 1))
        f = _fit(d, title, TITLE_FONT, 150, 1720)
    _center(d, (SIZE[0] // 2, 640 if "\n" in title else 600), title, f, WHITE)
    if ch.summary:
        _center(d, (SIZE[0] // 2, 880 if "\n" in title else 760), ch.summary, _fit(d, ch.summary, BODY_FONT, 56, 1600),
                (220, 220, 220))  # fmt: skip
    out.parent.mkdir(parents=True, exist_ok=True)
    im.save(out, "PNG")
    return out


def render_toc_card(chapters: list[Chapter], photo: Path | None, out: Path) -> Path:
    im = _dark_bg(photo, amount=0.74)
    d = ImageDraw.Draw(im)
    _center(d, (SIZE[0] // 2, 230), "오늘의 순서", _font(TITLE_FONT, 92), BLUE)
    items = [c for c in chapters if c.no > 0][:5]
    gap = 140 if len(items) <= 3 else 110
    y0 = 580 - gap * (len(items) - 1) // 2
    fn = _font(TITLE_FONT, 80)
    fonts = [_fit(d, c.title, BODY_FONT, 80, 1300) for c in items]
    # 번호 + 제목 덩어리를 가운데로(가장 긴 줄 기준, 줄마다 왼쪽 맞춤)
    block = max((90 + d.textlength(c.title, font=f) for c, f in zip(items, fonts, strict=True)), default=0)
    x0 = (SIZE[0] - block) // 2
    for k, (c, f) in enumerate(zip(items, fonts, strict=True)):
        y = y0 + k * gap
        d.text((x0 + 30, y), str(c.no), font=fn, fill=YELLOW, anchor="mm")
        d.text((x0 + 90, y), c.title, font=f, fill=WHITE, anchor="lm")
    out.parent.mkdir(parents=True, exist_ok=True)
    im.save(out, "PNG")
    return out


def render_hook(text: str, out: Path, kind: str = "chunk") -> Path:
    """훅(첫 문장) 글자: 화면 한가운데 크게. chunk는 매머드빵 흰 글씨, quote는 “따옴표”째 손글씨 노랑, act는 빨강.
    훅이 떠 있는 동안 사진을 조금 어둡게 깔아 글자가 어느 사진 위에서도 또렷하게."""
    img = Image.new("RGBA", SIZE, (0, 0, 0, 96))
    d = ImageDraw.Draw(img)
    text = text.strip()
    if len(text.replace(" ", "")) > 13:  # 긴 말은 두 줄로(작게 줄이지 않고 — 자막 줄 나누기와 같은 자리에서)
        from newsclip.autosub import _split_lines

        text = "\n".join(_split_lines(text, 2))
    if kind == "quote":
        text = f"“{text.strip(chr(34) + chr(39) + '“”‘’')}”"
        f, fill, stroke = _fit(d, text, QUOTE_FONT, HOOK_KEY_PX, 1760, 70), YELLOW, 7
    elif kind == "act":
        f, fill, stroke = _fit(d, text, TITLE_FONT, HOOK_KEY_PX, 1760, 70), RED, 9
    else:
        f, fill, stroke = _fit(d, text, TITLE_FONT, HOOK_PX, 1760, 60), WHITE, 9
    _center(d, (SIZE[0] // 2, SIZE[1] // 2 + 20), text, f, fill, stroke=stroke)
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out, "PNG")
    return out


def render_quote_card(spec: dict, photo: Path | None, ch: Chapter | None, channel: str, out: Path) -> Path:
    """발언 카드: 왼쪽 인물 사진, 오른쪽 손글씨 노란 인용 + 누가·언제."""
    im = Image.new("RGB", SIZE, (10, 14, 22))
    d = ImageDraw.Draw(im)
    face = _cover(photo, (760, SIZE[1]), (0.5, 0.3))
    cx = 1330 if face is not None else SIZE[0] // 2
    if face is not None:
        im.paste(face, (0, 0))
        fade = Image.new("L", (240, 1))
        for x in range(240):
            fade.putpixel((x, 0), int(255 * x / 240))
        im.paste(Image.new("RGB", (240, SIZE[1]), (10, 14, 22)), (520, 0), fade.resize((240, SIZE[1])))
    _tag(d, ch, channel)
    quote = str(spec.get("text") or "").strip().strip("\"'“”‘’")
    quote = _wrap(f"“{quote}”", 9)
    width = 1060 if face is not None else 1600
    _center(d, (cx, 470), quote, _fit(d, quote, QUOTE_FONT, 132, width, 60), YELLOW, spacing=30)
    who, when = str(spec.get("who") or "").strip(), str(spec.get("when") or "").strip()
    if who or when:
        d.rectangle((cx - 320, 690, cx + 320, 694), fill=BLUE)
    if who:
        _center(d, (cx, 760), who, _fit(d, who, BODY_FONT, 52, width), WHITE)
    if when:
        _center(d, (cx, 830), when, _fit(d, when, BODY_FONT, 40, width), (190, 190, 190))
    out.parent.mkdir(parents=True, exist_ok=True)
    im.save(out, "PNG")
    return out


def render_end_card(channel: str, photo: Path | None, out: Path) -> Path:
    """끝 화면: 채널 이름과 한 줄. 아래쪽은 비워 둔다(유튜브 최종 화면의 다음 영상·구독 버튼 자리)."""
    im = _dark_bg(photo, amount=0.74, blur=40)
    d = ImageDraw.Draw(im)
    _center(d, (SIZE[0] // 2, 190), channel, _font(TITLE_FONT, 120), WHITE)
    _center(d, (SIZE[0] // 2, 300), "구독과 좋아요, 다음 소식도 함께해요", _font(BODY_FONT, 54), YELLOW)
    out.parent.mkdir(parents=True, exist_ok=True)
    im.save(out, "PNG")
    return out


# ---- 편집 계획 ----------------------------------------------------------------------------------------


_CLEAN: dict[str, bool] = {}


def _usable(a) -> bool:  # noqa: ANN001 - Asset
    """카드·바탕에 써도 되는 사진: 받은 사진이고 영상 썸네일(큰 제목 글씨)이 아닌 것."""
    if a is None or a.media != "image" or not a.local_path or not Path(a.local_path).exists():
        return False
    if a.local_path not in _CLEAN:
        from newsclip.download import text_heavy

        _CLEAN[a.local_path] = not text_heavy(a)
    return _CLEAN[a.local_path]


def _photos(sc: Scene) -> list:
    """장면의 쓸 수 있는 사진(고른 것 먼저)."""
    seen, out = set(), []
    for a in [sc.chosen, *sc.candidates]:
        if a is not None and a.local_path not in seen and _usable(a):
            seen.add(a.local_path)
            out.append(a)
    return out


def _photo(sc: Scene) -> Path | None:
    got = _photos(sc)
    return Path(got[0].local_path) if got else None


def _first_photo(scenes: list[Scene]) -> Path | None:
    return next((p for p in map(_photo, scenes) if p is not None), None)


def _speaker_photo(spec: dict, sc: Scene, pool: list[Scene]) -> Path | None:
    """발언 카드 인물 사진: 그 말을 한 사람 이름이 제목에 든 사진만(그 장면 먼저, 다음은 같은 챕터). 없으면 사진 없이 —
    다른 사람 사진에 그 사람 말을 붙이지 않는다."""
    name = (str(spec.get("who") or "").split() or [""])[0]
    if len(name) < 2:
        return None
    for s_ in [sc, *[x for x in pool if x.id != sc.id]]:
        for a in _photos(s_):
            if name in (a.title or ""):
                return Path(a.local_path)
    return None


def _clean_v1(scenes: list[Scene]) -> list[Scene]:
    """V1에 깔 사진이 영상 썸네일(큰 제목 글씨)이면 같은 장면의 다른 사진으로, 없으면 가까운 앞뒤 장면 사진으로 바꾼다."""
    out = []
    for i, sc in enumerate(scenes):
        a = sc.chosen
        if a is None or a.media != "image" or _usable(a):
            out.append(sc)
            continue
        alt = next(iter(_photos(sc)), None)
        for d in range(1, len(scenes)):
            if alt is not None:
                break
            for j in (i - d, i + d):
                if 0 <= j < len(scenes) and (got := _photos(scenes[j])):
                    alt = got[0]
                    break
        if alt is None:
            out.append(sc)
            continue
        rest = [x for x in sc.candidates if x.local_path != alt.local_path and _usable(x)]
        out.append(sc.model_copy(update={"chosen": alt, "candidates": [alt, *rest]}))
    return out


def _open_cuts(scenes: list[Scene], opening_end: float, cut_at: list[float]) -> list[Scene]:
    """콜드 오픈 장면은 장면 안에서도 다른 사진으로 넘긴다: 쓸 수 있는 사진 수만큼(컷은 OPEN_CUT_MIN초 이상),
    자르는 때는 자막이 바뀌는 때에 맞춘다(없으면 고르게)."""
    out: list[Scene] = []
    for sc in _clean_v1(scenes):
        photos = _photos(sc)
        dur = sc.end_sec - sc.start_sec
        n = min(len(photos), int(dur // OPEN_CUT_MIN)) if sc.start_sec < opening_end else 1
        if n < 2 or (sc.chosen is not None and sc.chosen.media != "image"):
            out.append(sc)
            continue
        inner = [t for t in cut_at if sc.start_sec + OPEN_CUT_MIN <= t <= sc.end_sec - OPEN_CUT_MIN]
        bounds = [sc.start_sec]
        for k in range(1, n):
            want = sc.start_sec + dur * k / n
            t = min(inner, key=lambda x: abs(x - want)) if inner else want
            if t - bounds[-1] >= OPEN_CUT_MIN:
                bounds.append(t)
        bounds.append(sc.end_sec)
        for k in range(len(bounds) - 1):
            a = photos[k % len(photos)]
            rest = [x for x in sc.candidates if x.local_path != a.local_path]
            out.append(sc.model_copy(update={
                "id": sc.id if k == 0 else f"{sc.id}{chr(97 + k)}",
                "start_sec": bounds[k], "end_sec": bounds[k + 1], "chosen": a, "candidates": [a, *rest],
            }))  # fmt: skip
    return out


_QUOTE_SPAN = re.compile("(['\"“‘])([^'\"“”‘’]{2,16})(['\"”’])")
MARK_MAX = 14  # 강조할 말(색 글자) 길이 상한(공백 빼고)
# 10분 영상에서 너무 흔해 강조가 안 되는 센 말(쇼츠는 그대로 강조)
_COMMON_STRONG = re.compile("논란|의혹|비판|비난|경고")


def _mark(text: str, keywords: set[str]) -> tuple[str, tuple[int, int, int]] | None:
    """강조할 말과 색: 센 말이 든 낱말 → 빨강, 따옴표째 인용(짧은 것) → 노랑, 핵심 키워드 낱말 → 초록.
    따옴표 짝이 안 맞는 줄(인용이 줄 사이에서 끊긴 줄)은 강조하지 않는다."""
    from newsclip.render import EMPH_GREEN, EMPH_RED, EMPH_YELLOW, is_hot
    from newsclip.shorts_render import STRONG

    if not _balanced(text):
        return None
    for w in text.split():
        if STRONG.search(w) and not _COMMON_STRONG.search(w):
            return w.rstrip(",.?!…"), EMPH_RED
    m = _QUOTE_SPAN.search(text)
    if m and len(re.sub(r"\s", "", m.group(2))) <= MARK_MAX:
        return m.group(0), EMPH_YELLOW
    for w in text.split():
        if is_hot(w, keywords) and not is_hot(w, set()):
            return w.rstrip(",.?!…"), EMPH_GREEN
    return None


def _pick(
    lines: list, keywords: set[str], opening_end: float, after: float
) -> dict[int, tuple[tuple[int, int, int], str]]:
    """강조 자막: 콜드 오픈은 OPEN_SAG초, 그 뒤는 EMPH_SAG초 흐름이 늘어질 때만 하나씩(after = 훅이 끝난 때).
    줄 전체가 아니라 그 줄의 센 말·인용만 색으로(반환: 줄 번호 → (색, 강조할 말))."""
    from newsclip.render import EMPH_GREEN, EMPH_RED, EMPH_YELLOW
    from newsclip.shorts_render import EMPH_LOOK

    order = (EMPH_RED, EMPH_YELLOW, EMPH_GREEN)
    cands = [(k, mk) for k, c in enumerate(lines) if c.start >= after and (mk := _mark(c.text, keywords))]
    picked: dict[int, tuple[tuple[int, int, int], str]] = {}
    used: set[str] = set()  # 같은 말은 한 번만 강조
    last, i = after, 0
    while i < len(cands):
        t = lines[cands[i][0]].start
        if t - last < (OPEN_SAG if t < opening_end else EMPH_SAG) or _flat(cands[i][1][0]) in used:
            i += 1
            continue
        window = [
            j
            for j in range(i, len(cands))
            if lines[cands[j][0]].start - t <= EMPH_LOOK and _flat(cands[j][1][0]) not in used
        ]
        best = min(window, key=lambda j: (order.index(cands[j][1][1]), j))
        phrase, col = cands[best][1]
        picked[cands[best][0]] = (col, phrase)
        used.add(_flat(phrase))
        last = lines[cands[best][0]].start
        i = best + 1
    return picked


def _merge_flashes(lines: list) -> list:
    """1초도 안 떠 있는 짧은 줄('말했는데요,', '바로 이어서')을 이웃 줄과 합친다. 끝이 맺어진 꼬리는 앞 줄에,
    머리는 뒤 줄에. 합쳐 길어지면 두 줄로."""
    from dataclasses import replace

    from newsclip.autosub import _SENT_END_RE

    out = list(lines)
    i = 0
    while i < len(out):
        c = out[i]
        short = c.end - c.start < FLASH_SEC and len(c.text.replace(" ", "")) <= FLASH_CHARS
        tail = _SENT_END_RE.search(c.text.strip()) or c.text.strip().endswith(",")
        if short and tail and i > 0 and c.start - out[i - 1].end < 0.15:
            p = out[i - 1]
            sep = " " if len(p.text) + len(c.text) < SUB_LINE_LONG + 4 else "\n"
            out[i - 1] = replace(p, end=c.end, text=f"{p.text.strip()}{sep}{c.text.strip()}")
            del out[i]
            continue
        if short and not tail and i + 1 < len(out) and out[i + 1].start - c.end < 0.15:
            n = out[i + 1]
            sep = " " if len(n.text) + len(c.text) < SUB_LINE_LONG + 4 else "\n"
            out[i + 1] = replace(n, start=c.start, text=f"{c.text.strip()}{sep}{n.text.strip()}")
            del out[i]
            continue
        i += 1
    return out


def _flat(text: str) -> str:
    """비교용: 띄어쓰기·따옴표·문장부호를 뺀 글자."""
    return re.sub("[\\s\"'“”‘’.,!?…]", "", text)


def _balanced(text: str) -> bool:
    """따옴표 짝이 맞는 줄인가(인용이 줄 사이에서 끊기지 않았나)."""
    return (
        all(text.count(q) % 2 == 0 for q in "\"'")
        and text.count("“") == text.count("”")
        and (text.count("‘") == text.count("’"))
    )


def _hook(
    lines: list, words: list | None, lx: Path
) -> tuple[list[tuple[Path, float, float]], set[int], float | None]:
    """첫 문장(HOOK_SEC 전에 시작하는 자막부터 문장이 끝나는 자막까지) → 화면 한가운데 큰 글씨 한 줄.
    조각으로 넘기지 않고 가장 센 말 하나(따옴표 안 발언은 손글씨 노랑, 없으면 문장 끝 행위는 빨강, 둘 다 없으면
    첫 줄)를 첫 문장 내내(적어도 HOOK_MIN초) 띄운다. 아래 자막은 그 말이 든 줄만 뺀다.
    반환: (그림 목록, 뺄 자막 줄 번호, 핵심이 뜨는 때)."""
    from newsclip.shorts_render import hook_key

    n = _first_sentence(lines)
    if not n:
        return [], set(), None
    start, end = lines[0].start, lines[n - 1].end
    text = " ".join(c.text.replace("\n", " ").strip() for c in lines[:n])
    key = hook_key(text)
    kind, phrase = key if key is not None and key[1] in text else ("chunk", lines[0].text.strip())
    later = [c.start for c in lines[n:]]
    end = max(end, min(start + HOOK_MIN, later[0] if later else start + HOOK_MIN))
    art = render_hook(phrase, lx / f"hook_{_key(phrase, kind)}.png", kind)

    core = _flat(phrase)
    drop = {k for k in range(n) if core and (core in _flat(lines[k].text) or _flat(lines[k].text) in core)}
    return [(art, start, end)], drop, (start if kind != "chunk" else None)


def _first_sentence(lines: list) -> int:
    """첫 문장 자막 줄 수: HOOK_SEC 전에 시작하는 자막부터 문장이 끝나는 자막까지."""
    from newsclip.shorts_render import _SENT_END

    n = 0
    for cue in lines:
        if cue.start >= HOOK_SEC:
            break
        n += 1
        if _SENT_END.search(cue.text.strip()):
            break
    return n


def _key(*parts: object) -> str:
    return hashlib.sha1("|".join(map(str, parts)).encode()).hexdigest()[:10]


def voice_level(path: Path) -> float:
    """음성 트랙 크기(1.0 = 그대로): 최고점(true peak)이 VOICE_PEAK를 넘으면 그만큼 낮춘다. 못 재면 1.0."""
    try:
        r = subprocess.run(
            ["ffmpeg", "-hide_banner", "-i", str(path), "-af", "ebur128=peak=true", "-f", "null", "-"],
            capture_output=True,
            text=True,
            timeout=300,
        )
        peaks = re.findall(r"Peak:\s+(-?[\d.]+|-inf) dBFS", r.stderr)
        peak = float(peaks[-1]) if peaks and peaks[-1] != "-inf" else None
    except (OSError, subprocess.SubprocessError, ValueError):
        return 1.0
    if peak is None or peak <= VOICE_PEAK:
        return 1.0
    return round(10 ** ((VOICE_PEAK - peak) / 20), 4)


def sfx_mp4(project_dir: Path) -> dict[str, Path]:
    """효과음을 제작실에 올릴 수 있는 mp4(AAC)로(wav는 올릴 수 없다). 이미 있으면 그대로."""
    out = {}
    for name, wav in ensure_sfx(project_dir).items():
        mp4 = wav.with_suffix(".mp4")
        if not mp4.exists():
            subprocess.run(
                [
                    "ffmpeg",
                    "-y",
                    "-loglevel",
                    "error",
                    "-i",
                    str(wav),
                    "-c:a",
                    "aac",
                    "-b:a",
                    "192k",
                    str(mp4),
                ],
                check=True,
                capture_output=True,
            )
        out[name] = mp4
    return out


def build(
    project_dir: Path,
    scenes: list[Scene],
    lines: list,
    *,
    total: float,
    channel: str,
    quotes: dict | None = None,
    keywords: set[str] = frozenset(),
    words: list | None = None,
) -> LongEdit:
    """장면(topic = 챕터 헤더)·자막 줄(srt.Cue)·발언 카드 지정(queries.json "quotes": 장면 ID → {text, who, when}) →
    timeline.xml에 더할 트랙·효과음·마커와 설명란 챕터."""
    from newsclip.render import EMPH_RED

    lx = project_dir / "long"
    chapters = chapters_of(scenes)
    by_id = {sc.id: sc for sc in scenes}
    cards: list[tuple[Path, float, float]] = []
    pops: list[float] = []
    # 목차 카드: 콜드 오픈에서 목차를 부르는 문장(없으면 콜드 오픈 마지막 장면) 동안 — 주제가 둘 이상일 때만(3분 단독은 없음)
    opening = next((c for c in chapters if c.no == 0), None)
    if opening is not None and sum(c.no > 0 for c in chapters) >= 2:
        sc = next((s for s in reversed(opening.scenes) if _TOC_RE.search(s.text)), opening.scenes[-1])
        end = min(max(sc.end_sec, sc.start_sec + TOC_SEC[0]), sc.start_sec + TOC_SEC[1], opening.end)
        png = render_toc_card(
            chapters, _first_photo(opening.scenes), lx / f"toc_{_key(*[c.title for c in chapters])}.png"
        )
        cards.append((png, sc.start_sec, end))
    # 챕터 카드(정리 포함)
    for ch in chapters:
        if ch.no == 0:
            continue
        png = render_chapter_card(
            ch, _first_photo(ch.scenes), lx / f"chapter_{_key(ch.no, ch.title, ch.summary)}.png"
        )
        cards.append((png, ch.start, min(ch.start + CHAPTER_CARD_SEC, ch.end)))
    # 발언 카드: 작업자가 고른 장면에서(챕터 카드와 겹치면 그 뒤로)
    ch_of = {sc.id: ch for ch in chapters for sc in ch.scenes}
    for sid, spec in (quotes or {}).items():
        sc = by_id.get(sid)
        if sc is None or not isinstance(spec, dict) or not str(spec.get("text") or "").strip():
            continue
        start = sc.start_sec
        for _p, a, b in cards:
            if a <= start < b:
                start = b
        end = min(max(sc.end_sec, start + QUOTE_SEC[0]), start + QUOTE_SEC[1], total)
        if end - start < 1.5:
            continue
        ch = ch_of.get(sid)
        face = _speaker_photo(spec, sc, ch.scenes if ch else [sc])
        png = render_quote_card(spec, face, ch, channel, lx / f"quote_{_key(sid, spec, face)}.png")
        cards.append((png, start, end))
        pops.append(start)
    # 끝 화면: 채널 끝 인사(채널 이름이 나오는 마지막 장면)부터 끝까지 — 길어도 20초
    tail = [sc for sc in scenes if channel and channel in sc.text and sc.start_sec > total * 0.8]
    end_at = max(tail[-1].start_sec if tail else total - 8.0, total - END_SEC, 0.0)
    cards.append(
        (render_end_card(channel, _first_photo(scenes), lx / f"end_{_key(channel)}.png"), end_at, total)
    )
    cards.sort(key=lambda c: c[1])

    def covered(t: float) -> bool:
        return any(a <= t < b for _p, a, b in cards)

    # 짧게 스치는 줄은 이웃 줄과 합치고, 훅(HOOK_CARD): 첫 문장의 센 말 한 줄을 화면 한가운데 크게. 끄면 첫 문장도
    # 보통 자막 그대로 — 강조 색은 첫 문장이 끝난 뒤부터
    lines = _merge_flashes([c for c in lines if c.end > c.start])
    hook, drop, key_at = _hook(lines, words, lx) if HOOK_CARD else ([], set(), None)
    n = _first_sentence(lines)
    hook_end = hook[0][2] if hook else lines[n - 1].end if n else 0.0
    if key_at is not None:
        pops.append(key_at)
    # 자막: 인용이 두 줄에 걸치면 줄마다 따옴표 짝을 맞추고(앞뒤 문맥으로), 카드에 가린 줄·훅이 띄운 줄은 빼고,
    # 흐름이 늘어질 때만 강조(색 글자 — 콜드 오픈은 더 자주)
    from newsclip.shorts_render import pair_quotes

    opening_end = opening.end if opening is not None else 0.0
    shown = dict(zip(range(len(lines)), pair_quotes([c.text for c in lines]), strict=True))
    keep = [k for k, c in enumerate(lines) if k not in drop and not covered((c.start + c.end) / 2)]
    lines, texts = [lines[k] for k in keep], [shown[k] for k in keep]
    emph = _pick(lines, keywords, opening_end, hook_end)
    subs = list(hook)
    for k, cue in enumerate(lines):
        color, mark = emph.get(k, (None, None))
        tone = None if color is None else RED if color == EMPH_RED else YELLOW  # 센 말은 빨강, 나머지 노랑
        png = render_sub(texts[k], lx / f"sub_{_key(texts[k], tone, mark)}.png", tone, mark)
        subs.append((png, cue.start, cue.end))  # 강조 줄도 효과음 없이 색만
    # 챕터 이름·채널 이름: 챕터마다 한 장(콜드 오픈은 채널 이름만)
    chrome = [
        (render_chrome(ch, channel, lx / f"chrome_{_key(ch.no, ch.title, channel)}.png"), ch.start, ch.end)
        for ch in chapters
    ]
    shade = [(render_shade(lx / "shade.png"), 0.0, total)]
    # 효과음: 화제 전환·질문(장면 문장) + 발언 카드·훅 팝(아래 자막에는 효과음 없음). 서로 0.5초 안이면 앞의 것만
    snd = sfx_mp4(project_dir)
    sounds = [(c.at, snd[c.name]) for c in sfx_cues(scenes) if c.name in (WHOOSH, DING)]
    sounds += [(ch.start, snd[WHOOSH]) for ch in chapters if ch.no != 0]
    sounds += [(max(0.0, t - 0.03), snd[HIT]) for t in pops]
    kept: list[tuple[float, Path]] = []
    for at, path in sorted(sounds):
        if at < total and all(abs(at - a) >= 0.5 for a, _p in kept):
            kept.append((at, path))
    markers = [
        (ch.start, "챕터", f"{ch.no}. {ch.title}" if ch.no > 0 else ch.title) for ch in chapters if ch.no != 0
    ]
    tracks = [shade, subs, chrome, cards]
    files = sorted({p for tr in tracks for p, _a, _b in tr} | {p for _a, p in kept})
    v1 = _open_cuts(scenes, opening_end, [c.start for c in lines] + [a for _p, a, _b in hook])
    return LongEdit(tracks, kept, markers, chapters, youtube_chapters(chapters), files, v1)
