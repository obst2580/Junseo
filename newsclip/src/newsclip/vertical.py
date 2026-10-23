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
SUB_TOP = 1340  # 대본 자막 윗선(사진 아랫부분이 검게 이어지는 자리)
CROP_MAX = 1.9  # 이보다 넓은 사진(파노라마)은 자르지 않고 넣는다
SUB_LINE = 14  # 세로 화면 자막 한 줄 글자 수(가로는 20)
HEAD_LINE_COLORS = ((255, 255, 255), (255, 222, 64))  # 첫 줄 흰색, 둘째 줄 노랑
STROKE = (12, 12, 12)
NAVY, RED = (11, 31, 75), (215, 20, 26)
CHANNEL_BLUE = (62, 136, 200)  # 정치는푸르다 채널 아이콘 하늘색
SKY = (0, 156, 222)  # 널스윤서 강조 하늘색(흰 바탕에서 또렷하게 읽히는 진한 하늘색)
HEALTH_BOX = (0, 436, 1080, 1466)  # 널스윤서 사진 칸: 제목 띠(0~430)와 하늘색 선 바로 아래, 거의 정사각형
FONT_CDN = "https://cdn.jsdelivr.net/gh/google/fonts@main/ofl/"


@dataclass(frozen=True)
class Style:
    """세로 쇼츠 디자인 한 벌(상단 제목·바탕·출처 표시)."""

    name: str
    tint: tuple[int, int, int] = (0, 0, 0)  # 흐린 바탕에 섞는 색
    tint_amount: float = 0.5
    solid_bg: tuple[int, int, int] | None = None  # 있으면 흐린 사진 대신 이 색 한 가지로 바탕을 칠한다
    font_file: str = ""  # 상단 제목 폰트(비우면 Pretendard ExtraBold) — FONT_CDN에서 한 번 받아 둔다
    font_local: str = ""  # 패키지에 같이 둔 상단 제목 폰트(newsclip/fonts). 있으면 이것을 먼저 쓴다
    font_path: str = ""  # FONT_CDN 안의 경로
    bands: tuple[tuple[int, int, int], ...] = ()  # 줄마다 띠 색(비우면 띠 없이 검은 테두리 글씨)
    text_colors: tuple[tuple[int, int, int], ...] = HEAD_LINE_COLORS
    caption_box: tuple[int, int, int, int] = (0, 0, 0, 150)  # 출처 표시 상자 색
    caption_y: int = 0  # 가로 화면 출처 표시를 이만큼 아래로(롱폼: 왼쪽 위 챕터 이름 밑)
    head_box: tuple[int, int, int, int] = HEAD  # 상단 제목 자리(1080x1920 기준)
    head_px: int = 124  # 상단 제목 시작 글자 크기(자리에 안 들어가면 줄인다)
    head_line: float = 1.22  # 줄 간격(글자 크기 배수)
    stroke: float = 0.08  # 검은 테두리 두께(글자 크기 배수). 0이면 테두리 없이
    shadow: float = 0.0  # 글자 뒤 흐린 그림자 세기(0~1). 0이면 그림자 없음
    edge: tuple[int, int, int] | None = None  # 있으면 바탕을 검정으로 깔고 화면 위·아래 끝을 이 색으로 마감
    edge_top: int = 0  # 위 끝에서 이 높이까지 edge 색 → 검정 그라데이션
    edge_bottom: int = 0  # 아래 끝에서 이 높이까지 검정 → edge 색 그라데이션
    photo_fade: int = 0  # 사진 칸 아랫부분 이 높이만큼을 검정으로 자연스럽게 잇는다
    # 있으면 바탕을 검정으로 깔고 화면 위부터 top_until까지 이 색으로 칠한다
    top_bg: tuple[int, int, int] | None = None
    top_until: int = 0  # top_bg를 칠할 높이(1080x1920 기준, 보통 사진 칸 윗선)
    top_line: tuple[int, int, int] | None = None  # 있으면 top_bg 아래 끝에 이 색 선(제목 띠와 사진 칸 사이)
    under_bg: tuple[int, int, int] = (
        0,
        0,
        0,
    )  # top_bg 디자인에서 그 아래(사진 칸 둘레·아래) 색 — 사진 아랫부분도 이 색으로 이어진다
    # 세로 사진도 칸에 꽉 차게 잘라 넣는다(검은 옆 띠 없이 — 세로 소재를 주로 쓰는 채널)
    fill_box: bool = False
    box: tuple[int, int, int, int] | None = None  # 사진 칸(1080x1920 기준). 비우면 BOX(4:3)
    sub_shift: int = 0  # 대본 자막을 기본 자리(render.SUB_TEXT_Y)에서 이만큼 아래로(사진 칸이 다른 디자인)
    # 첫 문장 훅: flash(짧게 잘라 사진 칸 가운데에 크게) | chart(간호 차트판 손글씨, newsclip.chart_hook)
    # | plain(핵심어만 띄우지 않고 말한 그대로 보통 자막 — 뉴스 채널)
    hook: str = "flash"
    # 뉴스 채널 편집 규칙(널스윤서와 따로 간다 — 기본값은 예전 그대로)
    emph_sag: float = 0.0  # 강조 자막: 0이면 4초 간격으로 여럿, 있으면 흐름이 이만큼(초) 늘어질 때만 하나씩
    quote_pairs: bool = False  # 인용이 두 자막에 걸치면 조각마다 따옴표 짝을 맞춘다
    wide_top: bool = False  # 칸보다 납작한 사진(파노라마)을 칸 위에 붙인다(출처 표시가 사진 위에 앉게)
    peak: float = 0.95  # 소리 최고점(믹스 끝 리미터, 1.0 = 0 dBFS)
    bgm_volume: float = 0.22  # 배경음악 크기(내레이션이 없을 때 기준)
    duck: tuple[float, float] = (
        0.03,
        8.0,
    )  # 말할 때 배경음악 줄이기(사이드체인 문턱, 비율) — 비율이 클수록 많이 줄인다

    @property
    def font_url(self) -> str:
        return FONT_CDN + self.font_path if self.font_path else ""


# 뉴스 채널(정치는푸르다·자유뉴스365) 쇼츠 편집: 강조 자막은 흐름이 8초 늘어질 때만, 인용 따옴표 짝 맞추기,
# 납작한 사진은 칸 위에, 소리 최고점 -1.4 dBFS(유튜브 재압축 뒤에도 -1 dBTP 안쪽), 배경음악은 말할 때도
# 들리게(목소리 -14 LUFS 아래 약 12 dB — 예전 0.22·(0.03, 8)은 말하는 동안 27 dB 아래로 눌려 거의 안 들렸다).
# 첫 문장도 핵심어('무죄를 받았습니다')만 크게 띄우지 않고 말 전체를 보통 자막으로
NEWS_EDIT = {
    "hook": "plain",
    "emph_sag": 8.0,
    "quote_pairs": True,
    "wide_top": True,
    "peak": 0.85,
    "bgm_volume": 0.35,
    "duck": (0.1, 2.0),
}

STYLES: dict[str, Style] = {
    # 정치는푸르다 10분 롱폼(가로): 출처 표시를 왼쪽 위 챕터 이름(newsclip.long_edit) 밑으로
    "long": Style("long", caption_y=76),
    # 정치는푸르다: 210 매머드빵 큰 글씨(테두리 없이 흐린 그림자), 둘째 줄 진한 노랑. 바탕은 검정, 화면 위·아래 끝은
    # 채널 아이콘 하늘색으로 마감, 사진 아랫부분은 검정으로 자연스럽게 이어져 그 위에 자막이 앉는다
    "default": Style(
        "default",
        font_local="210MammothPpang-R.TTF",
        text_colors=((255, 255, 255), (255, 236, 0)),
        head_box=(24, 190, 1056, 540),
        head_px=150,
        head_line=1.12,
        stroke=0.0,
        shadow=0.85,
        edge=CHANNEL_BLUE,
        edge_top=520,
        edge_bottom=380,
        photo_fade=260,
        **NEWS_EDIT,
    ),
    "navy_red": Style(
        "navy_red",
        solid_bg=(0, 0, 0),  # 사진 칸 위아래는 검은색
        font_file="BlackHanSans-Regular.ttf",
        font_path="blackhansans/BlackHanSans-Regular.ttf",  # 블랙한산스(OFL)
        bands=(NAVY, RED),
        text_colors=((255, 255, 255), (255, 255, 255)),
        caption_box=(*NAVY, 200),
        **NEWS_EDIT,
    ),
    # 널스윤서(건강): 위쪽은 흰 바탕에 검은 글씨, 둘째 줄은 하늘색 강조 글씨, 그 아래 하늘색 선. 사진 칸은 거의
    # 정사각형으로 크게(세로 소재가 덜 잘린다). 사진 아랫부분 그라데이션과 자막 자리는 정치는푸르다와 같은 구성.
    # 첫 문장은 간호 차트판에 손글씨로
    "health": Style(
        "health",
        top_bg=(255, 255, 255),
        top_until=HEALTH_BOX[1],
        top_line=SKY,
        text_colors=((17, 17, 17), SKY),
        head_box=(40, 40, 1040, 400),
        head_px=132,
        head_line=1.16,
        stroke=0.0,
        fill_box=True,
        box=HEALTH_BOX,
        under_bg=(255, 255, 255),  # 사진 칸 아래도 흰색
        photo_fade=260,  # 정치는푸르다처럼 사진 아랫부분을 바탕(흰색)으로 자연스럽게 잇고 그 위에 자막
        sub_shift=HEALTH_BOX[3] - BOX[3],  # 자막도 정치는푸르다처럼 사진 칸 아래 끝에서 같은 높이
        hook="chart",
        peak=0.85,  # 소리 최고점: AAC로 줄인 뒤에도 깨지지 않게(뉴스 채널과 같은 값)
    ),
}


FONT_DIR = Path(__file__).parent / "fonts"


def local_font(st: Style) -> str | None:
    """디자인의 패키지 폰트 경로(없으면 None)."""
    p = FONT_DIR / st.font_local if st.font_local else None
    return str(p) if p and p.exists() else None


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
    plain = plain_background(size, st)
    if plain is not None:
        bg = plain
    else:
        small = ImageOps.fit(
            rgb, (w // 4, h // 4), method=Image.Resampling.BILINEAR
        )  # 작게 흐려서 키우면 빠르다
        bg = small.filter(ImageFilter.GaussianBlur(10)).resize(size, Image.Resampling.BILINEAR)
        bg = Image.blend(bg, Image.new("RGB", size, st.tint), st.tint_amount)
    x0, y0, x1, y1 = _box(size, st)
    bw, bh = x1 - x0, y1 - y0
    if 1.0 <= rgb.width / rgb.height <= CROP_MAX:  # 16:9까지는 양옆만 조금 잘라 칸을 채운다
        photo = ImageOps.fit(rgb, (bw, bh), method=Image.Resampling.LANCZOS, centering=(0.5, 0.4))
    elif (
        st.fill_box and rgb.width < rgb.height
    ):  # 세로 사진: 위쪽(보통 얼굴·손) 중심으로 가로 띠를 잘라 칸을 채운다
        photo = ImageOps.fit(rgb, (bw, bh), method=Image.Resampling.LANCZOS, centering=(0.5, 0.35))
    else:
        photo = ImageOps.contain(rgb, (bw, bh), method=Image.Resampling.LANCZOS)
    mask = _fade_mask(photo.size, round(st.photo_fade * h / SIZE[1])) if st.photo_fade else None
    # 칸보다 납작한 사진(파노라마)은 칸 위에 붙인다 — 가운데에 띄우면 위에 검은 띠가 생기고 출처 표시가 사진 밖에 뜬다
    top = y0 if st.wide_top and photo.height < bh else y0 + (bh - photo.height) // 2
    bg.paste(photo, (x0 + (bw - photo.width) // 2, top), mask)
    return bg


def plain_background(size: tuple[int, int], st: Style) -> Image.Image | None:
    """사진과 상관없는 디자인 바탕(위쪽 단색 / 위·아래 끝 색 / 한 가지 색). 흐린 사진 바탕 디자인이면 None."""
    if st.top_bg is not None:
        bg = Image.new("RGB", size, st.under_bg)
        until = round(st.top_until * size[1] / SIZE[1])
        bg.paste(st.top_bg, (0, 0, size[0], until))
        if st.top_line is not None:
            bg.paste(st.top_line, (0, max(0, until - round(6 * size[1] / SIZE[1])), size[0], until))
        return bg
    if st.edge is not None:
        return _edge_background(size, st)
    if st.solid_bg is not None:
        return Image.new("RGB", size, st.solid_bg)
    return None


def _edge_background(size: tuple[int, int], st: Style) -> Image.Image:
    """검정 바탕에 위 끝은 edge 색 → 검정, 아래 끝은 검정 → edge 색으로 이어지는 세로 그라데이션."""
    w, h = size
    top, bottom = round(st.edge_top * h / SIZE[1]), round(st.edge_bottom * h / SIZE[1])
    col = Image.new("RGB", (1, h), (0, 0, 0))
    px = col.load()
    for y in range(h):
        t = 0.0
        if top and y < top:
            t = (1 - y / top) ** 1.6
        elif bottom and y >= h - bottom:
            t = ((y - (h - bottom)) / bottom) ** 1.6
        px[0, y] = tuple(round(c * t) for c in st.edge)
    return col.resize(size, Image.Resampling.NEAREST)


def _fade_mask(size: tuple[int, int], fade: int) -> Image.Image:
    """사진 붙이기 마스크: 위는 그대로, 아랫부분 fade 높이에서 점점 투명해져 바탕(검정)으로 이어진다."""
    w, h = size
    col = Image.new("L", (1, h), 255)
    px = col.load()
    fade = min(fade, h)
    for y in range(h - fade, h):
        px[0, y] = round(255 * (1 - (y - (h - fade)) / fade) ** 1.3)
    return col.resize(size, Image.Resampling.NEAREST)


def box_of(style: str | Style | None) -> tuple[int, int, int, int]:
    """디자인의 사진 칸(1080x1920 기준)."""
    st = style if isinstance(style, Style) else style_of(style)
    return st.box or BOX


def _box(size: tuple[int, int], st: Style | None = None) -> tuple[int, int, int, int]:
    b = box_of(st) if st is not None else BOX
    sx, sy = size[0] / SIZE[0], size[1] / SIZE[1]
    return round(b[0] * sx), round(b[1] * sy), round(b[2] * sx), round(b[3] * sy)


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
        word = text[:i].rsplit(" ", 1)[-1]
        return abs(i - mid) + (2 if len(word) == 1 else 0) - (2 if word.endswith(",") else 0)

    cut = min(spaces, key=cost) if spaces else round(mid)
    # 줄이 바뀌는 자리의 쉼표는 뺀다(작은 화면에서 마침표처럼 보인다: '역삼동 화재, / 안전은?' → '역삼동 화재 / 안전은?')
    a, b = text[:cut].strip().removesuffix(","), text[cut:].strip()
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
    x0, y0, x1, y1 = (round(v * s) for v in st.head_box)
    path = font_path or korean_font_path()
    spare = korean_font_path()
    pad = round(34 * s) if st.bands else 0  # 띠 안쪽 여백(좌우)
    gap = round(14 * s) if st.bands else 0  # 띠 사이
    fsize = round(st.head_px * s)
    while True:
        font = _load(path, fsize)
        fallback = _load(spare, fsize) if spare and spare != path else None
        runs = [_runs(ln, font, fallback) for ln in lines]
        widths = [sum(d.textlength(t, font=f) for t, f in r) for r in runs]
        lh = round(fsize * (1.32 if st.bands else st.head_line))
        total = lh * len(lines) + gap * max(0, len(lines) - 1)
        if (max(widths, default=0) + 2 * pad <= x1 - x0 and total <= y1 - y0) or fsize <= round(60 * s):
            break
        fsize -= round(6 * s) or 1
    y = y0 + (y1 - y0 - total) // 2
    if st.shadow:
        # 테두리 대신 글자 뒤에 흐린 검은 그림자를 깔아 어떤 바탕에서도 글자가 떠 보이게 한다
        sh = Image.new("RGBA", size, (0, 0, 0, 0))
        sd = ImageDraw.Draw(sh)
        ys = y
        for r, width in zip(runs, widths, strict=True):
            xs = (w - width) / 2
            for t, f in r:
                sd.text(
                    (xs, ys + lh // 2 + round(fsize * 0.04)),
                    t,
                    font=f,
                    fill=(0, 0, 0, round(255 * st.shadow)),
                    anchor="lm",
                    stroke_width=round(fsize * 0.06),
                    stroke_fill=(0, 0, 0, round(255 * st.shadow)),
                )
                xs += sd.textlength(t, font=f)
            ys += lh + gap
        img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(fsize * 0.09)))
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
                stroke_width=0 if st.bands or not st.stroke else max(4, round(fsize * st.stroke)),
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
