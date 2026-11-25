"""
피부 디테일 생성: MakeHuman 피부 텍스처(2048)에서
  1) 주름 높이맵: 밝기 하이패스(그려진 주름·잔결) + 모공 노이즈 → 4096 그레이 JPG (뷰어에서 bumpMap)
  2) 얼굴 풍화: 머리 UV 영역에만 햇볕 그을림·잡티·홍조를 얹은 diffuse
Usage: python3 skin_detail.py <diffuse.jpg|png> <out_height.jpg> <out_diffuse.jpg> [tan=0..1] [spots=0..1] [seed]
"""
import sys, numpy as np
from PIL import Image, ImageFilter

src, out_h, out_d = sys.argv[1:4]
tan = float(sys.argv[4]) if len(sys.argv) > 4 else .5
spots = float(sys.argv[5]) if len(sys.argv) > 5 else .5
rng = np.random.default_rng(int(sys.argv[6]) if len(sys.argv) > 6 else 7)

im = Image.open(src).convert('RGB'); W = im.size[0]
a = np.asarray(im).astype(np.float32) / 255

# skin mask: everything that is not the flat background fill colour (sampled at a known empty corner of the layout)
bg = a[int(W * .03), int(W * .97)]
flat = (np.abs(a - bg).max(-1) < .015)
for ax in (0, 1):   # background is a perfectly flat fill: also require no texture in both neighbours
    flat &= (np.abs(np.roll(a, 1, ax) - a).max(-1) < .006) & (np.abs(np.roll(a, -1, ax) - a).max(-1) < .006)
flat = np.asarray(Image.fromarray((flat * 255).astype(np.uint8)).filter(ImageFilter.MedianFilter(7))) > 127
skin = (~flat).astype(np.float32)
skin = np.asarray(Image.fromarray((skin * 255).astype(np.uint8)).filter(ImageFilter.MinFilter(5)).filter(ImageFilter.GaussianBlur(3))) / 255

# head region of the MakeHuman UV layout (right-hand lobe: face sideways, scalp above/below)
yy, xx = np.mgrid[0:W, 0:W] / W
head = ((xx > .635) & (yy > .17) & (yy < .85)).astype(np.float32)
head = np.asarray(Image.fromarray((head * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(W * .01))) / 255 * skin

# --- 1) height: high-pass of luminance (painted wrinkles are darker), upsampled, plus pores
L = a @ np.array([.3, .59, .11], np.float32)
def blur(x, r):   # gaussian blur via FFT (PIL cannot blur float images)
    h, w = x.shape; fy = np.fft.fftfreq(h)[:, None]; fx = np.fft.rfftfreq(w)[None, :]
    return np.fft.irfft2(np.fft.rfft2(x) * np.exp(-2 * (np.pi * r) ** 2 * (fx ** 2 + fy ** 2)), s=(h, w)).astype(np.float32)
hp = L - blur(L, W / 2048 * 6)
hp = np.clip(hp / (np.percentile(np.abs(hp[skin > .5]), 98) + 1e-6), -1, 1)
H2 = 2 * W
hpu = np.asarray(Image.fromarray(hp, 'F').resize((H2, H2), Image.BICUBIC))
sk2 = np.asarray(Image.fromarray((skin * 255).astype(np.uint8)).resize((H2, H2), Image.BILINEAR)) / 255
hd2 = np.asarray(Image.fromarray((head * 255).astype(np.uint8)).resize((H2, H2), Image.BILINEAR)) / 255
pores = np.zeros((H2, H2), np.float32)
n = int(H2 * H2 / 22)
py, px = rng.integers(0, H2, n), rng.integers(0, H2, n)
pores[py, px] = -rng.uniform(.6, 1.0, n)
pores = blur(pores, .9) * 2.2 + blur(rng.standard_normal((H2, H2)).astype(np.float32), .6) * .35
height = .5 + (hpu * .30 + pores * .22 * (.6 + .4 * hd2)) * sk2
Image.fromarray(np.clip(height * 255, 0, 255).astype(np.uint8), 'L').save(out_h, quality=88)

# --- 2) weathering on the face: warmer/darker tan, blotchy sun spots, ruddy cheeks
col = a.copy()
# even out the baked-in light blotches of the source face (keep fine detail, flatten broad luminance swings)
Lb = blur(L, W / 2048 * 40); mean = (Lb * head).sum() / (head.sum() + 1e-6)
col *= (1 + head * .7 * (mean / np.maximum(Lb, 1e-3) - 1))[..., None]
# inpaint whitish patches painted into the source face (e.g. between the brows): brighter AND paler than surroundings
br = blur(a[..., 2] / np.maximum(a[..., 0], 1e-3), W / 2048 * 2.5)   # pale patches are bluer relative to red
Ls = blur(L, W / 2048 * 2.5)
face = ((xx > .70) & (xx < .90) & (yy > .36) & (yy < .68))           # bare-skin face area only (scalp stubble excluded)
bad = ((br - blur(br, W / 2048 * 60)) > .05) & ((Ls - blur(Ls, W / 2048 * 60)) > .03) & face & (skin > .5)
badm = np.clip(blur(bad.astype(np.float32), W / 2048 * 4) * 4, 0, 1)
keep = 1 - badm
fill = np.stack([blur(col[..., c] * keep, W / 2048 * 18) for c in range(3)], -1) / np.maximum(blur(keep, W / 2048 * 18), 1e-3)[..., None]
col = col * keep[..., None] + fill * badm[..., None]
print('inpainted px', int(bad.sum()))
# knock down small bright patches (baked highlights): where the mid-scale luminance pops above its surroundings
Lm = blur(col @ np.array([.3, .59, .11], np.float32), W / 2048 * 5); Lw = blur(Lm, W / 2048 * 30)
pop = np.clip(Lm / np.maximum(Lw, 1e-3), 1, 2)
col /= (1 + (pop - 1) * head)[..., None]
# match the body's skin tone to the face so the neck seam disappears
body = skin * (1 - np.clip(head * 3, 0, 1))
cf = (col * head[..., None]).reshape(-1, 3).sum(0) / head.sum(); cb = (col * body[..., None]).reshape(-1, 3).sum(0) / (body.sum() + 1e-6)
col *= (1 + body[..., None] * (cf / cb - 1))
# sun tan everywhere (farmer's neck and hands too)
tan_rgb = np.array([.86, .74, .62], np.float32)              # multiply: browner, less pink
k = (tan * skin)[..., None]
col = col * (1 - k) + col * tan_rgb * k
blot = blur(rng.standard_normal((W, W)).astype(np.float32), W / 2048 * 14); blot /= np.abs(blot).max() + 1e-6
col *= 1 - (np.clip(blot, 0, 1) ** 1.5 * .22 * spots * head)[..., None] * np.array([.6, 1, 1.25], np.float32)
dots = np.zeros((W, W), np.float32); m = int(260 * spots)
dy, dx = rng.integers(0, W, m), rng.integers(0, W, m); dots[dy, dx] = rng.uniform(.5, 1, m)
dots = np.clip(blur(dots, W / 2048 * 2.2) * 9, 0, 1) * head
col *= 1 - dots[..., None] * np.array([.18, .28, .36], np.float32) * spots   # small brown age spots
Image.fromarray(np.clip(col * 255, 0, 255).astype(np.uint8)).save(out_d, quality=92)
print('height', out_h, H2, 'diffuse', out_d)
