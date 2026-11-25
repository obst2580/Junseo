"""
커스터마이징 기본 몸 패킹: python3 pack_custom.py <m|f> <src_dir(custom_m)> <out_dir>
  - 모프 타깃 POSITION 을 16비트 정규화 정수로 양자화 (KHR_mesh_quantization) -> 용량 절반
  - glTF JSON + 바이너리(base64, 12MB 조각) 로 아티팩트 서버 제약에 맞춤
  - 머리카락: 무채색 결 텍스처 (뷰어에서 머리색/새치를 입힘)
  - 옷: 주름 음영(회색) + 영역 마스크(R=상의, G=하의, B=원본 유지) -> 뷰어에서 슬롯별 색
  - 피부: 나이대별(청년/중년/노년) diffuse 3종 + 주름·모공 높이맵
"""
import sys, os, json, base64, math, subprocess, shutil, struct, numpy as np
from PIL import Image, ImageDraw, ImageFilter

GENDER, src, dst = sys.argv[1:4]
HERE = os.path.dirname(os.path.abspath(__file__))
MPFB = '/tmp/claude-0/-home-user/6a787f25-3a4b-56f2-b301-9738cd9b9de9/scratchpad/human/extroot/.user/user_default/mpfb/data'
os.makedirs(os.path.join(dst, 'tex'), exist_ok=True)
g = json.load(open(os.path.join(src, 'base.gltf'))); raw = open(os.path.join(src, g['buffers'][0]['uri']), 'rb').read()
meta = json.load(open(os.path.join(src, 'custom_meta.json')))

# ---------------------------------------------------------------- quantize morph targets
CT = {5126: ('f', 4), 5123: ('H', 2), 5125: ('I', 4), 5122: ('h', 2), 5121: ('B', 1)}
NC = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4}
def view_bytes(vi): v = g['bufferViews'][vi]; o = v.get('byteOffset', 0); return raw[o:o + v['byteLength']]
new_views = {}   # bufferView index -> replacement bytes
morph_acc = set()
for m in g['meshes']:
    for p in m['primitives']:
        for t in p.get('targets', []): morph_acc.add(t['POSITION'])
q = 0
for ai in morph_acc:
    a = g['accessors'][ai]
    if a['componentType'] != 5126: continue
    def quant(vi, count):
        arr = np.frombuffer(view_bytes(vi), np.float32)[:count * 3]
        return np.clip(np.round(arr * 32767), -32767, 32767).astype(np.int16).tobytes()
    if 'sparse' in a: continue   # three.js re-normalises sparse values on load (setX): keep sparse morphs as float
    elif 'bufferView' in a:
        amax = float(np.abs(np.frombuffer(view_bytes(a['bufferView']), np.float32)).max())
        if amax >= 1: continue
        new_views[a['bufferView']] = quant(a['bufferView'], a['count'])
    a['componentType'] = 5122; a['normalized'] = True
    for k in ('min', 'max'):
        if k in a: a[k] = [round(x * 32767) / 32767 for x in a[k]]
    q += 1
out = bytearray()
for vi, v in enumerate(g['bufferViews']):
    b = new_views.get(vi, view_bytes(vi))
    while len(out) % 4: out.append(0)
    v['byteOffset'] = len(out); v['byteLength'] = len(b); out += b
    if 'byteStride' in v and vi in new_views: v['byteStride'] = 6
g['buffers'][0]['byteLength'] = len(out); del g['buffers'][0]['uri']
g.setdefault('extensionsUsed', []).append('KHR_mesh_quantization'); g.setdefault('extensionsRequired', []).append('KHR_mesh_quantization')
print('quantized morph accessors', q, 'bin', len(raw) // 1e6, '->', len(out) // 1e6, 'MB')

# ---------------------------------------------------------------- textures
def lum(a): return a[..., :3] @ np.array([.3, .59, .11], np.float32)
def blur(x, r):
    h, w = x.shape; fy = np.fft.fftfreq(h)[:, None]; fx = np.fft.rfftfreq(w)[None, :]
    return np.fft.irfft2(np.fft.rfft2(x) * np.exp(-2 * (np.pi * r) ** 2 * (fx ** 2 + fy ** 2)), s=(h, w)).astype(np.float32)
uri_of = {}
HAIR_KEYS = ('short', 'long', 'bob', 'braid', 'ponytail', 'hair', 'gingerhair', 'male02_diffuse')
for im in g['images']:
    p = os.path.join(src, im['uri']); base, ext = os.path.splitext(im['uri']); img = Image.open(p); name = os.path.basename(base).lower()
    if img.mode == 'RGBA' and img.getextrema()[3][0] < 250 and any(k in name for k in HAIR_KEYS):
        a = np.asarray(img.convert('RGBA')).astype(np.float32) / 255; L = lum(a); m = a[..., 3] > .5
        lo, hi = np.percentile(L[m], (3, 97)); t = np.clip((L - lo) / max(hi - lo, 1e-3), 0, 1)
        g_ = .35 + .65 * t   # neutral strand detail; the viewer multiplies the chosen hair colour
        la = Image.fromarray((np.dstack([g_, a[..., 3]]) * 255).clip(0, 255).astype(np.uint8), 'LA')
        if la.size[0] > 1024: la = la.resize((1024, 1024), Image.LANCZOS)
        la.save(os.path.join(dst, im['uri']), optimize=True)
    elif img.mode == 'RGBA' and img.getextrema()[3][0] < 250 and 'normal' not in name:
        if img.size[0] > 1024: img = img.resize((1024, 1024), Image.LANCZOS)
        img.save(os.path.join(dst, im['uri']), optimize=True)
    else:
        img.convert('RGB').save(os.path.join(dst, base + '.jpg'), quality=86); im['uri'] = base + '.jpg'; im['mimeType'] = 'image/jpeg'
    uri_of[name] = im['uri']
# eye texture
for im in g['images']:
    if 'brown_eye' in im['uri']: shutil.copy(os.path.join(HERE, 'eye_dark_rgba.png'), os.path.join(dst, im['uri']))

# garments: shade (grey, x0.6 headroom) where tinted, original colour where kept; mask R=top G=bottom B=keep
garments = {}
for c in meta['clothes']:
    fn = f'{c}_diffuse'; uri = uri_of.get(fn)
    if not uri: print('no tex for', c); continue
    orig = np.asarray(Image.open(os.path.join(src, 'tex', fn + '.png')).convert('RGB')).astype(np.float32) / 255; W = orig.shape[0]
    mimg = Image.new('L', (W, W), 0); d = ImageDraw.Draw(mimg)
    for cls, uv in meta['uvclass'][c]:
        if cls == 'bottom': d.polygon([(u % 1 * W, (1 - v % 1) * W) for u, v in uv], fill=255)
    L = lum(orig); used = L > .02; bottom = (np.asarray(mimg) > 127) & used; top = used & ~bottom
    keep = np.zeros_like(used)
    if 'elegantsuit' in c:   # white shirt collar/cuffs and the tie keep their own colours
        keep = used & (blur(L, 2) > .42)
        yy, xx = np.mgrid[0:W, 0:W] / W; keep |= used & (xx > .33) & (xx < .45) & (yy < .2)
    top &= ~keep; bottom &= ~keep
    shade = np.zeros_like(L)
    for reg in (top, bottom):
        if reg.any():
            Lb = blur(L, 1.5); mm = Lb[reg].mean(); shade[reg] = (np.clip(Lb / max(mm, 1e-3), .3, 1.6) ** .85)[reg]
    col = np.where(keep[..., None], orig, (shade * .6)[..., None].repeat(3, -1))
    Image.fromarray((col * 255).clip(0, 255).astype(np.uint8)).save(os.path.join(dst, uri), quality=88)
    mask = np.stack([top, bottom, keep], -1).astype(np.float32)
    mask = np.stack([blur(mask[..., i], 1) for i in range(3)], -1)
    mfn = f'tex/{c}_mask.png'; Image.fromarray((mask * 255).clip(0, 255).astype(np.uint8)).save(os.path.join(dst, mfn))
    garments[c] = {'mask': mfn}
    print('garment', c, 'top', int(top.sum()), 'bottom', int(bottom.sum()), 'keep', int(keep.sum()))

# skin: three ages, weathering per age, one shared height map
ages = {'young': 'young_asian_%s', 'middle': 'middleage_asian_%s', 'old': 'old_asian_%s'}
sex = 'male' if GENDER == 'm' else 'female'; skins = {}
for k, pat in ages.items():
    d = os.path.join(MPFB, 'skins', pat % sex); f = next(x for x in os.listdir(d) if x.endswith('.png') and 'diffuse' in x.lower())
    outd = f'tex/skin_{k}.jpg'; outh = os.path.join(dst, 'tex', f'skin_height_{k}.jpg')
    tan, spots = {'young': (.1, .1), 'middle': (.2, .3), 'old': (.3, .6)}[k]
    subprocess.run([sys.executable, os.path.join(HERE, 'skin_detail.py'), os.path.join(d, f), outh, os.path.join(dst, outd), str(tan), str(spots)], check=True, stdout=subprocess.DEVNULL)
    skins[k] = outd
    if k != 'middle': os.remove(outh)
skin_h = 'tex/skin_height_middle.jpg'

# ---------------------------------------------------------------- write
gj = json.dumps(g, separators=(',', ':'))
b64 = base64.b64encode(bytes(out)).decode(); CH = 12_000_000
parts = [b64[i:i + CH] for i in range(0, len(b64), CH)]
for i, ptxt in enumerate(parts): json.dump({'data': ptxt}, open(os.path.join(dst, f'bin{i}.json'), 'w'))
open(os.path.join(dst, 'base.json'), 'w').write(gj)
json.dump({'parts': len(parts), 'morphs': meta['morphs'], 'hairs': meta['hairs'], 'clothes': meta['clothes'], 'shoes': meta['shoes'],
           'garments': garments, 'skins': skins, 'skinHeight': skin_h},
          open(os.path.join(dst, 'manifest.json'), 'w'), ensure_ascii=False)
print('parts', len(parts), 'json', len(gj) // 1000, 'KB')
