"""템플릿 4개(미술관 · 지하철 광고판 · 버스 · 선수 카드)의 바탕 JPG + 앞장 PNG 를 만든다.

그리는 순서: 바탕 JPG → (빛번짐: 흐린 사진, 지하철만) → 사진 → 앞장 PNG.
앞장(overlay)이 사진을 「진짜 그 자리에 있는 것」처럼 만든다:
  · 끝 마감  — 판 테두리를 원래 그림 그대로 덮어서 사진 끝이 테두리 밑으로 들어간다 (흰 틈 · 잘린 끝이 안 보인다)
  · 입체감   — 액자 턱이 드리우는 그림자, 판 안쪽 가장자리 어둑함, 버스 옆면의 곡면 명암 · 이음새 홈
  · 빛       — 조명의 밝고 어두운 쪽, 유리 · 광택 반사, 광고판에서 새어 나오는 빛, 카드 빛줄기
사진 칸 밖은 대부분 투명해서 파일이 작다."""
import json, math, os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '..', '..', 'assets', 'templates')
os.makedirs(OUT, exist_ok=True)
SRC = os.path.join(os.path.dirname(__file__), 'src')
SS = 4  # 가장자리 매끄럽게 (4배로 그려서 줄인다)
BLACK, WHITE = np.array([0.0, 0, 0]), np.array([255.0, 255, 255])


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def gblur(a, sigma):
    """가우스 흐림 (상자 흐림 3번). a: H×W 또는 H×W×3"""
    if sigma <= 0:
        return a
    r = max(1, int(round((math.sqrt(4 * sigma * sigma + 1) - 1) / 2)))
    out = a.astype(float)
    for axis in (0, 1):
        for _ in range(3):
            pad = [(0, 0)] * out.ndim
            pad[axis] = (r + 1, r)
            c = np.cumsum(np.pad(out, pad, mode='edge'), axis=axis)
            n = out.shape[axis]
            out = (np.take(c, np.arange(2 * r + 1, 2 * r + 1 + n), axis=axis) - np.take(c, np.arange(n), axis=axis)) / (2 * r + 1)
    return out


def norm_blur(L, mask, sigma):
    """mask 안쪽 값만으로 흐린다 (바깥의 어두운 테두리가 번져 들어오지 않게)"""
    return gblur(L * mask, sigma) / np.maximum(gblur(mask, sigma), 1e-3)


def inner(hole, sigma):
    """칸 안쪽 가장자리에서 1 → 안으로 갈수록 0"""
    return np.clip(2 * hole * gblur(1 - hole, sigma), 0, 1)


def outer(hole, sigma):
    """칸 바깥 가장자리에서 1 → 밖으로 갈수록 0"""
    return np.clip(2 * (1 - hole) * gblur(hole, sigma), 0, 1)


def dilate(mask, px):
    if px < 1:
        return np.clip(mask, 0, 1)
    im = Image.fromarray((np.clip(mask, 0, 1) * 255).astype('uint8'))
    return np.asarray(im.filter(ImageFilter.MaxFilter(2 * px + 1))).astype(float) / 255


def erode(mask, px):
    if px < 1:
        return np.clip(mask, 0, 1)
    im = Image.fromarray((np.clip(mask, 0, 1) * 255).astype('uint8'))
    return np.asarray(im.filter(ImageFilter.MinFilter(2 * px + 1))).astype(float) / 255


def shift(a, dx, dy):
    """a 를 (dx, dy) 만큼 민다 (빈 자리는 가장자리 값)"""
    H, W = a.shape
    out = np.pad(a, ((abs(dy), abs(dy)), (abs(dx), abs(dx))), mode='edge')
    return out[abs(dy) - dy: abs(dy) - dy + H, abs(dx) - dx: abs(dx) - dx + W]


def poly_cover(size, pts, radius=0.0):
    """다각형(둥근 모서리 가능)이 각 픽셀을 덮는 비율 0~1"""
    W, H = size
    m = Image.new('L', (W * SS, H * SS), 0)
    d = ImageDraw.Draw(m)
    P = [(x * SS, y * SS) for x, y in pts]
    if radius <= 0:
        d.polygon(P, fill=255)
    else:
        r = radius * SS
        c = np.mean(P, 0)
        inset = []
        n = len(P)
        for k in range(n):
            p0, p1, p2 = np.array(P[k - 1]), np.array(P[k]), np.array(P[(k + 1) % n])

            def inward(a, b):
                t = (b - a) / np.linalg.norm(b - a)
                nn = np.array([-t[1], t[0]])
                return nn if np.dot(c - (a + b) / 2, nn) > 0 else -nn
            n1, n2 = inward(p0, p1), inward(p1, p2)
            a1, d1 = p0 + n1 * r, p1 - p0
            a2, d2 = p1 + n2 * r, p2 - p1
            t = np.linalg.solve(np.array([d1, -d2]).T, a2 - a1)
            inset.append(tuple(a1 + d1 * t[0]))
        d.polygon(inset, fill=255)
        d.line(inset + [inset[0]], fill=255, width=int(2 * r), joint='curve')
        for x, y in inset:
            d.ellipse([x - r, y - r, x + r, y + r], fill=255)
    return np.asarray(m.resize((W, H), Image.BOX)).astype(float) / 255


def grow_quad(q, px):
    """앱 src/lib/warp.ts 의 growQuad 와 같은 식"""
    cx, cy = np.mean(q, 0)
    out = []
    for x, y in q:
        l = math.hypot(x - cx, y - cy) or 1
        out.append((x + (x - cx) / l * px * 1.4, y + (y - cy) / l * px * 1.4))
    return out


def panel_uv(size, q):
    """각 픽셀이 사각형 q 안에서 어디쯤인지 (u: 왼→오, v: 위→아래, 0~1). 원근 변환의 역."""
    (x0, y0), (x1, y1), (x2, y2), (x3, y3) = q
    dx1, dx2, dx3 = x1 - x2, x3 - x2, x0 - x1 + x2 - x3
    dy1, dy2, dy3 = y1 - y2, y3 - y2, y0 - y1 + y2 - y3
    det = dx1 * dy2 - dx2 * dy1
    g = (dx3 * dy2 - dx2 * dy3) / det
    h = (dx1 * dy3 - dx3 * dy1) / det
    M = np.array([[x1 - x0 + g * x1, x3 - x0 + h * x3, x0], [y1 - y0 + g * y1, y3 - y0 + h * y3, y0], [g, h, 1]])
    Mi = np.linalg.inv(M)
    W, H = size
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    p = np.stack([xx, yy, np.ones_like(xx)], -1) @ Mi.T
    return p[..., 0] / p[..., 2], p[..., 1] / p[..., 2]


def panel_mask(L, q, lo, hi, in_px=3, out_px=3):
    """판 모양을 원래 그림의 밝기로 정확히 딴다: 판 안쪽은 꽉 채우고, 가장자리는 밝기 경계를 따른다
    (꼭짓점 4개로 그은 선과 실제 판 끝이 1~2px 어긋나서 생기는 흰 틈을 없앤다)"""
    size = (L.shape[1], L.shape[0])
    core = erode(poly_cover(size, q), in_px)
    near = dilate(poly_cover(size, q), out_px)
    lum = np.clip((L - lo) / (hi - lo), 0, 1)
    return np.maximum(core, lum * near)


def edge_fill(bg, hole, px):
    """판 경계의 반쯤 걸친 픽셀은 원래 그림에서 판의 흰빛이 섞여 있다 — 그대로 덮으면 사진 둘레에 흰 줄이 생긴다.
    그 자리는 바로 바깥 테두리 색으로 채운다 (사진과 테두리가 깨끗하게 만난다)"""
    out_m = (hole < 0.02).astype(float) * dilate(hole, px + 4)
    fill = gblur(bg * out_m[..., None], 2.5) / np.maximum(gblur(out_m, 2.5), 1e-3)[..., None]
    return np.where((hole > 0.02)[..., None], fill, bg)


def cover_grow(hole, q):
    """판 전체를 사진이 덮으려면 사각형을 몇 px 키워야 하는지 (사진 끝과 판 끝 사이 틈이 0 이 될 때까지)"""
    size = (hole.shape[1], hole.shape[0])
    for g in range(0, 12):
        if not ((hole > 0.02) & (poly_cover(size, grow_quad(q, g)) < 0.999)).any():
            return g
    return 12


def compose(layers, size):
    """layers: [(rgb, alpha)] 아래 → 위 (rgb 는 색 하나 또는 H×W×3). 한 장의 RGBA 로 합친다"""
    W, H = size
    acc_c = np.zeros((H, W, 3))
    acc_a = np.zeros((H, W))
    for rgb, a in layers:
        a = np.clip(a, 0, 1)
        acc_c = np.asarray(rgb, float) * a[..., None] + acc_c * (1 - a[..., None])
        acc_a = a + acc_a * (1 - a)
    rgb = np.where(acc_a[..., None] > 1e-4, acc_c / np.maximum(acc_a[..., None], 1e-4), 0)
    alpha = np.clip(acc_a * 255, 0, 255)
    alpha[alpha < 1.5] = 0  # 거의 투명한 곳은 완전 투명 (파일이 작아진다)
    return Image.fromarray(np.dstack([np.clip(rgb, 0, 255), alpha]).astype('uint8'), 'RGBA')


def save_bg(name, im):
    """바탕을 저장하고, 저장된 JPG 를 다시 읽어 돌려준다 — 앞장이 덮는 테두리를 바탕과 「똑같은」 픽셀로 그리려고"""
    im.convert('RGB').save(f'{OUT}/{name}.jpg', quality=88, optimize=True, progressive=True)
    return np.asarray(Image.open(f'{OUT}/{name}.jpg').convert('RGB')).astype(float)


def save_overlay(name, ov, meta, size):
    ov.save(f'{OUT}/{name}-overlay.png', optimize=True)
    meta['width'], meta['height'] = size
    return meta


def light_shading(L, hole, base_pct, sigma, gain_dark, max_dark, gain_light, max_light, dead=0.02):
    """판의 밝기 변화 중 느린 것(조명 · 곡면)만 → 검정/흰색 반투명"""
    low = norm_blur(L, hole, sigma)
    base = np.percentile(low[hole > 0.99], base_pct)
    r = low / base
    return np.clip((1 - r - dead) * gain_dark, 0, max_dark) * hole, np.clip((r - 1 - dead) * gain_light, 0, max_light) * hole


metas = {}

# ── 미술관: 금박 액자 속 그림 ─────────────────────────────────────────────────────────
# 위에서 내리쬐는 전시 조명: 액자 윗턱이 그림 위로 그림자를 길게 드리우고, 아래로 갈수록 어두워진다.
# 그림에는 은은한 니스 광택. 액자 안쪽 턱은 원래 그림 그대로 사진 끝을 덮는다.
im = Image.open(f'{SRC}/museum-src.jpg').convert('RGB')
W, H = im.size
bg = save_bg('museum', im)
x0, y0, x1, y1 = 460, 460, 812, 954
hole = poly_cover((W, H), [(x0, y0), (x1, y0), (x1, y1), (x0, y1)])
yy, xx = np.mgrid[0:H, 0:W].astype(float)
w, h = x1 - x0, y1 - y0
u, v = (xx - x0) / w, (yy - y0) / h
dt, db, dl, dr = yy - y0, y1 - yy, xx - x0, x1 - xx
pos = lambda d: np.clip(d, 0, None)
# 액자 턱 그림자: 위(조명 반대편)는 길고 진하게, 옆은 짧게, 아래는 아주 얇게 + 맞닿은 곳의 진한 선
cast = [0.62 * (1 - smooth(0, 34, pos(dt))) ** 1.7, 0.42 * (1 - smooth(0, 14, pos(dl))) ** 1.5, 0.36 * (1 - smooth(0, 13, pos(dr))) ** 1.5,
        0.22 * (1 - smooth(0, 6, pos(db))), 0.55 * np.exp(-pos(dt) / 2.2), 0.4 * np.exp(-pos(dl) / 1.8), 0.4 * np.exp(-pos(dr) / 1.8), 0.3 * np.exp(-pos(db) / 1.6)]
shadow = 1 - np.prod([1 - c for c in cast], axis=0)
# 조명: 위쪽 가운데가 가장 밝고 아래 · 옆으로 어두워진다
falloff = 0.05 + 0.24 * smooth(0.15, 1.05, v) ** 1.3 + 0.12 * (np.abs(u - 0.5) * 2) ** 2.2
spot = 0.09 * np.exp(-(((u - 0.5) / 0.42) ** 2 + ((v - 0.22) / 0.3) ** 2))
# 니스 광택: 오른쪽 위에서 비스듬히 지나가는 얇은 빛
glare = 0.075 * np.exp(-(((u * 0.8 - v * 0.55) - 0.43) / 0.07) ** 2) * smooth(0.0, 0.25, 1 - v)
warm = np.array([214.0, 150, 86])
ring = (1 - hole) * dilate(hole, 4)
ov = compose([
    (warm, 0.07 * hole),
    (BLACK, falloff * hole),
    (WHITE, (spot + glare) * hole),
    (BLACK, shadow * hole),
    (bg, ring),
], (W, H))
metas['museum'] = save_overlay('museum', ov, {'slots': [{'quad': [[x0 - 1, y0 - 1], [x1 + 1, y0 - 1], [x1 + 1, y1 + 1], [x0 - 1, y1 + 1]], 'aspect': w / h}]}, (W, H))

# ── 지하철 광고판: 뒤에서 빛나는 라이트박스 ───────────────────────────────────────────
# 받은 그림의 위아래 검은 띠는 잘라낸다. 판 모양은 밝기로 정확히 따서 테두리와 사진 사이 흰 틈이 없다.
# 빛번짐: (1) 앱이 흐린 사진을 판보다 크게 깔고(glow) 앞장이 거리에 따라 걷어 내서 사진 색 빛이 테두리 · 벽에 번진다
#         (2) 판 둘레 흰 빛무리. 유리: 천장 불빛 줄무늬 반사 + 위쪽 옅은 반사. 판 가장자리는 안쪽으로 어둑하다.
S = 2
CROP_TOP, CROP_BOTTOM = 137, 657
im = Image.open(f'{SRC}/subway-src.png').convert('RGB').crop((0, CROP_TOP, 672, CROP_BOTTOM))
im = im.resize((im.width * S, im.height * S), Image.LANCZOS)
W, H = im.size
bg = save_bg('subway', im)
L = bg.mean(-1)
quad = [(103.5, 269.3), (590.3, 230.6), (589.0, 553.0), (103.5, 511.9)]
Q = [(x * S, (y - CROP_TOP) * S) for x, y in quad]
hole = panel_mask(L, Q, 80, 190, in_px=4, out_px=5)
core = erode(hole, 6)
u, v = panel_uv((W, H), Q)
# 유리 반사: 원래 판의 천장 불빛 줄무늬 (주변보다 밝은 가는 선)
hp = L - norm_blur(L, core, 5)
streak = gblur(np.clip((hp - 1.5) / 7, 0, 1) * core, 1.0)
low_dark, _ = light_shading(L, core, 90, 18, 1.4, 0.3, 0, 0)
sheen = 0.07 * (1 - smooth(0, 0.55, v)) * smooth(-0.2, 0.6, 1 - u)
rim_dark = 0.22 * inner(hole, 9) + 0.08 * inner(hole, 40)
# 빛번짐(사진 색): glow 를 보이게 할 만큼만 걷어 낸다 — 판에서 멀어질수록 바탕을 다시 덮는다
GLOW = 90
glow_area = dilate(poly_cover((W, H), grow_quad(Q, GLOW)), 3)
grow = cover_grow(hole, Q)
ring_px = math.ceil(grow * 1.4) + 2  # 판보다 넘친 사진을 덮는 테두리 폭
ring = (1 - hole) * dilate(hole, ring_px)
bezel = edge_fill(bg, hole, ring_px)
# 테두리(ring)가 덮는 곳부터 서서히 보이게 — 테두리 바로 바깥에서 밝기가 툭 끊기지 않도록
glow_see = 0.42 * outer(hole, 24) ** 0.8 * gblur(1 - dilate(hole, ring_px), 4)
halo = 0.1 * outer(hole, 10) + 0.07 * outer(hole, 32)
ov = compose([
    (bg, (1 - glow_see) * (1 - hole) * glow_area),
    (bezel, ring),
    (WHITE, halo),
    (BLACK, (low_dark + rim_dark) * hole),
    (WHITE, (0.42 * streak + sheen) * hole),
], (W, H))
metas['subway'] = save_overlay('subway', ov, {'slots': [{'quad': [list(p) for p in Q], 'aspect': 2.0, 'grow': grow,
                                                          'glow': {'grow': GLOW, 'blur': 0.045, 'wash': 0.35}}]}, (W, H))

# ── 버스 옆면 광고 2칸: 곡면 차체에 붙인 랩핑 ─────────────────────────────────────────
# 광고는 반듯한 사각형(원근)이다 — 원래 흰 판은 모양이 고르지 않아서(오른쪽 좁은 띠가 아래로 더 내려온다 등)
# 사각형 밖으로 삐져나온 흰 판은 바탕에서 둘레 차체 색으로 칠한다 (사진 끝에 흰 틈 · 계단이 안 생긴다).
# 차체 곡면 명암(아래로 휘어 들어가며 어두워짐)과 이음새 홈(옅은 선 + 아래 턱의 빛),
# 흐린 하늘이 비치는 위쪽 광택과 비스듬한 반사, 랩핑 끝의 얇은 선.
S = 1.5
im = Image.open(f'{SRC}/bus-src.png').convert('RGB')
im = im.resize((round(im.width * S), round(im.height * S)), Image.LANCZOS)
W, H = im.size
src = np.asarray(im).astype(float)
L = src.mean(-1)
quads = [[(41.3, 450.3), (320.3, 446.9), (312.2, 694.8), (34.7, 622.4)],
         [(556.4, 554.9), (789.0, 574.4), (777.9, 914.1), (549.1, 836.0)]]
Qs = [[(x * S, y * S) for x, y in q] for q in quads]
holes = [poly_cover((W, H), q, radius=4.0) for q in Qs]
hole = np.clip(holes[0] + holes[1], 0, 1)
# 흰 판 남은 자리 = 광고 둘레의 밝은 판 (광고가 덮는 곳은 빼고)
white = np.maximum.reduce([np.clip((L - 115) / 60, 0, 1) * dilate(poly_cover((W, H), q), 24) for q in Qs])
white = dilate(white, 2)
leftover = np.clip(white - dilate(hole, 1), 0, 1)
paint_from = (1 - white) * (1 - hole)
fill = gblur(src * paint_from[..., None], 5) / np.maximum(gblur(paint_from, 5), 1e-3)[..., None]
wide = gblur(src * paint_from[..., None], 14) / np.maximum(gblur(paint_from, 14), 1e-3)[..., None]
fill = np.where((gblur(paint_from, 5) > 0.08)[..., None], fill, wide)
painted = src * (1 - leftover[..., None]) + fill * leftover[..., None]
bg = save_bg('bus', Image.fromarray(np.clip(painted, 0, 255).astype('uint8')))
layers = []
for q, h_ in zip(Qs, holes):
    core = erode(h_, 4)
    # 명암 · 이음새는 원래 흰 판의 밝기에서 (광고가 붙은 판 그대로)
    dark, light = light_shading(L, h_, 75, 9, 1.25, 0.5, 1.3, 0.22)
    hp = L - norm_blur(L, core, 2.5)
    seam = gblur(np.clip((-hp - 6) / 30, 0, 1) * core, 0.6)
    lip = np.clip(shift(seam, 0, 2) - seam, 0, 1)
    u, v = panel_uv((W, H), q)
    gloss = 0.2 * (1 - smooth(0.0, 0.4, v)) ** 1.4 + 0.13 * np.exp(-(((u * 0.9 + v * 0.45) - 0.8) / 0.055) ** 2)
    curve = 0.16 * smooth(0.5, 1.05, v)
    edge = 0.3 * inner(h_, 0.9)
    layers += [
        (np.array([150.0, 158, 168]), 0.06 * h_),  # 흐린 날 빛 (살짝 차분하게)
        (BLACK, (dark + curve) * h_),
        (WHITE, light * h_),
        (BLACK, 0.16 * seam),
        (WHITE, 0.1 * lip * core),
        (WHITE, gloss * h_),
        (BLACK, edge),
    ]
grows = [cover_grow(h_, q) for q, h_ in zip(Qs, holes)]
ring_px = math.ceil(max(grows) * 1.4) + 2
layers.append((bg, (1 - hole) * dilate(hole, ring_px)))
ov = compose(layers, (W, H))
metas['bus'] = save_overlay('bus', ov, {'slots': [{'quad': [list(p) for p in Qs[0]], 'aspect': 1.55, 'grow': grows[0]},
                                                   {'quad': [list(p) for p in Qs[1]], 'aspect': 0.85, 'grow': grows[1]}]}, (W, H))

# ── 선수 카드: 카드 그림 자리에 사진 ─────────────────────────────────────────────────
# 양옆 · 위는 카드에 스며들고, 아래는 빛줄기(파란 선) 위에서 그늘지며 카드로 넘어간다. 숫자 · 아이콘은 사진 위에.
# 빛: 사진이 카드로 넘어가는 경계에 파란 테두리 빛(light wrap), 빛줄기 번짐, 카드 위를 비스듬히 지나는 반사.
S = 2
im = Image.open(f'{SRC}/card-src.png').convert('RGB')
im = im.resize((im.width * S, im.height * S), Image.LANCZOS)
W, H = im.size
bg = save_bg('card', im)
yy, xx = np.mgrid[0:H, 0:W].astype(float) / S  # 원본 좌표로 계산
bx0, by0, bx1, by1 = 206, 225, 511, 560
inbox = ((xx >= bx0) & (xx <= bx1) & (yy >= by0) & (yy <= by1)).astype(float)
near = dilate(inbox, 6)  # 사진이 칸보다 1~2px 넘쳐도 카드 그림이 덮는다 (왼쪽 흰 세로줄의 원인)
feather = np.maximum.reduce([1 - smooth(bx0, bx0 + 44, xx), smooth(bx1 - 44, bx1, xx), 1 - smooth(by0, by0 + 40, yy)])
fade = smooth(468, 552, yy)
L = bg.mean(-1)
R, B = bg[..., 0], bg[..., 2]
text = np.zeros((H, W))
for (tx0, ty0, tx1, ty1) in [(150, 244, 247, 304), (174, 310, 221, 345), (166, 354, 224, 382), (171, 392, 220, 445)]:
    m = (xx >= tx0) & (xx <= tx1) & (yy >= ty0) & (yy <= ty1)
    text = np.maximum(text, m * np.clip((L - 115) / 55, 0, 1) * np.clip((R - B + 10) / 40, 0, 1))
card_a = np.where(inbox > 0, np.maximum.reduce([feather, fade, text]), near)
edge_shade = np.maximum.reduce([0.55 * (1 - smooth(bx0, bx0 + 70, xx)), 0.55 * smooth(bx1 - 70, bx1, xx), 0.4 * (1 - smooth(by0, by0 + 60, yy))])
shade = np.maximum(0.55 * smooth(400, 530, yy), edge_shade) * inbox
# 테두리 빛: 사진이 카드로 녹아드는 곳(반쯤 비치는 곳)에 카드의 파란 빛이 감긴다
wrap = 0.32 * np.clip(4 * feather * (1 - feather), 0, 1) * (1 - fade) * inbox
# 빛줄기 번짐 (원본 y≈552 의 파란 선): 사진의 그늘 위로 빛이 번진다
beam = (0.4 * np.exp(-((yy - 552) / 5) ** 2) + 0.16 * np.exp(-((yy - 548) / 18) ** 2)) * smooth(bx0 - 30, bx0 + 60, xx) * (1 - smooth(bx1 - 60, bx1 + 30, xx))
# 카드 위 비스듬한 반사 (사진 칸 근처만, 옅게)
sweep = 0.09 * np.exp(-(((xx - bx0) * 0.8 - (yy - by0) * 0.6 - 150) / 26) ** 2) * inbox * (1 - fade)
ov = compose([
    (np.array([14.0, 22, 70]), 0.14 * inbox),
    (np.array([10.0, 16, 48]), shade),
    (np.array([110.0, 150, 255]), wrap),
    (bg, card_a),
    (WHITE, sweep),
    (np.array([170.0, 195, 255]), beam),
], (W, H))
metas['card'] = save_overlay('card', ov, {'slots': [{'quad': [[bx0 * S, by0 * S], [bx1 * S, by0 * S], [bx1 * S, by1 * S], [bx0 * S, by1 * S]], 'aspect': (bx1 - bx0) / (by1 - by0)}]}, (W, H))

# 사진 칸 좌표 (src/lib/templates.ts 와 디자인 실험실에 옮겨 적는다)
print(json.dumps(metas, indent=1))
