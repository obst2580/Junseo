"""python3 pack.py <src_dir> <gltf_name> <pub_dir> : glTF -> artifact-friendly JSON pack + material extras"""
import json, base64, os, sys, shutil
from PIL import Image
src, name, dst = sys.argv[1], sys.argv[2], sys.argv[3]
SKIN = {    # face weathering per character: (tan, spots)
  'ijang': (.55, .6),
  'halmeoni': (.38, .8),
}
GRAY_HAIR = {  # recolour dark hair textures to grey (keeps strand contrast and alpha)
  'halmeoni': (196, 190, 180),
}
POSE = {       # per-character standing pose, applied in the viewer (radians, + = bend forward)
  'halmeoni': {'spine_01': .2, 'spine_02': .14, 'spine_03': .06, 'neck_01': -.2, 'head': -.18},
}
TINTS = {   # multiply colours for grey-authored MakeHuman textures (per character)
  'ijang': {'hair': '#3b3732', 'eyelashes': '#1c1816'},
  'halmeoni': {'eyelashes': '#2a2420'},
}
os.makedirs(os.path.join(dst, 'tex'), exist_ok=True)
g = json.load(open(os.path.join(src, name + '.gltf')))
for im in g['images']:
    p = os.path.join(src, im['uri']); base, ext = os.path.splitext(im['uri'])
    img = Image.open(p)
    if img.mode == 'RGBA' and img.getextrema()[3][0] < 250 and '_normal' not in base:
        img.save(os.path.join(dst, im['uri']), optimize=True)
    else:
        img.convert('RGB').save(os.path.join(dst, base + '.jpg'), quality=88, optimize=True); im['uri'] = base + '.jpg'; im['mimeType'] = 'image/jpeg'
# replace MakeHuman's red-brown iris with a dark Korean-brown eye texture (same layout)
here = os.path.dirname(os.path.abspath(__file__))
for im in g['images']:
    if 'brown_eye' in im['uri'] and os.path.exists(os.path.join(here, 'eye_dark_rgba.png')):
        shutil.copy(os.path.join(here, 'eye_dark_rgba.png'), os.path.join(dst, im['uri']))
if name in GRAY_HAIR:
    import numpy as np
    for m in g['materials']:
        if any(h in m['name'] for h in ('short', 'long', 'bob', 'braid', 'ponytail', 'afro', 'hair')) and 'baseColorTexture' in m.get('pbrMetallicRoughness', {}):
            im = g['images'][g['textures'][m['pbrMetallicRoughness']['baseColorTexture']['index']]['source']]
            p = os.path.join(dst, im['uri']); img = Image.open(p).convert('RGBA'); a = np.asarray(img).astype(np.float32) / 255
            L = a[..., :3] @ np.array([.3, .59, .11]); lo, hi = np.percentile(L[a[..., 3] > .5], (5, 95))
            t = np.clip((L - lo) / max(hi - lo, 1e-3), 0, 1)[..., None]
            rgb = np.array(GRAY_HAIR[name]) / 255 * (.55 + .55 * t)
            Image.fromarray(np.clip(np.dstack([rgb, a[..., 3:]]) * 255, 0, 255).astype(np.uint8)).save(p); print('grey hair', im['uri'])
raw = open(os.path.join(src, g['buffers'][0]['uri']), 'rb').read(); del g['buffers'][0]['uri']
json.dump(g, open(os.path.join(dst, 'human.json'), 'w'))
json.dump({'data': base64.b64encode(raw).decode()}, open(os.path.join(dst, 'human_bin.json'), 'w'))
normals = {}
for m in g['materials']:
    short = m['name'].split('.', 1)[-1]
    for f in os.listdir(os.path.join(dst, 'tex')):
        if f.startswith(short) and '_normal' in f: normals[m['name']] = 'tex/' + f
tint = {}
for m in g['materials']:
    for key, col in TINTS.get(name, {}).items():
        if key in m['name'].lower() or (key == 'hair' and any(h in m['name'] for h in ('short', 'long', 'bob', 'braid', 'ponytail'))):
            tint[m['name']] = col
# skin detail: wrinkle/pore height map (bumpMap in the viewer) + weathered face diffuse
import subprocess
skin_h = None
for m in g['materials']:
    if m['name'].endswith('.body') and name in SKIN:
        im = g['images'][g['textures'][m['pbrMetallicRoughness']['baseColorTexture']['index']]['source']]
        dif = os.path.join(dst, im['uri']); skin_h = 'tex/skin_height.jpg'
        subprocess.run([sys.executable, os.path.join(here, 'skin_detail.py'), dif, os.path.join(dst, skin_h), dif, *map(str, SKIN[name])], check=True)
json.dump({'normals': normals, 'tints': tint, 'skinHeight': skin_h, 'pose': POSE.get(name, {})}, open(os.path.join(dst, 'extras.json'), 'w'))
print('normals', normals); print('tints', tint)
