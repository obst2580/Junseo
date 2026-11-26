"""
Prepare a downloaded car model for the game:  blender -b -P prep_car.py -- <in.glb> <out.glb> <length_m> [max_tris]
 - drops line-only geometry (SketchUp edges), applies transforms
 - scales to the real length, centres it, wheels on the ground; forward = glTF +Z (Blender -Y)
 - finds the four wheels (round loose parts low at the corners + anything inside them) and splits them out as
   wheel_FL / wheel_FR / wheel_RL / wheel_RR with their origin at the hub so the game can spin and steer them
 - decimates the body if it is heavy, exports Draco-compressed GLB
"""
import bpy, bmesh, sys, math
from mathutils import Vector
a = sys.argv[sys.argv.index('--') + 1:]; src, dst, L = a[0], a[1], float(a[2]); MAXT = int(a[3]) if len(a) > 3 else 70000
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=src)
objs = [o for o in bpy.context.scene.objects if o.type == 'MESH']
for o in bpy.context.scene.objects: o.select_set(False)
for o in objs: o.select_set(True)
bpy.context.view_layer.objects.active = objs[0]
bpy.ops.object.parent_clear(type='CLEAR_KEEP_TRANSFORM')
bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
bpy.ops.object.join(); body = bpy.context.view_layer.objects.active
if body.data.has_custom_normals: bpy.ops.mesh.customdata_custom_splitnormals_clear()   # imported normals fight the fixed faces: let Blender recompute
print('materials', [(m.name) for m in body.data.materials][:40])
for o in list(bpy.context.scene.objects):
    if o != body: bpy.data.objects.remove(o, do_unlink=True)
bm = bmesh.new(); bm.from_mesh(body.data)
import os
DROP = [x for x in os.environ.get('DROP', '').split(',') if x]   # back-face materials doubled under the real skin (SketchUp)
if DROP:
    bad = {i for i, m in enumerate(body.data.materials) if m and any(m.name == d or m.name.startswith(d) for d in DROP)}
    bmesh.ops.delete(bm, geom=[f for f in bm.faces if f.material_index in bad], context='FACES')
loose = [e for e in bm.edges if not e.link_faces]; bmesh.ops.delete(bm, geom=loose, context='EDGES')
bmesh.ops.delete(bm, geom=[v for v in bm.verts if not v.link_edges], context='VERTS')
bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-6 * max(body.dimensions))
bmesh.ops.recalc_face_normals(bm, faces=bm.faces)   # SketchUp exports: faces point every which way
bm.to_mesh(body.data); bm.free()
# scale / centre: length along Blender Y (glTF Z), wheels on z = 0
bb = [body.matrix_world @ Vector(c) for c in body.bound_box]
mn = Vector((min(v.x for v in bb), min(v.y for v in bb), min(v.z for v in bb))); mx = Vector((max(v.x for v in bb), max(v.y for v in bb), max(v.z for v in bb)))
s = L / (mx.y - mn.y)
me = body.data
for v in me.vertices: v.co = Vector(((v.co.x - (mn.x + mx.x) / 2) * s, (v.co.y - (mn.y + mx.y) / 2) * s, (v.co.z - mn.z) * s))
me.update()
W = (mx.x - mn.x) * s; H = (mx.z - mn.z) * s
if os.environ.get('OUTWARD'):   # make every face look away from the car's long axis (fixes SketchUp faces that face inwards)
    bm = bmesh.new(); bm.from_mesh(me); n = 0
    for f in bm.faces:
        c = f.calc_center_median(); ref = Vector((0, max(-L / 2 + .9, min(L / 2 - .9, c.y)), H * .45))
        if f.normal.dot(c - ref) < 0: f.normal_flip(); n += 1
    bm.to_mesh(me); bm.free(); me.update(); print('flipped', n)
print('size', round(W, 2), round(L, 2), round(H, 2))
# split loose parts, classify wheels
bpy.ops.object.select_all(action='DESELECT'); body.select_set(True); bpy.context.view_layer.objects.active = body
bpy.ops.object.mode_set(mode='EDIT'); bpy.ops.mesh.select_all(action='SELECT'); bpy.ops.mesh.separate(type='LOOSE'); bpy.ops.object.mode_set(mode='OBJECT')
parts = [o for o in bpy.context.scene.objects if o.type == 'MESH']
def box(o):
    vs = [o.matrix_world @ v.co for v in o.data.vertices]
    lo = Vector((min(v.x for v in vs), min(v.y for v in vs), min(v.z for v in vs))); hi = Vector((max(v.x for v in vs), max(v.y for v in vs), max(v.z for v in vs))); return lo, hi
seeds = []
for o in parts:
    if not o.data.vertices: continue
    lo, hi = box(o); d = hi - lo; c = (lo + hi) / 2
    if .3 < d.y < 1.0 and .3 < d.z < 1.0 and abs(d.y - d.z) < .12 * max(d.y, d.z) + .03 and d.x < .45 and c.z < .7 and abs(c.x) > W / 2 - .5 and lo.z < .08:
        seeds.append((o, lo, hi))
# group seeds into four corners
groups = {}
for o, lo, hi in seeds:
    c = (lo + hi) / 2; k = ('L' if c.x > 0 else 'R', 'F' if c.y < 0 else 'R')   # Blender -Y is the front
    g = groups.setdefault(k, [Vector((1e9,) * 3), Vector((-1e9,) * 3), []])
    g[0] = Vector((min(g[0].x, lo.x), min(g[0].y, lo.y), min(g[0].z, lo.z))); g[1] = Vector((max(g[1].x, hi.x), max(g[1].y, hi.y), max(g[1].z, hi.z)))
print('wheel groups', {k: len(v[2]) + 0 for k, v in groups.items()}, 'seeds', len(seeds))
assigned = {}
for o in parts:
    if not o.data.vertices: continue
    lo, hi = box(o)
    for k, (glo, ghi, _) in groups.items():
        pad = .03
        if lo.x >= glo.x - pad and hi.x <= ghi.x + pad and lo.y >= glo.y - pad and hi.y <= ghi.y + pad and lo.z >= glo.z - pad and hi.z <= ghi.z + pad:
            assigned.setdefault(k, []).append(o); break
def join(objs, name):
    bpy.ops.object.select_all(action='DESELECT')
    for o in objs: o.select_set(True)
    bpy.context.view_layer.objects.active = objs[0]
    if len(objs) > 1: bpy.ops.object.join()
    o = bpy.context.view_layer.objects.active; o.name = name; o.data.name = name; return o
wheels = []
for k, objs in assigned.items():
    side, end = k; w = join(objs, f'wheel_{"F" if end == "F" else "R"}{side}')
    lo, hi = box(w); c = (lo + hi) / 2
    bpy.context.scene.cursor.location = c; bpy.ops.object.select_all(action='DESELECT'); w.select_set(True); bpy.context.view_layer.objects.active = w
    bpy.ops.object.origin_set(type='ORIGIN_CURSOR'); wheels.append(w)
    print(w.name, [round(v, 3) for v in c], 'r', round((hi.z - lo.z) / 2, 3))
rest = [o for o in bpy.context.scene.objects if o.type == 'MESH' and o not in wheels and o.data.vertices]
body = join(rest, 'body')
tris = sum(len(p.vertices) - 2 for p in body.data.polygons)
if tris > MAXT:
    m = body.modifiers.new('dec', 'DECIMATE'); m.ratio = MAXT / tris; m.use_collapse_triangulate = True
    bpy.ops.object.select_all(action='DESELECT'); body.select_set(True); bpy.context.view_layer.objects.active = body; bpy.ops.object.modifier_apply(modifier='dec')
print('body tris', tris, '->', sum(len(p.vertices) - 2 for p in body.data.polygons))
for m in bpy.data.materials: m.use_backface_culling = False   # export double-sided
for o in bpy.context.scene.objects:
    if o.type == 'MESH':
        for p_ in o.data.polygons: p_.use_smooth = True
        bpy.ops.object.select_all(action='DESELECT'); o.select_set(True); bpy.context.view_layer.objects.active = o
        try: bpy.ops.object.shade_smooth_by_angle(angle=math.radians(32))
        except Exception as e: print('smooth', e)
bpy.ops.export_scene.gltf(filepath=dst, export_format='GLB', export_draco_mesh_compression_enable=True, export_draco_mesh_compression_level=6, export_apply=True, export_yup=True)
print('DONE', dst)
