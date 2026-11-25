"""
기존 MakeHuman 의상 텍스처의 UV 배치·주름 음영은 살리고 무늬만 바꾼다.
  retex.py suit <suit_diffuse> <tex_src> <out_dir>   -> suit_halmeoni.png (바지=꽃무늬 몸빼, 셔츠=잔꽃 블라우스),
                                                        vest_layout.png, boot_layout.png, armcover_layout.png
  retex.py shoe <shoe_diffuse> <tex_src> <out_dir>   -> shoe_rubber.png (신발+양말 전체를 고무장화로)
"""
import sys, os, numpy as np
from PIL import Image, ImageFilter

mode, src, ts, out = sys.argv[1:5]; os.makedirs(out, exist_ok=True)
a = np.asarray(Image.open(src).convert('RGB')).astype(np.float32) / 255; W = a.shape[0]
L = a @ np.array([.3, .59, .11], np.float32)

def tiled(name, reps):
    t = np.asarray(Image.open(os.path.join(ts, name)).convert('RGB')).astype(np.float32) / 255; n = t.shape[0]
    y, x = np.mgrid[0:W, 0:W]
    return t[(y * reps * n // W) % n, (x * reps * n // W) % n]

def shading(mask, r=3):
    """original fold/shadow shading as a multiplier around 1 (fabric print and seams removed by a mild blur)"""
    Lb = np.asarray(Image.fromarray((L * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(W / 2048 * r))) / 255
    m = (Lb * mask).sum() / max(mask.sum(), 1)
    return np.clip(Lb / max(m, 1e-3), .35, 1.6) ** .8

def save(arr, name, alpha=None):
    img = Image.fromarray(np.clip(arr * 255, 0, 255).astype(np.uint8))
    img.save(os.path.join(out, name)); print('->', name)

if mode == 'suit':
    used = L > .03                                            # black background = unused texture space
    pants = used & (a[..., 2] > a[..., 0] + .08)              # denim is blue; the shirt is red/black/white stripes
    pants = np.asarray(Image.fromarray((pants * 255).astype(np.uint8)).filter(ImageFilter.MedianFilter(9))) > 127
    shirt = used & ~pants
    sp, ss = shading(pants)[..., None], shading(shirt, 12)[..., None]   # stripes are finer than folds
    suit = np.zeros_like(a)
    suit[pants] = (tiled('momppae_floral.png', 2) * sp)[pants]
    suit[shirt] = (tiled('blouse_print.png', 2) * ss)[shirt]
    save(suit, 'suit_halmeoni.png')
    save(tiled('vest_quilt.png', 10) * (.85 + .15 * ss), 'vest_layout.png')
    save(tiled('boot_rubber.png', 2) * (.9 + .1 * sp), 'boot_layout.png')
    save(tiled('momppae_floral.png', 5) * (.8 + .2 * ss), 'armcover_layout.png')
    np.save(os.path.join(out, 'suit_pants_mask.npy'), np.packbits(pants))
elif mode == 'shoe':
    used = L > .02
    rub = tiled('boot_rubber.png', 3) * shading(used, 10)[..., None] ** .6
    rub[~used] = 0
    save(rub, 'shoe_rubber.png')
