"""
게임용 세계 데이터: layout.json + features.json + height.npy -> map_pub/
  world.json   도로(높이 포함)·철길·물길·연못·건물(종류/층수/지붕)·비닐하우스·나무·지명
  height.json  지형 고도 (uint16, 4 m 격자, 도로 폭만큼 평탄화) base64
  ground.png   바닥 재질 분류 (2 m): 0 밭/풀, 1 논, 2 하우스 마당, 3 마당/공터, 4 물, 5 숲, 6 맨땅/자갈
Usage: python3 build_world.py <mapgen/out> <map_pub>
"""
import sys, os, json, math, base64, random, numpy as np
from PIL import Image

src, dst = sys.argv[1:3]; os.makedirs(dst, exist_ok=True)
random.seed(7); np.random.seed(7)
L = json.load(open(os.path.join(src, 'layout.json'))); F = json.load(open(os.path.join(src, 'features.json')))
H = np.load(os.path.join(src, 'height.npy')).astype(np.float64)
B = L['bounds']; STEP = L['step']
def hidx(x, y): return (y - B[1]) / STEP, (x - B[0]) / STEP
def hget(x, y):
    j, i = hidx(x, y); j = min(max(j, 0), H.shape[0] - 1.001); i = min(max(i, 0), H.shape[1] - 1.001)
    j0, i0 = int(j), int(i); tj, ti = j - j0, i - i0
    return (H[j0, i0] * (1 - ti) * (1 - tj) + H[j0, i0 + 1] * ti * (1 - tj) + H[j0 + 1, i0] * (1 - ti) * tj + H[j0 + 1, i0 + 1] * ti * tj)

# ---------------------------------------------------------------- smooth the raw DEM a little (30 m SRTM-like steps)
def blur2(a, r):
    hh, ww = a.shape; fy = np.fft.fftfreq(hh)[:, None]; fx = np.fft.rfftfreq(ww)[None, :]
    return np.fft.irfft2(np.fft.rfft2(a) * np.exp(-2 * (np.pi * r) ** 2 * (fx ** 2 + fy ** 2)), s=(hh, ww))
H = blur2(H, 1.2)

# ---------------------------------------------------------------- roads: smoothed centreline heights, then carve the terrain
roads = L['roads']
def smooth_line(z, k):
    if len(z) < 3: return z
    z = np.array(z); out = np.copy(z)
    for i in range(len(z)): out[i] = z[max(0, i - k):i + k + 1].mean()
    return out
carve = np.full(H.shape, np.nan); carve_w = np.zeros(H.shape)
order = sorted(roads, key=lambda r: r['w'])   # wide roads win where they cross small ones
for rd in order:
    pts = rd['pts']; z = [hget(x, y) for x, y in pts]
    k = 6 if rd['w'] >= 10 else 3
    z = smooth_line(z, k); rd['z'] = [round(float(v), 2) for v in z]
    half = rd['w'] / 2 + 1.5
    for (x, y), zz in zip(pts, z):
        j, i = hidx(x, y); r = int(half / STEP) + 2
        for dj in range(-r, r + 1):
            for di in range(-r, r + 1):
                jj, ii = int(round(j)) + dj, int(round(i)) + di
                if 0 <= jj < H.shape[0] and 0 <= ii < H.shape[1]:
                    d = math.hypot((jj - j) * STEP, (ii - i) * STEP)
                    w = 1 if d < half else max(0, 1 - (d - half) / 6)
                    if w > carve_w[jj, ii]: carve_w[jj, ii] = w; carve[jj, ii] = zz
m = carve_w > 0; H[m] = H[m] * (1 - carve_w[m]) + carve[m] * carve_w[m]
for rd in roads: rd['z'] = [round(float(hget(x, y)) + .06, 2) for x, y in rd['pts']]
for rl in L['rails']: rl['z'] = [round(float(hget(x, y)) + .3, 2) for x, y in rl['pts']]
for wv in L['waters']: wv['z'] = [round(float(hget(x, y)) - .4, 2) for x, y in wv['pts']]

# ---------------------------------------------------------------- road proximity helpers (for filtering)
seg = []
for rd in roads:
    p = rd['pts']
    for a, b in zip(p, p[1:]): seg.append((a[0], a[1], b[0], b[1], rd['w'] / 2))
seg = np.array(seg)
def road_dist(x, y):
    ax, ay, bx, by, hw = seg.T; dx, dy = bx - ax, by - ay; L2 = dx * dx + dy * dy + 1e-9
    t = np.clip(((x - ax) * dx + (y - ay) * dy) / L2, 0, 1); d = np.hypot(ax + t * dx - x, ay + t * dy - y) - hw
    return d.min()
def in_patch(x, y, name):
    g = next(p['game'] for p in L['patches'] if p['name'] == name); return g[0] <= x <= g[2] and g[1] <= y <= g[3]

# ---------------------------------------------------------------- greenhouses
ghs = []
for g in F['greenhouses']:
    if g['l'] < 8 or g['w'] < 3: continue
    x, y = g['c']
    if road_dist(x, y) < 1: continue
    ghs.append({'c': [x, y], 'l': g['l'], 'w': min(g['w'], 8.5), 'a': g['a'], 'z': round(float(hget(x, y)), 2)})
# fill the compressed farmland between the patches with a few greenhouse rows (same look as the real ones)
def in_any_patch(x, y): return any(p['game'][0] <= x <= p['game'][2] and p['game'][1] <= y <= p['game'][3] for p in L['patches'])
for _ in range(220):
    x, y = random.uniform(B[0], B[2]), random.uniform(B[1], B[3])
    if in_any_patch(x, y) or road_dist(x, y) < 8: continue
    a = random.choice([0, math.pi / 2]) + random.uniform(-.25, .25); n = random.randint(3, 8); l = random.uniform(30, 70)
    for k in range(n):
        ox, oy = -math.sin(a) * (k * 7.5), math.cos(a) * (k * 7.5)
        if road_dist(x + ox, y + oy) < 5: break
        ghs.append({'c': [round(x + ox, 1), round(y + oy, 1)], 'l': round(l, 1), 'w': 6.5, 'a': round(a, 3), 'z': round(float(hget(x + ox, y + oy)), 2)})
print('greenhouses', len(ghs))

# ---------------------------------------------------------------- buildings
def in_rect(px, py, c, l, w, a):
    dx, dy = px - c[0], py - c[1]; u = dx * math.cos(a) + dy * math.sin(a); v = -dx * math.sin(a) + dy * math.cos(a); return abs(u) <= l / 2 and abs(v) <= w / 2
blds = []
# OSM footprints first (훈련소 barracks, schools, a few houses) as oriented rectangles of their polygon
for b in L['buildings']:
    P = np.array(b['pts']); c = P.mean(0); Q = P - c
    ev, evec = np.linalg.eigh(np.cov(Q.T) + np.eye(2) * 1e-6); ax = evec[:, 1]
    u = Q @ ax; v = Q @ np.array([-ax[1], ax[0]])
    lv = int(b['levels']) if (b.get('levels') or '').isdigit() else (4 if in_patch(c[0], c[1], '훈련소') else 2)
    blds.append({'c': [round(c[0], 1), round(c[1], 1)], 'l': round(u.max() - u.min(), 1), 'w': round(v.max() - v.min(), 1), 'a': round(math.atan2(ax[1], ax[0]), 3),
                 'kind': 'barracks' if in_patch(c[0], c[1], '훈련소') else 'civic', 'floors': lv, 'roof': 'flat', 'col': None, 'name': b.get('name', ''), 'src': 'osm'})
def overl(c, l, w, a):
    for o in blds:
        if math.dist(c, o['c']) < (max(l, w) + max(o['l'], o['w'])) / 2 and (in_rect(c[0], c[1], o['c'], o['l'] + 2, o['w'] + 2, o['a']) or in_rect(o['c'][0], o['c'][1], c, l + 2, w + 2, a)): return True
    return False
gh_index = {}
for g in ghs: gh_index.setdefault((int(g['c'][0] // 50), int(g['c'][1] // 50)), []).append(g)
def on_greenhouse(x, y):
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for g in gh_index.get((int(x // 50) + dx, int(y // 50) + dy), []):
                if in_rect(x, y, g['c'], g['l'], g['w'], g['a']): return True
    return False
for b in sorted(F['buildings'], key=lambda b: -b['l'] * b['w']):
    x, y = b['c']; l, w = b['l'] * 1.04, b['w'] * 1.04
    if road_dist(x, y) < 0.5 or on_greenhouse(x, y) or overl([x, y], l, w, b['a']): continue
    area = l * w
    town = in_patch(x, y, '연무읍'); camp = in_patch(x, y, '훈련소')
    if camp: kind, floors, roof = 'barracks', random.choice([2, 3, 3, 4]), 'flat'
    elif area > 260: kind, floors, roof = 'warehouse', 1, 'shed'
    elif town and area > 90: kind, floors, roof = 'shop', random.choice([2, 2, 3, 3, 4]), 'flat'
    else: kind, floors, roof = 'house', 1, 'gable'
    blds.append({'c': [x, y], 'l': round(l, 1), 'w': round(w, 1), 'a': b['a'], 'kind': kind, 'floors': floors, 'roof': roof, 'col': b['col'], 'rc': b['roof'], 'src': 'sat'})
for b in blds:
    corners = [(b['c'][0] + sx * b['l'] / 2 * math.cos(b['a']) - sy * b['w'] / 2 * math.sin(b['a']), b['c'][1] + sx * b['l'] / 2 * math.sin(b['a']) + sy * b['w'] / 2 * math.cos(b['a'])) for sx in (-1, 1) for sy in (-1, 1)]
    zs = [hget(*p) for p in corners]; b['z'] = round(float(min(zs)), 2); b['zt'] = round(float(max(zs)), 2)
print('buildings', len(blds), {k: sum(1 for b in blds if b['kind'] == k) for k in ('house', 'warehouse', 'shop', 'barracks', 'civic')})

# ---------------------------------------------------------------- landmarks (hand placed, game logic hooks)
named = {n['name']: n['p'] for n in L['named']}
LANDMARKS = [
    {'id': 'hall', 'name': '신화2리 마을회관', 'p': [0, 0]},
    {'id': 'jigudae', 'name': '연무지구대', 'p': named.get('논산경찰서 연무지구대')},
    {'id': 'eupsamuso', 'name': '연무읍사무소', 'p': named.get('연무읍사무소')},
    {'id': 'hanaro', 'name': '연무하나로마트', 'p': named.get('연무하나로마트')},
    {'id': 'terminal', 'name': '연무대터미널', 'p': named.get('연무대터미널')},
    {'id': 'station', 'name': '연무대역', 'p': named.get('연무대')},
    {'id': 'camp_gate', 'name': '육군훈련소 입영심사대', 'p': next((n['p'] for n in L['named'] if '입영' in n['name']), None)},
]
LANDMARKS = [l for l in LANDMARKS if l['p']]
for l in LANDMARKS: l['z'] = round(float(hget(*l['p'])), 2)
# keep the hall plot clear of detected roofs (the hall itself is built in the client)
blds = [b for b in blds if math.dist(b['c'], (0, 0)) > 14]

# ---------------------------------------------------------------- trees
trees = [[x, y, round(float(hget(x, y)), 2)] for x, y in F['trees'] if road_dist(x, y) > 2 and not on_greenhouse(x, y)]
for wv in L['waters']:   # stream-side willows / brush
    for k, (x, y) in enumerate(wv['pts']):
        if k % 3 == 0 and random.random() < .5:
            ox, oy = random.uniform(-6, 6), random.uniform(-6, 6)
            if road_dist(x + ox, y + oy) > 3: trees.append([round(x + ox, 1), round(y + oy, 1), round(float(hget(x + ox, y + oy)), 2)])
print('trees', len(trees))

# ---------------------------------------------------------------- ground class raster (2 m)
R2 = 2.0; GW, GH = int((B[2] - B[0]) / R2), int((B[3] - B[1]) / R2)
ground = np.zeros((GH, GW), np.uint8)
yy, xx = np.mgrid[0:GH, 0:GW]
# farmland between patches: big paddy blocks vs dry fields, with field-scale noise
nz = np.random.rand(GH // 40 + 2, GW // 40 + 2)
blocks = np.asarray(Image.fromarray((nz * 255).astype(np.uint8)).resize((GW, GH), Image.NEAREST)) / 255
ground[blocks > .45] = 1
for r in F['rasters']:
    img = np.asarray(Image.open(os.path.join(src, r['file'])))
    g = r['game']; x0 = int((g[0] - B[0]) / R2); y0 = int((B[3] - g[3]) / R2)
    h_, w_ = img.shape; ys0, xs0 = max(0, y0), max(0, x0); ys1, xs1 = min(GH, y0 + h_), min(GW, x0 + w_)
    ground[ys0:ys1, xs0:xs1] = img[ys0 - y0:ys1 - y0, xs0 - x0:xs1 - x0]
Image.fromarray(ground).save(os.path.join(dst, 'ground.png'))

# ---------------------------------------------------------------- write
hmin = float(H.min()) - 1
q = np.clip(np.round((H - hmin) * 500), 0, 65535).astype('<u2')
json.dump({'w': H.shape[1], 'h': H.shape[0], 'step': STEP, 'x0': B[0], 'y0': B[1], 'hmin': hmin, 'scale': 1 / 500,
           'data': base64.b64encode(q.tobytes()).decode()}, open(os.path.join(dst, 'height.json'), 'w'))
def rnd(o): return o
world = {'bounds': B, 'roads': [{k: r[k] for k in ('kind', 'w', 'name', 'pts', 'z', 'bridge')} for r in roads], 'rails': L['rails'], 'waters': L['waters'],
         'ponds': [], 'lakes': [{'pts': a['pts'], 'z': round(float(min(hget(x, y) for x, y in a['pts'])) - .2, 2)} for a in L['areas'] if a['kind'] == 'water'],
         'buildings': blds, 'greenhouses': ghs, 'trees': trees, 'landmarks': LANDMARKS, 'patches': L['patches'],
         'ground': {'file': 'ground.png', 'res': R2, 'x0': B[0], 'y1': B[3], 'w': GW, 'h': GH}}
json.dump(world, open(os.path.join(dst, 'world.json'), 'w'), ensure_ascii=False, separators=(',', ':'))
print('world.json', os.path.getsize(os.path.join(dst, 'world.json')) // 1000, 'KB; height', os.path.getsize(os.path.join(dst, 'height.json')) // 1000, 'KB')
