"""
농촌 의상용 타일 텍스처 생성 (PIL, 1024px, 타일링):
  momppae_floral.png  꽃무늬 몸빼 (팥색 바탕에 큰 꽃)
  blouse_print.png    잔꽃무늬 블라우스 (연한 바탕)
  vest_quilt.png      분홍 누빔 조끼
  hat_cloth.png       베이지 면 농사 모자 (직조 결)
  boot_rubber.png     짙은 녹색 고무장화
Usage: python3 gen_textures.py <out_dir>
"""
import sys, os, math, numpy as np
from PIL import Image, ImageDraw, ImageFilter

out = sys.argv[1]; os.makedirs(out, exist_ok=True)
N = 1024
rng = np.random.default_rng(3)

def noise(scale, amp, seed):
    r = np.random.default_rng(seed); s = max(2, N // scale)
    small = r.standard_normal((s, s)).astype(np.float32)
    big = np.asarray(Image.fromarray(small, 'F').resize((N, N), Image.BICUBIC))
    return big * amp

def weave(amp):  # fine cotton weave: alternating thread brightness
    y, x = np.mgrid[0:N, 0:N]
    return (np.sin(x * math.pi / 2.0) * np.sin(y * math.pi / 2.0)) * amp

def tile_draw(img, fn, count, rmin, rmax, seed):
    """draw motifs at random positions, wrapped so the texture tiles"""
    r = np.random.default_rng(seed); d = ImageDraw.Draw(img)
    for _ in range(count):
        cx, cy, rad = r.uniform(0, N), r.uniform(0, N), r.uniform(rmin, rmax)
        for ox in (-N, 0, N):
            for oy in (-N, 0, N):
                if -rad * 2 < cx + ox < N + rad * 2 and -rad * 2 < cy + oy < N + rad * 2:
                    fn(d, cx + ox, cy + oy, rad, r)

def flower(cols, petals=(5, 6)):
    def f(d, cx, cy, rad, r):
        n = int(r.integers(*petals, endpoint=True)); a0 = r.uniform(0, 6.3); col = cols[int(r.integers(len(cols)))]
        for k in range(n):
            a = a0 + k * 2 * math.pi / n; px, py = cx + math.cos(a) * rad * .55, cy + math.sin(a) * rad * .55
            d.ellipse([px - rad * .48, py - rad * .48, px + rad * .48, py + rad * .48], fill=col)
        d.ellipse([cx - rad * .24, cy - rad * .24, cx + rad * .24, cy + rad * .24], fill=(232, 196, 92))
    return f

def leaf(col):
    def f(d, cx, cy, rad, r):
        a = r.uniform(0, 6.3); dx, dy = math.cos(a) * rad, math.sin(a) * rad
        d.polygon([(cx - dx, cy - dy), (cx - dy * .35, cy + dx * .35), (cx + dx, cy + dy), (cx + dy * .35, cy - dx * .35)], fill=col)
    return f

def finish(img, grain=.05, seed=0, blur=.6):
    a = np.asarray(img.filter(ImageFilter.GaussianBlur(blur))).astype(np.float32) / 255
    a *= (1 + noise(64, grain, seed) + weave(.035))[..., None]
    return Image.fromarray(np.clip(a * 255, 0, 255).astype(np.uint8))

# 몸빼: deep plum ground, big pink/white/yellow flowers with green leaves (classic market momppae print)
im = Image.new('RGB', (N, N), (92, 40, 58))
tile_draw(im, leaf((58, 92, 52)), 70, 26, 40, 11)
tile_draw(im, flower([(226, 120, 150), (238, 226, 214), (214, 82, 104), (236, 170, 70)]), 46, 38, 58, 12)
finish(im, seed=1).save(os.path.join(out, 'momppae_floral.png'))

# 블라우스: cream ground, tiny lilac/rose flowers
im = Image.new('RGB', (N, N), (226, 214, 200))
tile_draw(im, flower([(170, 128, 176), (204, 120, 130), (120, 140, 170)], (5, 5)), 420, 9, 14, 21)
finish(im, grain=.04, seed=2).save(os.path.join(out, 'blouse_print.png'))

# 분홍 누빔 조끼: diamond quilting with stitch lines and puff shading
y, x = np.mgrid[0:N, 0:N] / N
u, v = (x + y) * 8 % 1, (x - y) * 8 % 1
puff = np.sin(u * math.pi) * np.sin(v * math.pi)                 # 1 in cell centre, 0 on seams
base = np.array([208, 122, 143], np.float32) / 255
a = base[None, None] * (.72 + .28 * puff[..., None] ** .6)
a *= (1 + noise(48, .04, 5) + weave(.03))[..., None]
seam = (np.minimum(np.minimum(u, 1 - u), np.minimum(v, 1 - v)) < .012)
a[seam] *= .62
Image.fromarray(np.clip(a * 255, 0, 255).astype(np.uint8)).save(os.path.join(out, 'vest_quilt.png'))
Image.fromarray(np.clip((.5 + .5 * puff) * 255, 0, 255).astype(np.uint8)).save(os.path.join(out, 'vest_quilt_height.png'))

# 농사 모자: beige cotton with sun-faded mottling and a few soil smudges
a = np.ones((N, N, 3), np.float32) * np.array([200, 186, 150], np.float32) / 255
a *= (1 + noise(16, .04, 7) + noise(128, .03, 8) + weave(.06))[..., None]
sm = np.clip(noise(10, 1, 9) - 1.3, 0, 1)[..., None]
a = a * (1 - sm * .5) + np.array([.42, .34, .24]) * sm * .5
Image.fromarray(np.clip(a * 255, 0, 255).astype(np.uint8)).save(os.path.join(out, 'hat_cloth.png'))

# 고무장화: dark green rubber, dried mud splashes toward the bottom (v=1 is the bottom of the UV island, roughly)
a = np.ones((N, N, 3), np.float32) * np.array([46, 66, 48], np.float32) / 255
a *= (1 + noise(32, .05, 10))[..., None]
mud = np.clip(noise(20, 1, 11) + noise(80, .5, 12) - 1.6, 0, 1)[..., None]
a = a * (1 - mud * .6) + np.array([.30, .26, .20]) * mud * .6
Image.fromarray(np.clip(a * 255, 0, 255).astype(np.uint8)).save(os.path.join(out, 'boot_rubber.png'))
print('textures ->', out)
