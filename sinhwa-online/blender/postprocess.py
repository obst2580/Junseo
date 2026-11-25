"""Post-process a Blender build for the web viewer.
python3 postprocess.py <build_dir> <publish_dir>
- lightmaps (float16 .npy, rows bottom-up) -> gamma-encoded JPEG, rows top-down (load with flipY=false)
- roughness PNGs -> grayscale JPEG, texture URIs rewritten
"""
import json, os, shutil, sys
import numpy as np
from PIL import Image, ImageFilter

src, dst = sys.argv[1], sys.argv[2]
LMAX = 4.0          # lightmap range: stored = (L / LMAX) ** (1/2.2)
os.makedirs(os.path.join(dst, 'tex'), exist_ok=True)

stats = {}
for tag in ('ground', 'build'):
    a = np.load(os.path.join(src, f'lm_{tag}.npy')).astype(np.float32)
    a = np.flipud(a)
    a = np.nan_to_num(a, nan=0.0, posinf=LMAX, neginf=0.0)
    stats[tag] = {'mean': float(a.mean()), 'p95': float(np.percentile(a, 95)), 'max': float(a.max())}
    enc = np.clip(a / LMAX, 0, 1) ** (1 / 2.2)
    im = Image.fromarray((enc * 255 + .5).astype(np.uint8), 'RGB')
    im = im.filter(ImageFilter.MedianFilter(3))          # knock out fireflies from the bake
    im.save(os.path.join(dst, f'lm_{tag}.jpg'), quality=90, optimize=True)
    # the average edge value of the ground bake lights the unbaked outer ground
    if tag == 'ground':
        edge = np.concatenate([a[:8].reshape(-1, 3), a[-8:].reshape(-1, 3), a[:, :8].reshape(-1, 3), a[:, -8:].reshape(-1, 3)])
        stats['ground_edge'] = [float(v) for v in edge.mean(0)]

g = json.load(open(os.path.join(src, 'scene.gltf')))
for im in g.get('images', []):
    uri = im['uri']; p = os.path.join(src, uri)
    base, ext = os.path.splitext(uri)
    name = base.split('/')[-1]
    if '_1k' in name and not name.startswith(('Concrete', 'Asphalt', 'Ground', 'Grass', 'Plaster', 'Painted', 'Wood', 'Corrugated', 'Gravel')):
        # Poly Haven prop textures: 512 px is plenty at prop scale
        im0 = Image.open(p); im0 = im0.convert('RGBA' if im0.mode in ('RGBA', 'LA', 'P') and ext.lower() == '.png' else 'RGB')
        im0 = im0.resize((min(512, im0.width), min(512, im0.height)), Image.LANCZOS)
        if im0.mode == 'RGBA':
            im0.save(os.path.join(dst, uri)); continue
        im0.save(os.path.join(dst, base + '.jpg'), quality=82, optimize=True); im['uri'] = base + '.jpg'; im['mimeType'] = 'image/jpeg'; continue
    if ext.lower() == '.png' and 'Roughness' in base:
        Image.open(p).convert('L').save(os.path.join(dst, base + '.jpg'), quality=88, optimize=True)
        im['uri'] = base + '.jpg'; im['mimeType'] = 'image/jpeg'
    elif ext.lower() == '.png' and Image.open(p).mode in ('RGBA', 'LA', 'P'):
        Image.open(p).convert('RGBA').save(os.path.join(dst, uri), optimize=True)   # keep cut-out alpha (leaves)
    elif ext.lower() == '.png' and not base.split('/')[-1].startswith('sign_'):
        Image.open(p).convert('RGB').save(os.path.join(dst, base + '.jpg'), quality=90, optimize=True)
        im['uri'] = base + '.jpg'; im['mimeType'] = 'image/jpeg'
    elif ext.lower() in ('.jpg', '.jpeg'):
        q = 90 if 'Normal' in base else 84          # source JPEGs ship at ~q100; re-encode for the web
        Image.open(p).convert('RGB').save(os.path.join(dst, uri), quality=q, optimize=True)
    else:
        shutil.copy(p, os.path.join(dst, uri))
# artifact hosting serves neither .gltf/.bin nor data: URLs -> glTF JSON without buffer uri + base64 buffer in JSON
import base64
raw = open(os.path.join(src, g['buffers'][0]['uri']), 'rb').read(); del g['buffers'][0]['uri']
json.dump(g, open(os.path.join(dst, 'scene.json'), 'w'))
b64 = base64.b64encode(raw).decode(); CH = 12_000_000          # artifact files must stay under 16 MB each
parts = [b64[i:i + CH] for i in range(0, len(b64), CH)]
for i, part in enumerate(parts): json.dump({'data': part}, open(os.path.join(dst, f'scene_bin{i}.json'), 'w'))
json.dump({'parts': len(parts)}, open(os.path.join(dst, 'scene_bin.json'), 'w'))
json.dump({'lmax': LMAX, 'stats': stats, 'sun': open(os.path.join(src, 'sun.txt')).read().strip()}, open(os.path.join(dst, 'bake.json'), 'w'))
print(json.dumps(stats))

# ---- ground mask (R = dirt, G = gravel, B = grass density) in lightmap space: col=(x+180)/360, row=(z+180)/360
from PIL import ImageDraw
lay = json.load(open(os.path.join(src, 'layout.json')))
S = 1024; E = 180.0
px = lambda x, z: ((x + E) / (2 * E) * S, (z + E) / (2 * E) * S)
if 'region' in lay:   # village build: ground lightmap spans the region (game x east / y north; three z = -y)
    X0, Y0, X1, Y1 = lay['region']; S = 2048
    px = lambda x, z: ((x - X0) / (X1 - X0) * S, (z + Y1) / (Y1 - Y0) * S)
for k in ('lanes', 'roads', 'rects', 'polys'): lay.setdefault(k, [])
def img(): return Image.new('L', (S, S), 0)
def strip_poly(x0, z0, x1, z1, w):
    dx, dz = x1 - x0, z1 - z0; L = (dx * dx + dz * dz) ** .5; nx, nz = -dz / L * w / 2, dx / L * w / 2
    return [px(x0 + nx, z0 + nz), px(x1 + nx, z1 + nz), px(x1 - nx, z1 - nz), px(x0 - nx, z0 - nz)]
rng = np.random.default_rng(4)
def smooth_noise(cells, blur):
    n = Image.fromarray((rng.random((cells, cells)) * 255).astype(np.uint8)).resize((S, S), Image.BICUBIC)
    return np.asarray(n.filter(ImageFilter.GaussianBlur(blur)), dtype=np.float32) / 255
dirt, grav, block = img(), img(), img()
dd, gd, bd = ImageDraw.Draw(dirt), ImageDraw.Draw(grav), ImageDraw.Draw(block)
for (x0, z0, x1, z1, w) in lay['lanes']:
    dd.polygon(strip_poly(x0, z0, x1, z1, w + 3.2), fill=210); bd.polygon(strip_poly(x0, z0, x1, z1, w + .6), fill=255)
for (x0, z0, x1, z1, w) in lay['roads']:
    dd.polygon(strip_poly(x0, z0, x1, z1, w + 2.4), fill=150); bd.polygon(strip_poly(x0, z0, x1, z1, w + 1.2), fill=255)
for (x0, x1, z0, z1, kind) in lay['rects']:
    r = [px(x0, z0), px(x1, z1)]
    if kind == 'yard': dd.rectangle([px(x0 - 2, z0 - 2), px(x1 + 2, z1 + 2)], fill=170); bd.rectangle(r, fill=255)
    elif kind == 'gh': gd.rectangle([px(x0 - 1, z0 - 1.5), px(x1 + 1, z1 + 1.5)], fill=120); dd.rectangle([px(x0 - 1.5, z0 - 2), px(x1 + 1.5, z1 + 2)], fill=140); bd.rectangle(r, fill=255)
    elif kind == 'paddy': bd.rectangle(r, fill=255)
    else: bd.rectangle(r, fill=255)
for poly in lay['polys']:
    pts = [px(x, z) for x, z in poly]; gd.polygon(pts, fill=200); bd.polygon(pts, fill=255)
noise = smooth_noise(14, 6)
R_ = np.clip(np.asarray(dirt.filter(ImageFilter.GaussianBlur(3)), np.float32) / 255 + np.clip((noise - .62) * 2.2, 0, .55), 0, 1)
G_ = np.asarray(grav.filter(ImageFilter.GaussianBlur(2.5)), np.float32) / 255
blk = np.asarray(block.filter(ImageFilter.GaussianBlur(1.5)), np.float32) / 255
B_ = np.clip((1 - blk) * (1 - R_ * .85) * (.55 + .45 * smooth_noise(30, 4)), 0, 1)
if 'region' in lay and os.environ.get('WORLD'):   # village: grass / dirt / gravel from the land classes (0 field 1 paddy 2 gh-yard 3 yard 4 water 5 forest 6 bare)
    WJ = json.load(open(os.path.join(os.environ['WORLD'], 'world.json'))); G = WJ['ground']
    cls = np.asarray(Image.open(os.path.join(os.environ['WORLD'], G['file'])))
    if cls.ndim == 3: cls = cls[..., 0]
    xs = X0 + (np.arange(S) + .5) / S * (X1 - X0); ys = Y1 - (np.arange(S) + .5) / S * (Y1 - Y0)
    cc = np.clip(((xs - G['x0']) / G['res']).astype(int), 0, cls.shape[1] - 1); rr = np.clip(((G['y1'] - ys) / G['res']).astype(int), 0, cls.shape[0] - 1)
    C = cls[rr[:, None], cc[None, :]]
    def soft(v, r): return np.asarray(Image.fromarray((v * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(r)), np.float32) / 255
    gw = soft(np.select([C == 0, C == 1, C == 5], [.55, .1, .95], 0.).astype(np.float32), 3)
    dw = soft(np.select([C == 6, C == 2], [1., .55], 0.).astype(np.float32), 3)
    vw = soft((C == 3).astype(np.float32) * .6, 2)
    patch = smooth_noise(60, 3)
    B_ = np.clip((1 - blk) * gw * (.35 + .9 * patch) * (1 - R_ * .7), 0, 1)
    R_ = np.clip(R_ + dw * .8, 0, 1); G_ = np.clip(G_ + vw, 0, 1)
    A_ = soft((C == 1).astype(np.float32), 1.5) * (1 - blk * .0)   # paddy weight (harvested stubble field)
mask = np.stack([R_, G_, B_], -1)
if 'A_' in dir(): mask = np.dstack([mask, A_])
Image.fromarray((mask * 255).astype(np.uint8), 'RGBA' if mask.shape[2] == 4 else 'RGB').save(os.path.join(dst, 'groundmask.png'))
print('mask ok')
if 'region' in lay and os.environ.get('WORLD'):   # terrain height over the region (16-bit in R/G) so instanced grass sits on the ground
    import base64
    HJ = json.load(open(os.path.join(os.environ['WORLD'], 'height.json')))
    hu = np.frombuffer(base64.b64decode(HJ['data']), dtype='<u2').reshape(HJ['h'], HJ['w']).astype(np.float32) * HJ['scale'] + HJ['hmin']
    N = 512; X0, Y0, X1, Y1 = lay['region']
    xs = X0 + (np.arange(N) + .5) / N * (X1 - X0); ys = Y1 - (np.arange(N) + .5) / N * (Y1 - Y0)   # rows: north -> south (= three z ascending)
    gx, gy = np.meshgrid(xs, ys); i = np.clip((gx - HJ['x0']) / HJ['step'], 0, HJ['w'] - 1.001); j = np.clip((gy - HJ['y0']) / HJ['step'], 0, HJ['h'] - 1.001)
    i0, j0 = i.astype(int), j.astype(int); ti, tj = i - i0, j - j0
    h = (hu[j0, i0] * (1 - ti) + hu[j0, i0 + 1] * ti) * (1 - tj) + (hu[j0 + 1, i0] * (1 - ti) + hu[j0 + 1, i0 + 1] * ti) * tj
    lo, hi = float(h.min()) - 1, float(h.max()) + 1; q = np.round((h - lo) / (hi - lo) * 65535).astype(np.uint32)
    Image.fromarray(np.dstack([(q >> 8), (q & 255), np.zeros_like(q)]).astype(np.uint8), 'RGB').save(os.path.join(dst, 'heightmap.png'))
    b = json.load(open(os.path.join(dst, 'bake.json'))); b.update(region=lay['region'], hlo=lo, hhi=hi); json.dump(b, open(os.path.join(dst, 'bake.json'), 'w'))
    print('heightmap', lo, hi)
