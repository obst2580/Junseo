"""
읍내·훈련소 건물용 텍스처 (PIL). python3 gen_town_tex.py <tex>/derived
  facade_*.jpg   한 칸(3.6 m) x 한 층(3.2 m) 반복 타일: 상가(타일+띠창), 빌라(붉은 벽돌), 생활관(베이지 도장), 관공서(석재)
  storefront.jpg 1층 상가 유리면 한 칸
  signs.png      한글 간판 아틀라스 4열 x 8행 (셀 512x128), 0..N-1 순서는 SIGNS 목록
  roof_flat.jpg  옥상 녹색 방수 페인트(얼룩·크랙)
"""
import os, sys, numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter
D = sys.argv[1]; FONT = os.path.join(os.path.dirname(D.rstrip('/')), 'BlackHanSans.ttf')
rng = np.random.default_rng(7)

def noise(w, h, s, amp):
    n = rng.standard_normal((max(2, h // s), max(2, w // s))).astype(np.float32)
    return np.asarray(Image.fromarray(n, 'F').resize((w, h), Image.BICUBIC)) * amp

def finish(a, name, q=90):
    Image.fromarray(np.clip(a, 0, 255).astype(np.uint8)).save(os.path.join(D, name), quality=q); print(name)

W, H = 576, 512                       # 3.6 m x 3.2 m
def base(col, grain=6):
    a = np.ones((H, W, 3), np.float32) * np.array(col, np.float32)
    return a * (1 + noise(W, H, 24, .05)[..., None] + noise(W, H, 3, .03)[..., None])

def window(a, x0, y0, x1, y1, frame=(205, 205, 200), glass=(58, 70, 80), mull=True):
    a[y0:y1, x0:x1] = frame
    g = a[y0 + 8:y1 - 8, x0 + 8:x1 - 8]; hh = g.shape[0]
    grad = np.linspace(1.25, .8, hh)[:, None, None]
    g[:] = np.array(glass, np.float32) * grad * (1 + noise(g.shape[1], hh, 30, .08)[..., None])
    if mull: m = (x0 + x1) // 2; a[y0:y1, m - 4:m + 4] = frame
    a[y1:y1 + 10, x0 - 6:x1 + 6] = np.array(frame) * .8    # sill

# 상가: 밝은 회색 타일 + 가로 띠창
a = base((206, 203, 196))
for y in range(0, H, 32): a[y:y + 2] *= .88                 # tile joints
for x in range(0, W, 48): a[:, x:x + 2] *= .9
window(a, 40, 150, 536, 360, frame=(170, 172, 175), glass=(60, 78, 92))
a[440:470] = (150, 146, 138)                                   # floor band
finish(a, 'facade_shop.jpg')
# 빌라: 붉은 벽돌
a = base((150, 78, 58))
for row, y in enumerate(range(0, H, 22)):
    a[y:y + 3] = (190, 182, 168); off = 0 if row % 2 else 30
    for x in range(off, W, 60): a[y:y + 22, x:x + 3] = (190, 182, 168)
a *= (1 + noise(W, H, 8, .08))[..., None]
window(a, 120, 140, 456, 380, frame=(225, 225, 220), glass=(55, 66, 74))
a[380:400, 100:476] = (200, 198, 192)
finish(a, 'facade_villa.jpg')
# 생활관: 베이지 도장 콘크리트, 규칙적인 창
a = base((214, 200, 168))
a *= (1 + np.clip(noise(W, H, 60, .5), 0, 1) * -.12)[..., None]   # rain streaks / dirt
window(a, 150, 150, 426, 370, frame=(235, 235, 228), glass=(52, 62, 70))
a[470:500] = (180, 160, 120)
finish(a, 'facade_barracks.jpg')
# 관공서: 석재 패널
a = base((198, 196, 188))
for y in range(0, H, 128): a[y:y + 3] *= .8
for x in range(0, W, 144): a[:, x:x + 3] *= .8
window(a, 60, 130, 516, 380, frame=(90, 92, 96), glass=(46, 60, 72))
finish(a, 'facade_civic.jpg')
# 1층 상가 유리면
a = base((120, 122, 125))
a[40:H - 10, 20:W - 20] = (40, 52, 60)
a[40:H - 10, 20:W - 20] *= np.linspace(1.4, .8, H - 50)[:, None, None]
for x in (20, 200, 380, W - 28): a[40:H - 10, x:x + 8] = (150, 152, 155)
a[40:50] = (150, 152, 155)
# shelves / goods glimpsed inside
for y in (200, 300, 400):
    a[y:y + 6, 30:W - 30] = (90, 84, 70)
    for x in range(36, W - 40, 18): a[y - 30:y, x:x + 12] = rng.integers(60, 200, 3)
a[40:H - 10, 20:W - 20] = a[40:H - 10, 20:W - 20] * .75 + 255 * .25 * np.linspace(.6, 0, W - 40)[None, :, None]   # reflection
finish(a, 'storefront.jpg')
# 옥상 녹색 방수 페인트
R = 1024; a = np.ones((R, R, 3), np.float32) * np.array((86, 128, 92), np.float32)
a *= (1 + noise(R, R, 40, .12) + noise(R, R, 4, .05))[..., None]
st = np.clip(noise(R, R, 90, 1.0) - .4, 0, 1)[..., None]; a = a * (1 - st * .45) + np.array((120, 112, 96)) * st * .45
img = Image.fromarray(np.clip(a, 0, 255).astype(np.uint8)); d = ImageDraw.Draw(img)
for _ in range(40):
    x, y = rng.integers(0, R, 2); pts = [(int(x), int(y))]
    for _ in range(8): x += rng.integers(-40, 40); y += rng.integers(-40, 40); pts.append((int(x), int(y)))
    d.line(pts, fill=(60, 70, 58), width=2)
img.save(os.path.join(D, 'roof_flat.jpg'), quality=88); print('roof_flat.jpg')

SIGNS = ['연무식당', '훈련소 면회식당', '군장점', '충남약국', '논산정육점', '연무PC방', '코인노래방', '편의점', '연무부동산', '헤어샵 미소',
         '철물건재', '연무다방', '세탁소', '치킨나라', '국밥집', '전파사', '면회 숙박', '모텔 연무', '농협', '분식', '안경원', '꽃집',
         '이발소', '중국집 만리장성', '태권도', '피아노 학원', '수선집', '마트', '카페', '휴대폰', '빵집', '한의원',
         '연무읍사무소', '연무지구대', '하나로마트', '연무대고속터미널', '우체국', '보건지소', '연무초등학교', '연무교회']
COLS_CIVIC = [(30, 60, 120)]
COLS = [(176, 30, 36), (24, 72, 150), (24, 110, 64), (210, 120, 20), (90, 40, 120), (30, 30, 34), (200, 170, 20), (0, 120, 140)]
CW, CH = 512, 128; atlas = Image.new('RGB', (CW * 4, CH * 10), (40, 40, 40)); f = ImageFont.truetype(FONT, 78)
for i, t in enumerate(SIGNS):
    c = COLS[i % len(COLS)] if i < 32 else (28, 64, 124); cell = Image.new('RGB', (CW, CH), c); dd = ImageDraw.Draw(cell)
    dd.rectangle([4, 4, CW - 5, CH - 5], outline=(255, 255, 255), width=4)
    size = 78
    while True:
        f_ = ImageFont.truetype(FONT, size); bb = dd.textbbox((0, 0), t, font=f_)
        if bb[2] - bb[0] < CW - 40 or size < 30: break
        size -= 4
    fg = (20, 20, 20) if sum(c) > 400 else (255, 255, 255)
    dd.text(((CW - (bb[2] - bb[0])) // 2 - bb[0], (CH - (bb[3] - bb[1])) // 2 - bb[1]), t, font=f_, fill=fg)
    atlas.paste(cell, ((i % 4) * CW, (i // 4) * CH))
atlas.save(os.path.join(D, 'signs.png')); print('signs.png', len(SIGNS))
