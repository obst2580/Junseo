"""
신화리 마을 구역(실제 배치) — 룩데브 v2 빌더 재사용 — Blender 4.2 headless scene builder + lightmap baker + glTF exporter.
Run:  blender -b -P build_lookdev.py -- <tex_dir> <out_dir> [samples]
Coordinates: the three.js page uses x=east, z=south. Blender uses x=east, y=north, z=up,
so a three.js point (x, z) is placed at Blender (x, -z). glTF export converts axes back.
"""
import bpy, bmesh, math, os, sys, random
import numpy as np
from mathutils import Vector, Matrix

argv = sys.argv[sys.argv.index('--') + 1:]
WORLD = os.environ['WORLD']; REG = tuple(float(v) for v in os.environ.get('REGION', '-250,-350,350,250').split(','))
TEX, OUT = argv[0], argv[1]
SAMPLES = int(argv[2]) if len(argv) > 2 else 32
LM_SIZE_BUILD = int(argv[3]) if len(argv) > 3 else 4096
LM_SIZE_GROUND = int(argv[4]) if len(argv) > 4 else 4096
REUSE_LM = len(argv) > 5 and argv[5] == 'reuse'   # keep existing lm_*.npy (same geometry), only re-bake prop vertex colours
os.makedirs(OUT, exist_ok=True)
random.seed(7)
R = random.uniform

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
scene.unit_settings.system = 'METRIC'

def P(x3, z3):            # three.js (x, z) -> Blender (x, y)
    return (x3, -z3)

# ---------------------------------------------------------------- materials
def img(path, noncolor=False):
    im = bpy.data.images.load(path, check_existing=True)
    if noncolor:
        im.colorspace_settings.name = 'Non-Color'
    return im

def tex_path(asset, kind):
    return os.path.join(TEX, asset, f'{asset}_1K-JPG_{kind}.jpg')

MATS = {}
def mat(name, color=None, asset=None, color_file=None, normal=True, rough=0.8, metal=0.0, tile=4.0,
        alpha=1.0, emissive=None, normal_strength=1.0, lm=True, rough_tex=True):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    bsdf = nt.nodes['Principled BSDF']
    bsdf.inputs['Roughness'].default_value = rough
    bsdf.inputs['Metallic'].default_value = metal
    uvn = nt.nodes.new('ShaderNodeUVMap'); uvn.uv_map = 'UVMap'
    cfile = color_file or (tex_path(asset, 'Color') if asset else None)
    if cfile:
        t = nt.nodes.new('ShaderNodeTexImage'); t.image = img(cfile)
        nt.links.new(uvn.outputs['UV'], t.inputs['Vector'])
        nt.links.new(t.outputs['Color'], bsdf.inputs['Base Color'])
    elif color:
        bsdf.inputs['Base Color'].default_value = (*color, 1)
    if asset and normal and os.path.exists(tex_path(asset, 'NormalGL')):
        t = nt.nodes.new('ShaderNodeTexImage'); t.image = img(tex_path(asset, 'NormalGL'), True)
        nm = nt.nodes.new('ShaderNodeNormalMap'); nm.inputs['Strength'].default_value = normal_strength
        nt.links.new(uvn.outputs['UV'], t.inputs['Vector'])
        nt.links.new(t.outputs['Color'], nm.inputs['Color'])
        nt.links.new(nm.outputs['Normal'], bsdf.inputs['Normal'])
    if asset and rough_tex and os.path.exists(tex_path(asset, 'Roughness')):
        t = nt.nodes.new('ShaderNodeTexImage'); t.image = img(tex_path(asset, 'Roughness'), True)
        nt.links.new(uvn.outputs['UV'], t.inputs['Vector'])
        nt.links.new(t.outputs['Color'], bsdf.inputs['Roughness'])
    if alpha < 1:
        bsdf.inputs['Alpha'].default_value = alpha
        try: m.blend_method = 'BLEND'
        except Exception: pass
        try: m.surface_render_method = 'BLENDED'
        except Exception: pass
    if emissive:
        bsdf.inputs['Emission Color'].default_value = (*emissive, 1)
        bsdf.inputs['Emission Strength'].default_value = 0.0
    m['tile'] = tile
    m['lm'] = lm
    MATS[name] = m
    return m

def srgb(h):
    h = h.lstrip('#'); c = [int(h[i:i+2], 16) / 255 for i in (0, 2, 4)]
    return tuple(((v + 0.055) / 1.055) ** 2.4 if v > 0.04045 else v / 12.92 for v in c)

D = os.path.join(TEX, 'derived')
def alpha_from_color(m):
    nt = m.node_tree; b = nt.nodes['Principled BSDF']
    t = next(n for n in nt.nodes if n.type == 'TEX_IMAGE')
    nt.links.new(t.outputs['Alpha'], b.inputs['Alpha'])
    try: m.blend_method = 'CLIP'
    except Exception: pass
M = {
  # ground group
  'g_ground':  mat('g_ground', asset='Ground037', tile=7, normal_strength=.8),
  'g_paddy':   mat('g_paddy', asset='Ground109', tile=5),
  'g_bund':    mat('g_bund', asset='Grass004', tile=3, rough=.95),
  'g_asphalt': mat('g_asphalt', asset='Asphalt031', tile=6, normal_strength=.6),
  'g_lane':    mat('g_lane', asset='Concrete034', tile=4),
  'g_yard':    mat('g_yard', asset='Concrete034', tile=4),
  'g_joint':   mat('g_joint', color=srgb('#2a2622'), rough=.95),
  'g_paintY':  mat('g_paintY', color=srgb('#d9a520'), rough=.6),
  'g_paintW':  mat('g_paintW', color=srgb('#e8e8e2'), rough=.6),
  'yard_b':    mat('yard_b', asset='Concrete034', tile=4),
  'lane_b':    mat('lane_b', asset='Concrete034', tile=4),
  # build group
  'plaster':   mat('plaster', asset='Plaster001', tile=3),
  'plaster2':  mat('plaster2', asset='PaintedPlaster017', tile=3),
  'tile':      mat('tile', color_file=os.path.join(D, 'tile_brown.jpg'), asset='Concrete046', tile=1.5, rough_tex=False, rough=.6),
  'roof_blue': mat('roof_blue', color_file=os.path.join(D, 'roof_blue.jpg'), asset='CorrugatedSteel005', tile=2, rough=.45, metal=.4, normal_strength=1.6),
  'roof_green':mat('roof_green', color_file=os.path.join(D, 'roof_green.jpg'), asset='CorrugatedSteel005', tile=2, rough=.45, metal=.4, normal_strength=1.6),
  'roof_red':  mat('roof_red', color_file=os.path.join(D, 'roof_red.jpg'), asset='CorrugatedSteel005', tile=2, rough=.45, metal=.4, normal_strength=1.6),
  'roof_orange':mat('roof_orange', color_file=os.path.join(D, 'roof_orange.jpg'), asset='CorrugatedSteel005', tile=2, rough=.45, metal=.4, normal_strength=1.6),
  'panel_grey':mat('panel_grey', color_file=os.path.join(D, 'panel_grey.jpg'), asset='CorrugatedSteel005', tile=2.5, rough=.4, metal=.5, normal_strength=1.4),
  'panel_blue':mat('panel_blue', color_file=os.path.join(D, 'panel_blue.jpg'), asset='CorrugatedSteel005', tile=2.5, rough=.4, metal=.5, normal_strength=1.4),
  'panel_white':mat('panel_white', color_file=os.path.join(D, 'panel_white.jpg'), asset='CorrugatedSteel005', tile=2.5, rough=.4, metal=.3, normal_strength=1.2),
  'block':     mat('block', asset='Concrete046', tile=2),
  'frame':     mat('frame', color=srgb('#c8ccd0'), rough=.3, metal=.8),
  'glass':     mat('glass', color=srgb('#1d2830'), rough=.04, metal=.2, emissive=srgb('#ffcf8a')),
  'wood':      mat('wood', asset='Wood095', tile=1.5, rough=.7),
  'wood_dark': mat('wood_dark', color=srgb('#4d3424'), rough=.8),
  'steel':     mat('steel', color=srgb('#6d7175'), rough=.45, metal=.85),
  'pole':      mat('pole', asset='Concrete034', tile=2),
  'paint_blue':mat('paint_blue', color=srgb('#1f4fa6'), rough=.28, metal=.2),
  'paint_red': mat('paint_red', color=srgb('#a8232a'), rough=.32, metal=.2),
  'tire':      mat('tire', color=srgb('#151515'), rough=.9),
  'box_white': mat('box_white', color=srgb('#ecebe4'), rough=.7),
  'bench':     mat('bench', color=srgb('#d6d9d2'), rough=.6),
  'plant':     mat('plant', color_file=os.path.join(D, 'berry_bed.png'), rough=.75, tile=1.2),
  'berry':     mat('berry', color=srgb('#c3202f'), rough=.4),
  'leaf':      mat('leaf', asset='Grass004', tile=1.2, rough=.85),
  'bark':      mat('bark', color=srgb('#3f3226'), rough=.95),
  'sign_hall': mat('sign_hall', color_file=os.path.join(D, 'sign_hall.png'), rough=.5, tile=0),
  'sign_elder':mat('sign_elder', color_file=os.path.join(D, 'sign_elder.png'), rough=.5, tile=0),
  'sign_bus':  mat('sign_bus', color_file=os.path.join(D, 'sign_bus.png'), rough=.4, tile=0),
  'sign_cold': mat('sign_cold', color_file=os.path.join(D, 'sign_cold.png'), rough=.5, tile=0),
  'onggi':     mat('onggi', color=srgb('#4a2e1c'), rough=.3, metal=.0),
  'gutter':    mat('gutter', color=srgb('#d8d8d2'), rough=.4, metal=.2),
  'leafcard':  mat('leafcard', color_file=os.path.join(D, 'leaf_rgba.png'), rough=.7, tile=0),
  'rope':      mat('rope', color=srgb('#d0ccc0'), rough=.8),
  'film_roll': mat('film_roll', color=srgb('#dfe6e2'), rough=.4),
  # not lightmapped
  'shadenet':  mat('shadenet', color=srgb('#151715'), rough=.9, alpha=.78, lm=False),
  'film':      mat('film', color=srgb('#eef3f0'), rough=.3, alpha=.42, lm=False),
  'rib':       mat('rib', color=srgb('#9aa0a3'), rough=.35, metal=.85, lm=False),
  'wire':      mat('wire', color=srgb('#161616'), rough=.6, lm=False),
}

alpha_from_color(M['leafcard'])
# ---------------------------------------------------------------- geometry helpers
GROUND, BUILD, NOLM, PROPS = [], [], [], []   # PROPS: vertex-colour lighting instead of a lightmap
def link(obj, group):
    bpy.context.collection.objects.link(obj)
    group.append(obj)
    return obj

def mesh_obj(name, verts, faces, mats, group, face_mats=None):
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], faces)
    for m in mats: me.materials.append(m)
    if face_mats:
        for p, fi in zip(me.polygons, face_mats): p.material_index = fi
    me.update()
    return link(bpy.data.objects.new(name, me), group)

def box(name, x0, x1, z0, z1, y0, y1, m, group, rot=0, pivot=None, bevel=0.0):
    """Axis box in three.js coords: x in [x0,x1], three-z in [z0,z1], height y0..y1; optional rotation (rad, three.js Y) about pivot."""
    bx0, by0 = P(x0, z1); bx1, by1 = P(x1, z0)
    v = [(bx0,by0,y0),(bx1,by0,y0),(bx1,by1,y0),(bx0,by1,y0),(bx0,by0,y1),(bx1,by0,y1),(bx1,by1,y1),(bx0,by1,y1)]
    f = [(0,3,2,1),(4,5,6,7),(0,1,5,4),(1,2,6,5),(2,3,7,6),(3,0,4,7)]
    o = mesh_obj(name, v, f, [m], group)
    if rot:
        px, py = P(*pivot) if pivot else (0, 0)
        o.data.transform(Matrix.Translation((-px, -py, 0)))
        o.data.transform(Matrix.Rotation(rot, 4, 'Z'))
        o.data.transform(Matrix.Translation((px, py, 0)))
    if bevel:
        b = o.modifiers.new('bev', 'BEVEL'); b.width = bevel; b.segments = 2; b.limit_method = 'ANGLE'
    return o

def quad_flat(name, x0, x1, z0, z1, y, m, group):
    a, b = P(x0, z1), P(x1, z0)
    return mesh_obj(name, [(a[0],a[1],y),(b[0],a[1],y),(b[0],b[1],y),(a[0],b[1],y)], [(0,1,2,3)], [m], group)

def strip(name, x0, z0, x1, z1, w, y, m, group):
    """Flat strip (road) from (x0,z0) to (x1,z1) in three coords, width w."""
    a, b = Vector((*P(x0, z0), 0)), Vector((*P(x1, z1), 0))
    d = (b - a).normalized(); n = Vector((-d.y, d.x, 0)) * (w / 2)
    v = [a - n, b - n, b + n, a + n]
    v = [(p.x, p.y, y) for p in v]
    return mesh_obj(name, v, [(0, 1, 2, 3)], [m], group)

LAYOUT = {'roads': [], 'lanes': [], 'rects': [], 'polys': []}
ROADS = []   # (x0,z0,x1,z1,half) for lane clipping
def lane(name, x0, z0, x1, z1, w, y, m, group):
    """Concrete farm lane split where it crosses an asphalt road (avoids double-layer lightmap texels)."""
    L = math.hypot(x1 - x0, z1 - z0); n = max(2, int(L / .25)); keep = []
    def inside(px, pz):
        for (a, b, c, d, h) in ROADS:
            vx, vz = c - a, d - b; t = max(0, min(1, ((px - a) * vx + (pz - b) * vz) / (vx * vx + vz * vz)))
            if math.hypot(px - (a + vx * t), pz - (b + vz * t)) < h + .15: return True
        return False
    run = None
    for i in range(n + 1):
        t = i / n; px, pz = x0 + (x1 - x0) * t, z0 + (z1 - z0) * t
        if not inside(px, pz):
            if run is None: run = t
        elif run is not None:
            keep.append((run, t)); run = None
    if run is not None: keep.append((run, 1))
    for k, (ta, tb) in enumerate(keep):
        if (tb - ta) * L < .5: continue
        strip(f'{name}_{k}', x0 + (x1 - x0) * ta, z0 + (z1 - z0) * ta, x0 + (x1 - x0) * tb, z0 + (z1 - z0) * tb, w, y, m, group)
    LAYOUT['lanes'].append([x0, z0, x1, z1, w])

PLACE_Y = [0.0]
def place(obj_list, x, z, rot, y=None):
    """Rotate (three.js Y rotation) and move a list of objects built around the origin (y = ground height)."""
    bx, by = P(x, z); y = PLACE_Y[0] if y is None else y
    T = Matrix.Translation((bx, by, y)) @ Matrix.Rotation(rot, 4, 'Z')
    for o in obj_list: o.data.transform(T)

def gable(name, L, Dp, H, o, m, group, thick=0.06):
    """Gable roof centred at origin, ridge along x, depth along three-z, base at y=0, with overhang o and sheet thickness."""
    x0, x1 = -L/2 - o, L/2 + o
    zf, zb = Dp/2 + o, -Dp/2 - o
    yeave = -o * H / (Dp / 2)
    def side(z_out):
        a = (x0, -z_out, yeave); b = (x1, -z_out, yeave); c = (x1, 0, H); d = (x0, 0, H)
        return [a, b, c, d]
    v = side(zf) + side(zb)
    f = [(0, 1, 2, 3), (5, 4, 7, 6)]
    obj = mesh_obj(name, v, f, [m], group)
    s = obj.modifiers.new('sol', 'SOLIDIFY'); s.thickness = thick; s.offset = 1
    # gable end walls (plaster-coloured triangles) are added by caller
    return obj

def tri_wall(name, L, Dp, H, m, group):
    v = [(-L/2, -Dp/2, 0), (-L/2, Dp/2, 0), (-L/2, 0, H), (L/2, Dp/2, 0), (L/2, -Dp/2, 0), (L/2, 0, H)]
    return mesh_obj(name, v, [(1, 0, 2), (4, 3, 5)], [m], group)

def window(name, cx, cz, y, w, h, face_rot, group, out=0.0):
    """Window on a wall facing three-z+ before rotation; returns objects (frame + glass)."""
    o = []
    for (a, b, c, d) in [(cx - w/2 - .08, cx + w/2 + .08, y + h/2, y + h/2 + .08), (cx - w/2 - .08, cx + w/2 + .08, y - h/2 - .08, y - h/2),
                         (cx - w/2 - .08, cx - w/2, y - h/2, y + h/2), (cx + w/2, cx + w/2 + .08, y - h/2, y + h/2)]:
        o.append(box(name + '_f', a, b, cz - .02 + out, cz + .13 + out, c, d, M['frame'], group))     # frame stands proud of the wall
    o.append(box(name + '_g', cx - w/2, cx + w/2, cz - .02 + out, cz + .03 + out, y - h/2, y + h/2, M['glass'], group))   # glass set back
    o.append(box(name + '_m', cx - .03, cx + .03, cz + out, cz + .1 + out, y - h/2, y + h/2, M['frame'], group))
    if GRILLE[0] and random.random() < .55:   # 방범창
        n = max(3, int(w / .14))
        for i in range(n + 1):
            bx_ = cx - w/2 + i * w / n
            o.append(box(name + '_bar', bx_ - .012, bx_ + .012, cz + .16 + out, cz + .19 + out, y - h/2 - .05, y + h/2 + .05, M['steel'], PROPS))
        for yy in (y - h/2 - .02, y + h/2 + .02):
            o.append(box(name + '_barh', cx - w/2 - .05, cx + w/2 + .05, cz + .13 + out, cz + .2 + out, yy - .02, yy + .02, M['steel'], PROPS))
    return o
GRILLE = [False]

# ---------------------------------------------------------------- CC0 assets (Poly Haven) + procedural props
PH = os.path.join(TEX, 'ph')
TEMPL = {}
DECIMATE = {'shrub_02': .06, 'shrub_04': .07, 'shrub_03': .2, 'weed_plant_02': .2, 'exterior_aircon_unit': .22,
            'korean_public_payphone_01': .15, 'plastic_crate_02': .5, 'compost_bags': .35, 'rusted_spade_01': .3, 'small_lpg_tank': .35,
            'plastic_monobloc_chair_01': .6, 'security_camera_01': .4, 'korean_fire_extinguisher_01': .4, 'covered_car': .5, 'utility_box_01': .6}
def template(aid):
    if aid in TEMPL: return TEMPL[aid]
    before = set(bpy.data.objects)
    gl = [f for f in os.listdir(os.path.join(PH, aid)) if f.endswith('.gltf')][0]
    bpy.ops.import_scene.gltf(filepath=os.path.join(PH, aid, gl))
    new = [o for o in bpy.data.objects if o not in before]
    meshes = [o for o in new if o.type == 'MESH']
    for o in meshes:
        mw = o.matrix_world.copy(); o.parent = None; o.data = o.data.copy(); o.matrix_world = Matrix(); o.data.transform(mw)
    for o in new:
        if o.type != 'MESH': bpy.data.objects.remove(o)
    bpy.ops.object.select_all(action='DESELECT')
    for o in meshes: o.select_set(True)
    bpy.context.view_layer.objects.active = meshes[0]
    if len(meshes) > 1: bpy.ops.object.join()
    t = bpy.context.active_object; t.name = 'T_' + aid
    if aid in DECIMATE:
        dm = t.modifiers.new('dec', 'DECIMATE'); dm.ratio = DECIMATE[aid]; bpy.ops.object.modifier_apply(modifier='dec')
    for m in t.data.materials:
        if m and m.node_tree:
            b = m.node_tree.nodes.get('Principled BSDF')
            if b and b.inputs['Alpha'].is_linked:
                try: m.blend_method = 'CLIP'
                except Exception: pass
    bb = [Vector(c) for c in t.bound_box]
    t['minz'] = min(v.z for v in bb); t['size'] = [max(v[i] for v in bb) - min(v[i] for v in bb) for i in range(3)]
    for coll in list(t.users_collection): coll.objects.unlink(t)
    TEMPL[aid] = t
    return t
def prop(aid, x, z, rot=0.0, y=0.0, s=1.0, fit=None, group=None, local=True):
    """Copy of a Poly Haven asset. With local=True the copy is placed in a caller's local frame (later moved by place())."""
    t = template(aid)
    o = t.copy(); o.data = t.data.copy(); o.name = aid
    if fit:   # fit=(axis, metres)
        s = fit[1] / max(t['size'][fit[0]], 1e-3)
    bx, by = P(x, z)
    o.data.transform(Matrix.Translation((bx, by, y - t['minz'] * s)) @ Matrix.Rotation(rot, 4, 'Z') @ Matrix.Scale(s, 4))
    o['keepuv'] = True
    return link(o, group if group is not None else PROPS)

def lathe(name, profile, m, group, x=0, z=0, y=0, seg=20):
    """Revolve a (radius, height) profile around the vertical axis."""
    v, f = [], []
    for i in range(seg):
        a = 2 * math.pi * i / seg
        for (r, h) in profile: v.append((math.cos(a) * r, math.sin(a) * r, h))
    n = len(profile)
    for i in range(seg):
        j = (i + 1) % seg
        for k in range(n - 1): f.append((i * n + k, j * n + k, j * n + k + 1, i * n + k + 1))
    v.append((0, 0, profile[-1][1])); top = len(v) - 1
    for i in range(seg): f.append((i * n + n - 1, ((i + 1) % seg) * n + n - 1, top))
    o = mesh_obj(name, v, f, [m], group)
    bx, by = P(x, z); o.data.transform(Matrix.Translation((bx, by, y)))
    return o
def onggi(name, x, z, s, group):   # 장독 (Korean earthenware jar) with lid
    prof = [(.18, 0), (.30, .12), (.38, .32), (.37, .5), (.26, .66), (.22, .7)]
    a = lathe(name, [(r * s, h * s) for r, h in prof], M['onggi'], group, x, z)
    b = lathe(name + '_lid', [(.27 * s, .7 * s), (.24 * s, .76 * s), (.06 * s, .8 * s)], M['onggi'], group, x, z)
    return [a, b]


# ================================================================ real layout (world.json, game coords: x east, y north = Blender x, y)
import json
WJ = json.load(open(os.path.join(WORLD, 'world.json'))); HJ = json.load(open(os.path.join(WORLD, 'height.json')))
import base64
_hu = np.frombuffer(base64.b64decode(HJ['data']), dtype='<u2').reshape(HJ['h'], HJ['w']).astype(np.float32) * HJ['scale'] + HJ['hmin']
def hget(x, y):
    i = (x - HJ['x0']) / HJ['step']; j = (y - HJ['y0']) / HJ['step']
    i = min(max(i, 0), HJ['w'] - 1.001); j = min(max(j, 0), HJ['h'] - 1.001); i0, j0 = int(i), int(j); ti, tj = i - i0, j - j0
    return float((_hu[j0, i0] * (1 - ti) + _hu[j0, i0 + 1] * ti) * (1 - tj) + (_hu[j0 + 1, i0] * (1 - ti) + _hu[j0 + 1, i0 + 1] * ti) * tj)
X0, Y0, X1, Y1 = REG
def inreg(x, y, m=0.0): return X0 + m <= x <= X1 - m and Y0 + m <= y <= Y1 - m
G3 = lambda gx, gy: (gx, -gy)   # game -> three (x, z)
SHADE = set()
# ---- ground: 2 m grid following the terrain, material per cell from the land classes
gimg = bpy.data.images.load(os.path.join(WORLD, WJ['ground']['file'])); gimg.colorspace_settings.name = 'Non-Color'; gw, gh_ = gimg.size
gpx = np.array(gimg.pixels[:], dtype=np.float32).reshape(gh_, gw, 4)[::-1, :, 0]   # row 0 = north edge
def gcls(x, y):
    c = int((x - WJ['ground']['x0']) / WJ['ground']['res']); r_ = int((WJ['ground']['y1'] - y) / WJ['ground']['res'])
    if 0 <= r_ < gh_ and 0 <= c < gw: return int(round(gpx[r_, c] * 255))
    return 0
STEP = 2.0
nx, ny = int((X1 - X0) / STEP), int((Y1 - Y0) / STEP)
verts = [(X0 + i * STEP, Y0 + j * STEP, hget(X0 + i * STEP, Y0 + j * STEP)) for j in range(ny + 1) for i in range(nx + 1)]
faces, fm = [], []
CLS_MAT = {0: 0, 1: 1, 2: 2, 3: 3, 4: 1, 5: 0, 6: 3}
for j in range(ny):
    for i in range(nx):
        a = j * (nx + 1) + i; faces.append((a, a + 1, a + nx + 2, a + nx + 1))
        fm.append(CLS_MAT.get(gcls(X0 + (i + .5) * STEP, Y0 + (j + .5) * STEP), 0))
mesh_obj('ground', verts, faces, [M['g_ground'], M['g_paddy'], M['lane_b'], M['g_yard']], GROUND, face_mats=fm)
# paddy banks (논두렁): raised grass strips on a rotated 22 x 70 m grid, only where both sides are paddy
ca_, sa_ = .946, .326
def to_field(x, y): return (ca_ * x + sa_ * y, -sa_ * x + ca_ * y)
def from_field(u, v): return (ca_ * u - sa_ * v, sa_ * u + ca_ * v)
umin, umax = -700, 700
nb = 0
for axis, spacing, other in ((0, 22.0, 70.0), (1, 70.0, 22.0)):
    k0 = int(umin // spacing)
    for k in range(k0, int(umax // spacing) + 1):
        c = k * spacing
        for t in np.arange(umin, umax, 4.0):
            u, v = (c, t) if axis == 0 else (t, c)
            x, y = from_field(u, v)
            if not inreg(x, y, 2): continue
            dx, dy = from_field(1, 0) if axis == 0 else from_field(0, 1)
            if gcls(x + dx * 2, y + dy * 2) != 1 or gcls(x - dx * 2, y - dy * 2) != 1: continue
            ex, ey = from_field(0, 2.05) if axis == 0 else from_field(2.05, 0)
            z = hget(x, y)
            p = [(x - ex - dx * .3, y - ey - dy * .3), (x + ex - dx * .3, y + ey - dy * .3), (x + ex + dx * .3, y + ey + dy * .3), (x - ex + dx * .3, y - ey + dy * .3)]
            vv = [(px, py, z - .05) for px, py in p] + [(px, py, z + .3) for px, py in p]
            mesh_obj('bund', vv, [(0, 1, 2, 3)[::-1], (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)], [M['g_bund']], GROUND); nb += 1
print('bunds', nb)
# ---- roads: ribbons with real heights; asphalt gets painted lines
def ribbon(name, pts, zs, w, lift, m, group):
    v, f = [], []
    for k, (p, z) in enumerate(zip(pts, zs)):
        a = pts[max(0, k - 1)]; b = pts[min(len(pts) - 1, k + 1)]; dx, dy = b[0] - a[0], b[1] - a[1]; l = math.hypot(dx, dy) or 1
        nx_, ny_ = -dy / l, dx / l
        v += [(p[0] + nx_ * w / 2, p[1] + ny_ * w / 2, z + lift), (p[0] - nx_ * w / 2, p[1] - ny_ * w / 2, z + lift)]
        if k: o = k * 2; f.append((o - 2, o - 1, o + 1, o))
    return mesh_obj(name, v, f, [m], group) if f else None
def offset_line(pts, off):
    out = []
    for k, p in enumerate(pts):
        a = pts[max(0, k - 1)]; b = pts[min(len(pts) - 1, k + 1)]; dx, dy = b[0] - a[0], b[1] - a[1]; l = math.hypot(dx, dy) or 1
        out.append((p[0] - dy / l * off, p[1] + dx / l * off))
    return out
MARK = {'motorway': 3, 'trunk': 3, 'primary': 2, 'secondary': 2, 'tertiary': 1}
ROAD_SEGS = []
for ri, rd in enumerate(WJ['roads']):
    run = [(p, z) for p, z in zip(rd['pts'], rd['z']) if inreg(p[0], p[1], -20)]
    if len(run) < 2: continue
    pts = [p for p, _ in run]; zs = [z for _, z in run]
    for a, b in zip(pts, pts[1:]): ROAD_SEGS.append((a, b, rd['w']))
    if rd['w'] >= 5.5:
        ribbon(f'road{ri}', pts, zs, rd['w'], .06, M['g_asphalt'], GROUND)
        mk = MARK.get(rd['kind'], 0)
        if mk:
            for s in (-1, 1): ribbon(f'lw{ri}', offset_line(pts, s * (rd['w'] / 2 - .35)), zs, .14, .075, M['g_paintW'], GROUND)
            if mk >= 2:
                for s in (-1, 1): ribbon(f'ly{ri}', offset_line(pts, s * .12), zs, .12, .075, M['g_paintY'], GROUND)
            else: ribbon(f'ly{ri}', pts, zs, .13, .075, M['g_paintY'], GROUND)
    elif rd['w'] >= 2.5: ribbon(f'lane{ri}', pts, zs, rd['w'], .05, M['g_lane'], GROUND)
    else: ribbon(f'path{ri}', pts, zs, rd['w'], .03, M['g_yard'], GROUND)
def road_near(x, y):
    best = None
    for a, b, w in ROAD_SEGS:
        dx, dy = b[0] - a[0], b[1] - a[1]; L2 = dx * dx + dy * dy or 1; t = max(0, min(1, ((x - a[0]) * dx + (y - a[1]) * dy) / L2))
        d = math.hypot(a[0] + t * dx - x, a[1] + t * dy - y)
        if best is None or d < best[0]: best = (d, a[0] + t * dx, a[1] + t * dy, w)
    return best
# ---- village hall (the approved lookdev model), turned and set on the terrain
HX, HY, HROT = 0.0, 2.0, .12
before = set(bpy.data.objects)
hall = []
hall.append(box('hall_body', -9, 9, -5, 5, 0, 4.2, M['plaster'], BUILD))
hall.append(box('hall_base', -9.06, 9.06, -5.06, 5.06, 0, .9, M['tile'], BUILD))
r = gable('hall_roof', 18, 10, 1.9, .8, M['roof_blue'], BUILD); r.data.transform(Matrix.Translation((0, 0, 4.2))); hall.append(r)
t = tri_wall('hall_gable', 18, 10, 1.9, M['plaster'], BUILD); t.data.transform(Matrix.Translation((0, 0, 4.2)))
hall.append(box('annex_body', -17, -9, -5, 3, 0, 3.6, M['plaster2'], BUILD))
hall.append(box('annex_base', -17.05, -9, -5.05, 3.05, 0, .8, M['tile'], BUILD))
ar = gable('annex_roof', 8, 8, 1.4, .6, M['roof_red'], BUILD)
ar.data.transform(Matrix.Rotation(math.pi / 2, 4, 'Z')); ar.data.transform(Matrix.Translation((*P(-13, -1), 3.6)))
at = tri_wall('annex_gable', 8, 8, 1.4, M['plaster2'], BUILD)
at.data.transform(Matrix.Rotation(math.pi / 2, 4, 'Z')); at.data.transform(Matrix.Translation((*P(-13, -1), 3.6)))
for k, wx in enumerate([-6, 2, 6]):
    window(f'hall_w{k}', wx, 5, 2.15, 2.4, 1.3, 0, BUILD)
window('annex_w', -13, 3, 2.0, 1.8, 1.1, 0, BUILD)
# entrance: aluminium door + canopy + step
box('hall_door_f', -3.25, -0.75, 4.98, 5.1, 0, 2.6, M['frame'], BUILD)
box('hall_door_g', -3.1, -0.9, 5.0, 5.12, .05, 2.45, M['glass'], BUILD)
box('hall_door_mul', -2.03, -1.97, 5.0, 5.14, .05, 2.45, M['frame'], BUILD)
box('hall_canopy', -4, 0, 5, 6.9, 2.85, 2.97, M['panel_white'], BUILD)
for sx in (-3.85, -.15):
    box(f'hall_canopy_post{sx}', sx - .05, sx + .05, 6.75, 6.85, 0, 2.85, M['steel'], BUILD)
box('hall_step', -4, 0, 5, 6.2, 0, .15, M['yard_b'], BUILD)
box('hall_sign', -3.5, 3.5, 5.0, 5.08, 3.0, 4.1, M['sign_hall'], BUILD)
box('elder_sign', -14.1, -11.9, 3.0, 3.07, 2.55, 3.25, M['sign_elder'], BUILD)
# side windows on the east wall
for k, wz in enumerate([-2, 2]):
    o = window(f'hall_e{k}', 0, 0, 2.15, 1.6, 1.1, 0, BUILD)
    for oo in o:
        oo.data.transform(Matrix.Rotation(math.pi / 2, 4, 'Z')); oo.data.transform(Matrix.Translation((*P(9.0, wz), 0)))
# 정자 + 평상 + 버스정류장
jx, jz = 10, 15
for (a, b) in [(-1.8, -1.8), (1.8, -1.8), (-1.8, 1.8), (1.8, 1.8)]:
    box(f'jz_post{a}{b}', jx + a - .12, jx + a + .12, jz + b - .12, jz + b + .12, 0, 2.7, M['wood'], BUILD, bevel=.02)
box('jz_floor', jx - 2.2, jx + 2.2, jz - 2.2, jz + 2.2, .45, .62, M['wood'], BUILD, bevel=.02)
jr = gable('jz_roof', 4.4, 4.4, 1.2, .5, M['roof_green'], BUILD); jr.data.transform(Matrix.Translation((*P(jx, jz), 2.7)))
jt = tri_wall('jz_gable', 4.4, 4.4, 1.2, M['wood_dark'], BUILD); jt.data.transform(Matrix.Translation((*P(jx, jz), 2.7)))
box('pyeong', -10.2, -7.8, 10.1, 11.9, .42, .54, M['wood_dark'], BUILD, bevel=.015)
for (a, b) in [(-1.1, -.8), (1.1, -.8), (-1.1, .8), (1.1, .8)]:
    box(f'pyeong_leg{a}{b}', -9 + a - .05, -9 + a + .05, 11 + b - .05, 11 + b + .05, 0, .42, M['wood_dark'], BUILD)
BZ = 37.0   # shelter just north of the village road
bus = [box('bus_roof', -21.6, -18.4, BZ - .1, BZ + 1.3, 2.45, 2.55, M['panel_white'], BUILD),
       box('bus_back', -21.6, -18.4, BZ - .1, BZ - .02, .4, 2.45, M['glass'], BUILD),
       box('bus_seat', -21.2, -18.8, BZ, BZ + .4, .42, .48, M['wood'], BUILD),
       box('bus_sign', -20.7, -19.3, BZ + 1.3, BZ + 1.35, 2.6, 3.3, M['sign_bus'], BUILD)]
for a in (-1.5, 1.5):
    bus.append(box(f'bus_post{a}', -20 + a - .05, -20 + a + .05, BZ - .1, BZ, 0, 2.45, M['steel'], BUILD))

hall_objs = [o for o in bpy.data.objects if o not in before]
T = Matrix.Translation((HX, HY, hget(HX, HY))) @ Matrix.Rotation(HROT, 4, 'Z')
for o in hall_objs:
    if o.type == 'MESH': o.data.transform(T)
# hall yard: concrete slabs
yard_v = []
for gx in range(-20, 14, 4):
    for gz in range(5, 21, 4):
        o = quad_flat(f'slab_{gx}_{gz}', gx + .03, min(gx + 4, 14) - .03, gz + .03, min(gz + 4, 21) - .03, 0.05, M['g_yard'], GROUND); o.data.transform(T)
# ---------------------------------------------------------------- houses
HOUSE_ANCHORS = []
def house(name, x, z, rot, w, d, roof_m, wall=False, gate_m=None, h=3.1):
    objs = []; GRILLE[0] = True
    objs.append(box(name + '_body', -w/2, w/2, -d/2, d/2, 0, h, random.choice([M['plaster'], M['plaster2']]), BUILD))
    objs.append(box(name + '_base', -w/2 - .04, w/2 + .04, -d/2 - .04, d/2 + .04, 0, .6, M['tile'], BUILD))
    rr = gable(name + '_roof', w, d, 1.6, .7, roof_m, BUILD); rr.data.transform(Matrix.Translation((0, 0, h))); objs.append(rr)
    tw = tri_wall(name + '_gable', w, d, 1.6, M['plaster'], BUILD); tw.data.transform(Matrix.Translation((0, 0, h))); objs.append(tw)
    n = max(2, int(w // 3.2))
    for k in range(n):
        wx = -w/2 + (k + .5) * w / n
        if k == 0:
            objs.append(box(name + '_door', wx - .55, wx + .55, d/2 - .02, d/2 + .08, 0, 2.1, M['frame'], BUILD))
            objs.append(box(name + '_doorg', wx - .45, wx + .45, d/2, d/2 + .1, .05, 2.0, M['glass'], BUILD))
        else:
            objs += window(f'{name}_w{k}', wx, d/2, 1.75, 1.6, 1.2, 0, BUILD)
            objs.append(box(f'{name}_sill{k}', wx - .9, wx + .9, d/2, d/2 + .14, 1.06, 1.12, M['yard_b'], BUILD))
    objs.append(box(name + '_veranda', -w/2, w/2, d/2, d/2 + 1.6, 0, .25, M['yard_b'], BUILD))
    if wall:
        W, Dd, wh = w + 6, d + 8, 1.5
        segs = [(-W/2, W/2, -Dd/2, -Dd/2 + .2), (-W/2, -W/2 + .2, -Dd/2, Dd/2), (W/2 - .2, W/2, -Dd/2, Dd/2),
                (-W/2, -1.9, Dd/2 - .2, Dd/2), (1.9, W/2, Dd/2 - .2, Dd/2)]
        for k, (a, b, c, e) in enumerate(segs):
            objs.append(box(f'{name}_wall{k}', a, b, c + 1.5, e + 1.5, 0, wh, M['block'], BUILD))
            objs.append(box(f'{name}_cap{k}', a - .03, b + .03, c + 1.47, e + 1.53, wh, wh + .08, M['yard_b'], BUILD))
        objs.append(box(name + '_gate', -1.9, 1.9, Dd/2 + 1.35, Dd/2 + 1.45, 0, 1.8, gate_m or M['paint_blue'], BUILD))
        for j in range(5):   # 장독대 in the back yard
            objs += onggi(f'{name}_jar{j}', -W/2 + 1.4 + (j % 3) * .85, -Dd/2 + 2.2 + (j // 3) * .8, R(.85, 1.15), BUILD)
        objs.append(box(name + '_jarbase', -W/2 + .9, -W/2 + 3.8, -Dd/2 + 1.6, -Dd/2 + 3.4, 0, .12, M['yard_b'], BUILD))
    # eave gutters, downspouts, ridge cap
    ye = h - .7 * 1.6 / (d / 2) - .06
    for sz in (-1, 1):
        objs.append(box(f'{name}_gut{sz}', -w/2 - .7, w/2 + .7, sz * (d/2 + .72) - .07, sz * (d/2 + .72) + .07, ye - .1, ye, M['gutter'], BUILD))
        objs.append(box(f'{name}_dsp{sz}', w/2 + .55, w/2 + .63, sz * (d/2 + .72) - .04, sz * (d/2 + .72) + .04, 0, ye, M['gutter'], BUILD))
    objs.append(box(name + '_ridge', -w/2 - .7, w/2 + .7, -.18, .18, h + 1.6 + .02, h + 1.6 + .09, roof_m, BUILD))
    # everyday clutter (CC0 assets)
    objs.append(prop('exterior_aircon_unit', w/2 + .5, R(-d/4, d/4), rot=math.pi / 2, fit=(0, .85)))
    for j in range(2): objs.append(prop('small_lpg_tank', -w/2 - .45, -d/2 + .5 + j * .55, fit=(2, 1.25)))
    objs.append(prop('utility_box_01', w/2 - .6, d/2 + .08, y=1.3, fit=(2, .45)))
    for j in range(3): objs.append(prop('planter_pot_clay', -w/2 + 1.6 + j * .6, d/2 + 1.25, y=.25, fit=(2, .38)))
    objs.append(prop('plastic_monobloc_chair_01', w/2 - 1.4, d/2 + .9, rot=R(-.6, .6), y=.25, fit=(2, .82)))
    objs.append(prop(random.choice(['trashbag', 'old_tyre', 'plastic_crate_02', 'plastic_jerrycan']), -w/2 - .6, d/2 - .6, rot=R(0, 6), fit=(2, .55)))
    objs.append(prop(random.choice(['shrub_02', 'shrub_03', 'shrub_04']), w/2 + 1.2, -d/2 - 1.0, rot=R(0, 6), fit=(2, R(1.0, 1.6))))
    # clothesline behind the house
    for sx in (-w/2 + 1, w/2 - 1): objs.append(box(f'{name}_clp{sx}', sx - .04, sx + .04, -d/2 - 2.3, -d/2 - 2.22, 0, 1.9, M['steel'], BUILD))
    objs.append(box(name + '_cl', -w/2 + 1, w/2 - 1, -d/2 - 2.28, -d/2 - 2.26, 1.85, 1.87, M['rope'], BUILD))
    GRILLE[0] = False
    ca, sa = math.cos(rot), math.sin(rot); ex, ez = w/2 + 3.2, d/2 + 4.2
    ax_, az_ = w/2 - .8, -d/2 + .3   # service-drop anchor under the eave
    HOUSE_ANCHORS.append(Vector((*P(x + ax_ * ca + az_ * sa, z - ax_ * sa + az_ * ca), h + .1)))
    LAYOUT['polys'].append([[x + px * ca + pz * sa, z - px * sa + pz * ca] for px, pz in [(-ex, -ez), (ex, -ez), (ex, ez), (-ex, ez)]])
    place(objs, x, z, rot)

# ---------------------------------------------------------------- greenhouses (film not lightmapped)
def greenhouse(name, x, z, length, beds, rot=0.0, r=3.6):
    objs_b, objs_n = [], []
    bpy.ops.mesh.primitive_cylinder_add(vertices=28, radius=r, depth=length, end_fill_type='NOTHING')
    film = bpy.context.active_object; film.name = name + '_film'
    bpy.context.collection.objects.unlink(film); bpy.context.scene.collection.objects.unlink(film) if film.name in bpy.context.scene.collection.objects else None
    film.data.transform(Matrix.Rotation(math.pi / 2, 4, 'X'))   # axis along Blender Y (= three z)
    bm = bmesh.new(); bm.from_mesh(film.data)
    bmesh.ops.delete(bm, geom=[v for v in bm.verts if v.co.z < -1e-4], context='VERTS')
    bm.to_mesh(film.data); bm.free()
    film.data.materials.append(M['film']); link(film, NOLM); objs_n.append(film)
    for s in (-1, 1):   # end caps (half discs)
        v = [(0, s * length / 2, 0)] + [(math.cos(a) * r, s * length / 2, math.sin(a) * r) for a in np.linspace(0, math.pi, 15)]
        f = [(0, k + 1, k + 2) if s > 0 else (0, k + 2, k + 1) for k in range(14)]
        objs_n.append(mesh_obj(f'{name}_cap{s}', v, f, [M['film']], NOLM))
    for t in np.arange(-length / 2, length / 2 + .01, 4.0):    # arched pipe ribs
        bpy.ops.mesh.primitive_torus_add(major_radius=r, minor_radius=.025, major_segments=24, minor_segments=4)
        tor = bpy.context.active_object; tor.name = f'{name}_rib'
        tor.data.transform(Matrix.Rotation(math.pi / 2, 4, 'X')); tor.data.transform(Matrix.Translation((0, t, 0)))
        bm = bmesh.new(); bm.from_mesh(tor.data)
        bmesh.ops.delete(bm, geom=[v for v in bm.verts if v.co.z < -.03], context='VERTS'); bm.to_mesh(tor.data); bm.free()
        tor.data.materials.append(M['rib'])
        bpy.context.collection.objects.unlink(tor); link(tor, NOLM); objs_n.append(tor)
    objs_b.append(box(name + '_floor', -r, r, -length / 2, length / 2, 0, .02, M['lane_b'], BUILD))
    for sx in (-1, 1):   # side film rolled up to ~1 m for ventilation
        ang = math.acos(min(1, 1.0 / r)); xx = sx * r * math.sin(ang)
        bpy.ops.mesh.primitive_cylinder_add(vertices=10, radius=.07, depth=length, location=(xx, 0, 1.0), rotation=(math.pi / 2, 0, 0))
        roll = bpy.context.active_object; roll.data.transform(roll.matrix_basis); roll.matrix_basis = Matrix()
        roll.data.materials.append(M['film_roll']); bpy.context.collection.objects.unlink(roll); link(roll, BUILD); objs_b.append(roll)
    for sz in (-1, 1):   # end door frame
        zz = sz * length / 2
        for px_ in (-.9, .9): objs_b.append(box(f'{name}_dp{sz}{px_}', px_ - .04, px_ + .04, zz - .04, zz + .04, 0, 2.1, M['steel'], BUILD))
        objs_b.append(box(f'{name}_dh{sz}', -.95, .95, zz - .04, zz + .04, 2.06, 2.14, M['steel'], BUILD))
    if name in SHADE:
        bpy.ops.mesh.primitive_cylinder_add(vertices=28, radius=r + .06, depth=length * .9, end_fill_type='NOTHING', rotation=(math.pi / 2, 0, 0))
        net = bpy.context.active_object; net.data.transform(net.matrix_basis); net.matrix_basis = Matrix()
        bm = bmesh.new(); bm.from_mesh(net.data); bmesh.ops.delete(bm, geom=[v for v in bm.verts if v.co.z < r * .35], context='VERTS'); bm.to_mesh(net.data); bm.free()
        net.data.materials.append(M['shadenet']); bpy.context.collection.objects.unlink(net); link(net, NOLM); objs_n.append(net)
    if beds:
        for b in (-1.8, 0, 1.8):
            objs_b.append(box(f'{name}_bench{b}', b - .4, b + .4, -length / 2 + 1, length / 2 - 1, .1, .95, M['bench'], BUILD))
            objs_b.append(box(f'{name}_plant{b}', b - .45, b + .45, -length / 2 + 1, length / 2 - 1, .95, 1.2, M['plant'], BUILD))
    LAYOUT['rects'].append([x - 4, x + 4, z - length / 2 - 1, z + length / 2 + 1, 'gh'])
    place(objs_b + objs_n, x, z, rot)

# ---------------------------------------------------------------- warehouses
def warehouse(name, x, z, rot, w, d, h, wall_m, roof_m):
    objs = [box(name + '_body', -w/2, w/2, -d/2, d/2, 0, h, wall_m, BUILD)]
    rr = gable(name + '_roof', w, d, 1.4, .4, roof_m, BUILD); rr.data.transform(Matrix.Translation((0, 0, h))); objs.append(rr)
    tw = tri_wall(name + '_gable', w, d, 1.4, wall_m, BUILD); tw.data.transform(Matrix.Translation((0, 0, h))); objs.append(tw)
    rd = template('rollershutter_door'); rr_ = 0 if rd['size'][0] >= rd['size'][1] else math.pi / 2
    objs.append(prop('rollershutter_door', -w/4, d/2 + .06, rot=rr_, fit=(0 if rr_ == 0 else 1, 4.2)))
    for j in range(3): objs.append(prop(random.choice(['compost_bags', 'cement_bag', 'plastic_crate_02']), w/4 + j * 1.3, d/2 + 1.2, rot=R(-.3, .3), fit=(0, 1.0)))
    objs.append(prop('plastic_jerrycan', w/2 - .5, d/2 + .6, rot=R(0, 6), fit=(2, .45)))
    ca, sa = math.cos(rot), math.sin(rot); ex, ez = w/2 + 1, d/2 + 4.5
    LAYOUT['polys'].append([[x + px * ca + pz * sa, z - px * sa + pz * ca] for px, pz in [(-ex, -ez), (ex, -ez), (ex, ez), (-ex, ez)]])
    objs.append(box(name + '_doorhead', -w/4 - 2.2, -w/4 + 2.2, d/2, d/2 + .4, 4, 4.5, M['steel'], BUILD))
    objs.append(box(name + '_apron', -w/2, w/2, d/2, d/2 + 4, 0, .08, M['yard_b'], BUILD))
    place(objs, x, z, rot)
    return objs
def wire_curve(a, b, sag, name='wire'):
    cu = bpy.data.curves.new(name, 'CURVE'); cu.dimensions = '3D'; cu.bevel_depth = .01; cu.bevel_resolution = 1
    sp = cu.splines.new('POLY'); pts = []
    for i in range(17):
        tt = i / 16; p_ = a.lerp(b, tt); p_.z -= math.sin(tt * math.pi) * sag; pts.append(p_)
    sp.points.add(len(pts) - 1)
    for i, p_ in enumerate(pts): sp.points[i].co = (p_.x, p_.y, p_.z, 1)
    ob = bpy.data.objects.new(name, cu); ob.data.materials.append(M['wire']); bpy.context.collection.objects.link(ob); NOLM.append(ob)
# ---------------------------------------------------------------- trees
def tree(name, x, z, s):
    bx, by = P(x, z); h = 6.5 * s
    bpy.ops.mesh.primitive_cone_add(vertices=10, radius1=.22 * s, radius2=.07 * s, depth=h * .75, location=(bx, by, h * .375))
    tr = bpy.context.active_object; tr.data.materials.append(M['bark']); bpy.context.collection.objects.unlink(tr); link(tr, PROPS)
    tr.data.transform(tr.matrix_basis); tr.matrix_basis = Matrix()
    centers = []
    for k in range(7):   # branches
        a = R(0, 2 * math.pi); el = R(.35, .9); L = R(1.6, 2.6) * s; y0 = R(.45, .7) * h
        d = Vector((math.cos(a) * math.cos(el), math.sin(a) * math.cos(el), math.sin(el)))
        p0 = Vector((bx, by, y0)); p1 = p0 + d * L; centers.append(p1)
        bpy.ops.mesh.primitive_cone_add(vertices=6, radius1=.07 * s, radius2=.025 * s, depth=L, location=(p0 + p1) / 2)
        br = bpy.context.active_object; br.rotation_euler = d.to_track_quat('Z', 'Y').to_euler()
        br.data.transform(br.matrix_basis); br.matrix_basis = Matrix()
        br.data.materials.append(M['bark']); bpy.context.collection.objects.unlink(br); link(br, PROPS)
    v, f, uvs = [], [], []
    for k in range(300):   # leaf cards clustered around branch tips and the crown
        c = random.choice(centers) if k % 3 else Vector((bx, by, h * .82))
        p = c + Vector((R(-1.3, 1.3), R(-1.3, 1.3), R(-.9, 1.0))) * s
        sz = R(.7, 1.1) * s
        n = Vector((R(-1, 1), R(-1, 1), R(-.4, 1))).normalized()
        t1 = n.cross(Vector((0, 0, 1))); t1 = t1.normalized() if t1.length > 1e-3 else Vector((1, 0, 0)); t2 = n.cross(t1)
        i = len(v)
        for (a_, b_) in [(-1, -1), (1, -1), (1, 1), (-1, 1)]: v.append(tuple(p + t1 * a_ * sz / 2 + t2 * b_ * sz / 2))
        f.append((i, i + 1, i + 2, i + 3)); uvs += [(0, 0), (1, 0), (1, 1), (0, 1)]
    lo = mesh_obj(name + '_leaves', v, f, [M['leafcard']], PROPS)
    uvl = lo.data.uv_layers.new(name='UVMap')
    for li, uv in enumerate(uvs): uvl.data[li].uv = uv
    lo['keepuv'] = True

# ---------------------------------------------------------------- vehicles
def truck(x, z, rot):
    o = []
    o.append(box('trk_cab', -.88, .88, 1.1, 2.75, .45, 2.05, M['paint_blue'], BUILD, bevel=.06))
    o.append(box('trk_ws', -.8, .8, 2.7, 2.78, 1.35, 1.95, M['glass'], BUILD))
    for s in (-1, 1): o.append(box(f'trk_sw{s}', s * .89 - .01, s * .89 + .01, 1.5, 2.45, 1.3, 1.85, M['glass'], BUILD))
    o.append(box('trk_bed', -.9, .9, -1.9, 1.05, .6, .8, M['steel'], BUILD))
    for s in (-1, 1): o.append(box(f'trk_side{s}', s * .9 - .03, s * .9 + .03, -1.9, 1.05, .8, 1.25, M['steel'], BUILD))
    o.append(box('trk_tail', -.9, .9, -1.93, -1.87, .8, 1.25, M['steel'], BUILD))
    o.append(box('trk_bumper', -.9, .9, 2.7, 2.85, .35, .6, M['steel'], BUILD))
    for k in range(6):
        o.append(box(f'trk_box{k}', -.65 + (k % 2) * .75, -.15 + (k % 2) * .75, -1.5 + (k // 2) * .55, -1.05 + (k // 2) * .55, .8, 1.08, M['box_white'], BUILD))
    for (a, b) in [(-.8, 1.9), (.8, 1.9), (-.8, -1.1), (.8, -1.1)]:
        bpy.ops.mesh.primitive_cylinder_add(vertices=18, radius=.36, depth=.24, location=(a, -b, .36), rotation=(0, math.pi / 2, 0))
        wh = bpy.context.active_object; wh.data.transform(wh.matrix_basis); wh.matrix_basis = Matrix()
        wh.data.materials.append(M['tire']); bpy.context.collection.objects.unlink(wh); link(wh, BUILD); o.append(wh)
    place(o, x, z, rot)
def tiller(x, z, rot):
    o = []
    o.append(box('til_engine', -.35, .35, .7, 1.8, .6, 1.3, M['paint_red'], BUILD, bevel=.05))
    o.append(box('til_tank', -.25, .25, .9, 1.4, 1.3, 1.55, M['paint_red'], BUILD, bevel=.04))
    for s in (-1, 1): o.append(box(f'til_h{s}', s * .3 - .025, s * .3 + .025, 1.8, 3.2, 1.05, 1.12, M['steel'], BUILD))
    o.append(box('til_bed', -.75, .75, -1.8, .55, .55, .62, M['steel'], BUILD))
    for (a, b, c, d) in [(-.78, -.72, -1.8, .55), (.72, .78, -1.8, .55)]:
        o.append(box('til_rail', a, b, c, d, .62, 1.0, M['steel'], BUILD))
    o.append(box('til_back', -.75, .75, -1.83, -1.77, .62, 1.0, M['steel'], BUILD))
    for (a, b, r) in [(-.55, 1.2, .42), (.55, 1.2, .42), (-.82, -.9, .3), (.82, -.9, .3)]:
        bpy.ops.mesh.primitive_cylinder_add(vertices=16, radius=r, depth=.2, location=(a, -b, r), rotation=(0, math.pi / 2, 0))
        wh = bpy.context.active_object; wh.data.transform(wh.matrix_basis); wh.matrix_basis = Matrix()
        wh.data.materials.append(M['tire']); bpy.context.collection.objects.unlink(wh); link(wh, BUILD); o.append(wh)
    place(o, x, z, rot)
# ---- buildings from the satellite pass
ROOFM = {'blue': ['roof_blue'], 'teal': ['roof_green'], 'warm': ['roof_red', 'roof_orange'], 'white': ['panel_grey', 'panel_white']}
nh = nw = 0
for k, b in enumerate(WJ['buildings']):
    x, y = b['c']
    if not inreg(x, y, 6) or math.hypot(x - HX, y - HY) < 30: continue
    PLACE_Y[0] = b['z']; rm = M[random.choice(ROOFM.get(b.get('rc') or 'white', ['roof_blue']))]
    if b['kind'] == 'house':
        w, d = min(max(b['l'], 7.5), 17), min(max(b['w'], 5.8), 10.5)
        house(f'h{k}', x, -y, b['a'], w, d, rm, wall=random.random() < .55, gate_m=random.choice([M['paint_blue'], M['roof_green'], M['steel']])); nh += 1
    elif b['kind'] == 'warehouse':
        w, d = min(b['l'], 46), min(b['w'], 26)
        wm = M['panel_blue'] if b.get('rc') == 'blue' else M['panel_white'] if b.get('rc') == 'white' else random.choice([M['panel_grey'], M['panel_blue']])
        warehouse(f'w{k}', x, -y, b['a'], w, d, random.uniform(4.6, 6.2), wm, rm); nw += 1
        if random.random() < .35: PLACE_Y[0] = hget(x, y); truck(x + math.cos(b['a']) * (w / 2 + 3), -(y + math.sin(b['a']) * (w / 2 + 3)), b['a'] + 1.4)
    else:
        PLACE_Y[0] = b['z']; house(f'h{k}', x, -y, b['a'], min(b['l'], 18), min(b['w'], 11), rm, h=3.2 * max(1, b.get('floors', 1))); nh += 1
print('houses', nh, 'warehouses', nw)
# ---- greenhouses (one tunnel per detected row)
ng = 0
for k, g in enumerate(WJ['greenhouses']):
    x, y = g['c']
    if not inreg(x, y, 4) or g['l'] < 10: continue
    PLACE_Y[0] = g['z']; greenhouse(f'gh{k}', x, -y, min(g['l'], 80), beds=random.random() < .15, rot=g['a'] - math.pi / 2, r=max(2.6, min(g['w'] / 2, 4.0)))
    if random.random() < .12: SHADE.add(f'gh{k}')
    ng += 1
print('greenhouses', ng)
# ---- utility poles along the lanes, wires between them and down to the houses
tops = []
for ri, rd in enumerate(WJ['roads']):
    if not (3 <= rd['w'] <= 10) or rd['kind'] == 'motorway': continue
    acc, nxt, prev = 0.0, random.uniform(10, 30), None
    pts = rd['pts']
    for kk in range(1, len(pts)):
        a, b = pts[kk - 1], pts[kk]; L = math.hypot(b[0] - a[0], b[1] - a[1])
        if not L: continue
        dx, dy = (b[0] - a[0]) / L, (b[1] - a[1]) / L
        while acc + L >= nxt:
            t = nxt - acc; x = a[0] + dx * t - dy * (rd['w'] / 2 + 1.3); y = a[1] + dy * t + dx * (rd['w'] / 2 + 1.3); nxt += 38 + random.uniform(-3, 4)
            if not inreg(x, y, 3): prev = None; continue
            z = hget(x, y)
            bpy.ops.mesh.primitive_cylinder_add(vertices=10, radius=.16, depth=10, location=(x, y, z + 5))
            pl = bpy.context.active_object; pl.data.materials.append(M['pole']); bpy.context.collection.objects.unlink(pl); link(pl, BUILD)
            pl.data.transform(pl.matrix_basis); pl.matrix_basis = Matrix()
            arm = box(f'arm{ri}_{kk}', -1.1, 1.1, -.07, .07, 9.3, 9.45, M['steel'], BUILD); place([arm], x, -y, math.atan2(dy, dx) + math.pi / 2, z)
            top = Vector((x, y, z + 9.45)); tops.append(top)
            if prev is not None and (top - prev).length < 60:
                for off in (-.85, 0, .85):
                    o_ = Vector((-dy * off, dx * off, 0)); wire_curve(prev + o_, top + o_, .85)
            prev = top
        acc += L
for anc in HOUSE_ANCHORS:
    if not tops: break
    top = min(tops, key=lambda t: (t - anc).length)
    if (top - anc).length < 45: wire_curve(top, anc, .5)
print('poles', len(tops))
# ---- trees: satellite clumps, yard trees, hillside wood; shrubs and weeds along edges
random.seed(5); nt_ = 0
pts = [(t[0], t[1]) for t in WJ['trees'] if inreg(t[0], t[1], 3)]
for b in WJ['buildings']:
    if inreg(b['c'][0], b['c'][1], 8) and b['kind'] == 'house' and random.random() < .4:
        a = random.uniform(0, 6.28); d = max(b['l'], b['w']) * .7 + 3; pts.append((b['c'][0] + math.cos(a) * d, b['c'][1] + math.sin(a) * d))
for _ in range(600):
    x, y = random.uniform(X0, X1), random.uniform(Y0, Y1)
    if hget(x, y) > 9 and gcls(x, y) in (0, 5): pts.append((x, y))
def blocked(x, y):
    rn = road_near(x, y); return rn is not None and rn[0] < rn[3] / 2 + 1.5
for k, (x, y) in enumerate(pts[:260]):
    if blocked(x, y): continue
    before = set(bpy.data.objects); tree(f'tree{k}', x, -y, random.uniform(.75, 1.25))
    for o in bpy.data.objects:
        if o not in before and o.type == 'MESH': o.data.transform(Matrix.Translation((0, 0, hget(x, y) - .1)))
    nt_ += 1
for k in range(240):
    x, y = random.uniform(X0, X1), random.uniform(Y0, Y1); rn = road_near(x, y)
    if rn is None or not (rn[3] / 2 + .6 < rn[0] < rn[3] / 2 + 4): continue   # roadside verge
    o = prop(random.choice(['shrub_02', 'shrub_03', 'shrub_04', 'weed_plant_02', 'weed_plant_02']), x, -y, rot=random.uniform(0, 6), fit=(2, random.uniform(.5, 1.5)), local=False)
    o.data.transform(Matrix.Translation((0, 0, hget(x, y))))
for k in range(30):   # tillers parked by fields
    x, y = random.uniform(X0, X1), random.uniform(Y0, Y1); rn = road_near(x, y)
    if rn is None or not (rn[3] / 2 + 1 < rn[0] < rn[3] / 2 + 3): continue
    PLACE_Y[0] = hget(x, y); tiller(x, -y, random.uniform(0, 6))
print('trees', nt_)

# ---------------------------------------------------------------- UVs
def world_box_uv(obj):
    if obj.get('keepuv'): return
    me = obj.data
    if not me.uv_layers: me.uv_layers.new(name='UVMap')
    bm = bmesh.new(); bm.from_mesh(me)
    uv = bm.loops.layers.uv.verify()
    mw = obj.matrix_world
    for f in bm.faces:
        m = me.materials[f.material_index] if me.materials else None
        tile = float(m['tile']) if m and 'tile' in m else 4.0
        n = f.normal
        ax = max(range(3), key=lambda i: abs(n[i]))
        if tile == 0:   # sign boards: stretch the whole image over the largest face
            cs = [l.vert.co for l in f.loops]
            if ax == 0: us, vs = [c.y for c in cs], [c.z for c in cs]; flip = n.x < 0
            elif ax == 1: us, vs = [c.x for c in cs], [c.z for c in cs]; flip = n.y > 0
            else: us, vs = [c.x for c in cs], [c.y for c in cs]; flip = False
            u0, u1, v0, v1 = min(us), max(us), min(vs), max(vs)
            for l, uu, vv in zip(f.loops, us, vs):
                u = (uu - u0) / max(u1 - u0, 1e-6); v = (vv - v0) / max(v1 - v0, 1e-6)
                l[uv].uv = ((1 - u) if flip else u, v)
            continue
        for l in f.loops:
            c = mw @ l.vert.co
            if ax == 0: u, v = (c.y if n.x > 0 else -c.y), c.z
            elif ax == 1: u, v = (-c.x if n.y > 0 else c.x), c.z
            else: u, v = c.x, c.y
            l[uv].uv = (u / tile, v / tile)
    bm.to_mesh(me); bm.free()

def apply_modifiers(objs):
    for o in objs:
        if o.type != 'MESH' or not o.modifiers: continue
        bpy.context.view_layer.objects.active = o
        for m in list(o.modifiers):
            bpy.ops.object.modifier_apply(modifier=m.name)

def join(objs, name):
    bpy.ops.object.select_all(action='DESELECT')
    for o in objs: o.select_set(True)
    bpy.context.view_layer.objects.active = objs[0]
    bpy.ops.object.join()
    j = bpy.context.active_object; j.name = name
    return j

meshes = [o for o in BUILD + GROUND + NOLM + PROPS if o.type == 'MESH']
apply_modifiers(meshes)
for o in meshes: world_box_uv(o)
# curves (wires) -> mesh
bpy.ops.object.select_all(action='DESELECT')
wires = [o for o in NOLM if o.type == 'CURVE']
for o in wires: o.select_set(True)
if wires:
    bpy.context.view_layer.objects.active = wires[0]; bpy.ops.object.convert(target='MESH')
NOLM = [o for o in bpy.context.scene.objects if o.name.startswith(('wire',)) or o in NOLM]

ground = join([o for o in GROUND if o.name in bpy.data.objects], 'LM_GROUND')
build = join([o for o in BUILD if o.name in bpy.data.objects], 'LM_BUILD')
props = join([o for o in PROPS if o.name in bpy.data.objects], 'VC_PROPS')
ground['lm'] = 'ground'; build['lm'] = 'build'; props['lm'] = 'vc'
nolm = [o for o in bpy.context.scene.objects if o.type == 'MESH' and o not in (ground, build, props)]
static = join(nolm, 'STATIC_NOLM') if nolm else None

# lightmap UVs
def add_lm_uv(obj, planar_extent=None):
    me = obj.data
    lm = me.uv_layers.new(name='LM')
    if planar_extent:   # ground: top-down projection over a fixed square
        e = planar_extent
        for poly in me.polygons:
            for li in poly.loop_indices:
                co = obj.matrix_world @ me.vertices[me.loops[li].vertex_index].co
                lm.data[li].uv = ((co.x + e) / (2 * e), (co.y + e) / (2 * e))
        return
    me.uv_layers.active = lm
    bpy.ops.object.select_all(action='DESELECT'); obj.select_set(True); bpy.context.view_layer.objects.active = obj
    bpy.ops.object.mode_set(mode='EDIT'); bpy.ops.mesh.select_all(action='SELECT')
    bpy.ops.uv.smart_project(angle_limit=math.radians(66), island_margin=0.002, area_weight=0.0, correct_aspect=True)
    bpy.ops.uv.pack_islands(margin=0.002, rotate=True)
    bpy.ops.object.mode_set(mode='OBJECT')
lmg = ground.data.uv_layers.new(name='LM')
for poly in ground.data.polygons:
    for li in poly.loop_indices:
        co = ground.matrix_world @ ground.data.vertices[ground.data.loops[li].vertex_index].co
        lmg.data[li].uv = ((co.x - X0) / (X1 - X0), (co.y - Y0) / (Y1 - Y0))
add_lm_uv(build)

# ---------------------------------------------------------------- lighting: HDRI world + sun aligned to the HDRI sun
hdr_path = os.path.join(TEX, 'DaySkyHDRI069A', 'DaySkyHDRI069A_2K_HDR.exr')
world = bpy.data.worlds.new('sky'); scene.world = world; world.use_nodes = True
wn = world.node_tree; env = wn.nodes.new('ShaderNodeTexEnvironment'); env.image = bpy.data.images.load(hdr_path)
wn.links.new(env.outputs['Color'], wn.nodes['Background'].inputs['Color'])
wn.nodes['Background'].inputs['Strength'].default_value = 1.0
H = env.image; w, h = H.size
px = np.array(H.pixels[:], dtype=np.float32).reshape(h, w, 4)
lum = px[..., 0] * .2126 + px[..., 1] * .7152 + px[..., 2] * .0722
lum[: h // 2] = 0   # only the upper hemisphere (pixel rows start at the bottom)
iy, ix = np.unravel_index(np.argmax(lum), lum.shape)
u, v = (ix + .5) / w, (iy + .5) / h
az = math.pi - 2 * math.pi * u          # atan2(y, x) in Blender world
el = (v - .5) * math.pi
sun_dir = Vector((math.cos(el) * math.cos(az), math.cos(el) * math.sin(az), math.sin(el)))
print('HDRI sun pixel', ix, iy, 'elevation deg', math.degrees(el), 'dir', tuple(sun_dir))
# clamp very low suns so the yard isn't all shadow
if el < math.radians(28):
    el = math.radians(28); sun_dir = Vector((math.cos(el) * math.cos(az), math.cos(el) * math.sin(az), math.sin(el)))
sun = bpy.data.lights.new('sun', 'SUN'); sun.energy = 4.2; sun.angle = math.radians(1.2); sun.color = (1.0, .96, .9)
so = bpy.data.objects.new('sun', sun); bpy.context.collection.objects.link(so)
so.rotation_euler = sun_dir.to_track_quat('Z', 'Y').to_euler()   # light shines along -Z, so point +Z at the sun

# ---------------------------------------------------------------- bake
scene.render.engine = 'CYCLES'
scene.cycles.device = 'CPU'
scene.cycles.samples = SAMPLES
scene.cycles.max_bounces = 4; scene.cycles.diffuse_bounces = 3; scene.cycles.glossy_bounces = 1; scene.cycles.transparent_max_bounces = 4
scene.render.bake.margin = 6
def bake(obj, size, tag):
    image = bpy.data.images.new(f'lm_{tag}', size, size, float_buffer=True)
    mats = set(s.material for s in obj.material_slots if s.material)
    for m in mats:
        nt = m.node_tree
        n = nt.nodes.new('ShaderNodeTexImage'); n.image = image; n.name = 'BAKE'
        for x in nt.nodes: x.select = False
        n.select = True; nt.nodes.active = n
    obj.data.uv_layers.active = obj.data.uv_layers['LM']
    bpy.ops.object.select_all(action='DESELECT'); obj.select_set(True); bpy.context.view_layer.objects.active = obj
    import time; t0 = time.time()
    bpy.ops.object.bake(type='DIFFUSE', pass_filter={'DIRECT', 'INDIRECT'}, use_clear=True, margin=6)
    print(f'baked {tag} {size}px in {time.time() - t0:.0f}s')
    arr = np.array(image.pixels[:], dtype=np.float32).reshape(size, size, 4)[..., :3]
    np.save(os.path.join(OUT, f'lm_{tag}.npy'), arr.astype(np.float16))
    for m in mats:
        m.node_tree.nodes.remove(m.node_tree.nodes['BAKE'])
    obj.data.uv_layers.active = obj.data.uv_layers['UVMap']
if not REUSE_LM:
    bake(ground, LM_SIZE_GROUND, 'ground')
    bake(build, LM_SIZE_BUILD, 'build')
# props / foliage / grilles: bake lighting into a float colour attribute (stored as L / LMAX, same range as the lightmaps)
LMAX = 4.0
ca_ = props.data.color_attributes.new('LMC', 'FLOAT_COLOR', 'CORNER')
props.data.color_attributes.active_color = ca_
try: props.data.color_attributes.render_color_index = props.data.color_attributes.active_color_index
except Exception: pass
bpy.ops.object.select_all(action='DESELECT'); props.select_set(True); bpy.context.view_layer.objects.active = props
import time as _t; t0 = _t.time()
# cut-out leaves would bake as black at their transparent card corners: make prop materials opaque for this bake only
relink = []
for slot in props.material_slots:
    m = slot.material
    if not (m and m.node_tree): continue
    b = m.node_tree.nodes.get('Principled BSDF')
    if not b: continue
    # Cycles divides the colour back out of the "lighting only" pass, so black texels (atlas corners) bake to 0:
    # bake with a plain white, opaque surface instead
    for sock in ('Alpha', 'Base Color'):
        if b.inputs[sock].is_linked:
            lk = b.inputs[sock].links[0]; relink.append((m, lk.from_socket, b.inputs[sock], tuple(b.inputs[sock].default_value) if sock == 'Base Color' else b.inputs[sock].default_value)); m.node_tree.links.remove(lk)
    relink.append((m, None, b.inputs['Base Color'], tuple(b.inputs['Base Color'].default_value)))
    b.inputs['Base Color'].default_value = (1, 1, 1, 1); b.inputs['Alpha'].default_value = 1.0
bpy.ops.object.bake(type='DIFFUSE', pass_filter={'DIRECT', 'INDIRECT'}, target='VERTEX_COLORS', use_clear=True)
for m, a, b, dv in relink:
    if a is None: b.default_value = dv
    else: m.node_tree.links.new(a, b)
buf = np.zeros(len(ca_.data) * 4, dtype=np.float32); ca_.data.foreach_get('color', buf)
buf = buf.reshape(-1, 4); buf[:, :3] = np.clip(buf[:, :3] / LMAX, 0, 1); buf[:, 3] = 1
ca_.data.foreach_set('color', buf.ravel())
print(f'baked props vertex colours in {_t.time() - t0:.0f}s, mean {buf[:, :3].mean():.3f}')
_li = np.zeros(len(props.data.loops), dtype=np.int32)
for poly in props.data.polygons: _li[poly.loop_start:poly.loop_start + poly.loop_total] = poly.material_index
for i, slot in enumerate(props.material_slots):
    if slot.material and slot.material.name in ('leafcard', 'bark'):
        sel = buf[_li == i, :3]; print('VCDBG', slot.material.name, len(sel), float(sel.mean()) if len(sel) else None)

# ---------------------------------------------------------------- export glTF (separate files)
import inspect
_props = bpy.ops.export_scene.gltf.get_rna_type().properties.keys()
EXPORT_COLORS = {'export_vertex_color': 'ACTIVE'} if 'export_vertex_color' in _props else ({'export_colors': True} if 'export_colors' in _props else {})
print('color export opts', EXPORT_COLORS)
for o in bpy.context.scene.objects: o.select_set(o.type == 'MESH')
bpy.ops.export_scene.gltf(filepath=os.path.join(OUT, 'scene.gltf'), export_format='GLTF_SEPARATE', use_selection=True,
                          export_texcoords=True, export_normals=True, export_extras=True, export_apply=True,
                          export_texture_dir='tex', export_image_format='AUTO', export_lights=False, export_cameras=False,
                          **EXPORT_COLORS,
                          export_draco_mesh_compression_enable=True, export_draco_mesh_compression_level=6,
                          export_draco_position_quantization=14, export_draco_normal_quantization=10,
                          export_draco_texcoord_quantization=14)
print('sun_dir_blender', tuple(sun_dir))
with open(os.path.join(OUT, 'sun.txt'), 'w') as f: f.write(','.join(f'{c:.5f}' for c in sun_dir))
import json
json.dump({'region': REG, **LAYOUT}, open(os.path.join(OUT, 'layout.json'), 'w'))
print('DONE')