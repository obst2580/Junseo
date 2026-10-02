"""템플릿 4개(미술관 · 지하철 광고판 · 버스 · 선수 카드)의 바탕 JPG + 앞장 PNG 를 만든다.
앞장(overlay)은 사진 위에 얹는 그림: 사진 칸 테두리 · 판의 빛과 그늘 · 이음새 · 카드 글자 · 그라데이션.
사진 칸 밖은 대부분 투명해서 파일이 작다."""
import json, math, os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '..', '..', 'assets', 'templates')
os.makedirs(OUT, exist_ok=True)
SS = 4  # 가장자리 매끄럽게 (4배로 그려서 줄인다)

def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)

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
        # 안쪽으로 r 만큼 줄인 다각형 + 두께 2r 선 + 꼭짓점 원 = 둥근 다각형
        inset = []
        n = len(P)
        for k in range(n):
            p0, p1, p2 = np.array(P[k - 1]), np.array(P[k]), np.array(P[(k + 1) % n])
            def inward(a, b):
                t = (b - a) / np.linalg.norm(b - a); nn = np.array([-t[1], t[0]])
                return nn if np.dot(c - (a + b) / 2, nn) > 0 else -nn
            n1, n2 = inward(p0, p1), inward(p1, p2)
            # 두 변을 r 만큼 안으로 민 선의 교점
            a1, d1 = p0 + n1 * r, p1 - p0
            a2, d2 = p1 + n2 * r, p2 - p1
            A = np.array([d1, -d2]).T
            t = np.linalg.solve(A, a2 - a1)
            inset.append(tuple(a1 + d1 * t[0]))
        d.polygon(inset, fill=255)
        d.line(inset + [inset[0]], fill=255, width=int(2 * r), joint='curve')
        for x, y in inset:
            d.ellipse([x - r, y - r, x + r, y + r], fill=255)
    return np.asarray(m.resize((W, H), Image.BOX)).astype(float) / 255

def dilate(mask, px):
    im = Image.fromarray((mask * 255).astype('uint8'))
    return np.asarray(im.filter(ImageFilter.MaxFilter(2 * px + 1))).astype(float) / 255

def compose(layers, size):
    """layers: [(rgb HxWx3, alpha HxW)] 아래 → 위. 한 장의 RGBA 로 합친다 (premultiplied 로 계산)."""
    H, W = size[1], size[0]
    acc_c = np.zeros((H, W, 3)); acc_a = np.zeros((H, W))
    for rgb, a in layers:
        acc_c = rgb * a[..., None] + acc_c * (1 - a[..., None])
        acc_a = a + acc_a * (1 - a)
    rgb = np.where(acc_a[..., None] > 1e-4, acc_c / np.maximum(acc_a[..., None], 1e-4), 0)
    return Image.fromarray(np.dstack([np.clip(rgb, 0, 255), np.clip(acc_a * 255, 0, 255)]).astype('uint8'), 'RGBA')

def shading(rgb, hole, base_pct, gain_dark=1.15, gain_light=1.6, max_dark=0.75, max_light=0.4, dead=0.025):
    """원래 판의 밝기 변화(빛 반사 · 그늘 · 이음새)를 검정/흰색 반투명으로 뽑는다"""
    L = rgb.mean(-1)
    inside = hole > 0.99
    base = np.percentile(L[inside], base_pct)
    r = L / base
    dark = np.clip((1 - r - dead) * gain_dark, 0, max_dark)
    light = np.clip((r - 1 - dead) * gain_light, 0, max_light)
    def blur(x, rad=0.7):
        return np.asarray(Image.fromarray((x * 255).astype('uint8')).filter(ImageFilter.GaussianBlur(rad))).astype(float) / 255
    dark, light = blur(dark), blur(light)
    color = np.where((light > dark)[..., None], 255.0, 0.0) * np.ones(3)
    return color, np.maximum(dark, light)

def save(name, bg, overlay, meta):
    bg.convert('RGB').save(f'{OUT}/{name}.jpg', quality=88, optimize=True, progressive=True)
    overlay.save(f'{OUT}/{name}-overlay.png', optimize=True)
    meta['width'], meta['height'] = bg.size
    return meta

SRC = os.path.join(os.path.dirname(__file__), 'src')
metas = {}

# ── 미술관: 금박 액자 속 그림 (사진 칸은 똑바른 직사각형, 액자 안쪽 그늘) ───────────────
im = Image.open(f'{SRC}/museum-src.jpg').convert('RGB')
W, H = im.size
x0, y0, x1, y1 = 460, 460, 812, 954
hole = poly_cover((W, H), [(x0, y0), (x1, y0), (x1, y1), (x0, y1)])
ring = np.clip(dilate(hole, 3) - hole, 0, 1)  # 사진이 1px 넘친 자리를 액자 안쪽 테로 덮는다
yy, xx = np.mgrid[0:H, 0:W].astype(float)
dt, db, dl, dr = yy - y0, y1 - yy, xx - x0, x1 - xx
shadow = np.maximum.reduce([0.55 * np.exp(-np.clip(dt, 0, None) / 8), 0.4 * np.exp(-np.clip(dl, 0, None) / 6),
                            0.32 * np.exp(-np.clip(dr, 0, None) / 6), 0.28 * np.exp(-np.clip(db, 0, None) / 5)]) * hole
orig = np.asarray(im).astype(float)
ov = compose([(np.zeros_like(orig), shadow), (orig, np.where(hole < 1, (1 - hole) * np.clip(dilate(hole, 3), 0, 1), 0))], (W, H))
metas['museum'] = save('museum', im, ov, {'slots': [{'quad': [[x0 - 1, y0 - 1], [x1 + 1, y0 - 1], [x1 + 1, y1 + 1], [x0 - 1, y1 + 1]], 'aspect': (x1 - x0) / (y1 - y0)}]})

# ── 지하철 광고판: 원근 + 조명 (가장자리 어둑함 · 천장 불빛 반사) ───────────────────────
S = 2
im = Image.open(f'{SRC}/subway-src.png').convert('RGB')
im = im.resize((im.width * S, im.height * S), Image.LANCZOS)
W, H = im.size
quad = [(103.5, 269.3), (590.3, 230.6), (589.0, 553.0), (103.5, 511.9)]
Q = [(x * S, y * S) for x, y in quad]
hole = poly_cover((W, H), Q)
orig = np.asarray(im).astype(float)
col, sa = shading(orig, hole, 97, gain_dark=1.0, max_dark=0.55)
edge = np.where(hole < 1, (1 - hole) * dilate(hole, 5), 0)
ov = compose([(col, sa * hole), (orig, edge)], (W, H))
metas['subway'] = save('subway', im, ov, {'slots': [{'quad': [list(p) for p in Q], 'aspect': 2.0, 'grow': 3}]})

# ── 버스 옆면 광고판 2개: 원근 + 판의 이음새 · 광택 ──────────────────────────────────
S = 1.5
im = Image.open(f'{SRC}/bus-src.png').convert('RGB')
im = im.resize((round(im.width * S), round(im.height * S)), Image.LANCZOS)
W, H = im.size
quads = [[(41.3, 450.3), (320.3, 446.9), (312.2, 694.8), (34.7, 622.4)],
         [(556.4, 554.9), (789.0, 574.4), (777.9, 914.1), (549.1, 836.0)]]
Qs = [[(x * S, y * S) for x, y in q] for q in quads]
orig = np.asarray(im).astype(float)
layers = []
holes = [poly_cover((W, H), q, radius=3.0) for q in Qs]
hole = np.clip(holes[0] + holes[1], 0, 1)
for h in holes:
    col, sa = shading(orig, h, 80, gain_dark=1.3, max_dark=0.8, gain_light=1.4, max_light=0.3)
    layers.append((col, sa * h))
edge = np.where(hole < 1, (1 - hole) * dilate(hole, 5), 0)
layers.append((orig, edge))
ov = compose(layers, (W, H))
metas['bus'] = save('bus', im, ov, {'slots': [{'quad': [list(p) for p in Qs[0]], 'aspect': 1.55, 'grow': 3},
                                               {'quad': [list(p) for p in Qs[1]], 'aspect': 0.85, 'grow': 3}]})

# ── 선수 카드: 그림 자리에 사진, 아래는 노란 선(빛줄기) 밑으로 그늘지며 카드에 스며든다 ────
S = 2
im = Image.open(f'{SRC}/card-src.png').convert('RGB')
im = im.resize((im.width * S, im.height * S), Image.LANCZOS)
W, H = im.size
orig = np.asarray(im).astype(float)
yy, xx = np.mgrid[0:H, 0:W].astype(float) / S  # 원본 좌표로 계산
bx0, by0, bx1, by1 = 206, 225, 511, 560
inbox = ((xx >= bx0) & (xx <= bx1) & (yy >= by0) & (yy <= by1)).astype(float)
# 양옆 · 위는 넓게 흐려서 카드 그림에 스며들게 (네모로 붙은 티가 안 나게)
feather = np.maximum.reduce([1 - smooth(bx0, bx0 + 44, xx), smooth(bx1 - 44, bx1, xx), 1 - smooth(by0, by0 + 40, yy)])
fade = smooth(468, 552, yy)  # 노란 선(549) 에서 카드로 완전히 넘어간다 → 빛줄기가 그대로 보인다
L = orig.mean(-1); R, B = orig[..., 0], orig[..., 2]
text = np.zeros((H, W))
for (tx0, ty0, tx1, ty1) in [(150, 244, 247, 304), (174, 310, 221, 345), (166, 354, 224, 382), (171, 392, 220, 445)]:
    m = (xx >= tx0) & (xx <= tx1) & (yy >= ty0) & (yy <= ty1)
    text = np.maximum(text, m * np.clip((L - 115) / 55, 0, 1) * np.clip((R - B + 10) / 40, 0, 1))
card_a = np.where(inbox > 0, np.maximum.reduce([feather, fade, text]), 1.0)
card_a = card_a * inbox  # 칸 밖은 바탕 JPG 가 그대로 보인다 (앞장은 투명)
# 그라데이션 그늘: 아래로 갈수록, 그리고 가장자리 쪽이 카드 남색으로 어두워진다
edge_shade = np.maximum.reduce([0.55 * (1 - smooth(bx0, bx0 + 70, xx)), 0.55 * smooth(bx1 - 70, bx1, xx), 0.4 * (1 - smooth(by0, by0 + 60, yy))])
shade = np.maximum(0.55 * smooth(400, 530, yy), edge_shade) * inbox
navy = np.zeros_like(orig) + np.array([10, 16, 48])
ov = compose([(navy, shade), (orig, card_a)], (W, H))
metas['card'] = save('card', im, ov, {'slots': [{'quad': [[bx0 * S, by0 * S], [bx1 * S, by0 * S], [bx1 * S, by1 * S], [bx0 * S, by1 * S]], 'aspect': (bx1 - bx0) / (by1 - by0)}]})

# 사진 칸 좌표 (src/lib/templates.ts 에 옮겨 적는다)
print(json.dumps(metas, indent=1))
