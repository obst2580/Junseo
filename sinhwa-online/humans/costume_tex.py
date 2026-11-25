"""
의상 텍스처 레시피 엔진 (pack.py 에서 호출). 원본 MakeHuman 옷의 주름·그늘 음영을 살리고 색·무늬만 바꾼다.

레시피 문자열:
  tile:<file>:<reps>        humans/tex_src 의 타일 이미지를 반복
  solid:#hex                단색 원단 (직조 결 + 얼룩)
  check:#a:#b:<n>           체크무늬 (n 칸)
  hivis                     형광 연두 메시 원단
  rubber:#hex  leather:#hex 고무 / 가죽
  ...+mud  ...+oil          흙 / 기름 얼룩 추가
  keep                      원본 그대로   tint:#hex  원본 명암에 색만 입힘
  <recipe>|suitshade:<file> 옷 원본 텍스처의 접힘 음영을 곱한다 (복제한 조각용)
옷 전체(suit) 스펙은 dict: {bottom: 레시피, top: 레시피, inner: 레시피(선택, 원본이 붉은 줄무늬인 안쪽 셔츠)}
"""
import os, math, json, numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__)); TS = os.path.join(HERE, 'tex_src')

def hexrgb(h): h = h.lstrip('#'); return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], np.float32) / 255

def blur(x, r):
    h, w = x.shape; fy = np.fft.fftfreq(h)[:, None]; fx = np.fft.rfftfreq(w)[None, :]
    return np.fft.irfft2(np.fft.rfft2(x) * np.exp(-2 * (np.pi * r) ** 2 * (fx ** 2 + fy ** 2)), s=(h, w)).astype(np.float32)

def noise(W, scale, seed):
    r = np.random.default_rng(seed); s = max(2, W // scale)
    return np.asarray(Image.fromarray(r.standard_normal((s, s)).astype(np.float32), 'F').resize((W, W), Image.BICUBIC))

def weave(W, period=2.0, amp=.035):
    y, x = np.mgrid[0:W, 0:W]; return np.sin(x * math.pi / period) * np.sin(y * math.pi / period) * amp

def fill(recipe, W, seed=1):
    base, *mods = recipe.split('+'); parts = base.split(':'); kind = parts[0]
    if kind == 'tile':
        t = np.asarray(Image.open(os.path.join(TS, parts[1])).convert('RGB')).astype(np.float32) / 255; n = t.shape[0]; reps = float(parts[2])
        y, x = np.mgrid[0:W, 0:W]; a = t[(y * reps * n / W).astype(int) % n, (x * reps * n / W).astype(int) % n]
    elif kind == 'solid':
        a = hexrgb(parts[1])[None, None] * (1 + noise(W, 48, seed) * .035 + noise(W, 8, seed + 1) * .03 + weave(W))[..., None]
    elif kind == 'check':
        n = float(parts[3]); y, x = np.mgrid[0:W, 0:W] / W
        bx, by = (x * n % 1) < .5, (y * n % 1) < .5; ca, cb = hexrgb(parts[1]), hexrgb(parts[2])
        mix = (bx.astype(np.float32) + by) / 2            # 0, .5, 1 -> gingham: dark, mid, light
        a = cb[None, None] * (1 - mix[..., None]) + ca[None, None] * mix[..., None]
        a *= (1 + weave(W, 1.5, .05) + noise(W, 32, seed) * .03)[..., None]
    elif kind == 'hivis':
        a = hexrgb('#d4e04a')[None, None] * (1 + weave(W, 3, .09) + noise(W, 24, seed) * .04)[..., None]
        y, x = np.mgrid[0:W, 0:W]; holes = ((x % 6 < 2) & (y % 6 < 2)); a[holes] *= .78   # mesh fabric
    elif kind in ('rubber', 'leather'):
        a = hexrgb(parts[1])[None, None] * (1 + noise(W, 32, seed) * (.05 if kind == 'rubber' else .09) + noise(W, 4, seed + 2) * .03)[..., None]
        if kind == 'leather': a *= (1 + np.clip(noise(W, 3, seed + 5), -1, 1) * .04)[..., None]   # grain
    else:
        raise ValueError(recipe)
    for m in mods:
        if m in ('mud', 'oil'):
            sp = np.clip(noise(W, 18, seed + 7) + noise(W, 70, seed + 8) * .5 - (1.3 if m == 'mud' else 1.5), 0, 1)[..., None]
            col = np.array([.22, .19, .15]) if m == 'mud' else np.array([.08, .07, .06])
            a = a * (1 - sp * .75) + col * sp * .75
    return np.clip(a, 0, 1.5)

def shading(L, mask, r):
    Lb = blur(L, r); m = (Lb * mask).sum() / max(mask.sum(), 1)
    return np.clip(Lb / max(m, 1e-3), .3, 1.7) ** .85

def load(p):
    return np.asarray(Image.open(p).convert('RGB').resize((2048, 2048), Image.LANCZOS) if Image.open(p).size[0] != 2048 else Image.open(p).convert('RGB')).astype(np.float32) / 255

def lum(a): return a @ np.array([.3, .59, .11], np.float32)

def raster(polys, W, cls_filter=None):
    img = Image.new('L', (W, W), 0); d = ImageDraw.Draw(img)
    for cls, uv in polys:
        if cls_filter and cls != cls_filter: continue
        d.polygon([(u % 1 * W, (1 - v % 1) * W) for u, v in uv], fill=255)
    return np.asarray(img) > 127

def apply_recipe(recipe, orig, mask, W, r=4, seed=1):
    if recipe == 'keep': return orig
    if recipe.startswith('tint:'):
        L = lum(orig); return hexrgb(recipe[5:])[None, None] * (L / max((L * mask).sum() / max(mask.sum(), 1), 1e-3))[..., None] ** .9
    return fill(recipe, W, seed) * shading(lum(orig), mask, r)[..., None]

def build(job, src_tex_dir, out_path, costume, preset):
    """job = ('suit'|'shoe', spec, original file name) or ('piece', recipe)"""
    W = 2048
    if job[0] in ('suit', 'shoe'):
        kind, spec, fn = job; orig = load(os.path.join(src_tex_dir, fn)); used = lum(orig) > .02
        if kind == 'suit':
            bottom = raster(costume['uvclass'], W, 'bottom') & used; top = used & ~bottom
            res = orig.copy()
            inner = None
            if spec.get('inner'):   # inner striped shirt of a jacket outfit: reddish original pixels inside the top
                red = blur((orig[..., 0] > orig[..., 1] + .08).astype(np.float32), 3) > .5; inner = top & red; top = top & ~red
                res[inner] = apply_recipe(spec['inner'], orig, inner, W, 10, 3)[inner]
            res[bottom] = apply_recipe(spec.get('bottom', 'keep'), orig, bottom, W, 4, 1)[bottom]
            res[top] = apply_recipe(spec.get('top', 'keep'), orig, top, W, 10, 2)[top]
        else:
            res = apply_recipe(spec, orig, used, W, 10, 4); res[~used] = 0
    else:
        recipe = job[1]; shade = None
        if '|suitshade:' in recipe:
            recipe, sfn = recipe.split('|suitshade:'); o = load(os.path.join(src_tex_dir, sfn)); used = lum(o) > .02
            shade = .8 + .2 * shading(lum(o), used, 10)
        res = fill(recipe, W, 9)
        if shade is not None: res = res * shade[..., None]
    Image.fromarray(np.clip(res * 255, 0, 255).astype(np.uint8)).save(out_path, quality=90)
    print('costume tex', os.path.basename(out_path), job[0])
