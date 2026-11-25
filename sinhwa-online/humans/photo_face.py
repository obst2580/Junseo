"""
정면 사진을 얼굴 피부 텍스처에 투영한다 (pack.py 에서 호출).
  python3 photo_face.py <src_dir> <gltf_name> <diffuse in/out> <photo_spec.json>
photo_spec: {photo, crop:[x0,y0,x1,y1](선택), lm:{eyeR,eyeL,mouth,chin} (사진 픽셀, eyeR=화면 왼쪽 눈),
             top: 눈 위로 쓸 높이(눈 사이 거리 배수), side: 좌우 반폭(눈 사이 배수), strength}
모델 쪽 기준점은 메시에서 자동으로 구한다: 눈 = 안구 메시 중심, 입 = 치아 앞면, 턱끝 = 정중선 최하단 앞쪽 점.
사진의 넓은 조명은 걷어내고(저주파 제거) 눈썹·눈매·입술·점 같은 디테일과 피부 톤만 옮긴다.
"""
import json, os, sys, struct, numpy as np
from PIL import Image, ImageFilter

src, name, dif_path, spec_path = sys.argv[1:5]
spec = json.load(open(spec_path))
g = json.load(open(os.path.join(src, name + '.gltf'))); raw = open(os.path.join(src, g['buffers'][0]['uri']), 'rb').read()
NC = {5126: np.float32, 5123: np.uint16, 5125: np.uint32, 5121: np.uint8}; NN = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4}

def acc(i):
    a = g['accessors'][i]; bv = g['bufferViews'][a['bufferView']]; dt = NC[a['componentType']]; n = NN[a['type']]
    off = bv.get('byteOffset', 0) + a.get('byteOffset', 0); st = bv.get('byteStride', 0) or np.dtype(dt).itemsize * n
    arr = np.ndarray((a['count'], n), dt, raw, off, (st, np.dtype(dt).itemsize)); return arr.astype(np.float64 if dt == np.float32 else np.int64)

def prim(key):
    for m in g['meshes']:
        for p in m['primitives']:
            if g['materials'][p['material']]['name'].endswith(key): return p
body = prim('.body'); P = acc(body['attributes']['POSITION']); N = acc(body['attributes']['NORMAL']); UV = acc(body['attributes']['TEXCOORD_0'])
F = acc(body['indices']).reshape(-1, 3)
eyes = acc(prim('.high-poly')['attributes']['POSITION']); teeth = acc(prim('teeth_base')['attributes']['POSITION'])
S = 2048; dif = np.asarray(Image.open(dif_path).convert('RGB')).astype(np.float32) / 255
eR = eyes[eyes[:, 0] < 0].mean(0); eL = eyes[eyes[:, 0] > 0].mean(0); ed = np.linalg.norm(eL - eR)
chin0 = eR[1] - 2.4 * ed
# rasterise head triangles into UV space: per-texel 3D position + normal
cen = P[F].mean(1); nz = N[F].mean(1)[:, 2]
sel = (cen[:, 1] > chin0) & (cen[:, 1] < eR[1] + .09) & (cen[:, 2] > eR[2] - .07) & (nz > .05) & (np.abs(cen[:, 0]) < 1.3 * ed + .02)
pos = np.zeros((S, S, 2)); nrm = np.zeros((S, S)); hit = np.zeros((S, S), bool)
for f in F[sel]:
    uv = UV[f] * S
    x0, y0 = np.floor(uv.min(0)).astype(int); x1, y1 = np.ceil(uv.max(0)).astype(int)
    if x1 - x0 > 200 or y1 - y0 > 200: continue
    ys, xs = np.mgrid[y0:y1 + 1, x0:x1 + 1]; px = np.c_[xs.ravel() + .5, ys.ravel() + .5]
    a, b, c = uv; den = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
    if abs(den) < 1e-9: continue
    l1 = ((b[1] - c[1]) * (px[:, 0] - c[0]) + (c[0] - b[0]) * (px[:, 1] - c[1])) / den
    l2 = ((c[1] - a[1]) * (px[:, 0] - c[0]) + (a[0] - c[0]) * (px[:, 1] - c[1])) / den; l3 = 1 - l1 - l2
    ins = (l1 > -.02) & (l2 > -.02) & (l3 > -.02)
    if not ins.any(): continue
    L = np.c_[l1, l2, l3][ins]; X = px[ins].astype(int); X = X[(X[:, 0] >= 0) & (X[:, 0] < S) & (X[:, 1] >= 0) & (X[:, 1] < S)]
    L = L[:len(X)]
    pos[X[:, 1], X[:, 0]] = L @ P[f][:, :2]; nrm[X[:, 1], X[:, 0]] = L @ N[f][:, 2]; hit[X[:, 1], X[:, 0]] = True
# model landmarks (glTF: +y up, +z forward; character's right eye has x<0 -> appears on the viewer's left)
# mouth = centroid of the lip texels (red in the MakeHuman skin texture), measured on the mesh via the UV raster
srcimg = [im['uri'] for im in g['images'] if 'skin' in im['uri'] or 'diffuse' in im['uri']][0]
st = np.asarray(Image.open(os.path.join(src, srcimg)).convert('RGB').resize((S, S))).astype(np.float32)
red = (st[..., 0] - st[..., 1] > 55) & hit & (np.abs(pos[..., 0]) < .03) & (pos[..., 1] < eR[1] - .03)
mouth = np.r_[0, pos[red][:, 1].mean() - .002]
# better: the lip parting on the midline profile = deepest point between the two lip bulges (below the nose tip)
mid_ = P[np.abs(P[:, 0]) < .004]; tipy = mid_[np.argmax(np.where(mid_[:, 1] > eR[1] - 2.2 * ed, mid_[:, 2], -9)), 1]
ys_ = np.arange(tipy - .012, tipy - .075, -.001); prof = []
for yv in ys_:
    m_ = mid_[np.abs(mid_[:, 1] - yv) < .0015]; prof.append(m_[:, 2].max() if len(m_) else np.nan)
prof = np.array(prof)
up = np.where((ys_ < tipy - .02) & (ys_ > tipy - .045), prof, -9); ip = int(np.argmax(up))       # upper lip peak
win = np.where((ys_ < ys_[ip] - .002) & (ys_ > ys_[ip] - .013), prof, 9); mouth = np.r_[0, ys_[int(np.argmin(win))]]   # lip parting
pk = [ip]
print('mouth y', round(float(mouth[1]), 4), 'nose tip y', round(float(tipy), 4), 'lip peaks', [round(float(ys_[i]), 4) for i in pk[:3]])
mid = P[(np.abs(P[:, 0]) < .006) & (P[:, 1] < mouth[1] - .015) & (P[:, 1] > mouth[1] - .09)]
pog = mid[np.argmax(mid[:, 2])]; ch = mid[mid[:, 2] > pog[2] - .022]; chin = ch[np.argmin(ch[:, 1])]
M = np.array([eR[:2], eL[:2], mouth, chin[:2]])
lm = spec['lm']; Q = np.array([lm['eyeR'], lm['eyeL'], lm['mouth'], lm['chin']], np.float64)
print('model lm', np.round(M, 4).tolist(), 'lip texels', int(red.sum()))
# affine model(x,y) -> photo(u,v) + gaussian RBF on residuals so all 4 landmarks land exactly
A = np.c_[M, np.ones(4)]; T, *_ = np.linalg.lstsq(A, Q, rcond=None); res = Q - A @ T
sig = .6 * ed
K = np.exp(-((M[:, None] - M[None]) ** 2).sum(-1) / (2 * sig * sig)); W = np.linalg.solve(K + 1e-6 * np.eye(4), res)
def to_photo(xy):
    k = np.exp(-((xy[:, None] - M[None]) ** 2).sum(-1) / (2 * sig * sig)); return np.c_[xy, np.ones(len(xy))] @ T + k @ W

img = Image.open(os.path.join(os.path.dirname(os.path.abspath(spec_path)), spec['photo'])).convert('RGB')
if spec.get('crop'): img = img.crop(tuple(spec['crop']))
ph = np.asarray(img).astype(np.float32) / 255; H, Wd = ph.shape[:2]
# photo face mask: ellipse from brow line to chin, feathered
qe = (Q[0] + Q[1]) / 2; pe = np.linalg.norm(Q[1] - Q[0])
top = qe[1] - spec.get('top', .55) * pe; bot = Q[3][1] + .05 * pe; cy = (top + bot) / 2; ry = (bot - top) / 2; rx = spec.get('side', .95) * pe
yy, xx = np.mgrid[0:H, 0:Wd].astype(np.float32)
d = np.sqrt(((xx - qe[0]) / rx) ** 2 + ((yy - cy) / ry) ** 2)
pmask = np.clip((1 - d) / (.3 if len(sys.argv) > 5 else .18), 0, 1) * np.clip((yy - top) / (.12 * pe), 0, 1)
# flatten photo lighting: divide by a masked low-pass, keep detail + local colour (lips, brows)
def lowpass(a, r):
    p = int(3 * r) + 1; a = np.pad(a.astype(np.float32), p, mode='edge'); h, w = a.shape
    fy = np.fft.fftfreq(h)[:, None]; fx = np.fft.rfftfreq(w)[None, :]
    b = np.fft.irfft2(np.fft.rfft2(a) * np.exp(-2 * (np.pi * r) ** 2 * (fx ** 2 + fy ** 2)), s=(h, w))
    return b[p:-p, p:-p].astype(np.float32)
r = .45 * pe; wsum = lowpass(pmask, r) + 1e-4
low = np.dstack([lowpass(ph[..., c] * pmask, r) / wsum for c in range(3)])
lumlow = low @ np.array([.3, .59, .11], np.float32)
detail = ph / np.maximum(low, .05)                        # chroma-neutral detail; lips/brows survive (smaller than the blur)
skin_ref = (ph * pmask[..., None]).reshape(-1, 3).sum(0) / pmask.sum()

ty, tx = np.nonzero(hit); q = to_photo(pos[ty, tx])
qi = np.clip(q, 0, [Wd - 1.001, H - 1.001]); x0 = qi[:, 0].astype(int); y0 = qi[:, 1].astype(int); fx = (qi[:, 0] - x0)[:, None]; fy = (qi[:, 1] - y0)[:, None]
def samp(a):
    a = a if a.ndim == 3 else a[..., None]
    return (a[y0, x0] * (1 - fx) * (1 - fy) + a[y0, x0 + 1] * fx * (1 - fy) + a[y0 + 1, x0] * (1 - fx) * fy + a[y0 + 1, x0 + 1] * fx * fy)
inside = (q[:, 0] >= 0) & (q[:, 0] < Wd - 1) & (q[:, 1] >= 0) & (q[:, 1] < H - 1)
dm = np.sqrt((((pos[ty, tx] - mouth) / [1.0 * ed, .35 * ed]) ** 2).sum(1))
mw = 1 - (1 - spec.get('mouth', .5)) * np.clip(1.6 - dm, 0, 1)
# nose: a frontal photo paints nostril shadows onto the sides of the 3D nose -> keep the mesh's own nose shading
tip = P[np.argmax(np.where((np.abs(P[:, 0]) < .01) & (P[:, 1] < eR[1]) & (P[:, 1] > mouth[1]), P[:, 2], -9))]
dn = np.sqrt((((pos[ty, tx] - tip[:2]) / [.45 * ed, .4 * ed]) ** 2).sum(1)); mw = mw * (1 - .75 * np.clip(1.4 - dn, 0, 1))
w = mw * samp(pmask)[:, 0] * np.clip((nrm[ty, tx] - .15) / .4, 0, 1) * inside * spec.get('strength', .8)
det = np.clip(samp(detail), .25, 1.35)
# target base = current texture low-pass (keeps the character's tan/weathering) pulled toward the photo skin tone
wimg = np.zeros((S, S), np.float32); wimg[ty, tx] = w
base_lp = np.dstack([lowpass(dif[..., c], 24) for c in range(3)])
tex_ref = (base_lp[ty, tx] * w[:, None]).sum(0) / max(w.sum(), 1e-3)
tone = (skin_ref / skin_ref.mean()) / (tex_ref / tex_ref.mean()); tone = tone ** .9 * (skin_ref.mean() / tex_ref.mean()) ** (.95 if len(sys.argv) > 5 else .5)
newc = base_lp[ty, tx] * tone * det
out = dif.copy(); wimg = lowpass(wimg, 2.0)[ty, tx][:, None]          # soften seams between triangles
out[ty, tx] = dif[ty, tx] * (1 - wimg) + newc * wimg
if len(sys.argv) > 5: out = dif * tone   # sticker mode: the whole skin takes the photo's skin tone so neck / ears / hands match the face
Image.fromarray(np.clip(out * 255, 0, 255).astype(np.uint8)).save(dif_path, quality=92)
print('photo face', name, 'texels', int((w > .05).sum()), 'tone', np.round(tone, 3))

# ---------------------------------------------------------------- face sticker: the photo itself on a thin shell over the face
# A grid in front view is cast onto the head (skin + eyeballs) so the shell also bridges the eye openings; its UVs are the
# photo coordinates, the photo's face mask becomes the texture alpha, and grazing angles fade out through vertex alpha.
if len(sys.argv) > 5:
    st_png, st_npz = sys.argv[5], sys.argv[6]
    ep = prim('.high-poly'); EF = acc(ep['indices']).reshape(-1, 3) + len(P)
    VP = np.vstack([P, eyes]); TRI = np.vstack([F[sel], EF])
    cz = VP[TRI].mean(1)
    TRI = TRI[(cz[:, 1] > chin[1] - .04) & (cz[:, 1] < eR[1] + .12) & (cz[:, 2] > eR[2] - .05)]
    A2 = VP[TRI][:, :, :2]; Z3 = VP[TRI][:, :, 2]
    step_ = .0018
    gx = np.arange(eR[0] - 1.6 * ed, eL[0] + 1.6 * ed + 1e-6, step_); gy = np.arange(chin[1] - .03, eR[1] + 1.4 * ed, step_)
    GX, GY = np.meshgrid(gx, gy); pts = np.c_[GX.ravel(), GY.ravel()]
    zhit = np.full(len(pts), -9.0)
    a, b, c = A2[:, 0], A2[:, 1], A2[:, 2]
    den = (b[:, 1] - c[:, 1]) * (a[:, 0] - c[:, 0]) + (c[:, 0] - b[:, 0]) * (a[:, 1] - c[:, 1]); ok = np.abs(den) > 1e-12
    for s0 in range(0, len(pts), 400):
        q = pts[s0:s0 + 400][:, None, :]
        l1 = ((b[:, 1] - c[:, 1]) * (q[..., 0] - c[:, 0]) + (c[:, 0] - b[:, 0]) * (q[..., 1] - c[:, 1])) / np.where(ok, den, 1)
        l2 = ((c[:, 1] - a[:, 1]) * (q[..., 0] - c[:, 0]) + (a[:, 0] - c[:, 0]) * (q[..., 1] - c[:, 1])) / np.where(ok, den, 1)
        l3 = 1 - l1 - l2; inside_ = ok & (l1 >= -1e-6) & (l2 >= -1e-6) & (l3 >= -1e-6)
        z = l1 * Z3[:, 0] + l2 * Z3[:, 1] + l3 * Z3[:, 2]
        zhit[s0:s0 + 400] = np.where(inside_, z, -9).max(1)
    qph = to_photo(pts); uvs = qph / [Wd, H]
    am = np.clip((1 - np.sqrt(((qph[:, 0] - qe[0]) / rx) ** 2 + ((qph[:, 1] - cy) / ry) ** 2)) / .3, 0, 1) * np.clip((qph[:, 1] - top) / (.12 * pe), 0, 1)
    ny_, nx_ = GX.shape; ZH = zhit.reshape(ny_, nx_)
    pit = ((np.abs(GX) < .028) & (np.abs(GY - mouth[1]) < .008)) | (np.hypot(GX - eR[0], GY - eR[1]) < .018) | (np.hypot(GX - eL[0], GY - eL[1]) < .018)
    # bridge cavities (mouth slit, eye corners): a texel far behind its neighbours takes the local surface height instead
    for _ in range(3):
        Zp = np.pad(ZH, 3, mode='edge'); nb = np.stack([Zp[3 + dy:3 + dy + ny_, 3 + dx:3 + dx + nx_] for dy in range(-3, 4) for dx in range(-3, 4)])
        hi = np.sort(nb, 0)[-12]; ZH = np.where((ZH < hi - .004) & (hi > -1) & pit, hi - .001, ZH)
    zhit = ZH.ravel()
    dzdx = np.gradient(ZH, step_, axis=1); dzdy = np.gradient(ZH, step_, axis=0)
    nrm3 = np.dstack([-dzdx, -dzdy, np.ones_like(ZH)]); nrm3 /= np.linalg.norm(nrm3, axis=2, keepdims=True); nrm3 = nrm3.reshape(-1, 3)
    va = np.clip((nrm3[:, 2] - .3) / .3, 0, 1) * (zhit > -1)
    pos3 = np.c_[pts, zhit] + nrm3 * .0012
    idx = np.arange(len(pts)).reshape(ny_, nx_); quads = np.c_[idx[:-1, :-1].ravel(), idx[:-1, 1:].ravel(), idx[1:, 1:].ravel(), idx[1:, :-1].ravel()]
    keepq = (zhit[quads] > -1).all(1) & ((am * va)[quads].max(1) > .01)
    # no stretched faces across depth jumps (eyelid -> eyeball edges are fine, nose side walls are not)
    keepq &= (zhit[quads].max(1) - zhit[quads].min(1)) < .012
    quads = quads[keepq]; used = np.unique(quads); remap = -np.ones(len(pts), int); remap[used] = np.arange(len(used))
    tris = np.r_[quads[:, [0, 2, 1]], quads[:, [0, 3, 2]]] if False else np.r_[quads[:, [0, 1, 2]], quads[:, [0, 2, 3]]]
    np.savez(st_npz, pos=pos3[used].astype(np.float32), nrm=nrm3[used].astype(np.float32), uv=uvs[used].astype(np.float32),
             col=np.c_[np.ones((len(used), 3)), va[used]].astype(np.float32), idx=remap[tris].astype(np.uint32))
    rgba = np.dstack([ph, pmask[..., None]])
    Image.fromarray(np.clip(rgba * 255, 0, 255).astype(np.uint8), 'RGBA').save(st_png)
    print('face sticker', len(used), 'verts', len(tris), 'tris')
