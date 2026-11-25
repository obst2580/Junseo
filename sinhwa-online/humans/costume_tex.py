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
    elif kind == 'knit':   # rib knit: vertical wales across u, fine stitch rows along v
        y, x = np.mgrid[0:W, 0:W] / W
        rib = .5 + .5 * np.cos(x * 2 * np.pi * 64); row = .5 + .5 * np.cos(y * 2 * np.pi * 180)
        a = hexrgb(parts[1])[None, None] * (.78 + .16 * rib + .06 * row + noise(W, 24, seed) * .03)[..., None]
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
            if spec.get('top', '').startswith('varsity') and costume.get('suit_geo'):
                vc, jk = varsity(orig, top | (inner if inner is not None else top), costume['suit_geo'], W)
                sh = shading(lum(orig), jk, 10)[..., None]; res[jk] = (vc * (.35 + .65 * sh))[jk]
                rest = top & ~jk; res[rest] = apply_recipe('solid:#1b2036', orig, rest, W, 10, 2)[rest]
            else:
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

# ---------------------------------------------------------------- school varsity jacket (대인고 과잠): painted by body position
FONTS = os.path.join(HERE, 'fonts')
def _font(n, size):
    from PIL import ImageFont; return ImageFont.truetype(os.path.join(FONTS, n), size)

def _raster_geo(geo, W):
    """per texel: rest-pose xyz, face normal, sleeve flag (-1 = not jacket)"""
    pos = np.zeros((W, W, 3), np.float32); nrm = np.zeros((W, W, 3), np.float32); cls = np.full((W, W), -1, np.int8)
    for t in geo['tris']:
        uv = np.array(t[:3], np.float64) * [W, W]; uv[:, 1] = W * (1 - np.array(t[:3])[:, 1] % 1) if False else W - uv[:, 1]
        P = np.array(t[3:6], np.float64); n = np.cross(P[1] - P[0], P[2] - P[0]); ln = np.linalg.norm(n)
        if ln < 1e-12: continue
        n /= ln
        x0, y0 = np.floor(uv.min(0)).astype(int); x1, y1 = np.ceil(uv.max(0)).astype(int)
        x0, y0 = max(x0, 0), max(y0, 0); x1, y1 = min(x1, W - 1), min(y1, W - 1)
        if x1 < x0 or y1 < y0 or (x1 - x0) * (y1 - y0) > 40000: continue
        ys, xs = np.mgrid[y0:y1 + 1, x0:x1 + 1]; px = xs.ravel() + .5; py = ys.ravel() + .5
        a, b, c = uv; den = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
        if abs(den) < 1e-9: continue
        l1 = ((b[1] - c[1]) * (px - c[0]) + (c[0] - b[0]) * (py - c[1])) / den
        l2 = ((c[1] - a[1]) * (px - c[0]) + (a[0] - c[0]) * (py - c[1])) / den; l3 = 1 - l1 - l2
        ins = (l1 > -.03) & (l2 > -.03) & (l3 > -.03)
        if not ins.any(): continue
        L = np.c_[l1, l2, l3][ins]; X = xs.ravel()[ins]; Y = ys.ravel()[ins]
        pos[Y, X] = L @ P; nrm[Y, X] = n; cls[Y, X] = t[6]
    return pos, nrm, cls

def _decal_img(kind, name):
    """RGBA decal and its size in metres (w, h)"""
    from PIL import Image, ImageDraw
    if kind == 'D':
        im = Image.new('RGBA', (400, 480), (0, 0, 0, 0)); d = ImageDraw.Draw(im); f = _font('BlackHanSans.ttf', 470)
        for off, col in ((14, (196, 150, 60, 255)), (7, (244, 240, 230, 255)), (0, (236, 232, 222, 255))):
            if off:
                for dx in range(-off, off + 1, 2):
                    for dy in range(-off, off + 1, 2): d.text((200 + dx, 250 + dy), 'D', font=f, fill=col, anchor='mm')
            else: d.text((200, 250), 'D', font=f, fill=col, anchor='mm')
        return im, (.10, .12)
    if kind == 'back':
        im = Image.new('RGBA', (1400, 600), (0, 0, 0, 0)); d = ImageDraw.Draw(im); f = _font('BlackHanSans.ttf', 300); f2 = _font('PlexKR600.ttf', 120)
        letters = 'DAEIN'; xs = np.linspace(180, 1220, 5)
        for i, (ch, x) in enumerate(zip(letters, xs)):
            y = 230 - 60 * (1 - ((i - 2) / 2) ** 2)    # arched
            for dx in range(-10, 11, 2):
                for dy in range(-10, 11, 2): d.text((x + dx, y + dy), ch, font=f, fill=(196, 150, 60, 255), anchor='mm')
            d.text((x, y), ch, font=f, fill=(206, 206, 200, 255), anchor='mm')
        d.text((700, 470), 'High School', font=f2, fill=(222, 172, 60, 255), anchor='mm')
        return im, (.36, .155)
    if kind == 'badge':
        im = Image.new('RGBA', (500, 500), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
        d.ellipse((10, 10, 490, 490), fill=(24, 40, 96, 255)); d.ellipse((40, 40, 460, 460), fill=(250, 250, 248, 255)); d.ellipse((120, 120, 380, 380), fill=(90, 150, 210, 255))
        d.polygon([(130, 300), (250, 230), (370, 300), (250, 370)], fill=(24, 40, 96, 255))
        d.text((250, 300), '大仁', font=_font('PlexKR600.ttf', 70), fill=(255, 255, 255, 255), anchor='mm')
        d.text((250, 82), '대인고등학교', font=_font('PlexKR600.ttf', 54), fill=(24, 40, 96, 255), anchor='mm')
        d.text((250, 425), 'SINCE 1991', font=_font('PlexKR600.ttf', 40), fill=(24, 40, 96, 255), anchor='mm')
        return im, (.085, .085)
    if kind == 'tag':   # 명찰: white plate, navy rim, the name big and bold
        im = Image.new('RGBA', (660, 220), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
        d.rounded_rectangle((0, 0, 659, 219), 26, fill=(24, 40, 96, 255)); d.rounded_rectangle((14, 14, 645, 205), 18, fill=(252, 252, 250, 255))
        f = _font('BlackHanSans.ttf', 150)
        while d.textbbox((0, 0), name, font=f)[2] > 560: f = _font('BlackHanSans.ttf', f.size - 8)
        d.text((330, 116), name, font=f, fill=(16, 16, 20, 255), anchor='mm')
        return im, (.15, .05)

def _project(col, pos, nrm, mask, img_wh, centre, right, up, facing, minface=.35):
    """planar decal: texels whose normal points along `facing`, mapped by (right, up) around `centre`"""
    im, (w, h) = img_wh; A = np.asarray(im).astype(np.float32) / 255; ih, iw = A.shape[:2]
    f_ = np.array(facing, np.float32); c_ = np.array(centre, np.float32)
    # the surface depth at the decal: outermost jacket texel along `facing` near the centre (pockets / flaps included)
    near = mask & (nrm @ f_ > minface) & (np.linalg.norm((pos - c_) - np.outer((pos - c_).reshape(-1, 3) @ f_, f_).reshape(pos.shape), axis=-1) < max(w, h) * .6)
    if near.any(): d_ = float((pos[near] @ f_).max()); c_ = c_ + f_ * (d_ - float(c_ @ f_))
    centre = c_
    rel = pos - np.array(centre, np.float32); u = rel @ np.array(right, np.float32) / w + .5; v = .5 - rel @ np.array(up, np.float32) / h
    sel = mask & (nrm @ np.array(facing, np.float32) > minface) & (u >= 0) & (u < 1) & (v >= 0) & (v < 1) & (rel @ np.array(facing, np.float32) > -.06)
    yy, xx = np.nonzero(sel)
    s = A[np.clip((v[yy, xx] * ih).astype(int), 0, ih - 1), np.clip((u[yy, xx] * iw).astype(int), 0, iw - 1)]
    col[yy, xx] = col[yy, xx] * (1 - s[:, 3:]) + s[:, :3] * s[:, 3:]

def varsity(orig, top, geo, W):
    pos, nrm, cls = _raster_geo(geo, W); lm = {k: np.array(v) for k, v in geo['lm'].items()}
    jacket = top & (cls >= 0)
    navy, grey = hexrgb('#1b2036'), hexrgb('#c4c4c0')
    col = np.where((cls == 1)[..., None], grey[None, None] * (1 + noise(W, 6, 3)[..., None] * .06 + noise(W, 40, 4)[..., None] * .03), navy[None, None] * (1 + noise(W, 40, 5)[..., None] * .04))
    # ribbing: cuffs (near the wrists), collar (around the neck), hem (bottom band) — navy with two white stripes
    def rib(d, width, stripes):
        r = np.ones(d.shape, np.float32) * 0; band = d < width
        for s0, s1 in stripes: r = np.maximum(r, ((d > s0) & (d < s1)).astype(np.float32))
        return band, r
    z = pos[..., 2]; wrist = np.minimum(np.linalg.norm(pos - lm['hand_l'], axis=-1), np.linalg.norm(pos - lm['hand_r'], axis=-1)) - .04
    for band, white in (rib(wrist, .075, [(.02, .032), (.044, .056)]), rib(z - geo['top_min_z'], .07, [(.022, .034), (.046, .058)]),
                        rib(lm['neck_01'][2] + .07 - z, .085, [(.025, .036), (.047, .058)]) if True else None):
        b = jacket & band & ((cls == 1) | (band & (wrist > .2)) if False else jacket & band)
        rib_c = np.where(white[..., None] > 0, hexrgb('#ecebe6')[None, None], navy[None, None]) * (1 + np.sin(pos[..., 0] * 900)[..., None] * .05)
        col[b] = rib_c[b]
    # patches
    front, back, right, up = [0, -1, 0], [0, 1, 0], [1, 0, 0], [0, 0, 1]
    chest_z = float(lm['spine_03'][2]) + .09; fy = float(pos[jacket][:, 1].min())
    _project(col, pos, nrm, jacket & (cls == 0), _decal_img('D', ''), [.10, fy, chest_z], right, up, front)
    _project(col, pos, nrm, jacket & (cls == 0), _decal_img('tag', geo['name']), [-.10, fy, chest_z + .025], right, up, front)
    by = float(pos[jacket][:, 1].max())
    _project(col, pos, nrm, jacket & (cls == 0), _decal_img('back', ''), [0, by, chest_z + .02], [-1, 0, 0], up, back)
    sh = lm['upperarm_l'] + (lm['lowerarm_l'] - lm['upperarm_l']) * .38; sx = float(pos[jacket & (cls == 1) & (np.abs(z - sh[2]) < .03)][:, 0].max())
    _project(col, pos, nrm, jacket & (cls == 1), _decal_img('badge', ''), [sx, sh[1], sh[2]], [0, 1, 0], up, [1, 0, 0], .3)
    return col, jacket
