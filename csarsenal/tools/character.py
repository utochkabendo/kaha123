"""CS-style operator model. Each part is modelled in its bone's local frame (metres):
+X = character's left, +Y = up, +Z = forward.  Bone offsets are exported to agent.json and
mirrored 1:1 by dev.csarsenal.anim.Skeleton (which also derives the hitboxes)."""
import math
from meshlib import *

BONES = {
    # name: (parent, offset)
    'pelvis': (None, (0, 0.97, 0)),
    'spine': ('pelvis', (0, 0.10, 0)),
    'chest': ('spine', (0, 0.20, 0)),
    'neck': ('chest', (0, 0.21, -0.005)),
    'head': ('neck', (0, 0.05, 0.01)),
    'upperarm_l': ('chest', (0.185, 0.165, -0.01)),
    'upperarm_r': ('chest', (-0.185, 0.165, -0.01)),
    'forearm_l': ('upperarm_l', (0, -0.29, 0)),
    'forearm_r': ('upperarm_r', (0, -0.29, 0)),
    'hand_l': ('forearm_l', (0, -0.255, 0)),
    'hand_r': ('forearm_r', (0, -0.255, 0)),
    'thigh_l': ('pelvis', (0.095, -0.06, 0)),
    'thigh_r': ('pelvis', (-0.095, -0.06, 0)),
    'shin_l': ('thigh_l', (0, -0.42, 0)),
    'shin_r': ('thigh_r', (0, -0.42, 0)),
    'foot_l': ('shin_l', (0, -0.42, 0)),
    'foot_r': ('shin_r', (0, -0.42, 0)),
}


def fist(side='r'):
    """Gloved hand gripping a cylinder whose axis is local Z (thumb +Z).
    Palm normal = +X for the right hand (the gripped object sits at +X), fingers wrap it."""
    g = Geo()
    g.merge(loft([(0.012, -0.004, 0, 0.017, 0.030), (0.0, -0.005, 0, 0.018, 0.034), (-0.04, -0.008, 0, 0.02, 0.043), (-0.08, -0.009, 0, 0.018, 0.045), (-0.092, -0.008, 0, 0.012, 0.04)],
                 'glove', segs=14, axis='y', squareness=0.55))
    cx, cy, R = 0.022, -0.074, 0.027
    fingers = [(0.028, 0.0085, 1.00), (0.009, 0.009, 1.08), (-0.010, 0.0085, 1.0), (-0.028, 0.0075, 0.82)]
    for (z, r, L) in fingers:
        a0 = math.radians(-165)
        a1 = a0 + 3.25 * L
        pts = [(-0.006, -0.08, z)]
        for i in range(9):
            a = a0 + (a1 - a0) * i / 8
            pts.append((cx + math.cos(a) * R, cy + math.sin(a) * R, z))
        g.merge(tube(pts, r, 'glove', segs=8, radius_end=r * 0.85))
        # knuckle
        g.merge(ellipsoid((-0.008, -0.083, z), 0.011, 0.01, 0.0095, 'glove', segs=8, rings=6))
    thumb = [(-0.002, -0.018, 0.028), (0.008, -0.036, 0.047), (0.026, -0.052, 0.054), (0.044, -0.064, 0.05), (0.056, -0.072, 0.04)]
    g.merge(tube(thumb, 0.0105, 'glove', segs=8, radius_end=0.0085))
    # wrist cuff
    g.merge(loft([(0.03, -0.004, 0, 0.022, 0.034), (0.0, -0.004, 0, 0.023, 0.036)], 'glove', segs=14, axis='y', squareness=0.3))
    if side == 'l':
        g.scale(-1, 1, 1)
    return g


def arm_parts(W, side):
    s = 1 if side == 'l' else -1
    ua = loft([(0.035, 0, 0, 0.05, 0.05), (0.0, 0, 0, 0.058, 0.058), (-0.09, 0, 0.002, 0.058, 0.055), (-0.21, 0, 0, 0.049, 0.047), (-0.30, 0, 0, 0.044, 0.042)], 'shirt', segs=14, axis='y')
    W.add('upperarm_' + side, ua)
    # shoulder patch
    W.add('upperarm_' + side, loft([(-0.04, s * 0.004, 0, 0.061, 0.059), (-0.1, s * 0.004, 0.002, 0.06, 0.057)], 'gear', segs=14, axis='y', squareness=0.2))
    fa = loft([(0.02, 0, 0, 0.042, 0.04), (0.0, 0, 0, 0.045, 0.043), (-0.07, 0, 0.004, 0.047, 0.043), (-0.19, 0, 0, 0.036, 0.032), (-0.215, 0, 0, 0.034, 0.03)], 'shirt', segs=14, axis='y')
    W.add('forearm_' + side, fa)
    W.add('forearm_' + side, loft([(-0.19, 0, 0, 0.037, 0.034), (-0.215, 0, 0, 0.037, 0.034), (-0.26, 0, 0, 0.031, 0.028)], 'glove', segs=14, axis='y'))
    W.add('hand_' + side, fist(side))


def leg_parts(W, side):
    s = 1 if side == 'l' else -1
    th = loft([(0.05, 0, 0, 0.08, 0.085), (0.0, 0, 0.004, 0.088, 0.092), (-0.12, 0, 0.008, 0.083, 0.088), (-0.32, 0, 0, 0.064, 0.068), (-0.43, 0, 0, 0.056, 0.06)], 'pants', segs=16, axis='y')
    W.add('thigh_' + side, th)
    # thigh pouch on the outer side
    if s > 0:
        W.add('thigh_' + side, box(0.07, -0.2, -0.05, 0.105, -0.06, 0.05, 'gear', bevel=0.008))
    else:
        W.add('thigh_' + side, box(-0.105, -0.2, -0.05, -0.07, -0.06, 0.05, 'gear', bevel=0.008))
    sh = loft([(0.03, 0, 0, 0.052, 0.056), (0.0, 0, 0, 0.056, 0.06), (-0.1, 0, -0.006, 0.058, 0.064), (-0.29, 0, 0, 0.045, 0.048), (-0.42, 0, 0, 0.04, 0.042)], 'pants', segs=16, axis='y')
    W.add('shin_' + side, sh)
    W.add('shin_' + side, ellipsoid((0, -0.02, 0.05), 0.05, 0.06, 0.03, 'gear', segs=12, rings=8))  # knee pad
    W.add('shin_' + side, loft([(-0.27, 0, 0, 0.05, 0.053), (-0.30, 0, 0, 0.052, 0.055), (-0.425, 0, 0.005, 0.05, 0.056)], 'boot', segs=16, axis='y'))
    ft = loft([(-0.065, 0, -0.035, 0.044, 0.04), (-0.02, 0, -0.04, 0.05, 0.043), (0.08, 0, -0.05, 0.052, 0.03), (0.16, 0, -0.055, 0.045, 0.022), (0.19, 0, -0.058, 0.022, 0.013)], 'boot', segs=14, axis='z', squareness=0.35)
    W.add('foot_' + side, ft)
    W.add('foot_' + side, box(-0.05, -0.08, -0.07, 0.05, -0.066, 0.195, 'sole', bevel=0.005))


def torso(W):
    W.add('pelvis', loft([(-0.14, 0, 0, 0.11, 0.085), (-0.08, 0, 0.004, 0.16, 0.108), (0.0, 0, 0.004, 0.168, 0.11), (0.1, 0, -0.004, 0.155, 0.1)], 'pants', segs=18, axis='y'))
    W.add('pelvis', loft([(0.055, 0, -0.003, 0.163, 0.107), (0.1, 0, -0.004, 0.16, 0.106)], 'belt', segs=18, axis='y', squareness=0.2))
    W.add('pelvis', box(-0.035, 0.06, 0.1, 0.035, 0.095, 0.116, 'metal', bevel=0.004))  # buckle
    W.add('spine', loft([(-0.01, 0, -0.003, 0.155, 0.1), (0.1, 0, 0.004, 0.15, 0.104), (0.21, 0, 0.01, 0.162, 0.11)], 'shirt', segs=18, axis='y'))
    W.add('chest', loft([(-0.01, 0, 0.01, 0.163, 0.11), (0.08, 0, 0.016, 0.18, 0.12), (0.15, 0, 0.01, 0.19, 0.115), (0.19, 0, 0.0, 0.165, 0.097), (0.215, 0, -0.005, 0.075, 0.062)], 'shirt', segs=18, axis='y'))
    for s in (1, -1):
        W.add('chest', ellipsoid((s * 0.172, 0.155, -0.01), 0.07, 0.06, 0.066, 'shirt', segs=14, rings=8))
    W.add('neck', loft([(-0.02, 0, 0, 0.056, 0.055), (0.07, 0, 0.004, 0.052, 0.052)], 'mask', segs=14, axis='y'))


def vest(W):
    g = loft([(-0.19, 0, 0.012, 0.172, 0.126), (-0.1, 0, 0.018, 0.182, 0.132), (0.02, 0, 0.022, 0.195, 0.14), (0.14, 0, 0.015, 0.186, 0.132), (0.175, 0, 0.008, 0.16, 0.115)], 'vest', segs=20, axis='y', squareness=0.62)
    W.add('vest', g)
    # front mag pouches
    for x in (-0.085, 0.0, 0.085):
        W.add('vest', _pouch(x))
    # shoulder straps
    for s in (1, -1):
        W.add('vest', tube([(s * 0.1, 0.17, 0.12), (s * 0.12, 0.22, 0.05), (s * 0.12, 0.22, -0.05), (s * 0.1, 0.17, -0.12)], 0.022, 'vest', segs=8))
    # radio pouch on the back
    W.add('vest', box(-0.07, -0.1, -0.19, 0.07, 0.08, -0.135, 'gear', bevel=0.01))


def _pouch(x):
    g = Geo()
    g.merge(box(x - 0.036, -0.17, 0.125, x + 0.036, -0.045, 0.172, 'gear', bevel=0.009))
    g.merge(box(x - 0.038, -0.07, 0.126, x + 0.038, -0.04, 0.176, 'gear', bevel=0.008))
    return g


def head(W):
    h = loft([(0.0, 0, 0.035, 0.035, 0.045), (0.025, 0, 0.022, 0.062, 0.08), (0.07, 0, 0.012, 0.077, 0.097), (0.12, 0, 0.004, 0.081, 0.1), (0.17, 0, -0.006, 0.073, 0.09),
              (0.205, 0, -0.012, 0.05, 0.065), (0.225, 0, -0.014, 0.012, 0.018)], 'mask', segs=20, axis='y')
    W.add('head', h)
    W.add('head', ellipsoid((0, 0.075, 0.1), 0.014, 0.022, 0.016, 'mask', segs=10, rings=6))  # nose
    # goggles
    for s in (1, -1):
        W.add('head', ellipsoid((s * 0.036, 0.105, 0.09), 0.028, 0.02, 0.012, 'lens', segs=14, rings=8))
        W.add('head', torus((s * 0.036, 0.105, 0.09), 0.027, 0.005, 'gear', segs=16, tube=6, axis='z'))
    W.add('head', loft([(0.095, 0, 0.0, 0.083, 0.101), (0.115, 0, 0.0, 0.083, 0.101)], 'gear', segs=20, axis='y'))
    # helmet (separate part: only drawn with a helmet)
    hel = loft([(0.1, 0, 0.0, 0.098, 0.118), (0.13, 0, -0.002, 0.1, 0.12), (0.19, 0, -0.008, 0.088, 0.108), (0.235, 0, -0.012, 0.055, 0.07), (0.255, 0, -0.014, 0.012, 0.015)], 'helmet', segs=22, axis='y', cap_start=True)
    W.add('helmet', hel)
    W.add('helmet', box(-0.02, 0.2, 0.07, 0.02, 0.23, 0.1, 'gear', bevel=0.005).rotate('x', -30, origin=(0, 0.21, 0.08)))  # NVG mount


def build():
    W = Model('agent')
    torso(W)
    vest(W)
    head(W)
    for s in ('l', 'r'):
        arm_parts(W, s)
        leg_parts(W, s)
    W.props['bones'] = {k: {'parent': p, 'offset': list(o)} for k, (p, o) in BONES.items()}
    return W


def world_offsets():
    out = {}
    for k in BONES:
        x = np.zeros(3)
        c = k
        while c:
            p, o = BONES[c]
            x += np.array(o)
            c = p
        out[k] = x
    return out


def rest_pose_geo(W):
    offs = world_offsets()
    g = Geo()
    for name, part in W.parts.items():
        bone = 'chest' if name == 'vest' else ('head' if name == 'helmet' else name)
        gg = part.copy().translate(*offs[bone])
        g.merge(gg)
    return g
