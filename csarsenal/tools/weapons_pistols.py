"""Pistols, knife, zeus, grenades, C4."""
from gunparts import *


def _std_pistol(W, slide_x0, slide_x1, slide_h, slide_w, frame_mat='polymer', slide_mat='metal_dark', grip_len=92,
                grip_angle=18, grip_depth=31, grip_w=27, barrel_r=5.5, yb=None, rail=True, guard=(8, 52)):
    yb = yb if yb is not None else 2 + slide_h * 0.55
    pistol_frame(W, slide_x0 + 4, slide_x1 - 6, 3, depth=11, w=slide_w - 2, mat=frame_mat, rail_len=rail)
    pistol_slide(W, slide_x0, slide_x1, 2, slide_h, slide_w, mat=slide_mat)
    W.add('bolt', cylinder((slide_x1 - 0.5, yb, 0), (slide_x1 + 1.0, yb, 0), barrel_r, 'metal', segs=12))
    bore(W, slide_x1 + 1.0, yb, barrel_r * 0.6, part='bolt')
    grip(W, 0, 3, angle=grip_angle, length=grip_len, width=grip_w, depth=grip_depth, mat=frame_mat)
    trigger_guard(W, guard[0], guard[1], -8, depth=22, thick=4, mat=frame_mat, width=10)
    return yb


def _pistol_mag(W, grip_angle, grip_len, depth=24, w=20, mat='metal_dark', base=True, extra=0):
    """magazine hidden inside the grip; only the base plate is visible. Built raked like the grip."""
    g = Geo()
    g.merge(extrude([(-depth / 2, 0), (depth / 2, 0), (depth / 2, -grip_len - 2 - extra), (-depth / 2, -grip_len - 2 - extra)], -w / 2, w / 2, mat, bevel=1.2))
    if base:
        g.merge(box(-depth / 2 - 2.5, -grip_len - 8 - extra, -w / 2 - 2, depth / 2 + 2.5, -grip_len - 1 - extra, w / 2 + 2, 'polymer', bevel=1.5))
    g.merge(cylinder((-depth / 2 + 4, 2, 0), (depth / 2 - 3, 2, 0), 3.6, 'brass', segs=10))
    g.rotate('z', -grip_angle).translate(-2, 1, 0)
    W.add('mag', g)


def glock18():
    W = Model('glock18')
    yb = _std_pistol(W, -18, 168, 27, 25, frame_mat='polymer', slide_mat='metal_dark', grip_angle=20, grip_len=90)
    _pistol_mag(W, 20, 88)
    W.point('grip', 0, -2)
    W.point('support', 18, -48, -14)
    W.point('muzzle', 170, yb)
    W.point('eject', 70, 24, 13)
    W.point('mag', -2, -88)
    W.point('slide', 0, 15)
    W.props.update(grip_angle=20, pistol=True)
    return W


def usp_s():
    W = Model('usp_s')
    yb = _std_pistol(W, -18, 178, 28, 26, frame_mat='polymer', slide_mat='metal_dark', grip_angle=16, grip_len=92)
    lathe_x(W, [(0, 6.5), (10, 6.5)], 179, yb, 'metal', segs=12)
    suppressor(W, 188, yb, 14.5, 150)
    _pistol_mag(W, 16, 90)
    W.point('grip', 0, -2)
    W.point('support', 18, -48, -14)
    W.point('muzzle', 180, yb)
    W.point('muzzle_sil', 338, yb)
    W.point('silencer', 188, yb)
    W.point('eject', 70, 24, 13)
    W.point('mag', -2, -90)
    W.props.update(grip_angle=16, pistol=True)
    return W


def p2000():
    W = Model('p2000')
    yb = _std_pistol(W, -18, 165, 29, 26, frame_mat='polymer', slide_mat='metal_dark', grip_angle=16, grip_len=90)
    W.add('body', box(-24, 12, -6, -16, 26, 6, 'metal_dark', bevel=1))  # hammer
    _pistol_mag(W, 16, 88)
    W.point('grip', 0, -2)
    W.point('support', 18, -48, -14)
    W.point('muzzle', 167, yb)
    W.point('eject', 70, 24, 13)
    W.point('mag', -2, -88)
    W.props.update(grip_angle=16, pistol=True)
    return W


def p250():
    W = Model('p250')
    yb = _std_pistol(W, -18, 162, 30, 27, frame_mat='polymer', slide_mat='metal', grip_angle=17, grip_len=88)
    W.add('body', box(-25, 12, -6, -17, 26, 6, 'metal_dark', bevel=1))
    _pistol_mag(W, 17, 86)
    W.point('grip', 0, -2)
    W.point('support', 18, -48, -14)
    W.point('muzzle', 164, yb)
    W.point('eject', 70, 24, 13)
    W.point('mag', -2, -86)
    W.props.update(grip_angle=17, pistol=True)
    return W


def fiveseven():
    W = Model('fiveseven')
    yb = _std_pistol(W, -18, 190, 26, 24, frame_mat='polymer', slide_mat='polymer', grip_angle=18, grip_len=98, grip_depth=30)
    _pistol_mag(W, 18, 96)
    W.point('grip', 0, -2)
    W.point('support', 18, -50, -14)
    W.point('muzzle', 192, yb)
    W.point('eject', 80, 22, 12)
    W.point('mag', -2, -96)
    W.props.update(grip_angle=18, pistol=True)
    return W


def cz75():
    W = Model('cz75')
    yb = _std_pistol(W, -18, 170, 25, 24, frame_mat='metal_dark', slide_mat='metal_dark', grip_angle=15, grip_len=92, grip_depth=30, rail=False)
    W.add('body', extrude([(-16, 3), (150, 3), (150, 14), (-16, 14)], -13, 13, 'metal_dark', bevel=1.2))  # frame rails around slide
    W.add('body', box(-26, 12, -6, -18, 25, 6, 'metal_dark', bevel=1))
    grip(W, 0, 3, angle=15, length=90, width=28.5, depth=29, mat='wood_dark')
    _pistol_mag(W, 15, 90)
    W.point('grip', 0, -2)
    W.point('support', 18, -48, -14)
    W.point('muzzle', 172, yb)
    W.point('eject', 70, 22, 12)
    W.point('mag', -2, -90)
    W.props.update(grip_angle=15, pistol=True)
    return W


def tec9():
    W = Model('tec9')
    yb = 30
    W.add('body', extrude([(-40, 6), (130, 6), (130, 48), (-40, 48)], -15, 15, 'metal_dark', bevel=2))
    W.add('body', lathe([(130, 11), (140, 13), (250, 13), (255, 10)], 'metal_dark', segs=16, axis='x', origin=(0, yb, 0)))
    for xx in range(150, 245, 14):
        W.add('body', cylinder((xx, yb, 12), (xx, yb, 14.2), 3.2, 'bore', segs=8))
        W.add('body', cylinder((xx, yb, -12), (xx, yb, -14.2), 3.2, 'bore', segs=8))
    barrel(W, 250, 262, yb, 6)
    bore(W, 262, yb, 4)
    grip(W, 0, 6, angle=18, length=90, width=26, depth=30, mat='polymer')
    trigger_guard(W, 10, 55, -4, depth=22, thick=4, width=10)
    # mag in front of the trigger guard
    straight_mag(W, 70, 6, 150, 28, 20, rake=4, mat='metal_dark')
    W.add('bolt', cylinder((40, 44, -12), (40, 44, -26), 4.2, 'metal', segs=10, bevel=0.6))
    W.point('grip', 0, -2)
    W.point('support', 85, -60)
    W.point('muzzle', 262, yb)
    W.point('eject', 60, 34, 15)
    W.point('mag', 84, 6)
    W.point('bolt', 40, 44, -20)
    W.props.update(grip_angle=18, pistol=True, support_kind='mag')
    return W


def beretta():
    W = Model('elite')
    yb = 16
    pistol_frame(W, -14, 140, 3, depth=11, w=24, mat='chrome', rail_len=0)
    # open-top slide: two side rails, exposed barrel
    W.add('bolt', extrude([(-18, 2), (100, 2), (100, 26), (-18, 26)], -12.5, 12.5, 'chrome', bevel=1.6))
    W.add('bolt', extrude([(100, 2), (150, 2), (150, 12), (140, 20), (100, 20)], -12.5, -5, 'chrome', bevel=1.2))
    W.add('bolt', extrude([(100, 2), (150, 2), (150, 12), (140, 20), (100, 20)], 5, 12.5, 'chrome', bevel=1.2))
    W.add('body', cylinder((95, yb, 0), (152, yb, 0), 6, 'chrome', segs=14))
    bore(W, 152, yb, 4)
    W.add('bolt', box(135, 20, -2, 142, 26, 2, 'chrome', bevel=0.5))
    W.add('body', box(-24, 12, -6, -16, 26, 6, 'chrome', bevel=1))
    grip(W, 0, 3, angle=14, length=94, width=28, depth=30, mat='wood_dark')
    trigger_guard(W, 8, 56, -8, depth=24, thick=4, mat='chrome', width=10)
    _pistol_mag(W, 14, 94)
    W.point('grip', 0, -2)
    W.point('support', 18, -48, -14)
    W.point('muzzle', 152, yb)
    W.point('eject', 60, 24, 12)
    W.point('mag', -2, -94)
    W.props.update(grip_angle=14, pistol=True, dual=True)
    return W


def deagle():
    W = Model('deagle')
    yb = 22
    # big triangular barrel + slide
    W.add('bolt', extrude([(-22, 2), (70, 2), (70, 40), (-18, 40), (-22, 34)], -14.5, 14.5, 'chrome', bevel=2))
    for i in range(6):
        xx = -16 + i * 4.5
        W.add('bolt', box(xx, 8, 14.2, xx + 1.8, 36, 15, 'bore', bevel=0.1))
        W.add('bolt', box(xx, 8, -15, xx + 1.8, 36, -14.2, 'bore', bevel=0.1))
    W.add('body', extrude([(70, 4), (215, 4), (215, 38), (210, 44), (70, 44)], -11, 11, 'chrome', bevel=2))
    W.add('body', box(80, 44, -4, 205, 48, 4, 'chrome', bevel=0.6))
    W.add('body', box(200, 44, -2, 207, 52, 2, 'chrome', bevel=0.5))
    bore(W, 215, yb, 5.5)
    pistol_frame(W, -18, 70, 3, depth=12, w=27, mat='chrome', rail_len=0)
    W.add('body', box(-28, 16, -6, -20, 32, 6, 'chrome', bevel=1))
    grip(W, 0, 3, angle=20, length=98, width=31, depth=36, mat='rubber')
    trigger_guard(W, 8, 60, -9, depth=26, thick=4.5, mat='chrome', width=11)
    _pistol_mag(W, 20, 96, depth=28, w=22)
    W.point('grip', 0, -2)
    W.point('support', 20, -52, -15)
    W.point('muzzle', 215, yb)
    W.point('eject', 40, 34, 14)
    W.point('mag', -2, -96)
    W.props.update(grip_angle=20, pistol=True)
    return W


def revolver():
    W = Model('revolver')
    yb = 34
    W.add('body', extrude(smooth_poly([(-30, 4), (60, 4), (60, 52), (40, 58), (-20, 58), (-34, 40)], 1), -12, 12, 'metal', bevel=2))
    # cylinder (separate part so it can spin)
    W.add('cylinder', lathe([(0, 12), (2, 18), (46, 18), (48, 12)], 'metal', segs=18, axis='x', origin=(5, yb - 8, 0)))
    for k in range(8):
        a = k * 45
        c = cylinder((4, 0, 0), (54, 0, 0), 3.4, 'bore', segs=8)
        c.translate(0, 13.5, 0).rotate('x', a, origin=(0, 0, 0)).translate(0, yb - 8, 0)
        W.add('cylinder', c)
    W.add('body', extrude([(60, 24), (230, 24), (230, 50), (60, 50)], -9, 9, 'metal', bevel=2))
    W.add('body', box(60, 48, -3, 225, 56, 3, 'metal', bevel=0.8))
    W.add('body', box(214, 54, -2, 224, 62, 2, 'metal', bevel=0.5))
    bore(W, 230, yb + 4, 5)
    W.add('body', extrude([(-36, 40), (-26, 40), (-18, 62), (-30, 62)], -3, 3, 'metal_dark', bevel=0.6))  # hammer
    grip(W, 0, 6, angle=26, length=92, width=30, depth=34, mat='rubber')
    trigger_guard(W, 10, 58, -2, depth=24, thick=4, mat='metal', width=10)
    W.point('grip', 0, -2)
    W.point('support', 18, -48, -14)
    W.point('muzzle', 230, yb + 4)
    W.point('eject', 30, yb, 20)
    W.point('mag', 30, yb - 8)
    W.point('cylinder', 30, yb - 8)
    W.props.update(grip_angle=26, pistol=True, revolver=True)
    return W


def zeus():
    W = Model('taser')
    W.add('body', extrude(smooth_poly([(-20, 2), (130, 2), (150, 12), (150, 40), (135, 50), (-10, 50), (-25, 34)], 1), -17, 17, 'zeus_yellow', bevel=4))
    W.add('body', box(150, 10, -12, 158, 42, 12, 'polymer', bevel=2))
    for z in (-6, 6):
        W.add('body', cylinder((157, 26, z), (161, 26, z), 3.5, 'metal', segs=10))
    grip(W, 0, 4, angle=18, length=90, width=28, depth=33, mat='polymer')
    trigger_guard(W, 8, 56, -6, depth=22, thick=4, width=10)
    W.add('body', box(20, 50, -8, 90, 54, 8, 'polymer', bevel=1))
    W.point('grip', 0, -2)
    W.point('support', 18, -48, -14)
    W.point('muzzle', 162, 26)
    W.point('eject', 60, 30, 16)
    W.point('mag', 0, -90)
    W.props.update(grip_angle=18, pistol=True)
    return W


# ======================================================================== knife
def knife():
    W = Model('knife')
    # handle along +x from the grip point, blade continues forward
    W.add('body', loft([(-55, 0, 0, 13, 11), (-50, 0, 0, 15, 12.5), (0, 0, 0, 14, 12), (40, 0, 0, 15, 12.5), (46, 0, 0, 13, 11)], 'polymer', axis='x', segs=16, squareness=0.35))
    for xx in (-35, -20, -5, 10, 25):
        W.add('body', lathe([(xx, 14.6), (xx + 4, 14.6)], 'rubber', segs=16, axis='x'))
    W.add('body', extrude([(46, -22), (52, -22), (52, 20), (46, 20)], -9, 9, 'metal_dark', bevel=1.5))  # guard
    W.add('body', ellipsoid((-58, 0, 0), 5, 12, 10, 'metal_dark', segs=12, rings=8))  # pommel
    # blade (drop point with fuller) - thin bevelled extrude
    blade = [(52, -11), (150, -12), (205, -8), (238, 4), (205, 10), (120, 12), (52, 12)]
    W.add('body', extrude(smooth_poly(blade, 1), -2.6, 2.6, 'blade', bevel=2.0))
    W.add('body', extrude([(70, 1), (170, 0), (170, 4), (70, 5)], -2.9, 2.9, 'blade_dark', bevel=0.8))
    W.point('grip', 0, 0)
    W.point('tip', 238, 4)
    W.props.update(knife=True)
    return W


def karambit():
    W = Model('karambit')
    W.add('body', loft([(-50, 0, 0, 11, 9), (-45, 0, 0, 13, 10), (30, 0, 0, 13, 10), (36, 0, 0, 11, 9)], 'polymer', axis='x', segs=14, squareness=0.3))
    W.add('body', torus((-66, 0, 0), 14, 4.5, 'metal_dark', segs=18, tube=8, axis='z'))
    pts = arc(36, -60, 75, 90, 30, 10)
    W.add('body', extrude(strip(pts, 22, 4), -2.5, 2.5, 'blade', bevel=1.8))
    W.point('grip', 0, 0)
    W.point('tip', pts[-1][0], pts[-1][1])
    W.props.update(knife=True)
    return W


# ======================================================================== grenades (axis = y, centre = origin)
def _fuze(W, top, pin_part='pin'):
    W.add('body', lathe([(top - 4, 12), (top, 12), (top + 18, 11), (top + 22, 8)], 'metal', segs=16, axis='y'))
    # spoon
    spoon = [(8, top + 20), (14, top + 18), (18, top), (20, top - 40), (16, top - 42), (13, top - 4), (8, top + 14)]
    W.add('spoon', extrude(spoon, -6, 6, 'metal', bevel=0.8).rotate('y', 90))
    # pin + ring
    W.add(pin_part, cylinder((0, top + 10, -2), (0, top + 10, -22), 1.6, 'metal', segs=8))
    W.add(pin_part, torus((0, top + 10, -32), 10, 1.6, 'metal', segs=18, tube=6, axis='x'))


def he():
    W = Model('hegrenade')
    W.add('body', ellipsoid((0, 0, 0), 31, 36, 31, 'grenade_od', segs=22, rings=14))
    W.add('body', lathe([(-38, 0), (-38, 10), (-34, 14)], 'grenade_od', segs=16, axis='y', cap_start=False))
    W.add('body', lathe([(28, 13), (38, 13)], 'grenade_od', segs=16, axis='y'))
    _fuze(W, 38)
    W.point('grip', 0, 0)
    W.props.update(grenade=True)
    return W


def _canister(W, h, r, mat, band=None, holes=False):
    W.add('body', lathe([(-h / 2, 0), (-h / 2, r - 2), (-h / 2 + 2, r), (h / 2 - 2, r), (h / 2, r - 2), (h / 2, 11)], mat, segs=22, axis='y', cap_start=False, cap_end=False))
    W.add('body', lathe([(-h / 2, 0.01), (-h / 2, r - 2)], mat, segs=22, axis='y', cap_start=False, cap_end=False))
    if band:
        W.add('body', lathe([(h * 0.1, r + 0.4), (h * 0.25, r + 0.4)], band, segs=22, axis='y'))
    if holes:
        for yy in (-h * 0.3, -h * 0.05, h * 0.2):
            for a in range(0, 360, 45):
                c = cylinder((0, yy, r - 1), (0, yy, r + 0.6), 3.2, 'bore', segs=8).rotate('y', a)
                W.add('body', c)
    _fuze(W, h / 2)


def flashbang():
    W = Model('flashbang')
    _canister(W, 110, 21, 'grenade_gray', holes=True)
    W.point('grip', 0, 0)
    W.props.update(grenade=True)
    return W


def smoke():
    W = Model('smokegrenade')
    _canister(W, 120, 29, 'grenade_smoke', band='grenade_band')
    W.point('grip', 0, 0)
    W.props.update(grenade=True)
    return W


def incendiary():
    W = Model('incgrenade')
    _canister(W, 118, 27, 'grenade_gray', band='grenade_red')
    W.point('grip', 0, 0)
    W.props.update(grenade=True)
    return W


def decoy():
    W = Model('decoy')
    _canister(W, 95, 24, 'grenade_decoy', band='metal')
    for yy in (-30, -15, 0, 15):
        W.add('body', lathe([(yy, 24.6), (yy + 4, 24.6)], 'metal', segs=20, axis='y'))
    W.point('grip', 0, 0)
    W.props.update(grenade=True)
    return W


def molotov():
    W = Model('molotov')
    prof = [(-80, 0), (-80, 30), (-76, 34), (10, 34), (30, 28), (45, 13), (80, 11), (86, 12), (90, 10), (90, 0.01)]
    W.add('body', lathe(prof, 'glass_green', segs=22, axis='y'))
    W.add('body', lathe([(-40, 34.4), (-5, 34.4)], 'label', segs=22, axis='y'))
    # burning rag stuffed in the neck
    W.add('body', loft([(86, 0, 0, 7, 7), (100, 2, 1, 9, 8), (118, -2, 3, 11, 6), (130, 3, 0, 6, 4)], 'cloth', segs=10, axis='y'))
    W.add('body', loft([(95, 6, 0, 3, 8), (110, 18, 0, 2, 9), (120, 26, 2, 1, 6)], 'cloth', segs=8, axis='y'))
    W.point('grip', 0, 0)
    W.point('rag', 0, 125, 0)
    W.props.update(grenade=True)
    return W


def c4():
    W = Model('c4')
    for i, zz in enumerate((-38, -12, 14)):
        W.add('body', box(-110, -20, zz, 110, 20, zz + 24, 'c4_clay', bevel=3))
    for xx in (-80, 80):
        W.add('body', box(xx - 6, -21.5, -40, xx + 6, 21.5, 40, 'tape', bevel=1))
    W.add('body', box(-60, 20, -30, 60, 30, 30, 'c4_panel', bevel=2))
    for r in range(3):
        for c_ in range(4):
            W.add('body', box(-40 + c_ * 16, 30, -20 + r * 12, -30 + c_ * 16, 33, -12 + r * 12, 'keys', bevel=0.8))
    W.add('body', box(-50, 30, 16, 50, 32, 28, 'lcd', bevel=0.5))
    W.add('body', cylinder((70, 26, -20), (95, 26, 25), 2.2, 'wire_red', segs=6))
    W.add('body', cylinder((70, 26, 20), (95, 24, -22), 2.2, 'wire_blue', segs=6))
    W.point('grip', 0, 0)
    W.point('led', 55, 32, -25)
    W.props.update(c4=True)
    return W


def kevlar():
    W = Model('kevlar')
    W.add('body', loft([(-150, 0, 0, 150, 60), (-140, 0, 0, 160, 70), (130, 0, 0, 160, 70), (150, 0, 0, 140, 55)], 'vest_od', segs=18, axis='y', squareness=0.6))
    for xx in (-80, 0, 80):
        W.add('body', box(xx - 30, -60, 65, xx + 30, 20, 95, 'vest_od', bevel=5))
    W.point('grip', 0, 0)
    return W


def helmet():
    W = Model('helmet')
    W.add('body', ellipsoid((0, 0, 0), 125, 105, 140, 'helmet_od', segs=22, rings=12))
    W.add('body', lathe([(-10, 132), (4, 138)], 'helmet_od', segs=22, axis='y'))
    W.point('grip', 0, 0)
    return W


def defuser():
    W = Model('defuser')
    W.add('body', box(-60, -15, -40, 60, 15, 40, 'polymer', bevel=4))
    W.add('body', cylinder((60, 0, -20), (110, 0, -20), 4, 'metal', segs=8))
    W.add('body', cylinder((60, 0, 20), (110, 0, 20), 4, 'metal', segs=8))
    W.add('body', box(-40, 15, -25, 40, 18, 25, 'lcd', bevel=1))
    W.point('grip', 0, 0)
    return W


PISTOLS = [glock18, usp_s, p2000, p250, fiveseven, cz75, tec9, beretta, deagle, revolver, zeus]
EQUIPMENT = [knife, karambit, he, flashbang, smoke, incendiary, decoy, molotov, c4, kevlar, helmet, defuser]


# ======================================================================== shell casings (axis = x)
def shell(name, L, r, neck=0.0, mat='brass', base_mat=None):
    W = Model(name)
    prof = [(0, r * 0.9), (0.6, r), (L * (0.7 if neck else 1.0), r)]
    if neck:
        prof += [(L * 0.78, r * neck), (L, r * neck)]
    W.add('body', lathe(prof, mat, segs=10, axis='x'))
    if base_mat:
        W.add('body', lathe([(-0.2, r * 1.02), (L * 0.25, r * 1.02)], base_mat, segs=10, axis='x'))
    return W


SHELLS = [lambda: shell('shell_rifle', 39, 5.6, neck=0.72), lambda: shell('shell_pistol', 19, 4.8),
          lambda: shell('shell_shotgun', 70, 10.5, mat='grenade_red', base_mat='brass')]
