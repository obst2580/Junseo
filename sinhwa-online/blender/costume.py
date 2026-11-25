"""
농촌 의상 빌더 (build_human.py 안에서 exec): MakeHuman 옷 메쉬를 다시 칠하고, 복제·부풀려 조끼/팔토시/장화를 만들고,
농사 모자(챙 + 목가리개)를 직접 모델링해 머리 뼈에 붙인다.
필요 변수: human, P, TEX_SRC (retex.py / gen_textures.py 출력 폴더)
"""
import bpy, bmesh, math, numpy as np
from mathutils import Vector

C = P['costume']
objs = {o.name: o for o in bpy.context.scene.objects}
arm = [o for o in bpy.context.scene.objects if o.type == 'ARMATURE'][0]
def find(part): return next(o for o in bpy.context.scene.objects if o.type == 'MESH' and part in o.name)
def bone_w(name, where='head'): b = arm.data.bones[name]; return arm.matrix_world @ (b.head_local if where == 'head' else b.tail_local)

def load_img(fn):
    return bpy.data.images.load(os.path.join(TEX_SRC, fn), check_existing=True)

def retexture(obj, fn):
    """swap the diffuse image of the object's MakeSkin material"""
    for m in obj.data.materials:
        for n in m.node_tree.nodes:
            if n.type == 'TEX_IMAGE' and n.image and 'diffuse' in n.image.name.lower():
                n.image = load_img(fn); print('retexture', obj.name, '->', fn)

def new_mat(name, fn, rough=.85):
    m = bpy.data.materials.new(name); m.use_nodes = True
    bsdf = m.node_tree.nodes['Principled BSDF']; bsdf.inputs['Roughness'].default_value = rough
    t = m.node_tree.nodes.new('ShaderNodeTexImage'); t.image = load_img(fn)
    m.node_tree.links.new(t.outputs['Color'], bsdf.inputs['Base Color'])
    return m

def pixels(img):
    w, h = img.size; return np.array(img.pixels[:], np.float32).reshape(h, w, 4)

def vert_world(obj):
    """rest-pose world positions of the object's base mesh (shape keys mixed in)"""
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

def push(obj, dist_m, sel=None):
    """move vertices out along their normals (dist in metres), on every shape key too"""
    k = 1 / obj.matrix_world.to_scale()[0]; me = obj.data
    nrm = [v.normal.copy() for v in me.vertices]
    idx = range(len(me.vertices)) if sel is None else sel
    if me.shape_keys:
        for kb in me.shape_keys.key_blocks:
            for i in idx: kb.data[i].co += nrm[i] * dist_m * k
    for i in idx: me.vertices[i].co += nrm[i] * dist_m * k

def duplicate(obj, name, keep_faces, mat):
    d = obj.copy(); d.data = obj.data.copy(); d.name = name; bpy.context.scene.collection.objects.link(d)
    for m in list(d.modifiers):
        if m.type in ('MASK', 'SUBSURF'): d.modifiers.remove(m)
    bm = bmesh.new(); bm.from_mesh(d.data); bm.faces.ensure_lookup_table()
    bmesh.ops.delete(bm, geom=[f for f in bm.faces if not keep_faces[f.index]], context='FACES')
    bm.to_mesh(d.data); bm.free()
    d.data.materials.clear(); d.data.materials.append(mat)
    return d

# ---------------------------------------------------------------- outfit
suit, shoes = find(C['suit']), find(C['shoes'])
src_img = next(n.image for m in suit.data.materials for n in m.node_tree.nodes if n.type == 'TEX_IMAGE' and n.image and 'diffuse' in n.image.name.lower())
px = pixels(src_img); H, W = px.shape[:2]
uv = suit.data.uv_layers.active.data
def is_pants(f):
    us = [uv[li].uv for li in f.loop_indices]; u = sum(p.x for p in us) / len(us) % 1; v = sum(p.y for p in us) / len(us) % 1
    c = px[min(H - 1, int(v * H)), min(W - 1, int(u * W))]; return c[2] > c[0] + .08
face_pants = [is_pants(f) for f in suit.data.polygons]
retexture(suit, C['suit_tex']); retexture(shoes, C['shoe_tex'])

dom = dominant_bone(suit); co = vert_world(suit)
ARMB = ('upperarm', 'lowerarm', 'hand', 'neck', 'head')   # collar stays blouse
knee_z = bone_w('calf_l').z
chest_y = bone_w('spine_03').y
front_gap = lambda v: abs(co[v][0]) < C.get('vest_gap', .045) and co[v][1] < chest_y   # open front (body faces -Y)
vest_keep = [(not face_pants[f.index]) and not any(dom[v].startswith(ARMB) for v in f.vertices) for f in suit.data.polygons]
cover_keep = [(not face_pants[f.index]) and all(dom[v].startswith(('lowerarm', 'hand')) for v in f.vertices) for f in suit.data.polygons]
boot_keep = [face_pants[f.index] and all(co[v][2] < knee_z - C.get('boot_drop', .05) for v in f.vertices) for f in suit.data.polygons]

push(suit, C.get('loose', .006))                                   # baggy granny clothes
vest = duplicate(suit, 'Human.vest', vest_keep, new_mat('Human.vest', 'vest_layout.png', .9))
def open_front(obj, gap):
    """cut a clean straight front opening: bisect at x = +-gap, then drop the front faces between the cuts"""
    mw, mi = obj.matrix_world, obj.matrix_world.inverted()
    bm = bmesh.new(); bm.from_mesh(obj.data)
    for sx in (1, -1):
        pw = Vector((sx * gap, 0, 0)); co_l = mi @ pw; no_l = (mi.to_3x3() @ Vector((1, 0, 0))).normalized()
        geom = bm.verts[:] + bm.edges[:] + bm.faces[:]
        bmesh.ops.bisect_plane(bm, geom=geom, plane_co=co_l, plane_no=no_l)
    dead = [f for f in bm.faces if (lambda c: abs(c.x) < gap and c.y < chest_y)(mw @ f.calc_center_median())]
    bmesh.ops.delete(bm, geom=dead, context='FACES'); bm.to_mesh(obj.data); bm.free()
open_front(vest, C.get('vest_gap', .05)); push(vest, .008)
cover = duplicate(suit, 'Human.armcover', cover_keep, new_mat('Human.armcover', 'armcover_layout.png', .85)); push(cover, .005)
boot = duplicate(suit, 'Human.boot', boot_keep, new_mat('Human.boot', 'boot_layout.png', .45)); push(boot, .012)
print('vest faces', sum(vest_keep), 'cover', sum(cover_keep), 'boot', sum(boot_keep))

# ---------------------------------------------------------------- farm hat (brim + neck flap), bound to the head bone
hco = vert_world(human); hg = human.vertex_groups['head'].index
headv = np.array([hco[v.index] for v in human.data.vertices if any(g.group == hg and g.weight > .5 for g in v.groups)])
hair = find(P['hair']); hv = vert_world(hair)
top = max(headv[:, 2].max(), hv[:, 2].max())
zb = headv[:, 2].max() - C.get('brim_down', .07)          # brim sits on the forehead, measured from the skull, not the hair volume
up = np.vstack([headv[headv[:, 2] > zb - .01], hv[hv[:, 2] > zb - .01]])
cx, cy = (up[:, 0].min() + up[:, 0].max()) / 2, (up[:, 1].min() + up[:, 1].max()) / 2
rx, ry = (up[:, 0].max() - up[:, 0].min()) / 2 * 1.05 + .004, (up[:, 1].max() - up[:, 1].min()) / 2 * 1.05 + .004
ztop = top + .01
SEG = 48
bm = bmesh.new(); uvl = bm.loops.layers.uv.new('UVMap')
def ring(z, sx, sy, a0=0, a1=2 * math.pi, n=SEG, closed=True):
    cnt = n if closed else n + 1
    return [bm.verts.new((cx + sx * math.cos(a0 + (a1 - a0) * i / n), cy + sy * math.sin(a0 + (a1 - a0) * i / n), z)) for i in range(cnt)]
def strip(r0, r1, v0, v1, closed=True, ureps=4):
    n = len(r0)
    for i in range(n if closed else n - 1):
        j = (i + 1) % n
        f = bm.faces.new((r0[i], r0[j], r1[j], r1[i]))
        for loop, (uu, vv) in zip(f.loops, ((i, v0), (i + 1, v0), (i + 1, v1), (i, v1))): loop[uvl].uv = (uu / n * ureps, vv)
# crown: flat-ish top with a rounded shoulder, near-vertical sides down to the brim line
PROF = [(0, 0), (.45, .02), (.75, .08), (.92, .2), (.99, .38), (1, .6), (1, 1)]   # (radius fraction, depth fraction)
rings = [ring(ztop - (ztop - zb) * d, rx * max(rf, .02), ry * max(rf, .02)) for rf, d in PROF]
for k in range(len(PROF) - 1): strip(rings[k], rings[k + 1], k / 6 * .5, (k + 1) / 6 * .5)
# wide brim, drooping at the edge
bw, droop = C.get('brim', .085), .016
prev = rings[-1]
for k in range(1, 5):
    t = k / 4; r = ring(zb - droop * t ** 1.6, rx + bw * t, ry + bw * t * 1.15)
    strip(prev, r, .5 + (k - 1) * .12, .5 + k * .12, ureps=6); prev = r
# neck flap: hangs from the crown base round the back and sides, flaring out a little
a0, a1 = math.radians(-8), math.radians(188)         # +Y is the back of the head (MakeHuman faces -Y)
fl, prevf = C.get('flap', .14), None
for k in range(6):
    t = k / 5; fo = 1.0 + .45 * t ** 1.4
    r = ring(zb - .004 - fl * t, rx * fo, ry * fo * 1.05, a0, a1, 32, closed=False)
    for i, v in enumerate(r):   # soft cloth folds that deepen toward the hem
        ph = a0 + (a1 - a0) * i / 32; w = 1 + .05 * t * math.sin(ph * 9)
        v.co.x = cx + (v.co.x - cx) * w; v.co.y = cy + (v.co.y - cy) * w
    if prevf: strip(prevf, r, 1 + (k - 1) * .2, 1 + k * .2, closed=False, ureps=3)
    prevf = r
me = bpy.data.meshes.new('Human.farmhat'); bm.to_mesh(me); bm.free()
hat = bpy.data.objects.new('Human.farmhat', me); bpy.context.scene.collection.objects.link(hat)
me.materials.append(new_mat('Human.farmhat', 'hat_cloth.png', .95))
bpy.context.view_layer.objects.active = hat
sol = hat.modifiers.new('thick', 'SOLIDIFY'); sol.thickness = .0025; sol.offset = 0
bpy.ops.object.select_all(action='DESELECT'); hat.select_set(True); bpy.ops.object.modifier_apply(modifier='thick')
for p in me.polygons: p.use_smooth = True
hat.parent = arm; hat.matrix_parent_inverse = arm.matrix_world.inverted()
vg = hat.vertex_groups.new(name='head'); vg.add(list(range(len(me.vertices))), 1.0, 'REPLACE')
am = hat.modifiers.new('Armature', 'ARMATURE'); am.object = arm
ev_ = vert_world(find('high-poly')); print('EYES z', round(ev_[:,2].mean(),3), 'headtop', round(headv[:,2].max(),3), 'zb', round(zb,3), 'hairtop', round(hv[:,2].max(),3), 'headv n', len(headv))
print('HAT', round(cx, 3), round(cy, 3), round(rx, 3), round(ry, 3), 'top', round(top, 3), 'verts', len(me.vertices))
