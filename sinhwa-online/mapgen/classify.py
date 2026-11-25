"""
위성사진(분석용, 게임에 넣지 않음)에서 배치 정보를 뽑는다: 구역별로
  - 건물 지붕: 색 계열(파랑/초록/주황·빨강/회색·흰색) + 회전 사각형(중심, 가로, 세로, 각도)
  - 비닐하우스: 밝은 무채색 + 줄무늬 결 -> 단지 사각형을 줄 방향으로 동(棟) 단위로 쪼갬
  - 물(저수지·연못), 나무 덩어리(점), 토지 분류 래스터(2 m: 논/밭/하우스/건물/물/숲/맨땅)
좌표는 layout.py 와 같은 게임 좌표(각 구역의 평행이동 적용).
Usage: python3 classify.py <sat_dir> <layout.json> <out_dir>
"""
import sys, os, math, json, numpy as np
from PIL import Image, ImageFilter

sat, layout_p, out = sys.argv[1:4]
L = json.load(open(layout_p))
C = (36.1350, 127.0687); KX = 111320 * math.cos(math.radians(C[0])); KY = 110540
Z = 17; N2 = 2 ** Z
def tile_xy(la, lo):
    x = (lo + 180) / 360 * N2; s = math.sin(math.radians(la)); y = (0.5 - math.log((1 + s) / (1 - s)) / (4 * math.pi)) * N2; return x, y

def mosaic(real_rect, res=1.0):
    """resample satellite imagery onto a north-up grid in real meters (res m/px)"""
    x0, y0, x1, y1 = real_rect; W = int((x1 - x0) / res); H = int((y1 - y0) / res)
    xs = x0 + (np.arange(W) + .5) * res; ys = y1 - (np.arange(H) + .5) * res
    lo = C[1] + xs / KX; la = C[0] + ys / KY
    tx = (lo + 180) / 360 * N2
    s = np.sin(np.radians(la)); ty = (0.5 - np.log((1 + s) / (1 - s)) / (4 * np.pi)) * N2
    TX, TY = np.meshgrid(tx, ty)
    img = np.zeros((H, W, 3), np.float32); cache = {}
    ix, iy = TX.astype(int), TY.astype(int); px = ((TX - ix) * 256).astype(int).clip(0, 255); py = ((TY - iy) * 256).astype(int).clip(0, 255)
    for key in set(zip(ix.ravel().tolist(), iy.ravel().tolist())):
        f = os.path.join(sat, f'{key[0]}_{key[1]}.jpg')
        if not os.path.exists(f): continue
        a = np.asarray(Image.open(f).convert('RGB')).astype(np.float32) / 255
        m = (ix == key[0]) & (iy == key[1]); img[m] = a[py[m], px[m]]
    return img

def hsv(a):
    r, g, b = a[..., 0], a[..., 1], a[..., 2]; mx = a.max(-1); mn = a.min(-1); d = mx - mn + 1e-6
    h = np.where(mx == r, ((g - b) / d) % 6, np.where(mx == g, (b - r) / d + 2, (r - g) / d + 4)) * 60
    return h, d / (mx + 1e-6), mx

def blur(x, r):
    hh, ww = x.shape; fy = np.fft.fftfreq(hh)[:, None]; fx = np.fft.rfftfreq(ww)[None, :]
    return np.fft.irfft2(np.fft.rfft2(x) * np.exp(-2 * (np.pi * r) ** 2 * (fx ** 2 + fy ** 2)), s=(hh, ww)).astype(np.float32)

def components(mask):
    """4-connected labelling (iterative union-find on runs, numpy-light)"""
    H, W = mask.shape; lab = np.zeros((H, W), np.int32); parent = [0]; nxt = 1
    def find(a):
        while parent[a] != a: parent[a] = parent[parent[a]]; a = parent[a]
        return a
    for y in range(H):
        row = mask[y]; x = 0
        while x < W:
            if not row[x]: x += 1; continue
            x0 = x
            while x < W and row[x]: x += 1
            ups = set(lab[y - 1, x0:x][lab[y - 1, x0:x] > 0].tolist()) if y else set()
            if ups:
                roots = {find(u) for u in ups}; r = min(roots)
                for o in roots: parent[o] = r
                lab[y, x0:x] = r
            else:
                parent.append(nxt); lab[y, x0:x] = nxt; nxt += 1
    roots = np.array([find(i) for i in range(nxt)]); return roots[lab]

def rect_of(ys, xs):
    """oriented rectangle from pixel coords (PCA), returns centre, length, width, angle(rad, of long axis, x=east)"""
    P = np.stack([xs, -ys], 1).astype(np.float64); c = P.mean(0); Q = P - c
    ev, evec = np.linalg.eigh(np.cov(Q.T) + np.eye(2) * 1e-6); a = evec[:, 1]
    u = Q @ a; v = Q @ np.array([-a[1], a[0]])
    L_ = np.percentile(u, 98) - np.percentile(u, 2); W_ = np.percentile(v, 98) - np.percentile(v, 2)
    cu = (np.percentile(u, 98) + np.percentile(u, 2)) / 2; cv = (np.percentile(v, 98) + np.percentile(v, 2)) / 2
    c = c + a * cu + np.array([-a[1], a[0]]) * cv
    return c, max(L_, 1), max(W_, 1), math.atan2(a[1], a[0])

CLASSES = ['field', 'paddy', 'greenhouse', 'built', 'water', 'trees', 'bare', 'road']
result = {'buildings': [], 'greenhouses': [], 'water': [], 'trees': [], 'rasters': []}
for P in L['patches']:
    rr, t = P['real'], P['t']; res = 1.0
    img = mosaic(rr, res); H_, W_ = img.shape[:2]
    h, s, v = hsv(img)
    lum = img @ np.array([.3, .59, .11])
    # texture: stripes of greenhouses -> strong local variance of luminance at ~2-4 m scale
    hp = lum - blur(lum, 2.0); tex = np.sqrt(blur(hp * hp, 3.0))
    blue = (h > 190) & (h < 240) & (s > .32) & (v > .3)
    teal = (h > 150) & (h <= 190) & (s > .3) & (v > .3)
    warm = ((h < 28) | (h > 335)) & (s > .38) & (v > .3)
    bright = (s < .16) & (v > .62)
    smooth = tex < .028
    water = ((v < .45) & (h > 165) & (h < 225) & (s > .22) & smooth) | ((v < .3) & (h > 150) & (h < 210) & (s > .3) & smooth)
    trees = (h > 55) & (h < 150) & (v < .42) & (s > .18) & (tex > .025)
    veg = (h > 50) & (h < 150) & (s > .12)
    gh_mask = bright & (tex > .045)
    roof_mask = blue | teal | warm | (bright & ~gh_mask)
    # ---- greenhouses: grow the striped bright areas into complexes, split into tunnels
    def opening(m, r): return blur((blur(m.astype(np.float32), r) > .8).astype(np.float32), r) > .12   # drops lines thinner than ~2r
    # greenhouse complexes: neighbourhood brightness + stripe texture, filled into solid blobs, thin roads removed
    gh_score = (blur(bright.astype(np.float32), 3) > .28) & (blur(tex, 3) > .04) & (blur(s, 3) < .2)
    ghm = opening(blur(gh_score.astype(np.float32), 2) > .4, 2.6)
    lab = components(ghm)
    ids, cnt = np.unique(lab[lab > 0], return_counts=True)
    for i, c in zip(ids, cnt):
        if c < 220: continue
        ys, xs = np.nonzero(lab == i); cen, Ln, Wd, ang = rect_of(ys, xs)
        if Wd < 5 or (Ln / Wd > 7 and Wd < 12): continue   # long thin = concrete farm road, not a greenhouse block
        # stripe direction: compare luminance variance along the two axes inside the rect
        sub = lum[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
        gx_ = np.abs(np.diff(sub, axis=1)).mean(); gy_ = np.abs(np.diff(sub, axis=0)).mean()
        tun_axis = ang if True else ang
        span_w = 7.0   # one tunnel ~6-7 m wide
        n = max(1, int(round(Wd / span_w)))
        for k in range(n):
            off = (k + .5) * Wd / n - Wd / 2; nx, ny = -math.sin(ang), math.cos(ang)
            cx, cy = cen[0] + nx * off, cen[1] + ny * off
            gxp, gyp = rr[0] + cx * res + t[0], rr[3] + cy * res + t[1]
            result['greenhouses'].append({'c': [round(gxp, 1), round(gyp, 1)], 'l': round(Ln * res, 1), 'w': round(Wd / n * res * .9, 1), 'a': round(ang, 3)})
    # ---- roofs (non-greenhouse): connected components per colour family
    for name, m in (('blue', blue), ('teal', teal), ('warm', warm), ('white', bright & ~ghm)):
        mm = opening(blur(m.astype(np.float32), .8) > .5, 1.2)
        lab = components(mm); ids, cnt = np.unique(lab[lab > 0], return_counts=True)
        for i, c in zip(ids, cnt):
            if c < 25 or c > 6000: continue
            ys, xs = np.nonzero(lab == i); cen, Ln, Wd, ang = rect_of(ys, xs)
            fill = c / max(Ln * Wd, 1)
            if fill < .45 or Wd < 3.5 or Ln / Wd > 6: continue
            col = np.median(img[ys, xs], 0)
            gxp, gyp = rr[0] + cen[0] * res + t[0], rr[3] + cen[1] * res + t[1]
            result['buildings'].append({'c': [round(gxp, 1), round(gyp, 1)], 'l': round(Ln * res, 1), 'w': round(Wd * res, 1), 'a': round(ang, 3),
                                        'roof': name, 'col': [round(float(x), 3) for x in col], 'area': int(c)})
    # ---- water bodies
    wm = opening(water, 2.0); lab = components(wm); ids, cnt = np.unique(lab[lab > 0], return_counts=True)
    for i, c in zip(ids, cnt):
        if c < 600: continue
        ys, xs = np.nonzero(lab == i); cen, Ln, Wd, ang = rect_of(ys, xs)
        result['water'].append({'c': [round(rr[0] + cen[0] + t[0], 1), round(rr[3] + cen[1] + t[1], 1)], 'l': round(Ln, 1), 'w': round(Wd, 1), 'a': round(ang, 3)})
    # ---- tree clumps: sample points on tree mask (one per ~6 m cell)
    tm = blur(trees.astype(np.float32), 1.5) > .45
    for y in range(3, H_, 6):
        for x in range(3, W_, 6):
            if tm[y, x]: result['trees'].append([round(rr[0] + x + t[0] + np.random.uniform(-2, 2), 1), round(rr[3] - y + t[1] + np.random.uniform(-2, 2), 1)])
    # ---- land class raster (2 m), used for ground materials
    cls = np.zeros((H_, W_), np.uint8)
    paddy = veg & (tex < .03) | (h > 30) & (h < 60) & (s > .2) & (tex < .025)
    cls[:] = 0; cls[paddy] = 1; cls[ghm] = 2; cls[blur(roof_mask.astype(np.float32), 1) > .5] = 3; cls[wm] = 4; cls[tm] = 5
    cls[(s < .14) & (v > .45) & ~ghm & (tex < .03)] = 6
    small = Image.fromarray(cls).resize((W_ // 2, H_ // 2), Image.NEAREST)
    fn = f"landclass_{P['name']}.png"; small.save(os.path.join(out, fn))
    result['rasters'].append({'file': fn, 'game': P['game'], 'res': 2.0})
    # debug overlay
    dbg = (img * 255).astype(np.uint8).copy()
    dbg[ghm] = (dbg[ghm] * .4 + np.array([255, 0, 255]) * .6).astype(np.uint8)
    rm = blur(roof_mask.astype(np.float32), 1) > .5; dbg[rm & ~ghm] = (dbg[rm & ~ghm] * .4 + np.array([255, 200, 0]) * .6).astype(np.uint8)
    dbg[wm] = (dbg[wm] * .4 + np.array([0, 120, 255]) * .6).astype(np.uint8); dbg[tm] = (dbg[tm] * .5 + np.array([0, 160, 0]) * .5).astype(np.uint8)
    Image.fromarray(dbg).save(os.path.join(out, f"classdbg_{P['name']}.jpg"), quality=80)
    print(P['name'], 'px', W_, H_, 'gh', len(result['greenhouses']), 'bld', len(result['buildings']), 'water', len(result['water']), 'trees', len(result['trees']))
result['classes'] = CLASSES
json.dump(result, open(os.path.join(out, 'features.json'), 'w'))
