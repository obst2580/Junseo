"""
CMU 모션캡처(BVH) -> MPFB game_engine 뼈대 리타깃. 결과는 뼈대만 있는 glTF(동작 클립 여러 개) + anims.json.
  - 기본 자세 차이(원본 T포즈 / 대상 A포즈)는 팔다리 뼈 방향을 맞춰 흡수, 몸통은 직립 기준 델타 회전
  - 걷는 방향을 캐릭터 정면(-Y)으로 회전 정렬
  - 반복 동작: 자세가 가장 잘 이어지는 구간을 자동 탐색해 루프로 자르고, 수평 이동을 빼서 제자리 동작 + 이동 속도 기록
  - 단발 동작: 지정 구간을 자르고 수평 이동은 유지(루트 모션)
Run: blender -b --factory-startup -P retarget_cmu.py -- <bvh_dir> <out.gltf>
CMU Graphics Lab Motion Capture Database (mocap.cs.cmu.edu), BVH 변환본 사용.
"""
import bpy, sys, os, math, json, addon_utils, numpy as np
from mathutils import Matrix, Quaternion, Vector

argv = sys.argv[sys.argv.index('--') + 1:]
BVH, OUT = argv[0], argv[1]
addon_utils.enable('bl_ext.user_default.mpfb', default_set=True, persistent=False)
bpy.ops.wm.read_factory_settings(use_empty=True)
addon_utils.enable('bl_ext.user_default.mpfb', default_set=True, persistent=False)
from bl_ext.user_default.mpfb.services.humanservice import HumanService
FPS = 30
bpy.context.scene.render.fps = FPS

# name: (file, mode, a, b)  loop: a..b = loop length range in seconds;  once: a..b = trim window in seconds
CLIPS = {
    'idle':      ('77_02', 'loop', 2.5, 5.0),
    'walk':      ('16_15', 'loop', .9, 1.4),
    'jog':       ('16_35', 'loop', .55, .9),
    'run':       ('09_01', 'loop', .5, .8),
    'sneak':     ('139_29', 'loop', 1.0, 2.2),
    'limp':      ('77_19', 'loop', 1.0, 2.2),
    'drunk':     ('91_09', 'loop', 1.6, 3.5),
    'wait':      ('141_20', 'loop', 3.0, 6.0),
    'jump':      ('16_05', 'once', .3, 2.2),
    'pickup':    ('26_09', 'once', .5, 4.5),
    'dig':       ('79_04', 'loop', 1.2, 3.0),
    'sweep':     ('80_61', 'loop', 1.0, 3.0),
    'push':      ('81_06', 'loop', 1.0, 3.0),
    'punch':     ('143_23', 'once', .5, 4.0),
    'draw_gun':  ('139_05', 'once', .3, 4.0),
    'shoot':     ('80_03', 'once', .5, 5.0),
    'phone':     ('80_25', 'once', .3, 8.0),
    'wave':      ('13_26', 'once', 1.0, 7.0),
    'sit_stand': ('143_18', 'once', .3, 6.0),
    'fall':      ('90_16', 'once', .3, 5.0),
    'get_up':    ('140_01', 'once', .3, 6.0),
    'shrug':     ('141_21', 'once', .2, 2.2),
    # ---- fighting: attacks (windows found by tools/strike detection on the CMU boxing / kick takes)
    'guard':      ('14_01', 'loopw', .7, 1.5, .1, 2.2),     # boxing stance bob (search only before the first punch)
    'jab':        ('13_18', 'once', 12.75, 13.8),
    'cross':      ('13_17', 'once', 4.75, 5.85),
    'hook':       ('13_18', 'once', 2.0, 3.15),
    'uppercut':   ('17_10', 'once', 17.6, 18.7),
    'combo_12':   ('14_03', 'once', 8.95, 10.2),
    'combo_jh':   ('14_03', 'once', 6.5, 7.85),
    'flurry':     ('80_10', 'once', 1.0, 4.9),
    'kick_front': ('74_04', 'once', .6, 2.4),
    'kick_side':  ('143_24', 'once', 1.85, 3.35),
    'knee':       ('143_24', 'once', 3.3, 4.55),
    'jump_kick':  ('75_16', 'once', .8, 2.85),
    # ---- fighting: reactions (mocap)
    'knockdown':  ('90_18', 'once', .3, 3.25),
    'getup_back': ('139_18', 'once', .3, 7.0),
    'stumble':    ('104_13', 'once', .3, 4.5),
    'hurt_walk':  ('91_26', 'loop', 1.0, 2.4),
}
# ---- fighting: hit reactions authored on the guard stance: (bone, world axis, peak angle rad) + pelvis shove (dx, dy, dz) m
# axes (Blender, character faces -Y): +X angle bends forward, -X bends back; +Z turns to the character's left; +Y tilts to its left
REACT = {
    # head
    'hit_face':   dict(rot=[('head', 'X', -.85), ('neck_01', 'X', -.45), ('spine_03', 'X', -.32), ('spine_02', 'X', -.18), ('spine_01', 'X', -.08),
                            ('upperarm_l', 'X', -.35), ('upperarm_r', 'X', -.35)], shove=(0, .3, 0), tp=.08, tau=.3),
    'hit_face_l': dict(rot=[('head', 'Z', -1.05), ('head', 'Y', -.4), ('neck_01', 'Z', -.45), ('spine_03', 'Z', -.35), ('spine_02', 'Z', -.18), ('spine_02', 'Y', -.18),
                            ('upperarm_l', 'Y', .35)], shove=(-.18, .1, 0), tp=.08, tau=.32),
    'hit_face_r': dict(rot=[('head', 'Z', 1.05), ('head', 'Y', .4), ('neck_01', 'Z', .45), ('spine_03', 'Z', .35), ('spine_02', 'Z', .18), ('spine_02', 'Y', .18),
                            ('upperarm_r', 'Y', -.35)], shove=(.18, .1, 0), tp=.08, tau=.32),
    'hit_chin':   dict(rot=[('head', 'X', -1.15), ('neck_01', 'X', -.5), ('spine_03', 'X', -.38), ('spine_02', 'X', -.22), ('pelvis', 'X', -.06),
                            ('upperarm_l', 'X', .3), ('upperarm_r', 'X', .3)], shove=(0, .22, .05), tp=.07, tau=.34),
    # trunk
    'hit_chest':  dict(rot=[('spine_03', 'X', -.38), ('spine_02', 'X', -.32), ('spine_01', 'X', -.15), ('head', 'X', .35), ('neck_01', 'X', .15),
                            ('upperarm_l', 'X', -.4), ('upperarm_r', 'X', -.4)], shove=(0, .5, 0), tp=.09, tau=.36),
    'hit_gut':    dict(rot=[('spine_01', 'X', .45), ('spine_02', 'X', .55), ('spine_03', 'X', .35), ('head', 'X', -.15), ('pelvis', 'X', .18),
                            ('upperarm_l', 'X', .5), ('upperarm_r', 'X', .5), ('lowerarm_l', 'Z', -.6), ('lowerarm_r', 'Z', .6)], shove=(0, .25, -.12), tp=.12, tau=.55),
    'hit_liver_l': dict(rot=[('spine_01', 'Y', .35), ('spine_02', 'Y', .4), ('spine_02', 'X', .25), ('spine_03', 'Y', .2), ('head', 'Y', -.2), ('pelvis', 'Y', -.1),
                             ('upperarm_l', 'X', .55), ('lowerarm_l', 'Z', -.7), ('calf_l', 'X', .35)], shove=(-.16, .08, -.1), tp=.11, tau=.5),
    'hit_liver_r': dict(rot=[('spine_01', 'Y', -.35), ('spine_02', 'Y', -.4), ('spine_02', 'X', .25), ('spine_03', 'Y', -.2), ('head', 'Y', .2), ('pelvis', 'Y', .1),
                             ('upperarm_r', 'X', .55), ('lowerarm_r', 'Z', .7), ('calf_r', 'X', .35)], shove=(.16, .08, -.1), tp=.11, tau=.5),
    # legs (low kick: the hit knee buckles, weight drops onto it)
    'hit_leg_l':  dict(rot=[('thigh_l', 'X', -.3), ('calf_l', 'X', .95), ('pelvis', 'Y', .18), ('spine_02', 'Y', -.15), ('spine_02', 'X', .15)], shove=(-.05, .05, -.13), tp=.1, tau=.45),
    'hit_leg_r':  dict(rot=[('thigh_r', 'X', -.3), ('calf_r', 'X', .95), ('pelvis', 'Y', -.18), ('spine_02', 'Y', .15), ('spine_02', 'X', .15)], shove=(.05, .05, -.13), tp=.1, tau=.45),
    # heavy
    'hit_heavy':  dict(rot=[('head', 'X', -1.0), ('neck_01', 'X', -.5), ('spine_03', 'X', -.45), ('spine_02', 'X', -.35), ('pelvis', 'X', -.2),
                            ('upperarm_l', 'Y', .9), ('upperarm_r', 'Y', -.9), ('upperarm_l', 'X', -.5), ('upperarm_r', 'X', -.5)], shove=(0, .9, -.08), tp=.15, tau=.45),
    'hit_spin_l': dict(rot=[('head', 'Z', -1.2), ('neck_01', 'Z', -.55), ('spine_03', 'Z', -.5), ('spine_02', 'Z', -.4), ('pelvis', 'Z', -.45), ('spine_02', 'Y', -.25),
                            ('upperarm_l', 'Y', .8), ('upperarm_r', 'Y', -.4)], shove=(-.55, .3, -.06), tp=.14, tau=.5),
    'hit_spin_r': dict(rot=[('head', 'Z', 1.2), ('neck_01', 'Z', .55), ('spine_03', 'Z', .5), ('spine_02', 'Z', .4), ('pelvis', 'Z', .45), ('spine_02', 'Y', .25),
                            ('upperarm_r', 'Y', -.8), ('upperarm_l', 'Y', .4)], shove=(.55, .3, -.06), tp=.14, tau=.5),
}
# contact data for the attacks: (time s, striking bone) — from the strike detection on the source takes
STRIKES = {
    'jab': [(.45, 'hand_l')], 'cross': [(.48, 'hand_r')], 'hook': [(.47, 'hand_r')], 'uppercut': [(.47, 'hand_r')],
    'combo_12': [(.45, 'hand_l'), (.65, 'hand_r')], 'combo_jh': [(.47, 'hand_l'), (.7, 'hand_r')],
    'flurry': [(round(t - 1.0, 2), 'hand_' + sd) for t, sd in [(1.27, 'r'), (1.5, 'r'), (1.77, 'l'), (2.03, 'l'), (2.27, 'r'), (2.5, 'r'), (2.77, 'l'), (3.03, 'l'),
               (3.3, 'r'), (3.57, 'r'), (3.83, 'l'), (4.07, 'l'), (4.33, 'r'), (4.57, 'r')]],
    'kick_front': [(.83, 'foot_r')], 'kick_side': [(.62, 'foot_l')], 'knee': [(.6, 'calf_r')], 'jump_kick': [(1.3, 'foot_r')], 'punch': [(.5, 'hand_l')],
}
for k_ in REACT: CLIPS[k_] = ('14_01', 'react', .4, 1.75)
MAP = {'Hips': 'pelvis', 'LowerBack': 'spine_01', 'Spine': 'spine_02', 'Spine1': 'spine_03', 'Neck': 'neck_01', 'Head': 'head',
       'LeftUpLeg': 'thigh_l', 'LeftLeg': 'calf_l', 'LeftFoot': 'foot_l', 'LeftToeBase': 'ball_l',
       'RightUpLeg': 'thigh_r', 'RightLeg': 'calf_r', 'RightFoot': 'foot_r', 'RightToeBase': 'ball_r',
       'LeftShoulder': 'clavicle_l', 'LeftArm': 'upperarm_l', 'LeftForeArm': 'lowerarm_l', 'LeftHand': 'hand_l',
       'RightShoulder': 'clavicle_r', 'RightArm': 'upperarm_r', 'RightForeArm': 'lowerarm_r', 'RightHand': 'hand_r'}
TRUNK = {'pelvis', 'spine_01', 'spine_02', 'spine_03', 'neck_01', 'head'}

human = HumanService.create_human(macro_detail_dict=dict(gender=1.0, age=.577, muscle=.5, weight=.5, proportions=.5, height=.5,
                                  cupsize=.5, firmness=.5, race=dict(asian=1.0, caucasian=0.0, african=0.0)), scale=0.1)
tgt = HumanService.add_builtin_rig(human, 'game_engine', import_weights=False)
tgt = tgt if tgt else next(o for o in bpy.context.scene.objects if o.type == 'ARMATURE')
bpy.data.objects.remove(human, do_unlink=True)
TW = tgt.matrix_world.copy(); TWr = TW.to_quaternion(); TWi = TW.inverted()
tb = tgt.data.bones
order = [b.name for b in tb]   # bones are stored parent-before-child
REST = {b.name: b.matrix_local.copy() for b in tb}
T_REST_W = {n: (TW @ REST[n]) for n in order}
fwd_t = Vector((0, -1, 0))   # MakeHuman faces -Y
pel_h = (TW @ REST['pelvis']).translation.z
for pb in tgt.pose.bones: pb.rotation_mode = 'QUATERNION'

def yaw_to(src_fwd, dst_fwd):
    a = math.atan2(src_fwd.y, src_fwd.x); b = math.atan2(dst_fwd.y, dst_fwd.x)
    return Quaternion((0, 0, 1), b - a)

meta = {}
for clip, spec in CLIPS.items():
    fn, mode, a, b = spec[:4]
    path = os.path.join(BVH, fn + '.bvh')
    if not os.path.exists(path): print('SKIP', clip); continue
    before = set(bpy.data.objects)
    bpy.ops.import_anim.bvh(filepath=path, axis_forward='-Z', axis_up='Y', rotate_mode='NATIVE', use_fps_scale=False, update_scene_fps=False,
                            update_scene_duration=False, global_scale=1.0)
    src = next(o for o in bpy.data.objects if o not in before)
    sb = src.data.bones; SW = src.matrix_world.copy()
    S_REST_W = {s: (SW @ sb[s].matrix_local) for s in MAP}
    act = src.animation_data.action; f0, f1 = [int(x) for x in act.frame_range]
    src_fps = 120; step = src_fps // FPS
    frames = list(range(f0, f1 + 1, step))
    # sample source world rotations + hip positions
    R = {s: [] for s in MAP}; HP = []; FW = []
    for f in frames:
        bpy.context.scene.frame_set(f)
        for s in MAP:
            R[s].append((SW @ src.pose.bones[s].matrix).to_quaternion())
        HP.append((SW @ src.pose.bones['Hips'].matrix).translation.copy())
        lat = (SW @ src.pose.bones['LeftUpLeg'].matrix).translation - (SW @ src.pose.bones['RightUpLeg'].matrix).translation
        FW.append(lat.cross(Vector((0, 0, 1))).normalized())   # forward = (left - right) x up
    n = len(frames); HP = np.array([tuple(p) for p in HP])
    # pick the segment
    if mode == 'loopw': mode_eff = 'loop'
    if mode in ('loop', 'loopw'):
        L0, L1 = int(a * FPS), int(b * FPS)
        core0, core1 = (int(spec[4] * FPS), min(n - 1, int(spec[5] * FPS))) if mode == 'loopw' else (int(n * .1), int(n * .9))
        keyb = ['LeftUpLeg', 'LeftLeg', 'RightUpLeg', 'RightLeg', 'Hips', 'LeftArm', 'RightArm', 'Spine1']
        Q = np.array([[tuple(R[s][i]) for s in keyb] for i in range(n)])
        best = (1e9, 0, 0)
        for i in range(core0, max(core0 + 1, core1 - L0)):
            for j in range(i + L0, min(i + L1, n - 1) + 1):
                d = 1 - np.abs((Q[i] * Q[j]).sum(-1)).clip(0, 1)
                hv = abs(HP[i, 2] - HP[j, 2]) * .01
                c = d.sum() + hv
                if c < best[0]: best = (c, i, j)
        _, i0, i1 = best
    else:
        i0, i1 = min(int(a * FPS), n - 2), min(int(b * FPS), n - 1)
    seg = list(range(i0, i1 + 1))
    # facing alignment: average hip facing over the segment -> character forward
    favg = sum((FW[i] for i in seg), Vector()); favg.z = 0; favg.normalize()
    Rc = yaw_to(favg, fwd_t)
    rest_lat = S_REST_W['LeftUpLeg'].translation - S_REST_W['RightUpLeg'].translation
    Rr = yaw_to(rest_lat.cross(Vector((0, 0, 1))).normalized(), fwd_t)
    # per-bone alignment of rest directions (limbs) in the rotated source rest frame
    S_REST_Q = {s: Rr @ S_REST_W[s].to_quaternion() for s in MAP}
    ALIGN = {}
    for s, t in MAP.items():
        if t in TRUNK: ALIGN[s] = Quaternion()
        else:
            td = T_REST_W[t].to_quaternion() @ Vector((0, 1, 0)); sd = S_REST_Q[s] @ Vector((0, 1, 0))
            ALIGN[s] = td.rotation_difference(sd)
    # hips: scale and in-place handling
    src_stand = S_REST_W['Hips'].translation.z if S_REST_W['Hips'].translation.z > 1 else np.median(HP[:, 2])
    stand_h = np.percentile(HP[seg, 2], 75) if mode in ('loop', 'loopw') else max(HP[seg[0], 2], np.percentile(HP[:, 2], 90))   # a clip that starts on the floor: scale by the standing height
    scale = pel_h / max(stand_h, 1e-3)
    P = np.array([tuple(Rc @ Vector(p)) for p in HP[seg]])
    P -= P[0]
    t = np.arange(len(seg)) / FPS
    speed = 0.
    if mode in ('loop', 'loopw'):
        for k in (0, 1):   # remove the linear drift (in-place loop), remember the speed
            co = np.polyfit(t, P[:, k], 1); P[:, k] -= np.polyval(co, t); speed = speed if k else 0
        dist = np.linalg.norm((np.array(tuple(Rc @ Vector(HP[seg[-1]]))) - np.array(tuple(Rc @ Vector(HP[seg[0]]))))[:2])
        speed = dist * scale / (t[-1] if t[-1] else 1)
    P *= scale
    # build the action on the target
    action = bpy.data.actions.new(clip); action.use_fake_user = True
    tgt.animation_data_create(); tgt.animation_data.action = action
    RX = REACT.get(clip)
    def env(tt):   # sharp rise to the hit, damped wobble back
        if not RX: return 0.
        tp, tau = RX['tp'], RX['tau']
        if tt < tp: x = tt / tp; return x * x * (3 - 2 * x)
        return math.exp(-(tt - tp) / tau) * math.cos((tt - tp) * 7.5) * .85 + .15 * math.exp(-(tt - tp) / (tau * 2.5))
    AX = {'X': Vector((1, 0, 0)), 'Y': Vector((0, 1, 0)), 'Z': Vector((0, 0, 1))}
    STRK = []
    for fi, i in enumerate(seg):
        A = {}   # armature-space posed matrices
        e = env(fi / FPS); DC = {}
        SF = {round(t_ * FPS): bn_ for t_, bn_ in STRIKES.get(clip, [])}
        for name in order:
            bone = tb[name]; parent = bone.parent
            base = (A[parent.name] @ REST[parent.name].inverted() @ REST[name]) if parent else REST[name].copy()
            src_name = next((s for s, tt in MAP.items() if tt == name), None)
            if src_name is None: A[name] = base; continue
            Ws = Rc @ R[src_name][i]
            Wt = Ws @ S_REST_Q[src_name].inverted() @ ALIGN[src_name] @ T_REST_W[name].to_quaternion()
            if RX:   # cumulative world-space hit rotation: a bone carries its ancestors' recoil
                par_ = bone.parent; d_ = DC.get(par_.name, Quaternion()) if par_ else Quaternion()
                while par_ is not None and par_.name not in DC: par_ = par_.parent; d_ = DC.get(par_.name, Quaternion()) if par_ else Quaternion()
                for bn, ax, ang in RX['rot']:
                    if bn == name: d_ = d_ @ Quaternion(AX[ax], ang * e)
                DC[name] = d_; Wt = d_ @ Wt
            Arot = (TWr.inverted() @ Wt).to_matrix().to_4x4()
            M = Arot.copy(); M.translation = base.translation
            if name == 'pelvis':
                hw = (TW @ REST['pelvis']).translation + Vector((P[fi, 0], P[fi, 1], P[fi, 2] - (P[:, 2].mean() if mode in ('loop', 'loopw') else 0) + (stand_h - stand_h) ))
                hw.z = pel_h + (HP[i, 2] - stand_h) * scale
                if RX:   # shoved back: travel keeps most of the way (no snap back), dip recovers
                    tt_ = fi / FPS; tr_ = min(1., tt_ / (RX['tp'] * 2.5)); tr_ = tr_ * tr_ * (3 - 2 * tr_)
                    hw += Vector((RX['shove'][0] * tr_, RX['shove'][1] * tr_, RX['shove'][2] * e))
                M.translation = TWi @ hw
            A[name] = M
            pb = tgt.pose.bones[name]
            if fi == 0 and name == 'pelvis': P0W = TW @ M.translation
            basis = (base.inverted() @ M) if parent else (REST[name].inverted() @ M)
            loc, rot, _ = basis.decompose()
            pb.rotation_quaternion = rot; pb.keyframe_insert('rotation_quaternion', frame=fi + 1)
            if name == 'pelvis': pb.location = loc; pb.keyframe_insert('location', frame=fi + 1)
        if fi in SF:   # contact point of the strike, relative to the start position (character faces -Y: forward = -dy, left = +dx)
            bn_ = SF[fi]; w_ = TW @ (A[bn_].translation if bn_ in A else Vector()); d_ = w_ - P0W
            STRK.append(dict(t=round(fi / FPS, 3), bone=bn_, fwd=round(-d_.y, 3), lat=round(d_.x, 3), up=round(w_.z, 3)))
    meta[clip] = dict(loop=mode in ('loop', 'loopw'), frames=len(seg), dur=round((len(seg) - 1) / FPS, 3), speed=round(float(speed), 3), src=fn)
    if STRK: meta[clip]['strikes'] = STRK
    print('CLIP', clip, meta[clip])
    bpy.data.objects.remove(src, do_unlink=True); bpy.data.actions.remove(act)   # keep only the retargeted clip
    for pb in tgt.pose.bones: pb.rotation_quaternion = (1, 0, 0, 0); pb.location = (0, 0, 0)

tgt.animation_data.action = None
bpy.ops.object.select_all(action='DESELECT'); tgt.select_set(True); bpy.context.view_layer.objects.active = tgt
bpy.ops.export_scene.gltf(filepath=OUT, export_format='GLTF_SEPARATE', use_selection=True, export_animations=True,
                          export_animation_mode='ACTIONS', export_skins=True, export_def_bones=False, export_frame_range=False,
                          export_force_sampling=True, export_optimize_animation_size=True)
json.dump({'fps': FPS, 'clips': meta}, open(os.path.join(os.path.dirname(OUT), 'anims.json'), 'w'), indent=1)
print('DONE')
