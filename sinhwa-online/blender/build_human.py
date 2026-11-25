"""
신화리 인물 시제품: MPFB(MakeHuman, CC0 에셋)로 인물을 만들어 glTF로 내보낸다.
Run (isolated Blender config, MPFB loaded from a scratch extensions dir):
  BLENDER_USER_EXTENSIONS=<extroot> BLENDER_USER_CONFIG=<cfg> blender -b --factory-startup -P build_human.py -- <preset> <out.gltf>
"""
import bpy, sys, os, addon_utils, json

argv = sys.argv[sys.argv.index('--') + 1:]
PRESET, OUT = argv[0], argv[1]
addon_utils.enable('bl_ext.user_default.mpfb', default_set=True, persistent=False)
from bl_ext.user_default.mpfb.services.humanservice import HumanService
from bl_ext.user_default.mpfb.services.targetservice import TargetService
from bl_ext.user_default.mpfb.services.locationservice import LocationService

DATA = LocationService.get_user_data()
def asset(kind, name, ext):
    d = os.path.join(DATA, kind, name)
    for f in os.listdir(d):
        if f.endswith(ext): return os.path.join(d, f)
    raise FileNotFoundError(d)

# age: 0.5 = 25 years, 1.0 = 90 years (MakeHuman macro scale)
def age(y): return 0.5 + (y - 25) / 65 * 0.5 if y >= 25 else y / 25 * 0.5

PRESETS = {
    'ijang': dict(  # 오만복 이장, 61세
        macro=dict(gender=1.0, age=age(61), muscle=.42, weight=.8, proportions=.45, height=.42, cupsize=.5, firmness=.4,
                   race=dict(asian=1.0, caucasian=0.0, african=0.0)),
        skin=('skins', 'old_asian_male', '.mhmat'),
        hair='short04', eyebrows='eyebrow009', clothes=['male_casualsuit05', 'shoes04'],
        targets={  # weathered farmer: lean face, cheekbones, deep folds (body stays heavy, face does not)
                 'head-age-incr': 1.0, 'head-fat-decr': .35, 'head-square': .45, 'forehead-temple-decr': .3,
                 'l-cheek-bones-incr': .5, 'r-cheek-bones-incr': .5, 'l-cheek-volume-decr': .3, 'r-cheek-volume-decr': .3,
                 'l-cheek-inner-decr': .25, 'r-cheek-inner-decr': .25, 'chin-width-incr': .3, 'chin-bones-incr': .3,
                 'neck-double-incr': .25, 'mouth-angles-down': .4, 'mouth-laugh-lines-in': 1.0,
                 'mouth-upperlip-volume-decr': .3, 'mouth-lowerlip-volume-decr': .15,
                 'l-eye-bag-incr': .6, 'r-eye-bag-incr': .6, 'nose-flaring-incr': .3, 'nose-scale-horiz-incr': .2, 'nose-volume-incr': .2}),
    'halmeoni': dict(  # 박순례, 78세 · 딸기 농가: 작고 마른 몸, 꽃무늬 몸빼 + 분홍 누빔 조끼 + 팔토시 + 고무장화 + 농사 모자
        macro=dict(gender=0.0, age=age(78), muscle=.3, weight=.38, proportions=.4, height=.18, cupsize=.35, firmness=.15,
                   race=dict(asian=1.0, caucasian=0.0, african=0.0)),
        skin=('skins', 'old_asian_female', '.mhmat'),
        hair='short01', eyebrows='eyebrow002', clothes=['male_casualsuit03', 'shoes02'],
        targets={'head-age-incr': 1.0, 'head-fat-decr': .2, 'l-cheek-volume-decr': .35, 'r-cheek-volume-decr': .35,
                 'l-cheek-bones-incr': .35, 'r-cheek-bones-incr': .35, 'mouth-laugh-lines-in': .9, 'mouth-angles-down': .25,
                 'mouth-upperlip-volume-decr': .45, 'mouth-lowerlip-volume-decr': .3, 'mouth-scale-depth-decr': .25,
                 'l-eye-bag-incr': .6, 'r-eye-bag-incr': .6, 'nose-volume-incr': .15, 'nose-flaring-incr': .2,
                 'chin-prominent-incr': .2, 'neck-scale-horiz-decr': .2},
        costume=dict(suit='male_casualsuit03', shoes='shoes02', suit_tex='suit_halmeoni.png', shoe_tex='shoe_rubber.png',
                     loose=.007, brim=.065, flap=.11, brim_down=.058)),
    'player': dict(  # 전입자 기본, 34세
        macro=dict(gender=1.0, age=age(34), muscle=.55, weight=.5, proportions=.55, height=.55, cupsize=.5, firmness=.5,
                   race=dict(asian=1.0, caucasian=0.0, african=0.0)),
        skin=('skins', 'middleage_asian_male', '.mhmat'),
        hair='short04', eyebrows='eyebrow003', clothes=['male_casualsuit02', 'shoes03']),
}
P = PRESETS[PRESET]

bpy.ops.wm.read_factory_settings(use_empty=True)
addon_utils.enable('bl_ext.user_default.mpfb', default_set=True, persistent=False)
human = HumanService.create_human(macro_detail_dict=P['macro'], scale=0.1)
for tname, w in P.get('targets', {}).items():   # face sculpt (age, jowls, eye bags...)
    path = TargetService.target_full_path(tname)
    if path: TargetService.load_target(human, path, weight=w, name=tname)
    else: print('MISSING TARGET', tname)
# rig BEFORE any clothes/hair so MPFB transfers bone weights onto them (otherwise sleeves stay frozen)
HumanService.add_builtin_rig(human, 'game_engine', import_weights=True)
HumanService.set_character_skin(asset(*P['skin']), human, skin_type='GAMEENGINE')
HumanService.add_mhclo_asset(asset('eyes', 'high-poly', '.mhclo'), human, asset_type='Eyes', subdiv_levels=0, material_type='MAKESKIN')
HumanService.add_mhclo_asset(asset('eyebrows', P['eyebrows'], '.mhclo'), human, asset_type='Eyebrows', subdiv_levels=0, material_type='MAKESKIN')
HumanService.add_mhclo_asset(asset('eyelashes', 'eyelashes01', '.mhclo'), human, asset_type='Eyelashes', subdiv_levels=0, material_type='MAKESKIN')
HumanService.add_mhclo_asset(asset('teeth', 'teeth_base', '.mhclo'), human, asset_type='Teeth', subdiv_levels=0, material_type='MAKESKIN')
HumanService.add_mhclo_asset(asset('tongue', 'tongue01', '.mhclo'), human, asset_type='Tongue', subdiv_levels=0, material_type='MAKESKIN')
HumanService.add_mhclo_asset(asset('hair', P['hair'], '.mhclo'), human, asset_type='Hair', subdiv_levels=0, material_type='MAKESKIN')
for c in P['clothes']:
    HumanService.add_mhclo_asset(asset('clothes', c, '.mhclo'), human, asset_type='Clothes', subdiv_levels=1, material_type='MAKESKIN')
if P.get('costume'):   # farm outfit: retextured clothes, vest/arm covers/boots, modelled farm hat
    TEX_SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'humans', 'tex_src')
    exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'costume.py')).read())
# parts the game rig has no bones for (eyeballs, teeth, tongue) come out with no weights and would collapse to the
# origin when skinned: bind every unweighted vertex of any rigged mesh to the head bone
for o in bpy.context.scene.objects:
    if o.type != 'MESH' or o == human or not any(m.type == 'ARMATURE' for m in o.modifiers): continue
    head = o.vertex_groups.get('head') or o.vertex_groups.new(name='head')
    bones = {b.name for b in [m for m in o.modifiers if m.type == 'ARMATURE'][0].object.data.bones}
    loose = [v.index for v in o.data.vertices if sum(g.weight for g in v.groups if o.vertex_groups[g.group].name in bones) < 1e-4]
    if loose:
        head.add(loose, 1.0, 'REPLACE'); print('BOUND TO HEAD', o.name, len(loose), 'of', len(o.data.vertices))
# smoother silhouette for close-ups: one Catmull-Clark level on the body (weights are interpolated)
sub = human.modifiers.new('Subdivision', 'SUBSURF'); sub.levels = 1; sub.render_levels = 1

# freeze body shape (macro targets) so the export carries the final mesh
TargetService.bake_targets(human)
for o in bpy.context.scene.objects:
    if o.type == 'MESH':
        bpy.context.view_layer.objects.active = o
        for m in list(o.modifiers):
            if m.type in ('MASK', 'SUBSURF'):
                m.show_viewport = True   # MPFB adds clothes subdivision disabled in the viewport
                if m.type == 'SUBSURF': m.levels = max(m.levels, m.render_levels, 1)
                try: bpy.ops.object.modifier_apply(modifier=m.name)
                except Exception as e: print('modapply', o.name, m.name, e)
stats = {o.name: len(o.data.polygons) for o in bpy.context.scene.objects if o.type == 'MESH'}
print('MESHES', json.dumps(stats))
print('MATS', [m.name for m in bpy.data.materials])
bpy.ops.object.select_all(action='SELECT')
bpy.ops.export_scene.gltf(filepath=OUT, export_format='GLTF_SEPARATE', use_selection=True, export_texture_dir='tex',
                          export_skins=True, export_animations=False, export_morph=False, export_apply=False,
                          export_image_format='AUTO')
print('DONE')
