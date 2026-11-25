"""
신화리 온라인 맵 배치: 실제 OSM 도로·지형을 게임 좌표로 옮긴다.
  세 구역(신화리 / 연무읍 시내 / 육군훈련소)은 실제 축척 그대로 잘라 가까이 붙이고, 사이의 들판은 압축(부드러운 변위장).
  출력: layout.json (도로·철길·물길·토지이용·건물·지명, 게임 좌표 m), height.npy (4 m 격자 고도), preview.png
좌표: 게임 x = 동(+), y = 북(+), 원점 = 신화2리 마을회관. 높이는 m.
Usage: python3 layout.py <osm_all.json> <dem_dir> <out_dir>
"""
import sys, os, json, math, numpy as np
from PIL import Image, ImageDraw, ImageFont

osm_p, dem_dir, out = sys.argv[1:4]; os.makedirs(out, exist_ok=True)
C = (36.1350, 127.0687); KX = 111320 * math.cos(math.radians(C[0])); KY = 110540
def real(la, lo): return ((lo - C[1]) * KX, (la - C[0]) * KY)

# ---------------------------------------------------------------- patches kept at true scale, and where they go
PATCHES = [  # name, real rect (x0, y0, x1, y1), translation to game space
    ('신화리', (-700, -600, 700, 600), (0, 0)),
    ('연무읍', (2150, -1450, 3150, -50), (-1300, 400)),
    ('훈련소', (2350, -2700, 3350, -1850), (-1350, 750)),
]
BOUNDS = (-760, -2000, 2060, 660)   # game rect kept (x0, y0, x1, y1)

def rect_dist(p, r):
    dx = max(r[0] - p[0], 0, p[0] - r[2]); dy = max(r[1] - p[1], 0, p[1] - r[3]); return math.hypot(dx, dy)
def disp(p):
    """smooth displacement: exact translation inside a patch, inverse-distance blend outside"""
    ws, tx, ty = 0., 0., 0.
    for _, r, t in PATCHES:
        d = rect_dist(p, r)
        if d == 0: return t
        w = 1 / (d ** 2.2); ws += w; tx += w * t[0]; ty += w * t[1]
    return (tx / ws, ty / ws)
def warp(p): d = disp(p); return (p[0] + d[0], p[1] + d[1])
def unwarp(g):
    p = list(g)
    for _ in range(30):
        d = disp(p); p = [g[0] - d[0], g[1] - d[1]]
    return p

# ---------------------------------------------------------------- DEM (terrarium z14 tiles)
tiles = {}
for f in os.listdir(dem_dir):
    if f.startswith('14_') and f.endswith('.png'):
        _, x, y = f[:-4].split('_'); a = np.asarray(Image.open(os.path.join(dem_dir, f)).convert('RGB')).astype(np.float64)
        tiles[(int(x), int(y))] = a[..., 0] * 256 + a[..., 1] + a[..., 2] / 256 - 32768
Z = 14; N2 = 2 ** Z
def elev(la, lo):
    fx = (lo + 180) / 360 * N2 * 256; s = math.sin(math.radians(la)); fy = (0.5 - math.log((1 + s) / (1 - s)) / (4 * math.pi)) * N2 * 256
    ix, iy = int(fx), int(fy); tx, ty = fx - ix, fy - iy
    def h(px, py):
        t = tiles.get((px // 256, py // 256)); return t[py % 256, px % 256] if t is not None else 0.
    return (h(ix, iy) * (1 - tx) * (1 - ty) + h(ix + 1, iy) * tx * (1 - ty) + h(ix, iy + 1) * (1 - tx) * ty + h(ix + 1, iy + 1) * tx * ty)
def elev_real(p): return elev(C[0] + p[1] / KY, C[1] + p[0] / KX)

STEP = 4
gx = np.arange(BOUNDS[0], BOUNDS[2] + STEP, STEP); gy = np.arange(BOUNDS[1], BOUNDS[3] + STEP, STEP)
H = np.zeros((len(gy), len(gx)), np.float32)
for j, y in enumerate(gy):
    for i, x in enumerate(gx): H[j, i] = elev_real(unwarp((x, y)))
H0 = float(elev_real((0, 0))); H -= H0   # heights relative to the village hall
print('height grid', H.shape, 'range', H.min(), H.max())

# ---------------------------------------------------------------- OSM features
d = json.load(open(osm_p)); NODES = d['nodes']
ROAD_W = {'motorway': 22, 'motorway_link': 8, 'trunk': 18, 'primary': 14, 'primary_link': 7, 'secondary': 10, 'secondary_link': 6,
          'tertiary': 8, 'tertiary_link': 6, 'unclassified': 6, 'residential': 5.5, 'living_street': 4.5, 'service': 4, 'track': 3,
          'path': 1.6, 'footway': 1.6, 'steps': 1.6, 'pedestrian': 4}
def inside(g, m=40): return BOUNDS[0] - m <= g[0] <= BOUNDS[2] + m and BOUNDS[1] - m <= g[1] <= BOUNDS[3] + m
def densify(pts, step=8):
    out = [pts[0]]
    for a, b in zip(pts, pts[1:]):
        n = max(1, int(math.dist(a, b) / step))
        for k in range(1, n + 1): out.append((a[0] + (b[0] - a[0]) * k / n, a[1] + (b[1] - a[1]) * k / n))
    return out
def way_pts(w, step=8):
    pts = [real(*NODES[n]) for n in w['nd'] if n in NODES]
    if len(pts) < 2: return []
    return [warp(p) for p in densify(pts, step)]
def clip_runs(pts):
    runs, cur = [], []
    for p in pts:
        if inside(p): cur.append(p)
        else:
            if len(cur) > 1: runs.append(cur)
            cur = []
    if len(cur) > 1: runs.append(cur)
    return runs
R = lambda pts: [[round(x, 1), round(y, 1)] for x, y in pts]
roads, rails, waters, areas, buildings = [], [], [], [], []
for wid, w in d['ways'].items():
    t = w['tags']
    if t.get('highway') in ROAD_W:
        for run in clip_runs(way_pts(w)):
            roads.append({'kind': t['highway'], 'w': ROAD_W[t['highway']], 'name': t.get('name', ''), 'pts': R(run),
                          'bridge': t.get('bridge') == 'yes', 'lanes': t.get('lanes')})
    elif t.get('railway') in ('rail',):
        for run in clip_runs(way_pts(w)): rails.append({'pts': R(run), 'bridge': t.get('bridge') == 'yes'})
    elif t.get('waterway') in ('stream', 'drain', 'river', 'canal', 'ditch'):
        for run in clip_runs(way_pts(w)): waters.append({'kind': t['waterway'], 'w': {'river': 18, 'canal': 6, 'stream': 4, 'drain': 2.5, 'ditch': 1.5}[t['waterway']], 'pts': R(run)})
    elif 'building' in t:
        pts = [warp(real(*NODES[n])) for n in w['nd'] if n in NODES]
        if len(pts) > 3 and all(inside(p, 0) for p in pts): buildings.append({'kind': t['building'], 'name': t.get('name', ''), 'levels': t.get('building:levels'), 'pts': R(pts)})
    else:
        kind = t.get('landuse') or t.get('natural') or t.get('leisure') or t.get('amenity') or t.get('military')
        if kind in ('farmland', 'residential', 'military', 'grass', 'wood', 'water', 'pitch', 'park', 'parking', 'barracks', 'base', 'school', 'industrial', 'commercial', 'cemetery', 'scrub', 'meadow', 'orchard', 'farmyard'):
            pts = [warp(p) for p in densify([real(*NODES[n]) for n in w['nd'] if n in NODES], 30)]
            if len(pts) > 3 and any(inside(p, 0) for p in pts): areas.append({'kind': kind, 'name': t.get('name', ''), 'pts': R(pts)})
named = []
for name, k, la, lo in d['named']:
    g = warp(real(la, lo))
    if inside(g, 0): named.append({'name': name, 'kind': k, 'p': [round(g[0], 1), round(g[1], 1)]})
print('roads', len(roads), 'rails', len(rails), 'water', len(waters), 'areas', len(areas), 'buildings', len(buildings), 'names', len(named))

json.dump({'bounds': BOUNDS, 'step': STEP, 'h0': H0, 'patches': [{'name': n, 'real': r, 't': t, 'game': [r[0] + t[0], r[1] + t[1], r[2] + t[0], r[3] + t[1]]} for n, r, t in PATCHES],
           'roads': roads, 'rails': rails, 'waters': waters, 'areas': areas, 'buildings': buildings, 'named': named},
          open(os.path.join(out, 'layout.json'), 'w'), ensure_ascii=False)
np.save(os.path.join(out, 'height.npy'), H)

# ---------------------------------------------------------------- preview
S = 0.5   # px per m
W_, H_ = int((BOUNDS[2] - BOUNDS[0]) * S), int((BOUNDS[3] - BOUNDS[1]) * S)
gyx = np.gradient(H, STEP); shade = np.clip(.75 + (gyx[1] * -.6 + gyx[0] * .6) * .5, .4, 1.1)
base = np.stack([.78 * shade, .82 * shade, .66 * shade], -1)
img = Image.fromarray((np.clip(base[::-1], 0, 1) * 255).astype(np.uint8)).resize((W_, H_))
dr = ImageDraw.Draw(img, 'RGBA')
def P(p): return ((p[0] - BOUNDS[0]) * S, (BOUNDS[3] - p[1]) * S)
COL = {'farmland': (190, 200, 120, 90), 'residential': (230, 200, 170, 90), 'military': (140, 150, 110, 110), 'barracks': (150, 140, 110, 150), 'base': (140, 150, 110, 60),
       'wood': (80, 120, 70, 120), 'water': (90, 140, 200, 200), 'pitch': (120, 180, 110, 150), 'parking': (180, 180, 180, 120), 'school': (220, 200, 140, 120)}
for a in areas: dr.polygon([P(p) for p in a['pts']], fill=COL.get(a['kind'], (200, 200, 200, 60)))
for wv in waters: dr.line([P(p) for p in wv['pts']], fill=(80, 130, 200, 255), width=max(1, int(wv['w'] * S)))
for rd in sorted(roads, key=lambda r: r['w']):
    c = (250, 250, 250, 255) if rd['w'] >= 8 else (235, 225, 200, 255) if rd['w'] >= 4 else (180, 160, 120, 255)
    dr.line([P(p) for p in rd['pts']], fill=c, width=max(1, int(rd['w'] * S)))
for rl in rails: dr.line([P(p) for p in rl['pts']], fill=(60, 60, 60, 255), width=2)
for b in buildings: dr.polygon([P(p) for p in b['pts']], fill=(120, 90, 80, 220))
for p in PATCHES:
    g = [p[1][0] + p[2][0], p[1][1] + p[2][1], p[1][2] + p[2][0], p[1][3] + p[2][1]]
    dr.rectangle([P((g[0], g[3])), P((g[2], g[1]))], outline=(200, 40, 40, 200), width=2)
try: font = ImageFont.truetype('/usr/share/fonts/truetype/nanum/NanumGothic.ttf', 14)
except Exception:
    import glob; fs = glob.glob('/usr/share/fonts/**/*CJK*', recursive=True) + glob.glob('/usr/share/fonts/**/*Nanum*', recursive=True)
    font = ImageFont.truetype(fs[0], 14) if fs else ImageFont.load_default()
seen = set()
for n in named:
    if n['name'] in seen or not n['kind']: continue
    seen.add(n['name']); x, y = P(n['p']); dr.ellipse([x - 3, y - 3, x + 3, y + 3], fill=(200, 30, 30, 255)); dr.text((x + 5, y - 8), n['name'], fill=(20, 20, 20, 255), font=font)
x, y = P((0, 0)); dr.ellipse([x - 6, y - 6, x + 6, y + 6], outline=(200, 0, 0, 255), width=3); dr.text((x + 8, y - 8), '신화2리 마을회관', fill=(150, 0, 0, 255), font=font)
img.save(os.path.join(out, 'preview.png')); print('preview', img.size)
