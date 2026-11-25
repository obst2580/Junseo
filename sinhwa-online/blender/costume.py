"""
의상 빌더 (build_human.py 안에서 exec). 설정은 P['costume']:
  suit / shoes     : 기본 MakeHuman 옷 메쉬 이름 (부분 문자열)
  suit_tex/shoe_tex: 옷 텍스처 레시피 (pack.py 의 costume_tex.py 가 원본 주름 음영 위에 칠함)
  pieces           : [{kind: vest|armcover|boot|apron|hood, tex: 레시피, push, ...}]  옷을 복제·부풀려 만드는 조각
  hat              : {kind: farm|police, tex: 레시피, ...}   직접 모델링, 머리 뼈에 고정
  glasses          : {frame: '#hex', lens: '#hex'}
  regions          : ['beard']  피부 UV 영역을 뽑아 skin_detail.py 로 넘김 (수염 자국)
텍스처는 여기서 만들지 않는다. 조각 재질은 자리표시 이미지(<preset>_<piece>.png)를 참조하고,
pack.py 가 costume.json(레시피 + 상·하의 UV 분류)을 읽어 실제 텍스처로 덮어쓴다.
필요 변수: human, P, PRESET, OUT
"""
import bpy, bmesh, math, json, numpy as np
from mathutils import Vector
from bpy_extras import mesh_utils

C = P['costume']
PH_DIR = os.path.join(os.path.dirname(OUT), 'placeholders'); os.makedirs(PH_DIR, exist_ok=True)
arm = [o for o in bpy.context.scene.objects if o.type == 'ARMATURE'][0]
def find(part): return next(o for o in bpy.context.scene.objects if o.type == 'MESH' and part in o.name)
def bone_w(name, where='head'): b = arm.data.bones[name]; return arm.matrix_world @ (b.head_local if where == 'head' else b.tail_local)
recipes = {}   # image file name -> texture recipe (consumed by pack.py)

def placeholder(key, recipe):
    fn = f'{PRESET}_{key}.png'; p = os.path.join(PH_DIR, fn)
    img = bpy.data.images.new(fn, 8, 8); img.filepath_raw = p; img.file_format = 'PNG'; img.save()
    recipes[fn] = recipe; return bpy.data.images.load(p, check_existing=True)

def new_mat(key, recipe, rough=.85, metal=0., double=False):
    m = bpy.data.materials.new('Human.' + key); m.use_nodes = True; m.use_backface_culling = not double
    bsdf = m.node_tree.nodes['Principled BSDF']; bsdf.inputs['Roughness'].default_value = rough; bsdf.inputs['Metallic'].default_value = metal
    if recipe.startswith('color:'):
        h = recipe[6:].lstrip('#'); bsdf.inputs['Base Color'].default_value = (*[(int(h[i:i + 2], 16) / 255) ** 2.2 for i in (0, 2, 4)], 1)
    else:
        t = m.node_tree.nodes.new('ShaderNodeTexImage'); t.image = placeholder(key, recipe)
        m.node_tree.links.new(t.outputs['Color'], bsdf.inputs['Base Color'])
    return m

def vert_world(obj):
    """rest-pose world positions of the object's base mesh (shape keys mixed in, masks off so indices match)"""
    dg = bpy.context.evaluated_depsgraph_get()
    mods = [(m, m.show_viewport) for m in obj.modifiers if m.type in ('MASK', 'SUBSURF')]
    for m, _ in mods: m.show_viewport = False
    dg.update(); ev = obj.evaluated_get(dg); me = ev.to_mesh()
    co = np.array([ev.matrix_world @ v.co for v in me.vertices]); ev.to_mesh_clear()
    for m, s in mods: m.show_viewport = s
    return co

def dominant_bone(obj):
    names = {g.index: g.name for g in obj.vertex_groups}; out = []
    for v in obj.data.vertices:
        best = max(v.groups, key=lambda g: g.weight, default=None); out.append(names.get(best.group, '') if best else '')
    return out

def push(obj, dist_m):
    k = 1 / obj.matrix_world.to_scale()[0]; me = obj.data; nrm = [v.normal.copy() for v in me.vertices]
    if me.shape_keys:
        for kb in me.shape_keys.key_blocks:
            for i, n in enumerate(nrm): kb.data[i].co += n * dist_m * k
    for i, n in enumerate(nrm): me.vertices[i].co += n * dist_m * k

def duplicate(obj, name, keep_faces, mat):
    d = obj.copy(); d.data = obj.data.copy(); d.name = 'Human.' + name; bpy.context.scene.collection.objects.link(d)
    for m in list(d.modifiers):
        if m.type in ('MASK', 'SUBSURF'): d.modifiers.remove(m)
    bm = bmesh.new(); bm.from_mesh(d.data); bm.faces.ensure_lookup_table()
    bmesh.ops.delete(bm, geom=[f for f in bm.faces if not keep_faces[f.index]], context='FACES')
    bm.to_mesh(d.data); bm.free(); d.data.materials.clear(); d.data.materials.append(mat)
    return d

def cut(obj, planes, drop):
    """bisect by world planes [(point, normal)], then delete faces whose world centre satisfies drop(c)"""
    mw, mi = obj.matrix_world, obj.matrix_world.inverted()
    bm = bmesh.new(); bm.from_mesh(obj.data)
    for pco, pno in planes:
        bmesh.ops.bisect_plane(bm, geom=bm.verts[:] + bm.edges[:] + bm.faces[:], plane_co=mi @ Vector(pco),
                               plane_no=(mi.to_3x3() @ Vector(pno)).normalized())
    nm = mw.to_3x3().inverted().transposed()
    bmesh.ops.delete(bm, geom=[f for f in bm.faces if (drop(mw @ f.calc_center_median(), (nm @ f.normal).normalized())
                     if drop.__code__.co_argcount == 2 else drop(mw @ f.calc_center_median()))], context='FACES')
    bm.to_mesh(obj.data); bm.free()

def bind(obj, bone='head'):
    obj.parent = arm; obj.matrix_parent_inverse = arm.matrix_world.inverted()
    vg = obj.vertex_groups.new(name=bone); vg.add(list(range(len(obj.data.vertices))), 1.0, 'REPLACE')
    obj.modifiers.new('Armature', 'ARMATURE').object = arm

def finish_mesh(name, bm, mat, thick=0.):
    me = bpy.data.meshes.new('Human.' + name); bm.to_mesh(me); bm.free()
    ob = bpy.data.objects.new('Human.' + name, me); bpy.context.scene.collection.objects.link(ob); me.materials.append(mat)
    if thick:
        bpy.context.view_layer.objects.active = ob; bpy.ops.object.select_all(action='DESELECT'); ob.select_set(True)
        s = ob.modifiers.new('thick', 'SOLIDIFY'); s.thickness = thick; s.offset = 0; bpy.ops.object.modifier_apply(modifier='thick')
    for p in me.polygons: p.use_smooth = True
    return ob

def mixed_copy(obj):
    """pieces are cut with bisect, which drops shape keys: bake the current shape-key mix into the copy's base first"""
    if obj.data.shape_keys:
        mix = vert_world(obj); mi = obj.matrix_world.inverted(); obj.shape_key_clear()
        for i, v in enumerate(obj.data.vertices): v.co = mi @ Vector(mix[i])

# ---------------------------------------------------------------- suit: classify faces into bottoms / top by UV island
suit = find(C['suit']); shoes = find(C['shoes']) if C.get('shoes') else None
dom = dominant_bone(suit); co = vert_world(suit)
LEG = ('thigh', 'calf', 'foot', 'ball')
face_bottom = [False] * len(suit.data.polygons)
for isl in mesh_utils.mesh_linked_uv_islands(suit.data):
    vs = {v for fi in isl for v in suit.data.polygons[fi].vertices}
    legf = sum(dom[v].startswith(LEG) for v in vs) / max(len(vs), 1)
    for fi in isl: face_bottom[fi] = legf > .45
uvl = suit.data.uv_layers.active.data
uvclass = [['bottom' if face_bottom[f.index] else 'top', [[round(uvl[li].uv.x, 5), round(uvl[li].uv.y, 5)] for li in f.loop_indices]] for f in suit.data.polygons]

def diffuse_img(o):
    return next((n.image for m in o.data.materials for n in m.node_tree.nodes if n.type == 'TEX_IMAGE' and n.image and 'diffuse' in n.image.name.lower()), None)
tex_jobs = {}
if C.get('suit_tex'): tex_jobs[os.path.basename(diffuse_img(suit).filepath)] = dict(kind='suit', spec=C['suit_tex'])
if shoes and C.get('shoe_tex'): tex_jobs[os.path.basename(diffuse_img(shoes).filepath)] = dict(kind='shoe', spec=C['shoe_tex'])
suit_src = os.path.basename(diffuse_img(suit).filepath)

if C.get('loose'): push(suit, C['loose'])
ARMB = ('upperarm', 'lowerarm', 'hand', 'neck', 'head')
knee_z, chest_y, chest_z, pelvis_z = bone_w('calf_l').z, bone_w('spine_03').y, bone_w('spine_03').z, bone_w('pelvis').z
for pc in C.get('pieces', []):
    k = pc['kind']; name = pc.get('name', k)
    if k != 'hood': recipes_key = pc['tex'] + '|suitshade:' + suit_src   # tile over the suit's own UV layout, keep its fold shading
    mat = new_mat(name, pc['tex'] if k == 'hood' else recipes_key, pc.get('rough', .85), double=True)
    if k == 'vest':
        keep = [(not face_bottom[f.index]) and not any(dom[v].startswith(ARMB) for v in f.vertices) for f in suit.data.polygons]
        o = duplicate(suit, name, keep, mat); mixed_copy(o); gap = pc.get('gap', .05)
        if gap: cut(o, [((gap, 0, 0), (1, 0, 0)), ((-gap, 0, 0), (1, 0, 0))], lambda c: abs(c.x) < gap and c.y < chest_y)
    elif k == 'armcover':
        keep = [(not face_bottom[f.index]) and all(dom[v].startswith(('lowerarm', 'hand')) for v in f.vertices) for f in suit.data.polygons]
        o = duplicate(suit, name, keep, mat); mixed_copy(o)
    elif k == 'boot':
        top = knee_z - pc.get('drop', .05)
        keep = [face_bottom[f.index] and all(co[v][2] < top + .03 for v in f.vertices) for f in suit.data.polygons]
        o = duplicate(suit, name, keep, mat); mixed_copy(o); cut(o, [((0, 0, top), (0, 0, 1))], lambda c: c.z > top)
    elif k == 'apron':   # flat cloth panel hanging in front of the body (bridges the legs instead of hugging them)
        zt = chest_z + pc.get('top', .06) if pc.get('bib', True) else pelvis_z + .1
        zb_ = knee_z + pc.get('bottom', .08); w = pc.get('width', .17); off = pc.get('push', .02)
        NZ, NX = 24, 16; bm = bmesh.new(); uvq = bm.loops.layers.uv.new('UVMap'); grid = []
        front = co[np.abs(co[:, 0]) < w * .9]
        prof = []
        for i in range(NZ + 1):
            z = zt + (zb_ - zt) * i / NZ; band = front[np.abs(front[:, 2] - z) < .03]
            prof.append(band[:, 1].min() if len(band) else (prof[-1] if prof else chest_y - .12))
        for i in range(1, NZ + 1): prof[i] = min(prof[i], prof[i - 1] + .004)   # hangs: never tucks back in sharply below
        for i in range(NZ + 1):
            z = zt + (zb_ - zt) * i / NZ; row = []
            for j in range(NX + 1):
                x = -w + 2 * w * j / NX; row.append(bm.verts.new((x, prof[i] - off + 2.2 * x * x, z)))
            grid.append(row)
        for i in range(NZ):
            for j in range(NX):
                f = bm.faces.new((grid[i][j], grid[i][j + 1], grid[i + 1][j + 1], grid[i + 1][j]))
                for loop, (uu, vv) in zip(f.loops, ((j, i), (j + 1, i), (j + 1, i + 1), (j, i + 1))): loop[uvq].uv = (uu / NX, 1 - vv / NZ)
        o = finish_mesh(name, bm, new_mat(name, pc['tex'], pc.get('rough', .85), double=True), .002)
        # skin it: top rows follow the spine, lower rows blend toward the thighs
        o.parent = arm; o.matrix_parent_inverse = arm.matrix_world.inverted(); o.modifiers.new('Armature', 'ARMATURE').object = arm
        gs = {n: o.vertex_groups.new(name=n) for n in ('spine_02', 'pelvis', 'thigh_l', 'thigh_r')}
        for v in o.data.vertices:
            t = (zt - v.co.z) / max(zt - zb_, 1e-3)
            if v.co.z > pelvis_z + .08: gs['spine_02'].add([v.index], 1, 'REPLACE')
            else:
                lw_ = min(1, max(0, t)); side = 'thigh_l' if v.co.x > 0 else 'thigh_r'
                gs['pelvis'].add([v.index], 1 - .6 * lw_, 'REPLACE'); gs[side].add([v.index], .6 * lw_, 'REPLACE')
        print('PIECE apron panel'); continue
    elif k == 'puffer':   # quilted down jacket: the whole top (sleeves too) pushed out with horizontal down channels
        keep = [(not face_bottom[f.index]) and not any(dom[v].startswith(('hand', 'head')) for v in f.vertices) for f in suit.data.polygons]
        o = duplicate(suit, name, keep, mat); mixed_copy(o)
        bm = bmesh.new(); bm.from_mesh(o.data); bmesh.ops.subdivide_edges(bm, edges=bm.edges[:], cuts=1, use_grid_fill=True); bm.to_mesh(o.data); bm.free()
        mw = o.matrix_world; k_ = 1 / mw.to_scale()[0]; per = pc.get('period', .13); base, amp = pc.get('push', .03), pc.get('amp', .022)
        o.data.update(); nrm = [v.normal.copy() for v in o.data.vertices]
        for v, n in zip(o.data.vertices, nrm):
            wz = (mw @ v.co).z; ph = (wz % per) / per
            v.co += n * (base + amp * math.sin(ph * math.pi) ** .6) * k_
        for p_ in o.data.polygons: p_.use_smooth = True
        print('PIECE puffer', len(o.data.polygons)); continue
    elif k == 'stripes':   # sports side stripes running down the outside of each trouser leg
        bco = vert_world(suit); bm_ = [i for i, v in enumerate(suit.data.vertices) if dom[i].startswith(('thigh', 'calf', 'foot')) or (dom[i].startswith('pelvis') and bco[i][2] < pelvis_z)]
        hip_z = pelvis_z - .02; ank_z = bone_w('foot_l').z + .06
        bm = bmesh.new(); nS = 34; width = pc.get('width', .012); gap = pc.get('gap', .022); n_str = pc.get('count', 2)
        for side in (1, -1):
            pts = []
            for i in range(nS + 1):
                z = hip_z + (ank_z - hip_z) * i / nS
                cand = [bco[j] for j in bm_ if abs(bco[j][2] - z) < .02 and bco[j][0] * side > .02]
                if not cand: continue
                c = max(cand, key=lambda q: q[0] * side); pts.append(Vector((c[0] + side * .004, c[1], c[2])))
            for s_ in range(n_str):
                off = (s_ - (n_str - 1) / 2) * gap
                ring = []
                for p_ in pts:
                    for e in (-width / 2, width / 2): ring.append(bm.verts.new((p_.x + side * .001, p_.y + off + e, p_.z)))
                for i in range(len(pts) - 1): bm.faces.new((ring[i * 2], ring[i * 2 + 1], ring[i * 2 + 3], ring[i * 2 + 2]))
        o = finish_mesh(name, bm, mat)
        o.parent = arm; o.matrix_parent_inverse = arm.matrix_world.inverted(); o.modifiers.new('Armature', 'ARMATURE').object = arm
        knee = bone_w('calf_l').z; gs = {n_: o.vertex_groups.new(name=n_) for n_ in ('thigh_l', 'thigh_r', 'calf_l', 'calf_r')}
        for v in o.data.vertices:
            sd = 'l' if v.co.x > 0 else 'r'; wz = (o.matrix_world @ v.co).z; t_ = max(0, min(1, (knee + .06 - wz) / .12))
            gs['thigh_' + sd].add([v.index], 1 - t_, 'REPLACE'); gs['calf_' + sd].add([v.index], t_, 'REPLACE')
        print('PIECE stripes'); continue
    elif k == 'hood':   # bunched hood lying behind the neck: a fat tube along an arc round the back of the neck
        nk = bone_w('neck_01'); R, rt = pc.get('radius', .075), pc.get('tube', .035); cz = nk.z - .005
        bm = bmesh.new(); uvq = bm.loops.layers.uv.new('UVMap'); rs = []; NA, NT = 20, 14
        for i in range(NA + 1):
            a = math.radians(10 + 160 * i / NA); ax, ay = math.cos(a), math.sin(a); sw = 1 - .45 * abs(i / NA - .5) * 2
            rs.append([bm.verts.new((nk.x + (R + rt * sw * math.cos(b)) * ax, nk.y + .015 + (R + rt * sw * math.cos(b)) * ay * 1.1,
                                     cz + rt * sw * 1.3 * math.sin(b))) for b in np.linspace(0, 2 * math.pi, NT + 1)[:-1]])
        for i in range(NA):
            for j in range(NT):
                f = bm.faces.new((rs[i][j], rs[i][(j + 1) % NT], rs[i + 1][(j + 1) % NT], rs[i + 1][j]))
                for loop, (uu, vv) in zip(f.loops, ((i, j), (i, j + 1), (i + 1, j + 1), (i + 1, j))): loop[uvq].uv = (uu / NA * 2, vv / NT)
        bind(finish_mesh(name, bm, mat), 'neck_01'); print('PIECE hood'); continue
    push(o, pc.get('push', .008)); print('PIECE', k, len(o.data.polygons))

# ---------------------------------------------------------------- head measurements (hats / glasses / skin regions)
hco = vert_world(human); hg = human.vertex_groups['head'].index
head_idx = [v.index for v in human.data.vertices if any(g.group == hg and g.weight > .5 for g in v.groups)]
headv = hco[head_idx]; skull_top = headv[:, 2].max()
eyes = vert_world(find('high-poly')); eye_z = eyes[:, 2].mean(); eye_front_y = eyes[:, 1].min()
eyeL, eyeR = eyes[eyes[:, 0] > 0], eyes[eyes[:, 0] < 0]
hair_o = next((o for o in bpy.context.scene.objects if o.type == 'MESH' and P.get('hair') and P['hair'] in o.name), None)
hv = vert_world(hair_o) if hair_o else headv
top = max(skull_top, hv[:, 2].max())

if C.get('hat'):
    H = C['hat']; kind = H.get('kind', 'farm'); zb = skull_top - H.get('brim_down', .06)
    up = np.vstack([headv[headv[:, 2] > zb - .01], hv[hv[:, 2] > zb - .01]])
    cx, cy = (up[:, 0].min() + up[:, 0].max()) / 2, (up[:, 1].min() + up[:, 1].max()) / 2
    rx, ry = (up[:, 0].max() - up[:, 0].min()) / 2 * 1.04 + .004, (up[:, 1].max() - up[:, 1].min()) / 2 * 1.04 + .004
    ztop = top + .008; bm = bmesh.new(); uvl2 = bm.loops.layers.uv.new('UVMap')
    def ring(z, sx, sy, a0=0, a1=2 * math.pi, n=48, closed=True, oy=0.):
        return [bm.verts.new((cx + sx * math.cos(a0 + (a1 - a0) * i / n), cy + oy + sy * math.sin(a0 + (a1 - a0) * i / n), z)) for i in range(n if closed else n + 1)]
    def strip(r0, r1, v0, v1, closed=True, ureps=4):
        n = len(r0)
        for i in range(n if closed else n - 1):
            j = (i + 1) % n; f = bm.faces.new((r0[i], r0[j], r1[j], r1[i]))
            for loop, (uu, vv) in zip(f.loops, ((i, v0), (i + 1, v0), (i + 1, v1), (i, v1))): loop[uvl2].uv = (uu / n * ureps, vv)
    if kind == 'farm':
        PROF = [(0, 0), (.45, .02), (.75, .08), (.92, .2), (.99, .38), (1, .6), (1, 1)]
        rings = [ring(ztop - (ztop - zb) * d, rx * max(rf, .02), ry * max(rf, .02)) for rf, d in PROF]
        for k in range(len(PROF) - 1): strip(rings[k], rings[k + 1], k / 6 * .5, (k + 1) / 6 * .5)
        bw, droop, prev = H.get('brim', .065), .016, rings[-1]
        for k in range(1, 5):
            t = k / 4; r = ring(zb - droop * t ** 1.6, rx + bw * t, ry + bw * t * 1.15); strip(prev, r, .5 + (k - 1) * .12, .5 + k * .12, ureps=6); prev = r
        a0, a1, fl, prevf = math.radians(-8), math.radians(188), H.get('flap', .11), None
        for k in range(6):
            t = k / 5; fo = 1.0 + .45 * t ** 1.4; r = ring(zb - .004 - fl * t, rx * fo, ry * fo * 1.05, a0, a1, 32, closed=False)
            for i, v in enumerate(r):
                ph = a0 + (a1 - a0) * i / 32; w = 1 + .05 * t * math.sin(ph * 9); v.co.x = cx + (v.co.x - cx) * w; v.co.y = cy + (v.co.y - cy) * w
            if prevf: strip(prevf, r, 1 + (k - 1) * .2, 1 + k * .2, closed=False, ureps=3)
            prevf = r
    elif kind == 'beanie':   # knit beanie: snug dome over the skull, slight slouch at the crown, folded cuff band
        sk = headv[headv[:, 2] > zb + .035]   # skull above the ears: the cuff hugs the head, not the ear tips
        rx = (sk[:, 0].max() - sk[:, 0].min()) / 2 * 1.06 + .006; ry = (sk[:, 1].max() - sk[:, 1].min()) / 2 * 1.04 + .006
        cx, cy = (sk[:, 0].min() + sk[:, 0].max()) / 2, (sk[:, 1].min() + sk[:, 1].max()) / 2
        rings = []
        for k in range(9):
            t = k / 8; a_ = t * math.pi / 2; z = zb + .045 + (ztop + .028 - zb - .045) * math.sin(a_)
            s_ = math.cos(a_) ** .9 * (1.0 + .04 * math.sin(t * math.pi)) + .02
            rings.append(ring(z, rx * max(s_, .05), ry * max(s_, .05)))
        for k in range(8): strip(rings[k], rings[k + 1], .2 + k * .1, .3 + k * .1, ureps=24)
        cuff0 = ring(zb - .004, rx * 1.03, ry * 1.03); cuff1 = ring(zb + .05, rx * 1.05, ry * 1.05)
        strip(cuff0, cuff1, 0, .2, ureps=24); strip(cuff1, rings[0], .2, .2, ureps=24)
    elif kind == 'police':   # peaked cap: straight band, flat crown a little wider than the band, visor in front (-Y)
        zt = zb + H.get('height', .08); grow = 1.1
        b0 = ring(zb, rx, ry); b1 = ring(zb + .04, rx * 1.01, ry * 1.01)
        c0 = ring(zt - .012, rx * grow, ry * grow * 1.06, oy=.006); c1 = ring(zt, rx * grow * .97, ry * grow * 1.03, oy=.006)
        strip(b0, b1, 0, .3); strip(b1, c0, .3, .6); strip(c0, c1, .6, .7)
        ctr = bm.verts.new((cx, cy + .006, zt))
        for i in range(48):
            f = bm.faces.new((c1[i], c1[(i + 1) % 48], ctr))
            for loop, (uu, vv) in zip(f.loops, ((i / 48, .7), ((i + 1) / 48, .7), (.5, 1))): loop[uvl2].uv = (uu * 4, vv)
        vin = b0[24:] + [b0[0]]   # angles pi..2pi: the front half (-Y)
        vout = [bm.verts.new((v.co.x + (v.co.x - cx) * .2, v.co.y + (v.co.y - cy) * .8 - .01, v.co.z - .016)) for v in vin]
        for i in range(len(vin) - 1):
            f = bm.faces.new((vin[i], vin[i + 1], vout[i + 1], vout[i]))
            for loop, (uu, vv) in zip(f.loops, ((i, 0), (i + 1, 0), (i + 1, 1), (i, 1))): loop[uvl2].uv = (uu / 24, vv * .2)
    hat = finish_mesh('hat', bm, new_mat('hat', H['tex'], H.get('rough', .9), double=True), H.get('thick', .0025)); bind(hat)
    print('HAT', kind, round(zb, 3), round(rx, 3), round(ry, 3))

if C.get('glasses'):   # frames: two rounded lenses, a bridge bar and temples back to the ears
    G = C['glasses']; zf = eye_front_y - .014; lw, lh = G.get('w', .025), G.get('h', .018)
    lx, rxe = eyeL[:, 0].mean(), eyeR[:, 0].mean(); bm = bmesh.new(); rims = []
    for cxe in (lx, rxe):
        n = 28; ctr = bm.verts.new((cxe, zf, eye_z)); rim = []
        for i in range(n):
            a = 2 * math.pi * i / n; c, s = math.cos(a), math.sin(a)
            x = cxe + lw * np.sign(c) * abs(c) ** .7; z = eye_z + .002 + lh * np.sign(s) * abs(s) ** .8
            rim.append(bm.verts.new((x, zf + .004 * ((x - cxe) / lw) ** 2, z)))
        for i in range(n): bm.faces.new((rim[i], rim[(i + 1) % n], ctr))
        rims.append([v.co.copy() for v in rim])
    bind(finish_mesh('lens', bm, new_mat('lens', 'color:' + G.get('lens', '#151515'), .05, double=True)))
    bm = bmesh.new()
    band = headv[(np.abs(headv[:, 2] - eye_z) < .012) & (headv[:, 1] < eye_front_y + .05)]   # face width at eye height (ears excluded)
    half = min(np.abs(band[:, 0]).max() + .004, max(abs(lx), abs(rxe)) + lw + .012)
    def bar(p0, p1, r=.0017):
        p0, p1 = Vector(p0), Vector(p1); d = (p1 - p0).normalized(); a = d.orthogonal().normalized(); b = d.cross(a)
        r0 = [bm.verts.new(p0 + (a * math.cos(t) + b * math.sin(t)) * r) for t in np.linspace(0, 2 * math.pi, 7)[:-1]]
        r1 = [bm.verts.new(p1 + (a * math.cos(t) + b * math.sin(t)) * r) for t in np.linspace(0, 2 * math.pi, 7)[:-1]]
        for i in range(6): bm.faces.new((r0[i], r0[(i + 1) % 6], r1[(i + 1) % 6], r1[i]))
    for rim in rims:
        for i in range(len(rim)): bar(rim[i], rim[(i + 1) % len(rim)], .0015)
    bar((lx - lw * .9, zf, eye_z + .012), (rxe + lw * .9, zf, eye_z + .012), .0016)
    for sx, cxe in ((1, lx), (-1, rxe)):
        bar((cxe + sx * lw, zf, eye_z + .01), (sx * half, zf + .02, eye_z + .008))
        bar((sx * half, zf + .02, eye_z + .008), (sx * (half - .004), zf + .095, eye_z - .01))
    bind(finish_mesh('frame', bm, new_mat('frame', 'color:' + G.get('frame', '#1b1b1b'), .3, metal=.3)))

skin_regions = {}
if 'beard' in C.get('regions', []):   # stubble zone: below the nose, above the jaw line, in front of the ears
    uvb = human.data.uv_layers.active.data; polys = []
    for f in human.data.polygons:
        c = hco[list(f.vertices)].mean(0)
        if eye_z - .13 < c[2] < eye_z - .045 and c[1] < eye_front_y + .065 and abs(c[0]) < .068:
            polys.append([[round(uvb[li].uv.x, 5), round(uvb[li].uv.y, 5)] for li in f.loop_indices])
    skin_regions['beard'] = polys; print('BEARD faces', len(polys))

if 'buzz' in C.get('regions', []):   # buzz-cut scalp: above the forehead line, and behind/above the ears
    uvb = human.data.uv_layers.active.data; polys = []; hs = set(head_idx)
    for f in human.data.polygons:
        if not all(v in hs for v in f.vertices): continue
        c = hco[list(f.vertices)].mean(0); dy = c[1] - eye_front_y
        hw_ = np.abs(headv[np.abs(headv[:, 2] - (eye_z + .06)) < .01][:, 0]).max()   # skull half-width above the ears
        if abs(c[0]) > hw_ - .004 and c[2] < eye_z + .045: continue                  # the ears stay skin
        if c[2] > eye_z + .055 or (c[2] > eye_z - .02 and dy > .075) or (c[2] > eye_z - .055 and dy > .11):
            polys.append([[round(uvb[li].uv.x, 5), round(uvb[li].uv.y, 5)] for li in f.loop_indices])
    skin_regions['buzz'] = polys; print('BUZZ faces', len(polys))
json.dump({'recipes': recipes, 'tex_jobs': tex_jobs, 'uvclass': uvclass, 'skin_regions': skin_regions},
          open(os.path.join(os.path.dirname(OUT), 'costume.json'), 'w'))
