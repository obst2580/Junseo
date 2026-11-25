"""
전입신고서 커스터마이징용 기본 몸 빌드 (성별별 1개).
  - 몸: MakeHuman 기본 몸 + 슬라이더별 모프 타깃(얼굴 17종 양방향, 나이·체격·체중 양방향), 서브디비전 1단계까지 구워 넣음
  - 따라가는 파츠: 눈·눈썹·속눈썹·이·혀·머리 스타일·옷·신발 — 같은 슬라이더 모프를 MHCLO 맞춤식으로 계산해 넣음
  - 직접 만든 파츠: 후드, 야구모자, 안경, 마스크(얼굴 모프를 따라감)
  - 몸 숨김: 옷마다 가리는 몸 꼭짓점을 _hide_<옷> 속성으로 내보냄 (뷰어 셰이더에서 입은 옷만 숨김)
  - 옷 UV 분류(하의/상의)를 custom_uvclass.json 으로 내보냄 (뷰어에서 슬롯별 색 칠하기용 마스크 생성)
Run: BLENDER_USER_EXTENSIONS=<extroot> BLENDER_USER_CONFIG=<cfg> blender -b --factory-startup -P build_custom.py -- <m|f> <out.gltf>
"""
import bpy, bmesh, sys, os, math, json, addon_utils, numpy as np
from mathutils import Vector

argv = sys.argv[sys.argv.index('--') + 1:]
GENDER, OUT = argv[0], argv[1]
addon_utils.enable('bl_ext.user_default.mpfb', default_set=True, persistent=False)
bpy.ops.wm.read_factory_settings(use_empty=True)
addon_utils.enable('bl_ext.user_default.mpfb', default_set=True, persistent=False)
from bl_ext.user_default.mpfb.services.humanservice import HumanService
from bl_ext.user_default.mpfb.services.targetservice import TargetService
from bl_ext.user_default.mpfb.services.locationservice import LocationService
from bl_ext.user_default.mpfb.entities.clothes.mhclo import Mhclo
try:
    from bl_ext.user_default.mpfb.entities.objectproperties import GeneralObjectProperties
except Exception:
    GeneralObjectProperties = None

DATA = LocationService.get_user_data()
def asset(kind, name, ext):
    d = os.path.join(DATA, kind, name)
    return next(os.path.join(d, f) for f in os.listdir(d) if f.endswith(ext))
def age(y): return 0.5 + (y - 25) / 65 * 0.5 if y >= 25 else y / 25 * 0.5

G = 1.0 if GENDER == 'm' else 0.0
MACRO = dict(gender=G, age=age(35), muscle=.5, weight=.5, proportions=.5, height=.5, cupsize=.5, firmness=.5,
             race=dict(asian=1.0, caucasian=0.0, african=0.0))
SKIN = 'middleage_asian_male' if G else 'middleage_asian_female'
HAIRS = ['short01', 'short02', 'short04', 'culturalibre_hair_02', 'bob02', 'toigo_curled_under_bob', 'long01', 'ponytail01']
CLOTHES = ['male_casualsuit01', 'male_casualsuit04', 'male_elegantsuit01', 'male_casualsuit03']
SHOES = ['shoes02', 'shoes03', 'shoes05', 'shoes06']

# slider -> (negative targets, positive targets); 'lr-' = both sides
FACE = {
    'eye_size': (['lr-eye-scale-decr'], ['lr-eye-scale-incr']),
    'eye_gap': (['lr-eye-trans-in'], ['lr-eye-trans-out']),
    'eye_tilt': (['lr-eye-corner2-down'], ['lr-eye-corner2-up']),
    'eye_open': (['lr-eye-height2-decr'], ['lr-eye-height2-incr']),
    'eye_bag': (['lr-eye-bag-decr'], ['lr-eye-bag-incr']),
    'brow': (['eyebrows-trans-down'], ['eyebrows-trans-up']),
    'nose_len': (['nose-scale-vert-decr'], ['nose-scale-vert-incr']),
    'nose_w': (['nose-scale-horiz-decr'], ['nose-scale-horiz-incr']),
    'nose_tip': (['nose-point-down'], ['nose-point-up']),
    'mouth_w': (['mouth-scale-horiz-decr'], ['mouth-scale-horiz-incr']),
    'lips': (['mouth-upperlip-volume-decr', 'mouth-lowerlip-volume-decr'], ['mouth-upperlip-volume-incr', 'mouth-lowerlip-volume-incr']),
    'jaw': (['chin-width-decr'], ['chin-width-incr']),
    'chin': (['chin-prominent-decr'], ['chin-prominent-incr']),
    'cheek': (['lr-cheek-bones-decr'], ['lr-cheek-bones-incr']),
    'face_shape': (['head-round'], ['head-square']),
    'face_fat': (['head-fat-decr'], ['head-fat-incr']),
    'face_len': (['head-scale-vert-decr'], ['head-scale-vert-incr']),
    'ears': (['lr-ear-scale-decr'], ['lr-ear-scale-incr']),
}
BODY = {'age': ('age', age(22), age(72)), 'muscle': ('muscle', .15, .95), 'weight': ('weight', .2, 1.0)}

human = HumanService.create_human(macro_detail_dict=MACRO, scale=0.1)
HumanService.add_builtin_rig(human, 'game_engine', import_weights=True)
HumanService.set_character_skin(asset('skins', SKIN, '.mhmat'), human, skin_type='GAMEENGINE')
proxies = {}   # name -> (object, mhclo path, kind)
def add(kind, name, folder, subdiv=0):
    before = {o.name for o in bpy.context.scene.objects}
    p = asset(folder, name, '.mhclo')
    HumanService.add_mhclo_asset(p, human, asset_type=kind, subdiv_levels=subdiv, material_type='MAKESKIN')
    new = [o for o in bpy.context.scene.objects if o.name not in before and o.type == 'MESH']
    proxies[name] = (new[0], p, kind); new[0].name = 'P.' + name
for kind, name, folder in [('Eyes', 'high-poly', 'eyes'), ('Eyebrows', 'eyebrow009', 'eyebrows'), ('Eyelashes', 'eyelashes01', 'eyelashes'),
                           ('Teeth', 'teeth_base', 'teeth'), ('Tongue', 'tongue01', 'tongue')]:
    add(kind, name, folder)
for h in HAIRS: add('Hair', h, 'hair')
for c in CLOTHES + SHOES: add('Clothes', c, 'clothes')
arm = [o for o in bpy.context.scene.objects if o.type == 'ARMATURE'][0]

# ---------------------------------------------------------------- body positions for every slider direction
def mix_positions(obj):
    k = obj.shape_key_add(name='__mix', from_mix=True); n = len(k.data)
    a = np.empty(n * 3, np.float32); k.data.foreach_get('co', a); obj.shape_key_remove(k); return a.reshape(-1, 3)
P0 = mix_positions(human)
basis = human.data.shape_keys.key_blocks[0]
B0 = np.empty(len(P0) * 3, np.float32); basis.data.foreach_get('co', B0); B0 = B0.reshape(-1, 3)
deltas = {}
def target_delta(tname):
    names = ['l-' + tname[3:], 'r-' + tname[3:]] if tname.startswith('lr-') else [tname]; d = np.zeros_like(P0)
    for n in names:
        path = TargetService.target_full_path(n)
        if not path: print('MISSING TARGET', n); continue
        TargetService.load_target(human, path, weight=1.0, name='__t')
        kb = human.data.shape_keys.key_blocks['__t']; a = np.empty(len(P0) * 3, np.float32); kb.data.foreach_get('co', a)
        d += a.reshape(-1, 3) - B0; human.shape_key_remove(kb)
    return d
for s, (neg, pos) in FACE.items():
    deltas[s + '-'] = sum(target_delta(t) for t in neg); deltas[s + '+'] = sum(target_delta(t) for t in pos)
for s, (key, lo, hi) in BODY.items():
    for sign, v in (('-', lo), ('+', hi)):
        m = dict(MACRO); m[key] = v
        tmp = HumanService.create_human(macro_detail_dict=m, scale=0.1)
        deltas[s + sign] = mix_positions(tmp) - P0
        bpy.data.objects.remove(tmp, do_unlink=True)
MORPHS = list(deltas.keys())
print('MORPHS', len(MORPHS), {k: round(float(np.abs(v).max()), 4) for k, v in deltas.items()})

# ---------------------------------------------------------------- refit every MHCLO proxy for each morph
scale_factor = (GeneralObjectProperties.get_value('scale_factor', entity_reference=human) if GeneralObjectProperties else None) or 0.1
def fit(mh, P):
    idx = np.array([mh.verts[i]['verts'] for i in range(len(mh.verts))]); w = np.array([mh.verts[i]['weights'] for i in range(len(mh.verts))])
    off = np.array([tuple(mh.verts[i]['offsets']) for i in range(len(mh.verts))], np.float32)
    xs = ys = zs = scale_factor
    if mh.x_scale:
        xs = abs(P[mh.x_scale[0], 0] - P[mh.x_scale[1], 0]) / mh.x_scale[2]
        ys = abs(P[mh.y_scale[0], 2] - P[mh.y_scale[1], 2]) / mh.y_scale[2]
        zs = abs(P[mh.z_scale[0], 1] - P[mh.z_scale[1], 1]) / mh.z_scale[2]
    return (P[idx] * w[..., None]).sum(1) + off * np.array([xs, zs, ys], np.float32)
def set_keys(obj, base, keys):
    if obj.data.shape_keys: obj.shape_key_clear()
    obj.data.vertices.foreach_set('co', base.astype(np.float32).ravel())
    obj.shape_key_add(name='Basis', from_mix=False)
    for n, co in keys.items():
        k = obj.shape_key_add(name=n, from_mix=False); k.data.foreach_set('co', co.astype(np.float32).ravel()); k.value = 0
MW3 = human.matrix_world.to_3x3()
def region_shift(sel):
    return {m: np.array(MW3 @ Vector(deltas[m][sel].mean(0))) for m in MORPHS}
W0 = np.array([human.matrix_world @ Vector(p) for p in P0])
def group_sel(gname, thr=.5):
    gi = human.vertex_groups[gname].index
    return np.array([any(g.group == gi and g.weight > thr for g in v.groups) for v in human.data.vertices])
hsel = group_sel('head'); top_sel = hsel & (W0[:, 2] > W0[hsel, 2].max() - .05)
SHIFT = {'eyes': {}, 'skull': region_shift(top_sel), 'neck': region_shift(group_sel('neck_01'))}
EPS = 5e-4   # local units (dm): smaller moves are noise and only defeat sparse morph storage
for m in MORPHS: deltas[m][np.abs(deltas[m]).max(1) < EPS] = 0
delete_sets = {}
for name, (obj, path, kind) in proxies.items():
    mh = Mhclo(); mh.load(path)
    f0 = fit(mh, P0)
    cur = np.empty(len(obj.data.vertices) * 3, np.float32); obj.data.vertices.foreach_get('co', cur)
    if obj.data.shape_keys:   # MPFB fitted the proxy into its basis/mix: compare against what is shown
        cur = mix_positions(obj).ravel()
    err = float(np.abs(cur.reshape(-1, 3) - f0).max()) if len(cur) == f0.size else -1
    print('FIT', name, len(f0), 'err', round(err, 5))
    keys = {}
    for m in MORPHS:
        if kind == 'Clothes' and m.rstrip('+-') not in BODY: keys[m] = f0; continue   # garments follow body sliders only
        d = fit(mh, P0 + deltas[m]) - f0; d[np.abs(d).max(1) < EPS] = 0; keys[m] = f0 + d
        if name == 'high-poly': SHIFT['eyes'][m] = np.array(human.matrix_world.to_3x3() @ Vector(d.mean(0)))
    set_keys(obj, f0, keys)
    if kind == 'Clothes' and mh.delverts: delete_sets[name] = sorted(set(mh.delverts))

# body: freeze the macro mix as the basis, then the slider keys on top
set_keys(human, P0, {m: P0 + deltas[m] for m in MORPHS})
for name, vs in delete_sets.items():   # per-garment "covered skin" flags, interpolated by subdivision, thresholded in the viewer
    a = human.data.attributes.new('_hide_' + name.replace('male_', '').replace('shoes', 'sh'), 'FLOAT', 'POINT')
    vals = np.zeros(len(human.data.vertices), np.float32); vals[vs] = 1; a.data.foreach_set('value', vals)
print('HIDE attrs', list(delete_sets))

# drop MakeHuman helper geometry (tights/skirt/hair helpers, joint cubes): keep only the 'body' vertex group
bg = human.vertex_groups.get('body')
if bg:
    keep = np.zeros(len(human.data.vertices), bool)
    for v in human.data.vertices:
        if any(g.group == bg.index and g.weight > .5 for g in v.groups): keep[v.index] = True
    bm = bmesh.new(); bm.from_mesh(human.data); bm.verts.ensure_lookup_table()
    bmesh.ops.delete(bm, geom=[bm.verts[i] for i in np.where(~keep)[0]], context='VERTS'); bm.to_mesh(human.data); bm.free()
    print('helpers removed', int((~keep).sum()))
for m in list(human.modifiers):
    if m.type == 'MASK': human.modifiers.remove(m)

# ---------------------------------------------------------------- bake one subdivision level into mesh + all shape keys
def bake_subdiv(obj, levels=1):
    for m in list(obj.modifiers):
        if m.type in ('SUBSURF', 'MASK'): obj.modifiers.remove(m)
    sub = obj.modifiers.new('bake_sub', 'SUBSURF'); sub.levels = levels; sub.render_levels = levels
    am = [m for m in obj.modifiers if m.type == 'ARMATURE']
    for m in am: m.show_viewport = False
    kbs = obj.data.shape_keys.key_blocks; names = [k.name for k in kbs][1:]
    dg = bpy.context.evaluated_depsgraph_get()
    def ev():
        dg.update(); e = obj.evaluated_get(dg); me = e.to_mesh(); a = np.empty(len(me.vertices) * 3, np.float32); me.vertices.foreach_get('co', a); e.to_mesh_clear(); return a
    for k in names: kbs[k].value = 0
    base = ev(); coords = {}
    for k in names:
        kbs[k].value = 1; coords[k] = ev(); kbs[k].value = 0
    dg.update(); newme = bpy.data.meshes.new_from_object(obj.evaluated_get(dg), preserve_all_data_layers=True, depsgraph=dg)
    obj.modifiers.remove(sub)
    for m in am: m.show_viewport = True
    old = obj.data; obj.data = newme
    obj.shape_key_add(name='Basis', from_mix=False)
    for k in names:
        d = (coords[k] - base).reshape(-1, 3); d[np.abs(d).max(1) < EPS] = 0
        kb = obj.shape_key_add(name=k, from_mix=False); kb.data.foreach_set('co', (base.reshape(-1, 3) + d).ravel()); kb.value = 0
    print('SUBDIV', obj.name, len(old.vertices), '->', len(newme.vertices), 'groups', len(obj.vertex_groups),
          'weighted', sum(1 for v in newme.vertices if len(v.groups)) )

# ---------------------------------------------------------------- hand-made pieces (built in world space at the base shape)
def world(obj, P): mw = obj.matrix_world; return np.array([mw @ Vector(p) for p in P])
Wb = world(human, P0) if False else None
hco = np.array([human.matrix_world @ v.co for v in human.data.vertices])
hg = human.vertex_groups['head'].index
head_idx = [v.index for v in human.data.vertices if any(g.group == hg and g.weight > .5 for g in v.groups)]
headv = hco[head_idx]; skull_top = headv[:, 2].max()
eyes_o = proxies['high-poly'][0]; eyes = np.array([eyes_o.matrix_world @ v.co for v in eyes_o.data.vertices])
eye_z, eye_front_y = eyes[:, 2].mean(), eyes[:, 1].min(); eyeL, eyeR = eyes[eyes[:, 0] > 0], eyes[eyes[:, 0] < 0]
def bone_w(n): return arm.matrix_world @ arm.data.bones[n].head_local
def new_mat(name, hexcol, rough=.85, metal=0.):
    m = bpy.data.materials.new(name); m.use_nodes = True; bsdf = m.node_tree.nodes['Principled BSDF']
    h = hexcol.lstrip('#'); bsdf.inputs['Base Color'].default_value = (*[(int(h[i:i + 2], 16) / 255) ** 2.2 for i in (0, 2, 4)], 1)
    bsdf.inputs['Roughness'].default_value = rough; bsdf.inputs['Metallic'].default_value = metal; return m
def finish(name, bm, mat, bone='head', thick=0.):
    me = bpy.data.meshes.new(name); bm.to_mesh(me); bm.free(); ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob); me.materials.append(mat)
    if thick:
        bpy.context.view_layer.objects.active = ob; bpy.ops.object.select_all(action='DESELECT'); ob.select_set(True)
        s = ob.modifiers.new('t', 'SOLIDIFY'); s.thickness = thick; s.offset = 0; bpy.ops.object.modifier_apply(modifier='t')
    for p in me.polygons: p.use_smooth = True
    ob.parent = arm; ob.matrix_parent_inverse = arm.matrix_world.inverted()
    vg = ob.vertex_groups.new(name=bone); vg.add(list(range(len(me.vertices))), 1.0, 'REPLACE')
    ob.modifiers.new('Armature', 'ARMATURE').object = arm
    return ob

# baseball cap: dome over the skull, band at the forehead, stiff bill to the front (-Y)
zb = skull_top - .055
up = headv[headv[:, 2] > zb - .01]
cx, cy = (up[:, 0].min() + up[:, 0].max()) / 2, (up[:, 1].min() + up[:, 1].max()) / 2
rx, ry = (up[:, 0].max() - up[:, 0].min()) / 2 * 1.06 + .006, (up[:, 1].max() - up[:, 1].min()) / 2 * 1.06 + .006
bm = bmesh.new(); uvl = bm.loops.layers.uv.new('UVMap')
def ring(z, sx, sy, n=48, a0=0, a1=2 * math.pi, closed=True):
    return [bm.verts.new((cx + sx * math.cos(a0 + (a1 - a0) * i / n), cy + sy * math.sin(a0 + (a1 - a0) * i / n), z)) for i in range(n if closed else n + 1)]
def strip(r0, r1, v0, v1, closed=True):
    n = len(r0)
    for i in range(n if closed else n - 1):
        j = (i + 1) % n; f = bm.faces.new((r0[i], r0[j], r1[j], r1[i]))
        for lp, (uu, vv) in zip(f.loops, ((i, v0), (i + 1, v0), (i + 1, v1), (i, v1))): lp[uvl].uv = (uu / n * 4, vv)
ztop = skull_top + .012; rings = []
for t in np.linspace(0, 1, 8):
    a = t * math.pi / 2; rings.append(ring(ztop - (ztop - zb) * (1 - math.cos(a)) ** .9, rx * max(math.sin(a), .03) ** .8, ry * max(math.sin(a), .03) ** .8))
for k in range(7): strip(rings[k], rings[k + 1], k / 7, (k + 1) / 7)
front = [v for v in rings[-1] if v.co.y < cy]; front.sort(key=lambda v: v.co.x)
bill = [bm.verts.new((v.co.x * 1.05, v.co.y - .065 * (1 - ((v.co.x - cx) / rx) ** 2) ** .5 - .004, v.co.z - .008)) for v in front]
for i in range(len(front) - 1): bm.faces.new((front[i], front[i + 1], bill[i + 1], bill[i]))
finish('X.cap', bm, new_mat('X.cap', '#3d4a4a', .8), thick=.0025)

# glasses (thin frame; lens colour set in the viewer: clear or dark)
zf = eye_front_y - .014; lw, lh = .025, .018; lx, rxe = eyeL[:, 0].mean(), eyeR[:, 0].mean()
bm = bmesh.new(); rims = []
for cxe in (lx, rxe):
    n = 28; ctr = bm.verts.new((cxe, zf, eye_z)); rim = []
    for i in range(n):
        a = 2 * math.pi * i / n; c, s = math.cos(a), math.sin(a)
        x = cxe + lw * np.sign(c) * abs(c) ** .7; z = eye_z + .002 + lh * np.sign(s) * abs(s) ** .8
        rim.append(bm.verts.new((x, zf + .004 * ((x - cxe) / lw) ** 2, z)))
    for i in range(n): bm.faces.new((rim[i], rim[(i + 1) % n], ctr))
    rims.append([v.co.copy() for v in rim])
finish('X.lens', bm, new_mat('X.lens', '#dfe6ea', .05))
band = headv[(np.abs(headv[:, 2] - eye_z) < .012) & (headv[:, 1] < eye_front_y + .05)]
half = min(np.abs(band[:, 0]).max() + .004, max(abs(lx), abs(rxe)) + lw + .012)
bm = bmesh.new()
def bar(p0, p1, r=.0016):
    p0, p1 = Vector(p0), Vector(p1); d = (p1 - p0).normalized(); a = d.orthogonal().normalized(); b = d.cross(a)
    r0 = [bm.verts.new(p0 + (a * math.cos(t) + b * math.sin(t)) * r) for t in np.linspace(0, 2 * math.pi, 7)[:-1]]
    r1 = [bm.verts.new(p1 + (a * math.cos(t) + b * math.sin(t)) * r) for t in np.linspace(0, 2 * math.pi, 7)[:-1]]
    for i in range(6): bm.faces.new((r0[i], r0[(i + 1) % 6], r1[(i + 1) % 6], r1[i]))
for rim in rims:
    for i in range(len(rim)): bar(rim[i], rim[(i + 1) % len(rim)], .0015)
bar((lx - lw * .9, zf, eye_z + .012), (rxe + lw * .9, zf, eye_z + .012))
for sx, cxe in ((1, lx), (-1, rxe)):
    bar((cxe + sx * lw, zf, eye_z + .01), (sx * half, zf + .02, eye_z + .008))
    bar((sx * half, zf + .02, eye_z + .008), (sx * (half - .004), zf + .095, eye_z - .01))
finish('X.frame', bm, new_mat('X.frame', '#1b1b1b', .3, .3))

# hood bunched behind the neck
nk = bone_w('neck_01'); R, rt = .08, .042; cz = nk.z - .005
bm = bmesh.new(); uvq = bm.loops.layers.uv.new('UVMap'); rs = []; NA, NT = 20, 14
for i in range(NA + 1):
    a = math.radians(10 + 160 * i / NA); ax, ay = math.cos(a), math.sin(a); sw = 1 - .45 * abs(i / NA - .5) * 2
    rs.append([bm.verts.new((nk.x + (R + rt * sw * math.cos(b)) * ax, nk.y + .015 + (R + rt * sw * math.cos(b)) * ay * 1.1,
                             cz + rt * sw * 1.3 * math.sin(b))) for b in np.linspace(0, 2 * math.pi, NT + 1)[:-1]])
for i in range(NA):
    for j in range(NT):
        f = bm.faces.new((rs[i][j], rs[i][(j + 1) % NT], rs[i + 1][(j + 1) % NT], rs[i + 1][j]))
        for lp, (uu, vv) in zip(f.loops, ((i, j), (i, j + 1), (i + 1, j + 1), (i + 1, j))): lp[uvq].uv = (uu / NA * 2, vv / NT)
finish('X.hood', bm, new_mat('X.hood', '#6b6e74', .9), bone='neck_01')

# face mask: a copy of the lower-face skin pushed out 6 mm, keeps the face morphs so it follows the sliders
mask_faces = [f.index for f in human.data.polygons
              if (lambda c: eye_z - .125 < c[2] < eye_z - .022 and c[1] < eye_front_y + .055 and abs(c[0]) < .07)(hco[list(f.vertices)].mean(0))]
mo = human.copy(); mo.data = human.data.copy(); mo.name = 'X.mask'; bpy.context.scene.collection.objects.link(mo)
bm = bmesh.new(); bm.from_mesh(mo.data); bm.faces.ensure_lookup_table(); ks = set(mask_faces)
bmesh.ops.delete(bm, geom=[f for f in bm.faces if f.index not in ks], context='FACES'); bm.to_mesh(mo.data); bm.free()
k = 1 / mo.matrix_world.to_scale()[0]; nrm = np.array([v.normal for v in mo.data.vertices])
for kb in mo.data.shape_keys.key_blocks:
    a = np.empty(len(kb.data) * 3, np.float32); kb.data.foreach_get('co', a); kb.data.foreach_set('co', (a.reshape(-1, 3) + nrm * .006 * k).ravel())
mo.data.vertices.foreach_set('co', np.array([kb.co for kb in mo.data.shape_keys.key_blocks[0].data]).ravel().astype(np.float32))
for a in [a.name for a in mo.data.attributes if a.name.startswith('_hide_')]: mo.data.attributes.remove(mo.data.attributes[a])
mo.data.materials.clear(); mo.data.materials.append(new_mat('X.mask', '#2a2b2e', .9))
print('MASK faces', len(mask_faces))

def rigid_keys(obj, shifts):
    base = np.array([v.co for v in obj.data.vertices], np.float32); mi = obj.matrix_world.inverted().to_3x3()
    obj.shape_key_add(name='Basis', from_mix=False)
    for m in MORPHS:
        d = np.array(mi @ Vector(shifts.get(m, (0, 0, 0))), np.float32)
        k = obj.shape_key_add(name=m, from_mix=False); k.data.foreach_set('co', (base + d).ravel()); k.value = 0
for nm, reg in (('X.lens', 'eyes'), ('X.frame', 'eyes'), ('X.cap', 'skull'), ('X.hood', 'neck')):
    rigid_keys(bpy.data.objects[nm], SHIFT[reg])
print('RIGID shifts', {r: round(float(max(np.abs(v).max() for v in SHIFT[r].values())), 4) for r in SHIFT})

# ---------------------------------------------------------------- subdivide body, clothes and the mask (keys included)
bake_subdiv(human)
# garments stay at MakeHuman resolution (smooth shading is enough; keeps the download small)
bake_subdiv(mo)

# garment UV classes (bottom vs top) for slot colouring in the viewer
from bpy_extras import mesh_utils
LEG = ('thigh', 'calf', 'foot', 'ball')
uvclass = {}
for c in CLOTHES:
    o = proxies[c][0]; names = {g.index: g.name for g in o.vertex_groups}
    dom = [names.get(max(v.groups, key=lambda g: g.weight).group, '') if len(v.groups) else '' for v in o.data.vertices]
    fb = [False] * len(o.data.polygons)
    for isl in mesh_utils.mesh_linked_uv_islands(o.data):
        vs = {v for fi in isl for v in o.data.polygons[fi].vertices}
        legf = sum(dom[v].startswith(LEG) for v in vs) / max(len(vs), 1)
        for fi in isl: fb[fi] = legf > .45
    uvl = o.data.uv_layers.active.data
    uvclass[c] = [['bottom' if fb[f.index] else 'top', [[round(uvl[li].uv.x, 4), round(uvl[li].uv.y, 4)] for li in f.loop_indices]] for f in o.data.polygons]
json.dump({'uvclass': uvclass, 'morphs': MORPHS, 'hairs': HAIRS, 'clothes': CLOTHES, 'shoes': SHOES},
          open(os.path.join(os.path.dirname(OUT), 'custom_meta.json'), 'w'))

bpy.ops.object.select_all(action='SELECT')
bpy.ops.export_scene.gltf(filepath=OUT, export_format='GLTF_SEPARATE', use_selection=True, export_texture_dir='tex',
                          export_skins=True, export_animations=False, export_morph=True, export_morph_normal=False,
                          export_attributes=True, export_apply=False, export_image_format='AUTO')
print('DONE')
