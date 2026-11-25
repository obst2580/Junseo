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
        targets={'head-age-incr': 1.0, 'head-fat-decr': .35, 'head-square': .45, 'head-scale-vert-incr': .35, 'forehead-temple-decr': .3,
                 'lr-cheek-bones-incr': .55, 'lr-cheek-volume-decr': .35, 'lr-cheek-inner-decr': .3, 'chin-width-incr': .3, 'chin-height-incr': .3,
                 'neck-double-incr': .25, 'mouth-angles-down': .5, 'mouth-laugh-lines-in': 1.0, 'mouth-upperlip-volume-decr': .4,
                 'mouth-lowerlip-volume-decr': .2, 'mouth-scale-horiz-incr': .25, 'lr-eye-bag-incr': .7, 'lr-eye-corner2-down': .5,
                 'lr-eye-eyefold-down': .6, 'lr-eye-scale-decr': .25, 'nose-flaring-incr': .5, 'nose-scale-horiz-incr': .4,
                 'nose-point-width-incr': .4, 'nose-volume-incr': .3, 'nose-base-down': .3, 'lr-ear-scale-incr': .4, 'lr-ear-lobe-incr': .6}),
    'halmeoni': dict(  # 박순례, 78세 · 딸기 농가: 작고 마른 몸, 꽃무늬 몸빼 + 분홍 누빔 조끼 + 팔토시 + 고무장화 + 농사 모자
        macro=dict(gender=0.0, age=age(78), muscle=.3, weight=.38, proportions=.4, height=.18, cupsize=.35, firmness=.15,
                   race=dict(asian=1.0, caucasian=0.0, african=0.0)),
        skin=('skins', 'old_asian_female', '.mhmat'),
        hair='short01', eyebrows='eyebrow002', clothes=['male_casualsuit03', 'shoes02'],
        targets={'head-age-incr': 1.0, 'head-fat-decr': .3, 'head-scale-horiz-decr': .15, 'head-invertedtriangular': .4,
                 'lr-cheek-volume-decr': .5, 'lr-cheek-bones-incr': .5, 'lr-cheek-inner-decr': .4, 'mouth-laugh-lines-in': 1.0,
                 'mouth-angles-down': .3, 'mouth-upperlip-volume-decr': .7, 'mouth-lowerlip-volume-decr': .5, 'mouth-scale-depth-decr': .6,
                 'mouth-trans-backward': .4, 'chin-prominent-incr': .5, 'chin-triangle': .4, 'lr-eye-bag-incr': .7, 'lr-eye-eyefold-down': .7,
                 'lr-eye-scale-decr': .3, 'lr-eye-corner2-down': .4, 'nose-scale-horiz-decr': .2, 'nose-point-down': .4, 'nose-hump-incr': .3,
                 'neck-scale-horiz-decr': .3, 'lr-ear-lobe-incr': .8, 'lr-ear-scale-incr': .3},
        costume=dict(suit='male_casualsuit03', shoes='shoes02', loose=.007,
                     suit_tex=dict(bottom='tile:momppae_floral.png:2', top='tile:blouse_print.png:2'), shoe_tex='rubber:#2e4230+mud',
                     pieces=[dict(kind='vest', tex='tile:vest_quilt.png:10', gap=.05, push=.008, rough=.9),
                             dict(kind='armcover', tex='tile:momppae_floral.png:5', push=.005),
                             dict(kind='boot', tex='rubber:#2e4230+mud', push=.012, drop=.05, rough=.45)],
                     hat=dict(kind='farm', tex='tile:hat_cloth.png:1', brim=.065, flap=.11, brim_down=.058))),
    'choi': dict(  # 최 실장, 45세 · 그린밸류 F&B: 몸에 딱 맞는 회색 정장 + 흙 묻은 고무장화, 포마드, 선글라스, 각진 턱
        macro=dict(gender=1.0, age=age(45), muscle=.55, weight=.5, proportions=.62, height=.62, cupsize=.5, firmness=.5,
                   race=dict(asian=1.0, caucasian=0.0, african=0.0)),
        skin=('skins', 'middleage_asian_male', '.mhmat'),
        hair='short04', eyebrows='eyebrow009', clothes=['male_elegantsuit01', 'shoes03'],
        targets={'head-square': 1.0, 'head-scale-vert-incr': .25, 'head-fat-decr': .4, 'chin-width-incr': .9, 'chin-bones-incr': .8,
                 'chin-prominent-incr': .4, 'chin-height-incr': .3, 'lr-cheek-volume-decr': .5, 'lr-cheek-bones-incr': .6,
                 'nose-scale-vert-incr': .6, 'nose-scale-horiz-decr': .35, 'nose-hump-incr': .4, 'nose-trans-forward': .3, 'nose-point-width-decr': .4,
                 'mouth-upperlip-volume-decr': .6, 'mouth-lowerlip-volume-decr': .4, 'mouth-scale-horiz-decr': .1, 'mouth-angles-down': .2,
                 'eyebrows-trans-down': .6, 'eyebrows-angle-down': .5, 'lr-eye-height2-decr': .3, 'lr-eye-corner1-up': .3,
                 'lr-ear-scale-decr': .3, 'lr-ear-rot-backward': .3, 'neck-scale-horiz-incr': .3},
        costume=dict(suit='male_elegantsuit01', shoes='shoes03', shoe_tex='rubber:#1e2220+mud',
                     pieces=[dict(kind='boot', tex='rubber:#1e2220+mud', push=.011, drop=.1, rough=.4)],
                     glasses=dict(frame='#151515', lens='#0b0d10', w=.027, h=.019))),
    'minseok': dict(  # 민석, 36세 · 마을 상생 매니저: 바랜 후드티, 청바지, 새 장화, 안경, 지저분한 수염
        macro=dict(gender=1.0, age=age(36), muscle=.4, weight=.55, proportions=.5, height=.55, cupsize=.5, firmness=.5,
                   race=dict(asian=1.0, caucasian=0.0, african=0.0)),
        skin=('skins', 'young_asian_male', '.mhmat'),
        hair='short01', eyebrows='eyebrow009', clothes=['male_casualsuit01', 'shoes05'],
        targets={'head-round': .9, 'head-fat-incr': .55, 'head-scale-horiz-incr': .2, 'lr-cheek-volume-incr': .7, 'lr-cheek-bones-decr': .3,
                 'chin-height-decr': .4, 'chin-prominent-decr': .5, 'chin-width-decr': .2, 'neck-double-incr': .5,
                 'nose-point-up': .6, 'nose-scale-vert-decr': .4, 'nose-volume-incr': .3, 'nose-nostrils-width-incr': .4,
                 'mouth-upperlip-volume-incr': .5, 'mouth-lowerlip-volume-incr': .7, 'mouth-scale-horiz-decr': .2,
                 'lr-eye-scale-decr': .3, 'lr-eye-height2-decr': .5, 'lr-eye-bag-incr': .5, 'lr-eye-epicanthus-in': .5,
                 'eyebrows-trans-up': .3, 'lr-ear-wing-incr': .5},
        costume=dict(suit='male_casualsuit01', shoes='shoes05', loose=.004,
                     suit_tex=dict(top='solid:#6c6f4c', bottom='keep'), shoe_tex='rubber:#3f5a3c',
                     pieces=[dict(kind='boot', tex='rubber:#3f5a3c', push=.011, drop=.08, rough=.3),
                             dict(kind='hood', tex='solid:#62654a', radius=.08, tube=.045)],
                     glasses=dict(frame='#2b2b2b', lens='#dfe6ea', w=.024, h=.017), regions=['beard'])),
    'police': dict(  # 김 순경, 29세 · 연무 파출소: 남색 근무복 + 형광 조끼 + 경찰 모자
        macro=dict(gender=1.0, age=age(29), muscle=.5, weight=.45, proportions=.55, height=.58, cupsize=.5, firmness=.5,
                   race=dict(asian=1.0, caucasian=0.0, african=0.0)),
        skin=('skins', 'young_asian_male', '.mhmat'),
        hair='short01', eyebrows='eyebrow009', clothes=['male_casualsuit05', 'shoes03'],
        targets={'head-oval': .8, 'head-fat-decr': .3, 'head-scale-horiz-decr': .15, 'chin-triangle': .6, 'chin-height-incr': .2,
                 'lr-cheek-volume-decr': .3, 'lr-eye-scale-incr': .55, 'lr-eye-height2-incr': .6, 'lr-eye-epicanthus-out': .5,
                 'lr-eye-corner2-down': .3, 'eyebrows-trans-up': .7, 'eyebrows-angle-up': .7, 'nose-scale-horiz-decr': .3,
                 'nose-point-width-decr': .3, 'nose-scale-vert-decr': .2, 'mouth-scale-horiz-decr': .25, 'mouth-lowerlip-volume-incr': .3,
                 'mouth-angles-up': .15, 'neck-scale-horiz-decr': .4, 'lr-ear-scale-incr': .7, 'lr-ear-wing-incr': .9, 'lr-ear-flap-incr': .5},
        costume=dict(suit='male_casualsuit01', shoes='shoes03', loose=.003,
                     suit_tex=dict(top='solid:#3c4f6b', bottom='solid:#2a3346'), shoe_tex='leather:#161616',
                     pieces=[dict(kind='vest', tex='hivis', gap=0, push=.007, rough=.7)],
                     hat=dict(kind='police', tex='solid:#2f3f58', brim_down=.05, height=.075, thick=.002))),
    'syuper': dict(  # 신화슈퍼 사장님, 55세: 진한 파마머리, 붉은 니트, 청록 조끼, 체크무늬 앞치마, 둥근 체형
        macro=dict(gender=0.0, age=age(55), muscle=.3, weight=.85, proportions=.4, height=.3, cupsize=.6, firmness=.3,
                   race=dict(asian=1.0, caucasian=0.0, african=0.0)),
        skin=('skins', 'middleage_asian_female', '.mhmat'),
        hair='short02', eyebrows='eyebrow002', clothes=['male_casualsuit03', 'shoes02'],
        targets={'head-round': 1.0, 'head-fat-incr': .7, 'head-scale-horiz-incr': .35, 'head-scale-vert-decr': .15,
                 'lr-cheek-volume-incr': .9, 'lr-cheek-bones-incr': .3, 'neck-double-incr': .8, 'chin-height-decr': .2,
                 'lr-eye-scale-decr': .45, 'lr-eye-height2-decr': .4, 'lr-eye-corner2-up': .4, 'lr-eye-bag-incr': .4,
                 'nose-scale-horiz-incr': .5, 'nose-flaring-incr': .6, 'nose-scale-vert-decr': .3, 'nose-volume-incr': .4,
                 'mouth-upperlip-volume-incr': .6, 'mouth-lowerlip-volume-incr': .8, 'mouth-scale-horiz-incr': .3, 'mouth-angles-up': .3,
                 'mouth-laugh-lines-in': .6, 'eyebrows-angle-up': .3},
        costume=dict(suit='male_casualsuit03', shoes='shoes02', loose=.006,
                     suit_tex=dict(top='solid:#b04a5a', bottom='solid:#3a3434'), shoe_tex='rubber:#7a5a4e',
                     pieces=[dict(kind='vest', tex='solid:#3a6b6a', gap=.06, push=.008),
                             dict(kind='apron', tex='check:#3a6b6a:#e8e2d4:9', bib=False, width=.19, bottom=.1, push=.02)])),
    'sangsa': dict(  # 박 상사(예), 58세 · 충성고물상: 스포츠머리, 사제 야상, 기름 묻은 가죽 앞치마, 전투화, 큰 체격
        macro=dict(gender=1.0, age=age(58), muscle=.72, weight=.72, proportions=.5, height=.62, cupsize=.5, firmness=.5,
                   race=dict(asian=1.0, caucasian=0.0, african=0.0)),
        skin=('skins', 'middleage_asian_male', '.mhmat'),
        hair=None, eyebrows='eyebrow009', clothes=['male_casualsuit05', 'shoes04'],
        targets={'head-square': 1.0, 'head-scale-horiz-incr': .3, 'head-age-incr': .6, 'head-fat-incr': .15, 'forehead-trans-forward': .6,
                 'forehead-nubian-incr': .5, 'eyebrows-trans-forward': .6, 'eyebrows-trans-down': .5, 'eyebrows-angle-down': .4,
                 'lr-eye-scale-decr': .4, 'lr-eye-push1-in': .6, 'lr-eye-push2-in': .6, 'lr-eye-height2-decr': .4, 'lr-eye-bag-incr': .5,
                 'nose-scale-horiz-incr': .7, 'nose-flaring-incr': .8, 'nose-point-width-incr': .7, 'nose-compression-compress': .6,
                 'nose-scale-vert-decr': .3, 'chin-width-incr': .8, 'chin-bones-incr': .9, 'chin-prominent-incr': .5,
                 'mouth-scale-horiz-incr': .4, 'mouth-angles-down': .4, 'mouth-upperlip-volume-decr': .2,
                 'neck-scale-horiz-incr': .8, 'neck-double-incr': .3, 'lr-ear-scale-incr': .3, 'lr-ear-flap-incr': .7, 'lr-ear-scale-depth-incr': .6},
        costume=dict(suit='male_casualsuit05', shoes='shoes04',
                     suit_tex=dict(top='solid:#3c4630', inner='solid:#4a4d38', bottom='solid:#30362a'), shoe_tex='leather:#1c1a18',
                     pieces=[dict(kind='boot', tex='leather:#1c1a18', push=.01, drop=.12, rough=.5),
                             dict(kind='apron', tex='leather:#6e5a3c+oil', width=.17, top=.05, bottom=.12, push=.02, rough=.6)], regions=['buzz'])),
    'anjun': dict(  # 안준: 키 크고 마른 20세 전후, 앞머리 짧은 머리, 웃는 인상 — 검은 플리스 집업 + 흰 줄 트레이닝 바지
        macro=dict(gender=1.0, age=age(20), muscle=.45, weight=.33, proportions=.62, height=.78, cupsize=.5, firmness=.55,
                   race=dict(asian=1.0, caucasian=0.0, african=0.0)),
        skin=('skins', 'young_asian_male', '.mhmat'),
        hair='culturalibre_hair_06', eyebrows='eyebrow009', clothes=['male_casualsuit05', 'shoes05'],
        targets={'head-scale-vert-incr': .35, 'head-oval': .4, 'chin-width-decr': .5, 'chin-triangle': .5, 'chin-height-incr': .3,
                 'lr-cheek-bones-incr': .55, 'lr-cheek-volume-decr': .35, 'head-fat-decr': .3, 'nose-scale-vert-incr': .35, 'nose-scale-horiz-decr': .2,
                 'lr-ear-flap-incr': 1.0, 'lr-ear-scale-incr': .3, 'lr-ear-wing-incr': .5, 'mouth-lowerlip-volume-incr': .25,
                 'neck-scale-horiz-decr': .3},
        costume=dict(suit='male_casualsuit05', shoes='shoes05', loose=.004,
                     suit_tex=dict(top='solid:#18191c', inner='solid:#ecebe6', bottom='solid:#141518'), shoe_tex='leather:#121212',
                     pieces=[dict(kind='stripes', tex='color:#efefea', count=2, width=.011, gap=.02)])),
    'jinwook': dict(  # 홍진욱: 키 크고 마른 체형, 곱슬기 짧은 머리, 차분한 긴 얼굴 — 검은 니트 집업 + 진갈색 슬랙스
        macro=dict(gender=1.0, age=age(20), muscle=.42, weight=.36, proportions=.6, height=.76, cupsize=.5, firmness=.55,
                   race=dict(asian=1.0, caucasian=0.0, african=0.0)),
        skin=('skins', 'young_asian_male', '.mhmat'),
        hair='short02', eyebrows='eyebrow009', clothes=['male_casualsuit05', 'shoes03'],
        targets={'head-scale-vert-incr': .15, 'head-fat-incr': .1, 'lr-cheek-volume-decr': .1,
                 'nose-scale-vert-incr': .35, 'nose-flaring-incr': .2, 'nose-point-width-incr': .2, 'nose-hump-incr': .2, 'lr-ear-flap-incr': .5, 'chin-width-incr': .45, 'chin-bones-incr': .3, 'lr-eye-height2-decr': .35, 'lr-eye-scale-decr': .15,
                 'mouth-scale-horiz-decr': .15, 'mouth-lowerlip-volume-incr': .2, 'chin-prominent-incr': .25, 'chin-height-incr': .3, 'eyebrows-trans-down': .2},
        costume=dict(suit='male_casualsuit05', shoes='shoes03', loose=.004,
                     suit_tex=dict(top='solid:#141517', inner='solid:#1b1c1f', bottom='solid:#33292a'), shoe_tex='leather:#111111')),
    'parkjin': dict(  # 박진: 보통 키에 다부진 체형, 가르마 머리, 둥근 얼굴 — 검은 숏패딩 + 흰 두 줄 트레이닝 바지 + 슬리퍼
        macro=dict(gender=1.0, age=age(20), muscle=.58, weight=.6, proportions=.5, height=.56, cupsize=.5, firmness=.55,
                   race=dict(asian=1.0, caucasian=0.0, african=0.0)),
        skin=('skins', 'young_asian_male', '.mhmat'),
        hair='culturalibre_hair_06', eyebrows='eyebrow009', clothes=['male_casualsuit01', 'shoes02'],
        targets={'head-round': .55, 'head-fat-incr': .3, 'lr-cheek-volume-incr': .45, 'head-scale-horiz-incr': .15, 'chin-width-incr': .25,
                 'lr-eye-scale-decr': .2, 'lr-eye-height2-decr': .3, 'lr-eye-corner2-down': .2, 'nose-scale-horiz-incr': .2, 'nose-flaring-incr': .3, 'nose-point-up': .2,
                 'mouth-upperlip-volume-incr': .25, 'mouth-lowerlip-volume-incr': .35, 'neck-scale-horiz-incr': .3},
        costume=dict(suit='male_casualsuit01', shoes='shoes02', loose=.004,
                     suit_tex=dict(top='solid:#111214', bottom='solid:#121316'), shoe_tex='rubber:#101010',
                     pieces=[dict(kind='puffer', tex='solid:#141518', rough=.55, push=.02, amp=.011, period=.16),
                             dict(kind='stripes', tex='color:#f2f2ee', count=2, width=.012, gap=.022)])),
    'woochan': dict(  # 조우찬: 다부진 운동형 체형, 아주 짧은 머리 + 크림색 니트 비니 — 검은 긴팔 티 + 흰 두 줄 트레이닝 바지 + 흰 운동화
        macro=dict(gender=1.0, age=age(20), muscle=.75, weight=.55, proportions=.55, height=.64, cupsize=.5, firmness=.6,
                   race=dict(asian=1.0, caucasian=0.0, african=0.0)),
        skin=('skins', 'young_asian_male', '.mhmat'),
        hair=None, eyebrows='eyebrow009', clothes=['male_casualsuit01', 'shoes05'],
        targets={'head-square': .55, 'head-scale-horiz-incr': .2, 'chin-width-incr': .5, 'chin-bones-incr': .35, 'head-fat-incr': .1, 'lr-cheek-bones-incr': .5,
                 'lr-eye-height2-decr': .3, 'lr-eye-corner2-up': .2, 'nose-scale-horiz-incr': .25, 'nose-volume-incr': .15,
                 'mouth-scale-horiz-incr': .1, 'neck-scale-horiz-incr': .55, 'lr-ear-scale-incr': .2},
        costume=dict(suit='male_casualsuit01', shoes='shoes05', loose=.002,
                     suit_tex=dict(top='solid:#16171a', bottom='solid:#121316'), shoe_tex='leather:#eeeeea',
                     pieces=[dict(kind='stripes', tex='color:#f2f2ee', count=2, width=.012, gap=.022)],
                     hat=dict(kind='beanie', tex='knit:#e9e3d2', brim_down=.1, rough=.95, thick=.004), regions=['buzz'])),
    'player': dict(  # 전입자 기본, 34세
        macro=dict(gender=1.0, age=age(34), muscle=.55, weight=.5, proportions=.55, height=.55, cupsize=.5, firmness=.5,
                   race=dict(asian=1.0, caucasian=0.0, african=0.0)),
        skin=('skins', 'middleage_asian_male', '.mhmat'),
        hair='short04', eyebrows='eyebrow003', clothes=['male_casualsuit02', 'shoes03']),
}
# young men modelled on photos: MakeHuman's asian base reads puffy with big round eyes and a broad nose; slim it toward the photos
FRIEND_FACE = {'nose-volume-decr': .45, 'nose-point-width-decr': .45, 'nose-flaring-decr': .25, 'nose-scale-depth-decr': .15,
               'lr-eye-height1-decr': .35, 'lr-eye-epicanthus-in': .4, 'lr-eye-bag-decr': .5, 'eyebrows-angle-down': .5,
               'mouth-lowerlip-volume-decr': .3, 'mouth-upperlip-volume-decr': .2, 'lr-cheek-inner-decr': .4, 'neck-double-decr': .5}
for k in ('anjun', 'jinwook', 'parkjin', 'woochan'):
    t = PRESETS[k]['targets']
    for n, w in FRIEND_FACE.items():
        opp = n.rsplit('-', 1)[0] + '-' + {'incr': 'decr', 'decr': 'incr'}.get(n.rsplit('-', 1)[1], '')
        if n not in t and opp not in t: t[n] = w
    t['lr-eye-height2-decr'] = 1.0; t['lr-eye-eyefold-down'] = max(t.get('lr-eye-eyefold-down', 0), .5); t['lr-eye-scale-decr'] = max(t.get('lr-eye-scale-decr', 0), .25)
PRESETS['parkjin']['targets'].update({'head-fat-incr': .05, 'lr-cheek-volume-incr': .15})
P = PRESETS[PRESET]

bpy.ops.wm.read_factory_settings(use_empty=True)
addon_utils.enable('bl_ext.user_default.mpfb', default_set=True, persistent=False)
human = HumanService.create_human(macro_detail_dict=P['macro'], scale=0.1)
TG = {}
for tname, w in P.get('targets', {}).items():   # 'lr-x' = symmetric l-x and r-x
    if tname.startswith('lr-'): TG['l-' + tname[3:]] = w; TG['r-' + tname[3:]] = w
    else: TG[tname] = w
for tname, w in TG.items():   # face sculpt (age, jowls, eye bags...)
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
if P.get('hair'): HumanService.add_mhclo_asset(asset('hair', P['hair'], '.mhclo'), human, asset_type='Hair', subdiv_levels=0, material_type='MAKESKIN')
for c in P['clothes']:
    HumanService.add_mhclo_asset(asset('clothes', c, '.mhclo'), human, asset_type='Clothes', subdiv_levels=1, material_type='MAKESKIN')
if P.get('costume'):   # farm outfit: retextured clothes, vest/arm covers/boots, modelled farm hat
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
