"""
덩어리(솔리드) 헤어 메시용 결 텍스처: UV 섬마다 길이 방향으로 머리카락 결을 그리고, 섬 가장자리를 가닥지게 투명 처리해
판자처럼 보이는 머리 덩어리를 머리 다발처럼 보이게 한다.
  hair_strands.py <gltf> <bin> <mesh name part> <out.png> <#rgb>
섬의 길이 방향은 UV 섬별 주성분(PCA)으로 구한다.
"""
import json, sys, numpy as np
from PIL import Image, ImageDraw

gp, bp, key, out, col = sys.argv[1:6]
g = json.load(open(gp)); raw = open(bp, 'rb').read()
NC = {5126: np.float32, 5123: np.uint16, 5125: np.uint32}; NN = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3}
def acc(i):
    a = g['accessors'][i]; bv = g['bufferViews'][a['bufferView']]; dt = NC[a['componentType']]; n = NN[a['type']]
    off = bv.get('byteOffset', 0) + a.get('byteOffset', 0); st = bv.get('byteStride', 0) or np.dtype(dt).itemsize * n
    return np.ndarray((a['count'], n), dt, raw, off, (st, np.dtype(dt).itemsize)).astype(np.float64)
pr = [m for m in g['meshes'] if key in m['name']][0]['primitives'][0]
uv = acc(pr['attributes']['TEXCOORD_0']); F = acc(pr['indices']).astype(int).reshape(-1, 3)
S = 1024
# UV islands: union-find over triangles sharing a vertex at the same uv
par = list(range(len(uv)))
def find(a):
    while par[a] != a: par[a] = par[par[a]]; a = par[a]
    return a
key_uv = {}
for i, (u, v) in enumerate(np.round(uv, 5)):
    k = (u, v)
    if k in key_uv: par[find(i)] = find(key_uv[k])
    else: key_uv[k] = i
for a, b, c in F: par[find(b)] = find(a); par[find(c)] = find(a)
isl = np.array([find(f[0]) for f in F])
ang = np.zeros((S, S), np.float32); mask = Image.new('L', (S, S), 0); dm = ImageDraw.Draw(mask)
angimg = Image.new('F', (S, S), 0.0); da = ImageDraw.Draw(angimg)
for k in np.unique(isl):
    tris = F[isl == k]; pts = uv[np.unique(tris)]
    if len(pts) < 3: continue
    c = pts - pts.mean(0); w, V = np.linalg.eigh(c.T @ c); d = V[:, 1]            # long axis of the island
    a = float(np.arctan2(d[1], d[0]))
    for t in tris:
        poly = [(uv[i][0] * S, uv[i][1] * S) for i in t]; dm.polygon(poly, fill=255); da.polygon(poly, fill=a)
mask = np.asarray(mask).astype(np.float32) / 255; ang = np.asarray(angimg)
rng = np.random.default_rng(3)
def blur(x, r):
    h, w = x.shape; fy = np.fft.fftfreq(h)[:, None]; fx = np.fft.rfftfreq(w)[None, :]
    return np.fft.irfft2(np.fft.rfft2(x) * np.exp(-2 * (np.pi * r) ** 2 * (fx ** 2 + fy ** 2)), s=(h, w)).astype(np.float32)
# strands: sample a 1D noise across the strand direction (coordinate perpendicular to the island's long axis)
yy, xx = np.mgrid[0:S, 0:S].astype(np.float32)
perp = -xx * np.sin(ang) + yy * np.cos(ang); along = xx * np.cos(ang) + yy * np.sin(ang)
def n1(x, period, seed):
    r = np.random.default_rng(seed).random(4096); i = x / period; i0 = np.floor(i).astype(int); f = i - i0; f = f * f * (3 - 2 * f)
    return r[i0 % 4096] * (1 - f) + r[(i0 + 1) % 4096] * f
fine = n1(perp + 4000, 1.6, 1) * .5 + n1(perp + 4000, 4.5, 2) * .35 + n1(perp + 4000, 13, 3) * .15
wav = n1(along + 4000, 40, 4)
edge = np.clip(blur(mask, 5) * 2.2 - .9, 0, 1)                 # 0 at island border -> 1 inside
a = np.clip(edge * 1.5 - (1 - fine) * .9 + .15, 0, 1) * mask
L = (.5 + .75 * fine) * (.85 + .3 * wav) * (.75 + .25 * edge)
rgb = np.array([int(col.lstrip('#')[i:i + 2], 16) for i in (0, 2, 4)]) / 255
img = np.dstack([rgb[None, None] * L[..., None], a])
Image.fromarray(np.clip(img * 255, 0, 255).astype(np.uint8), 'RGBA').save(out)
print('hair strands', key, len(np.unique(isl)), 'islands')
