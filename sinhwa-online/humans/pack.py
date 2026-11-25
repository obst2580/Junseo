"""python3 pack.py <src_dir> <gltf_name> <pub_dir> : glTF -> artifact-friendly JSON pack + material extras"""
import json, base64, os, sys, shutil, subprocess
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image
src, name, dst = sys.argv[1], sys.argv[2], sys.argv[3]
SKIN = {    # face weathering per character: (tan, spots)
  'ijang': (.55, .6),
  'halmeoni': (.38, .8),
  'choi': (.3, .3), 'minseok': (.15, .25), 'anjun': (.05, .05), 'jinwook': (.05, .05), 'parkjin': (.08, .08), 'woochan': (.12, .06), 'police': (.25, .15), 'syuper': (.3, .4), 'sangsa': (.55, .5),
}
GRAY_HAIR = {  # recolour hair textures (keeps strand contrast and alpha)
  'halmeoni': (196, 190, 180), 'choi': (30, 28, 28), 'minseok': (40, 34, 30), 'police': (30, 28, 27), 'syuper': (62, 42, 34), 'anjun': (24, 22, 21), 'jinwook': (26, 23, 21), 'parkjin': (28, 25, 23), 'woochan': (24, 22, 21),
}
POSE = {       # per-character standing pose, applied in the viewer (radians, + = bend forward)
  'halmeoni': {'spine_01': .2, 'spine_02': .14, 'spine_03': .06, 'neck_01': -.2, 'head': -.18},
}
TINTS = {   # multiply colours for grey-authored MakeHuman textures (per character)
  'ijang': {'hair': '#3b3732', 'eyelashes': '#1c1816'},
  'halmeoni': {'eyelashes': '#2a2420'},
  **{k: {'eyelashes': '#1c1816'} for k in ('choi', 'minseok', 'police', 'syuper', 'sangsa', 'anjun', 'jinwook', 'parkjin', 'woochan')},
}
os.makedirs(os.path.join(dst, 'tex'), exist_ok=True)
_ph = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'photos', name + '.json')
if os.path.exists(_ph) and json.load(open(_ph)).get('fit'):   # opt-in (experimental): characters modelled on a photo: reshape the head to the photo's proportions first
    import subprocess as _sp; _sp.run([sys.executable, os.path.join(os.path.dirname(os.path.abspath(__file__)), 'photo_face.py'), src, name, '--fit', _ph], check=True)
g = json.load(open(os.path.join(src, name + '.gltf')))
orig_name = {}
for im in g['images']:
    orig_name[id(im)] = os.path.basename(im['uri'])
    p = os.path.join(src, im['uri']); base, ext = os.path.splitext(im['uri'])
    img = Image.open(p)
    if img.mode == 'RGBA' and img.getextrema()[3][0] < 250 and '_normal' not in base:
        img.save(os.path.join(dst, im['uri']), optimize=True)
    else:
        img.convert('RGB').save(os.path.join(dst, base + '.jpg'), quality=88, optimize=True); im['uri'] = base + '.jpg'; im['mimeType'] = 'image/jpeg'
# costume textures: recipes over the original fold shading (see costume_tex.py / blender/costume.py)
cpath = os.path.join(src, 'costume.json'); costume = json.load(open(cpath)) if os.path.exists(cpath) else None
if costume:
    import costume_tex
    for im in g['images']:
        on = orig_name[id(im)]; outp = os.path.join(dst, im['uri'])
        if on in costume['tex_jobs']:
            j = costume['tex_jobs'][on]; costume_tex.build((j['kind'], j['spec'], on), os.path.join(src, 'tex'), outp, costume, name)
        elif on in costume['recipes']:
            costume_tex.build(('piece', costume['recipes'][on]), os.path.join(src, 'tex'), outp, costume, name)
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
            out = Image.fromarray(np.clip(np.dstack([rgb, a[..., 3:]]) * 255, 0, 255).astype(np.uint8)); (out.convert('RGB') if p.endswith('.jpg') else out).save(p); print('grey hair', im['uri'])
# solid low-poly hair (culturalibre_*): strand texture + frayed alpha so the clumps read as locks of hair
for m in g['materials']:
    if 'culturalibre_hair' in m['name']:
        pbr = m.setdefault('pbrMetallicRoughness', {})
        if 'baseColorTexture' not in pbr:   # some of these ship with a flat colour only: add a texture slot
            g.setdefault('images', []).append({'uri': ''}); g.setdefault('textures', []).append({'source': len(g['images']) - 1})
            pbr['baseColorTexture'] = {'index': len(g['textures']) - 1}
        pbr['baseColorFactor'] = [1, 1, 1, 1]
        im = g['images'][g['textures'][pbr['baseColorTexture']['index']]['source']]
        col = '#%02x%02x%02x' % tuple(int(min(255, c * 1.7)) for c in GRAY_HAIR.get(name, (30, 27, 25))); outp = os.path.join('tex', m['name'].split('.')[-1] + '_strands.png')
        subprocess.run([sys.executable, os.path.join(here, 'hair_strands.py'), os.path.join(src, name + '.gltf'), os.path.join(src, g['buffers'][0]['uri']),
                        m['name'].split('.')[-1].replace('culturalibre_', ''), os.path.join(dst, outp), col], check=True)
        im['uri'] = outp; im['mimeType'] = 'image/png'; m['alphaMode'] = 'BLEND'; m['doubleSided'] = True
        m['pbrMetallicRoughness']['roughnessFactor'] = .6; m.pop('extensions', None)
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
        extra = []
        if costume and costume.get('skin_regions'):
            rp = os.path.join(src, 'skin_regions.json'); json.dump(costume['skin_regions'], open(rp, 'w')); extra = ['7', rp]
        subprocess.run([sys.executable, os.path.join(here, 'skin_detail.py'), dif, os.path.join(dst, skin_h), dif, *map(str, SKIN[name]), *extra], check=True)
        ph = os.path.join(here, 'photos', name + '.json')   # characters modelled on a real person: project the front photo onto the face
        if os.path.exists(ph): subprocess.run([sys.executable, os.path.join(here, 'photo_face.py'), src, name, dif, ph,
                                               os.path.join(dst, 'tex', 'face_photo.png'), os.path.join(src, 'face_sticker.npz')], check=True)
# face sticker (characters modelled on a photo): append the photo shell as a skinned mesh bound to the head bone,
# and drop the modelled brows / lashes (the photo carries the real ones)
stp = os.path.join(src, 'face_sticker.npz')
if os.path.exists(os.path.join(here, 'photos', name + '.json')) and os.path.exists(stp):
    import numpy as np
    PSPEC = json.load(open(os.path.join(here, 'photos', name + '.json')))
    S_ = np.load(stp); n = len(S_['pos'])
    raw = bytearray(raw)
    def add_view(arr, target=None):
        while len(raw) % 4: raw.append(0)
        off = len(raw); b_ = np.ascontiguousarray(arr).tobytes(); raw.extend(b_)
        bv = {'buffer': 0, 'byteOffset': off, 'byteLength': len(b_)}
        if target: bv['target'] = target
        g['bufferViews'].append(bv); return len(g['bufferViews']) - 1
    def add_acc(arr, ctype, typ, target, minmax=False):
        a_ = {'bufferView': add_view(arr, target), 'componentType': ctype, 'count': int(arr.shape[0]), 'type': typ}
        if minmax: a_['min'] = arr.min(0).tolist(); a_['max'] = arr.max(0).tolist()
        g['accessors'].append(a_); return len(g['accessors']) - 1
    joints = g['skins'][0]['joints']; hj = next(i for i, j in enumerate(joints) if g['nodes'][j].get('name') == 'head')
    attrs = {'POSITION': add_acc(S_['pos'], 5126, 'VEC3', 34962, True), 'NORMAL': add_acc(S_['nrm'], 5126, 'VEC3', 34962),
             'TEXCOORD_0': add_acc(S_['uv'], 5126, 'VEC2', 34962), 'COLOR_0': add_acc(S_['col'], 5126, 'VEC4', 34962),
             'JOINTS_0': add_acc(np.tile(np.array([hj, 0, 0, 0], np.uint16), (n, 1)), 5123, 'VEC4', 34962),
             'WEIGHTS_0': add_acc(np.tile(np.array([1, 0, 0, 0], np.float32), (n, 1)), 5126, 'VEC4', 34962)}
    ia = add_acc(S_['idx'].reshape(-1), 5125, 'SCALAR', 34963)
    g['images'].append({'uri': 'tex/face_photo.png', 'mimeType': 'image/png'}); g['textures'].append({'source': len(g['images']) - 1, **({'sampler': 0} if g.get('samplers') else {})})
    g['materials'].append({'name': 'Human.facephoto', 'alphaMode': 'BLEND', 'doubleSided': False,
                           'pbrMetallicRoughness': {'baseColorTexture': {'index': len(g['textures']) - 1}, 'metallicFactor': 0, 'roughnessFactor': .62}})
    g['meshes'].append({'name': 'facephoto', 'primitives': [{'attributes': attrs, 'indices': ia, 'material': len(g['materials']) - 1}]})
    g['nodes'].append({'name': 'Human.facephoto', 'mesh': len(g['meshes']) - 1, 'skin': 0})
    sc = g['scenes'][g.get('scene', 0)]['nodes']; sc.append(len(g['nodes']) - 1)
    for i_, nd in enumerate(g['nodes']):
        if 'mesh' in nd and any(k in g['meshes'][nd['mesh']]['name'] for k in (('eyelash',) if PSPEC.get('keep_brows') else ('eyebrow', 'eyelash'))):
            if i_ in sc: sc.remove(i_)
            for pn in g['nodes']:
                if i_ in pn.get('children', []): pn['children'].remove(i_)
    g['buffers'][0]['byteLength'] = len(raw)
    json.dump(g, open(os.path.join(dst, 'human.json'), 'w'))
    json.dump({'data': base64.b64encode(bytes(raw)).decode()}, open(os.path.join(dst, 'human_bin.json'), 'w'))
    print('face sticker merged', n)
json.dump({'normals': normals, 'tints': tint, 'skinHeight': skin_h, 'pose': POSE.get(name, {})}, open(os.path.join(dst, 'extras.json'), 'w'))
print('normals', normals); print('tints', tint)
