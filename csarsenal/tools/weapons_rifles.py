"""Rifles, SMGs, snipers, heavy weapons."""
from gunparts import *


# ======================================================================== AK-47
def ak47():
    W = Model('ak47')
    yb = 44  # bore axis
    # receiver (stamped) + dust cover
    W.add('body', extrude([(-45, 6), (228, 6), (232, 12), (232, 50), (-45, 50)], -14, 14, 'metal_dark', bevel=1.5))
    W.add('body', loft([(-40, 50, 0, 13.5, 1), (-38, 52, 0, 13.5, 3), (200, 52, 0, 13.5, 3), (205, 50, 0, 13, 2)], 'metal_dark', axis='x', segs=14, squareness=0.4))
    W.add('body', extrude([(-42, 48), (205, 48), (208, 55), (195, 63), (-35, 63), (-42, 58)], -12.5, 12.5, 'metal_dark', bevel=3.0))
    stamped_ribs(W, 0, 180, 56, 25, n=3)
    # selector lever
    W.add('body', extrude([(40, 36), (150, 42), (150, 47), (40, 42)], 14, 16.2, 'metal', bevel=0.5))
    # rear sight block + leaf
    W.add('body', extrude([(228, 50), (270, 50), (270, 60), (240, 68), (228, 68)], -11, 11, 'metal_dark', bevel=1.2))
    W.add('body', extrude([(240, 66), (272, 66), (272, 70), (240, 70)], -8, 8, 'metal', bevel=0.6))
    # lower handguard (wood)
    W.add('body', extrude(smooth_poly([(232, 12), (400, 16), (404, 22), (404, 46), (232, 50)], 1), -17, 17, 'wood', bevel=4.0))
    for xx in (270, 300, 330, 360):
        W.add('body', box(xx, 24, -17.6, xx + 14, 38, 17.6, 'wood_dark', bevel=1.5))
    # gas tube + upper handguard
    W.add('body', cylinder((270, 62, 0), (440, 62, 0), 8.5, 'metal_dark', segs=14))
    W.add('body', loft([(272, 60, 0, 13, 13), (280, 61, 0, 14, 14), (398, 61, 0, 14, 14), (405, 60, 0, 12, 12)], 'wood', axis='x', segs=16))
    # gas block
    W.add('body', extrude([(430, 38), (470, 38), (470, 70), (440, 72), (430, 66)], -10, 10, 'metal_dark', bevel=1.5))
    # barrel
    barrel(W, 230, 590, yb, 9.0)
    W.add('body', cylinder((440, yb - 12, 0), (560, yb - 12, 0), 3.2, 'metal', segs=8))  # cleaning rod
    # front sight block
    W.add('body', extrude([(530, 34), (560, 34), (560, 56), (552, 80), (538, 80), (530, 56)], -8, 8, 'metal_dark', bevel=1.2))
    W.add('body', extrude([(541, 78), (549, 78), (548, 92), (542, 92)], -5.5, 5.5, 'metal_dark', bevel=0.6))
    # slant muzzle brake
    lathe_x(W, [(0, 10), (30, 10), (30, 7)], 590, yb, 'metal_dark', segs=16)
    W.add('body', box(612, yb + 3, -11, 622, yb + 11, 11, 'bore', bevel=0.3))
    bore(W, 620.5, yb, 5)
    # trigger guard, trigger
    trigger_guard(W, 18, 82, 6, depth=26, thick=4, mat='metal_dark', width=12)
    # pistol grip
    grip(W, 8, 8, angle=24, length=100, width=26, depth=32, mat='bakelite', finger=False)
    # stock (wood)
    stock = [(-45, 50), (-45, 12), (-80, 0), (-190, -30), (-395, -85), (-400, -80), (-402, 38), (-395, 46), (-250, 38), (-110, 44)]
    W.add('body', extrude(smooth_poly(stock, 1), -18, 18, 'wood', bevel=4.5))
    W.add('body', box(-408, -88, -19, -398, 42, 19, 'metal_dark', bevel=2.2))
    sling_swivel(W, -300, -52)
    # magazine (banana)
    curved_mag(W, 92, 8, 230, 42, 24, curve_deg=28, mat='mag_orange', ribs=True)
    # charging handle
    charging_handle_ak(W, 175, 42)
    W.point('grip', 0, -2)
    W.point('support', 300, 8)
    W.point('muzzle', 622, yb)
    W.point('eject', 150, 48, 14)
    W.point('mag', 110, 4)
    W.point('bolt', 171, 42, 28)
    W.point('stock', -400, 0)
    W.props.update(grip_angle=24)
    return W


# ======================================================================== AR-15 family (M4A4 / M4A1-S)
def ar15_base(W, quad=True, yb=38, barrel_end=520):
    # upper receiver
    W.add('body', extrude([(-32, 20), (200, 20), (200, 56), (-32, 56)], -15, 15, 'metal_dark', bevel=2.2))
    W.add('body', rail(-30, 198, 56, 11, 'metal_dark'))
    # forward assist + ejection port
    W.add('body', cylinder((20, 50, 12), (-2, 50, 20), 6.5, 'metal_dark', segs=12, bevel=1))
    W.add('body', box(40, 34, 14.8, 110, 50, 15.4, 'bore', bevel=0.3))
    W.add('body', box(60, 28, 15, 70, 34, 18, 'metal', bevel=0.6))  # brass deflector
    # lower receiver
    W.add('body', extrude([(-30, 20), (160, 20), (160, 8), (140, -4), (70, -4), (60, 4), (0, 4), (-30, 10)], -14, 14, 'metal_dark', bevel=1.8))
    # mag well
    W.add('body', extrude([(68, 4), (140, 4), (140, -30), (68, -30)], -15.5, 15.5, 'metal_dark', bevel=2.0))
    # grip + guard
    grip(W, 0, 6, angle=22, length=102, width=28, depth=34, mat='polymer', finger=True)
    trigger_guard(W, 12, 66, 5, depth=22, thick=4, mat='metal_dark', width=12)
    # selector
    W.add('body', box(18, 28, 14, 32, 34, 16.5, 'metal', bevel=0.5))
    # buffer tube + stock
    stock_ar(W, -32, 42)
    # handguard
    if quad:
        handguard_quad(W, 202, 380, yb, 22)
    else:
        handguard_round(W, 202, 380, yb, 21, mat='polymer', ribs=7)
    barrel(W, 380, barrel_end, yb, 8.5)
    return yb


def m4a4():
    W = Model('m4a4')
    yb = ar15_base(W, quad=True)
    # front sight tower on gas block
    W.add('body', extrude([(395, 30), (425, 30), (420, 62), (414, 80), (406, 80), (400, 62)], -8, 8, 'metal_dark', bevel=1.2))
    W.add('body', extrude([(408, 78), (412, 78), (412, 88), (408, 88)], -2.5, 2.5, 'metal_dark', bevel=0.4))
    flash_hider(W, 520, yb, 11, 50)
    # flip up rear sight
    W.add('body', extrude([(-20, 62), (5, 62), (5, 72), (-8, 82), (-20, 82)], -10, 10, 'metal_dark', bevel=1.0))
    # mag (slightly curved STANAG)
    curved_mag(W, 72, 0, 190, 62, 24, curve_deg=10, mat='metal_dark', ribs=True, floor_mat='polymer')
    # charging handle
    W.add('bolt', extrude([(-40, 50), (-28, 50), (-28, 58), (-40, 58)], -8, 8, 'metal_dark', bevel=0.8))
    W.add('bolt', extrude([(-46, 51), (-40, 51), (-40, 57), (-46, 57)], -16, 16, 'metal_dark', bevel=1.0))
    W.point('grip', 0, -2)
    W.point('support', 290, yb - 22)
    W.point('muzzle', 570, yb)
    W.point('eject', 80, 42, 15)
    W.point('mag', 100, 0)
    W.point('bolt', -43, 54, 0)
    W.props.update(grip_angle=22)
    return W


def m4a1s():
    W = Model('m4a1s')
    yb = ar15_base(W, quad=False, barrel_end=470)
    W.add('body', box(380, 26, -8, 400, 52, 8, 'metal_dark', bevel=1.2))  # gas block (low profile)
    W.add('body', extrude([(-20, 62), (5, 62), (5, 72), (-8, 82), (-20, 82)], -10, 10, 'metal_dark', bevel=1.0))
    W.add('body', extrude([(360, 62), (382, 62), (378, 80), (366, 80)], -8, 8, 'metal_dark', bevel=1.0))
    lathe_x(W, [(0, 8.5), (18, 8.5)], 452, yb, 'metal', segs=14)  # thread
    suppressor(W, 468, yb, 17, 205)
    curved_mag(W, 72, 0, 150, 62, 24, curve_deg=6, mat='metal_dark', ribs=True, floor_mat='polymer')
    W.add('bolt', extrude([(-40, 50), (-28, 50), (-28, 58), (-40, 58)], -8, 8, 'metal_dark', bevel=0.8))
    W.add('bolt', extrude([(-46, 51), (-40, 51), (-40, 57), (-46, 57)], -16, 16, 'metal_dark', bevel=1.0))
    W.point('grip', 0, -2)
    W.point('support', 290, yb - 22)
    W.point('muzzle', 470, yb)
    W.point('muzzle_sil', 673, yb)
    W.point('eject', 80, 42, 15)
    W.point('mag', 100, 0)
    W.point('bolt', -43, 54, 0)
    W.point('silencer', 468, yb)
    W.props.update(grip_angle=22)
    return W


# ======================================================================== Galil AR
def galil():
    W = Model('galil')
    yb = 42
    W.add('body', extrude([(-40, 6), (230, 6), (234, 14), (234, 52), (-40, 52)], -15, 15, 'metal_dark', bevel=1.8))
    W.add('body', loft([(-38, 52, 0, 14, 1), (-34, 53, 0, 14, 5), (215, 53, 0, 14, 5), (222, 52, 0, 13, 2)], 'metal_dark', axis='x', segs=14, squareness=0.45))
    # carry handle-ish rear sight
    W.add('body', extrude([(-30, 57), (0, 57), (0, 74), (-24, 74)], -9, 9, 'metal_dark', bevel=1.2))
    # handguard (polymer with vents)
    W.add('body', extrude(smooth_poly([(234, 10), (400, 14), (405, 22), (405, 66), (234, 62)], 1), -18, 18, 'polymer', bevel=4.0))
    for xx in range(250, 390, 22):
        W.add('body', box(xx, 44, -18.6, xx + 12, 58, 18.6, 'bore', bevel=0.8))
    barrel(W, 400, 560, yb, 9)
    W.add('body', extrude([(470, 34), (495, 34), (495, 58), (488, 84), (478, 84), (470, 58)], -8, 8, 'metal_dark', bevel=1.0))
    muzzle_brake(W, 560, yb, 11, 40)
    trigger_guard(W, 16, 80, 6, depth=26, thick=4, mat='metal_dark', width=12)
    grip(W, 6, 8, angle=22, length=100, width=27, depth=33, mat='polymer')
    # folding tube stock (skeleton)
    W.add('body', extrude(strip([(-40, 40), (-250, 34), (-360, 30)], 16), -12, 12, 'metal_dark', bevel=3))
    W.add('body', extrude(strip([(-60, 10), (-250, -10), (-360, -40)], 14), -12, 12, 'metal_dark', bevel=3))
    W.add('body', extrude([(-372, -60), (-352, -60), (-352, 46), (-372, 46)], -17, 17, 'rubber', bevel=3))
    curved_mag(W, 90, 8, 235, 44, 25, curve_deg=24, mat='polymer', ribs=True)
    charging_handle_ak(W, 175, 42)
    W.point('grip', 0, -2)
    W.point('support', 300, 12)
    W.point('muzzle', 600, yb)
    W.point('eject', 150, 48, 15)
    W.point('mag', 110, 4)
    W.point('bolt', 171, 42, 28)
    W.props.update(grip_angle=22)
    return W


# ======================================================================== FAMAS (bullpup)
def famas():
    W = Model('famas')
    yb = 40
    # main body: grip is in front of the mag (bullpup) -> origin at grip, receiver extends back
    body = [(-300, -50), (-300, 60), (-270, 70), (-60, 70), (140, 60), (210, 50), (240, 40), (240, 12), (140, 6), (40, 6), (20, 0), (-150, 0), (-170, -40), (-250, -52)]
    W.add('body', extrude(smooth_poly(body, 1), -20, 20, 'polymer', bevel=5.0))
    # long carry handle
    W.add('body', extrude(strip([(-230, 70), (-210, 110), (-60, 118), (80, 112), (110, 72)], 13), -9, 9, 'polymer', bevel=3))
    W.add('body', extrude([(-240, 64), (-200, 64), (-200, 106), (-240, 70)], -10, 10, 'polymer', bevel=2))
    barrel(W, 240, 420, yb, 8.5)
    W.add('body', cylinder((250, 30, 0), (330, 30, 0), 6, 'metal_dark', segs=10))  # bipod folded
    lathe_x(W, [(0, 11), (35, 11), (35, 8)], 420, yb, 'metal_dark', segs=16)
    bore(W, 455, yb, 5)
    trigger_guard(W, -40, 80, 6, depth=30, thick=5, mat='polymer', width=16)
    grip(W, 0, 6, angle=18, length=100, width=28, depth=34, mat='polymer')
    W.add('body', box(-300, -54, -21, -292, 62, 21, 'rubber', bevel=2.5))
    straight_mag(W, -130, 4, 150, 58, 23, rake=6, mat='metal_dark', floor_mat='polymer')
    W.add('bolt', extrude([(-80, 108), (-40, 108), (-40, 122), (-80, 122)], -6, 6, 'metal_dark', bevel=1))
    W.point('grip', 0, -2)
    W.point('support', 150, 8)
    W.point('muzzle', 455, yb)
    W.point('eject', -120, 40, 20)
    W.point('mag', -100, 0)
    W.point('bolt', -60, 115, 0)
    W.props.update(grip_angle=18)
    return W


# ======================================================================== AUG (bullpup, scope)
def aug():
    W = Model('aug')
    yb = 42
    body = [(-330, -40), (-330, 50), (-310, 64), (-80, 66), (90, 60), (90, 18), (60, 6), (-40, 6), (-60, 0), (-150, 0), (-180, -35), (-260, -45)]
    W.add('body', extrude(smooth_poly(body, 1), -22, 22, 'polymer_od', bevel=6.0))
    # receiver tube (aluminium)
    W.add('body', cylinder((-160, yb + 8, 0), (130, yb + 8, 0), 22, 'metal_dark', segs=18, bevel=2))
    # large trigger guard enclosing hand
    W.add('body', extrude(strip([(60, 6), (70, -40), (40, -75), (-10, -85), (-40, -60), (-45, 0)], 8), -10, 10, 'polymer_od', bevel=2.5))
    # vertical fore grip
    W.add('body', extrude(smooth_poly([(145, 30), (175, 30), (170, -60), (145, -62), (140, -30)], 1), -13, 13, 'polymer_od', bevel=4))
    barrel(W, 130, 400, yb, 9)
    flash_hider(W, 400, yb, 11, 45)
    grip(W, 0, 6, angle=12, length=90, width=28, depth=34, mat='polymer_od')
    W.add('body', box(-334, -44, -23, -326, 52, 23, 'rubber', bevel=2.5))
    straight_mag(W, -125, 4, 145, 58, 24, rake=8, mat='polymer_clear', floor_mat='polymer')
    # integrated scope
    scope(W, -130, 70, 110, r_tube=13, r_obj=20, r_eye=18, mat='metal_dark', turret=False)
    W.add('body', extrude([(-120, 66), (60, 66), (50, 96), (-110, 96)], -12, 12, 'metal_dark', bevel=3))
    W.add('bolt', extrude([(-20, 72), (15, 72), (15, 80), (-20, 80)], 20, 32, 'metal_dark', bevel=1))
    W.point('grip', 0, -2)
    W.point('support', 158, -10)
    W.point('muzzle', 445, yb)
    W.point('eject', -120, 45, 22)
    W.point('mag', -95, 0)
    W.point('bolt', 0, 76, 30)
    W.props.update(grip_angle=12, support_kind='vertical')
    return W


# ======================================================================== SG 553
def sg553():
    W = Model('sg553')
    yb = 40
    W.add('body', extrude([(-40, 6), (225, 6), (230, 12), (230, 56), (-40, 56)], -15, 15, 'metal_dark', bevel=2))
    W.add('body', rail(-30, 220, 56, 11, 'metal_dark'))
    # handguard with tan polymer
    W.add('body', extrude(smooth_poly([(230, 8), (390, 12), (395, 20), (395, 62), (230, 62)], 1), -19, 19, 'polymer_tan', bevel=4))
    barrel(W, 395, 530, yb, 9)
    flash_hider(W, 530, yb, 11, 40)
    trigger_guard(W, 14, 78, 6, depth=26, thick=4, mat='polymer', width=12)
    grip(W, 4, 8, angle=20, length=100, width=28, depth=34, mat='polymer')
    # side-folding stock
    W.add('body', extrude(smooth_poly([(-40, 52), (-40, 12), (-120, -5), (-300, -50), (-330, -50), (-330, 50), (-300, 52)], 1), -16, 16, 'polymer_tan', bevel=4))
    W.add('body', box(-338, -54, -17, -328, 54, 17, 'rubber', bevel=2.5))
    # scope
    scope(W, -60, 150, 95, r_tube=13, r_obj=21, r_eye=18, mounts=[-10, 100], mount_base=62)
    straight_mag(W, 80, 6, 175, 60, 24, rake=10, mat='polymer_clear', floor_mat='polymer')
    charging_handle_ak(W, 180, 42)
    W.point('grip', 0, -2)
    W.point('support', 300, 10)
    W.point('muzzle', 570, yb)
    W.point('eject', 150, 46, 15)
    W.point('mag', 110, 4)
    W.point('bolt', 176, 42, 28)
    W.props.update(grip_angle=20)
    return W


# ======================================================================== SMGs
def mac10():
    W = Model('mac10')
    yb = 30
    W.add('body', extrude([(-60, 0), (190, 0), (190, 58), (-60, 58)], -17, 17, 'metal_dark', bevel=2.5))
    W.add('bolt', extrude([(20, 58), (60, 58), (60, 66), (20, 66)], -4, 4, 'metal_dark', bevel=1))
    W.add('body', box(-50, 58, -4, 170, 62, 4, 'metal_dark', bevel=0.5))
    barrel(W, 190, 235, yb, 8)
    lathe_x(W, [(0, 9.5), (10, 9.5)], 225, yb, 'metal', segs=12)
    bore(W, 235, yb, 4.5)
    # mag through grip
    W.add('body', extrude([(-14, 0), (26, 0), (18, -110), (-26, -110)], -15, 15, 'polymer', bevel=3))
    straight_mag(W, -18, -108, 70, 34, 22, rake=-8, mat='metal_dark')
    W.parts['mag'].translate(0, 0, 0)
    trigger_guard(W, 30, 80, 0, depth=24, thick=4, mat='metal_dark', width=10)
    # front strap
    W.add('body', extrude(strip([(185, 30), (195, -20), (180, -45)], 6), -7, 7, 'polymer', bevel=1.5))
    W.point('grip', 0, -2)
    W.point('support', 190, -38)
    W.point('muzzle', 235, yb)
    W.point('eject', 90, 40, 17)
    W.point('mag', 0, -110)
    W.point('bolt', 40, 62, 0)
    W.props.update(grip_angle=-6, support_kind='strap')
    return W


def mp9():
    W = Model('mp9')
    yb = 34
    W.add('body', extrude(smooth_poly([(-70, 6), (200, 6), (205, 20), (205, 58), (-70, 58)], 1), -16, 16, 'polymer', bevel=3.5))
    W.add('body', rail(-60, 190, 58, 10, 'metal_dark'))
    barrel(W, 205, 250, yb, 8)
    bore(W, 250, yb, 4)
    # mag in grip
    W.add('body', extrude(smooth_poly([(-10, 6), (30, 6), (20, -100), (-24, -100)], 1), -14, 14, 'polymer', bevel=3))
    straight_mag(W, -18, -96, 80, 34, 22, rake=-6, mat='polymer')
    trigger_guard(W, 34, 110, 6, depth=30, thick=5, mat='polymer', width=12)
    # front vertical grip
    W.add('body', extrude(smooth_poly([(130, 6), (160, 6), (152, -52), (132, -55)], 1), -12, 12, 'polymer', bevel=3.5))
    # folding stock (folded on the right)
    W.add('body', extrude(strip([(-70, 40), (120, 40)], 12), 17, 23, 'polymer', bevel=2))
    W.add('bolt', extrude([(-80, 44), (-70, 44), (-70, 54), (-80, 54)], -12, 12, 'metal_dark', bevel=1))
    W.point('grip', 0, -2)
    W.point('support', 145, -25)
    W.point('muzzle', 250, yb)
    W.point('eject', 90, 44, 16)
    W.point('mag', 5, -100)
    W.point('bolt', -75, 50, 0)
    W.props.update(grip_angle=-4, support_kind='vertical')
    return W


def mp7():
    W = Model('mp7')
    yb = 36
    W.add('body', extrude(smooth_poly([(-100, 6), (180, 6), (190, 16), (190, 60), (-100, 60), (-110, 40)], 1), -17, 17, 'polymer', bevel=4))
    W.add('body', rail(-90, 175, 60, 10, 'metal_dark'))
    barrel(W, 190, 260, yb, 7.5)
    flash_hider(W, 250, yb, 9, 30)
    W.add('body', extrude(smooth_poly([(-10, 6), (28, 6), (20, -95), (-22, -95)], 1), -14, 14, 'polymer', bevel=3))
    straight_mag(W, -16, -92, 110, 32, 20, rake=-5, mat='polymer')
    trigger_guard(W, 30, 96, 6, depth=28, thick=5, mat='polymer', width=12)
    W.add('body', extrude(smooth_poly([(120, 6), (150, 6), (145, -60), (122, -62)], 1), -12, 12, 'polymer', bevel=3.5))
    # extended stock
    W.add('body', extrude(strip([(-110, 44), (-270, 44)], 10), -17, -11, 'metal_dark', bevel=1.5))
    W.add('body', extrude(strip([(-110, 44), (-270, 44)], 10), 11, 17, 'metal_dark', bevel=1.5))
    W.add('body', extrude(smooth_poly([(-270, 60), (-255, 60), (-255, -10), (-275, -10)], 1), -17, 17, 'polymer', bevel=3))
    W.add('bolt', extrude([(-100, 56), (-80, 56), (-80, 64), (-100, 64)], -14, 14, 'metal_dark', bevel=1))
    W.point('grip', 0, -2)
    W.point('support', 135, -30)
    W.point('muzzle', 280, yb)
    W.point('eject', 80, 44, 17)
    W.point('mag', 5, -95)
    W.point('bolt', -90, 60, 0)
    W.props.update(grip_angle=-4, support_kind='vertical')
    return W


def mp5sd():
    W = Model('mp5sd')
    yb = 38
    W.add('body', loft([(-40, 40, 0, 17, 22), (-35, 40, 0, 18, 23), (180, 40, 0, 18, 23), (185, 40, 0, 16, 20)], 'metal_dark', axis='x', segs=18, squareness=0.5))
    W.add('body', extrude([(-40, 0), (120, 0), (120, 22), (-40, 22)], -14, 14, 'polymer', bevel=2))
    W.add('body', box(60, -30, -14, 110, 5, 14, 'metal_dark', bevel=2))
    straight_mag(W, 68, -28, 170, 38, 22, rake=14, mat='metal_dark')
    grip(W, 0, 4, angle=18, length=95, width=28, depth=33, mat='polymer')
    trigger_guard(W, 10, 60, 2, depth=22, thick=4, width=12)
    # integral suppressor
    lathe_x(W, [(0, 20), (4, 22), (250, 22), (254, 19)], 180, yb, 'metal_dark', segs=22)
    bore(W, 434, yb, 5)
    # handguard over the suppressor start
    W.add('body', extrude(smooth_poly([(186, 10), (300, 12), (300, 66), (186, 66)], 1), -24, 24, 'polymer', bevel=4))
    # cocking tube + handle
    W.add('body', cylinder((180, 66, 0), (300, 66, 0), 7, 'metal_dark', segs=12))
    W.add('bolt', cylinder((240, 66, 6), (228, 66, 30), 4, 'metal', segs=10, bevel=0.6))
    # drum rear sight + fixed stock
    W.add('body', cylinder((-20, 68, -10), (-20, 68, 10), 10, 'metal_dark', segs=16))
    W.add('body', extrude(smooth_poly([(-40, 58), (-40, 8), (-120, -15), (-300, -45), (-315, -45), (-315, 52), (-300, 56)], 1), -17, 17, 'polymer', bevel=4))
    W.point('grip', 0, -2)
    W.point('support', 245, 12)
    W.point('muzzle', 434, yb)
    W.point('eject', 90, 48, 18)
    W.point('mag', 88, -28)
    W.point('bolt', 234, 66, 25)
    W.props.update(grip_angle=18)
    return W


def ump45():
    W = Model('ump45')
    yb = 38
    body = [(-40, 0), (230, 0), (235, 12), (235, 62), (-40, 62)]
    W.add('body', extrude(smooth_poly(body, 1), -19, 19, 'polymer', bevel=4))
    W.add('body', rail(-30, 220, 62, 10, 'metal_dark'))
    W.add('body', extrude(smooth_poly([(-40, 10), (-40, -30), (-10, -90), (30, -95), (30, 0)], 1), -15, 15, 'polymer', bevel=3.5))
    # thumbhole-ish grip region by darker insert
    grip(W, 0, 2, angle=16, length=86, width=26, depth=30, mat='polymer')
    trigger_guard(W, 14, 64, 0, depth=20, thick=4, width=12)
    W.add('body', box(80, -20, -16, 130, 2, 16, 'polymer', bevel=2))
    straight_mag(W, 86, -18, 170, 42, 24, rake=6, mat='polymer')
    barrel(W, 235, 290, yb, 9)
    bore(W, 290, yb, 5)
    # folding stock skeleton
    W.add('body', extrude(strip([(-40, 50), (-280, 40)], 14), -14, 14, 'polymer', bevel=3))
    W.add('body', extrude(strip([(-40, 5), (-280, -30)], 14), -14, 14, 'polymer', bevel=3))
    W.add('body', extrude([(-295, -55), (-275, -55), (-275, 55), (-295, 55)], -17, 17, 'rubber', bevel=3))
    W.add('bolt', cylinder((200, 58, 10), (190, 58, 30), 4.5, 'metal_dark', segs=10, bevel=0.6))
    W.point('grip', 0, -2)
    W.point('support', 180, 4)
    W.point('muzzle', 290, yb)
    W.point('eject', 100, 44, 19)
    W.point('mag', 107, -18)
    W.point('bolt', 195, 58, 25)
    W.props.update(grip_angle=16)
    return W


def p90():
    W = Model('p90')
    yb = 42
    P = 'polymer'
    # upper body along the whole length
    W.add('body', extrude(smooth_poly([(-240, -15), (-245, 40), (-228, 62), (170, 62), (200, 50), (212, 22), (190, -2), (150, -12), (-240, -15)], 1), -25, 25, P, bevel=7))
    # stock lower part (behind the thumbhole)
    W.add('body', extrude(smooth_poly([(-240, -12), (-40, -12), (-40, -40), (-70, -62), (-200, -58), (-242, -40)], 1), -24, 24, P, bevel=6))
    # frame bar enclosing the thumbhole
    W.add('body', extrude(strip([(-60, -58), (0, -80), (50, -82)], 16), -20, 20, P, bevel=5))
    # pistol grip + front finger hump
    W.add('body', extrude(smooth_poly([(35, -12), (140, -12), (148, -30), (128, -52), (112, -80), (80, -86), (45, -84), (40, -40)], 1), -21, 21, P, bevel=6))
    W.add('body', extrude([(95, -20), (105, -20), (105, -40), (95, -40)], -3, 3, 'metal_dark', bevel=0.5))  # trigger
    # translucent magazine on top
    W.add('mag', extrude(smooth_poly([(-170, 62), (140, 62), (150, 74), (140, 86), (-160, 86), (-170, 74)], 1), -24, 24, 'polymer_clear', bevel=3))
    red_dot(W, 60, 88)
    barrel(W, 205, 250, yb, 8)
    lathe_x(W, [(0, 10), (18, 10)], 232, yb, 'metal_dark', segs=12)
    bore(W, 250, yb, 4)
    W.add('bolt', box(-120, 50, 24, -95, 58, 30, 'metal_dark', bevel=1))
    W.point('grip', 60, -22)
    W.point('support', 150, -28)
    W.point('muzzle', 250, yb)
    W.point('eject', -60, -30, 0)
    W.point('mag', -20, 74)
    W.point('bolt', -108, 54, 27)
    W.props.update(grip_angle=6, support_kind='under', mag_top=True)
    return W


def bizon():
    W = Model('bizon')
    yb = 42
    W.add('body', extrude([(-40, 12), (230, 12), (230, 60), (-40, 60)], -15, 15, 'metal_dark', bevel=2))
    W.add('body', loft([(-38, 60, 0, 14, 1), (-34, 61, 0, 14, 5), (215, 61, 0, 14, 5), (222, 60, 0, 13, 2)], 'metal_dark', axis='x', segs=14, squareness=0.45))
    # helical magazine under the barrel
    W.add('mag', loft([(40, 0, 0, 24, 24), (46, 0, 0, 27, 27), (330, 0, 0, 27, 27), (345, 0, 0, 22, 22)], 'polymer', axis='x', segs=20))
    for xx in range(60, 330, 24):
        W.add('mag', lathe([(xx, 27.3), (xx + 6, 27.3)], 'polymer', segs=20, axis='x', origin=(0, 0, 0)))
    barrel(W, 230, 420, yb, 8)
    W.add('body', extrude([(360, 36), (385, 36), (380, 70), (366, 70)], -7, 7, 'metal_dark', bevel=1))
    lathe_x(W, [(0, 10), (30, 10)], 410, yb, 'metal_dark', segs=12)
    bore(W, 440, yb, 4)
    grip(W, 6, 12, angle=22, length=95, width=26, depth=32, mat='polymer')
    trigger_guard(W, 18, 78, 12, depth=24, thick=4, mat='metal_dark', width=12)
    W.add('body', extrude(strip([(-40, 50), (-250, 30)], 14), -12, 12, 'metal_dark', bevel=3))
    W.add('body', extrude(strip([(-40, 20), (-250, -30)], 12), -12, 12, 'metal_dark', bevel=3))
    W.add('body', extrude([(-265, -50), (-245, -50), (-245, 44), (-265, 44)], -16, 16, 'rubber', bevel=3))
    charging_handle_ak(W, 175, 48)
    W.point('grip', 0, -2)
    W.point('support', 200, -20)
    W.point('muzzle', 440, yb)
    W.point('eject', 150, 50, 15)
    W.point('mag', 190, 0)
    W.point('bolt', 171, 48, 28)
    W.props.update(grip_angle=22)
    return W


# ======================================================================== Snipers
def awp():
    W = Model('awp')
    yb = 44
    G = 'polymer_od'
    # chassis: butt, cheek, grip (thumbhole assembled from strips)
    W.add('body', extrude(smooth_poly([(-470, -110), (-470, 48), (-440, 58), (-330, 58), (-300, 44), (-300, 20), (-440, 18), (-440, -90)], 1), -20, 20, G, bevel=5))
    W.add('body', extrude(smooth_poly([(-300, 44), (-120, 48), (-120, 18), (-300, 18)], 1), -20, 20, G, bevel=5))
    W.add('body', extrude(strip([(-445, -92), (-300, -94), (-160, -96), (-50, -96)], 22), -18, 18, G, bevel=5))
    grip(W, -20, 12, angle=16, length=100, width=30, depth=36, mat=G)
    W.add('body', extrude(smooth_poly([(-120, 48), (330, 42), (345, 34), (345, 12), (60, 8), (40, 18), (-120, 10)], 1), -24, 24, G, bevel=5))
    W.add('body', box(-478, -114, -21, -468, 56, 21, 'rubber', bevel=3))
    # action
    W.add('body', cylinder((-130, yb + 12, 0), (120, yb + 12, 0), 19, 'metal_dark', segs=20, bevel=2))
    W.add('body', rail(-120, 110, yb + 30, 10, 'metal_dark'))
    # barrel (fluted look)
    barrel(W, 120, 720, yb + 12, 12.5, r_end=11)
    for a in range(0, 360, 60):
        W.add('body', box(360, yb + 12 - 1.5, 11.6, 700, yb + 12 + 1.5, 13.2, 'bore', bevel=0.2).rotate('x', a, origin=(0, yb + 12, 0)))
    muzzle_brake(W, 720, yb + 12, 15, 55, slots=3)
    trigger_guard(W, -10, 50, 12, depth=24, thick=5, mat='metal_dark', width=14)
    # mag
    straight_mag(W, 10, 12, 45, 70, 24, rake=0, mat='metal_dark')
    # scope
    scope(W, -170, 230, yb + 78, r_tube=15, r_obj=29, r_eye=22, mounts=[-60, 90], mount_base=yb + 32)
    # bolt handle
    W.add('bolt', cylinder((-80, yb + 12, 0), (-80, yb + 4, 44), 4.5, 'metal', segs=10))
    W.add('bolt', ellipsoid((-80, yb + 2, 48), 9, 9, 9, 'metal_dark', segs=12, rings=8))
    W.add('bolt', cylinder((-140, yb + 12, 0), (-128, yb + 12, 0), 14, 'metal', segs=16, bevel=1))
    W.point('grip', -20, 4)
    W.point('support', 150, 0)
    W.point('muzzle', 775, yb + 12)
    W.point('eject', 0, yb + 20, 19)
    W.point('mag', 40, 12)
    W.point('bolt', -80, yb + 2, 48)
    W.point('scope', -170, yb + 78)
    W.props.update(grip_angle=16)
    return W


def ssg08():
    W = Model('ssg08')
    yb = 44
    G = 'polymer'
    W.add('body', extrude(smooth_poly([(-420, -95), (-420, 45), (-395, 52), (-280, 50), (-120, 50), (-120, 18), (-260, 10), (-395, -80)], 1), -19, 19, G, bevel=5))
    W.add('body', extrude(smooth_poly([(-120, 50), (330, 44), (340, 36), (340, 18), (60, 10), (40, 18), (-120, 12)], 1), -21, 21, G, bevel=5))
    grip(W, -20, 12, angle=20, length=95, width=28, depth=34, mat=G)
    W.add('body', box(-428, -98, -20, -418, 50, 20, 'rubber', bevel=3))
    W.add('body', cylinder((-130, yb + 10, 0), (110, yb + 10, 0), 16, 'metal_dark', segs=18, bevel=2))
    barrel(W, 110, 680, yb + 10, 10, r_end=9)
    muzzle_brake(W, 680, yb + 10, 12, 40)
    trigger_guard(W, -8, 48, 12, depth=24, thick=5, mat='metal_dark', width=14)
    straight_mag(W, 10, 12, 30, 60, 22, rake=0, mat='polymer')
    scope(W, -150, 200, yb + 66, r_tube=13, r_obj=24, r_eye=19, mounts=[-50, 80], mount_base=yb + 24)
    W.add('bolt', cylinder((-80, yb + 10, 0), (-80, yb + 2, 40), 4, 'metal', segs=10))
    W.add('bolt', ellipsoid((-80, yb, 44), 8, 8, 8, 'metal_dark', segs=12, rings=8))
    W.add('bolt', cylinder((-140, yb + 10, 0), (-128, yb + 10, 0), 12, 'metal', segs=16, bevel=1))
    W.point('grip', -20, 4)
    W.point('support', 150, 2)
    W.point('muzzle', 720, yb + 10)
    W.point('eject', 0, yb + 16, 16)
    W.point('mag', 40, 12)
    W.point('bolt', -80, yb, 44)
    W.props.update(grip_angle=20)
    return W


def g3sg1():
    W = Model('g3sg1')
    yb = 42
    W.add('body', loft([(-60, yb + 6, 0, 17, 22), (-55, yb + 6, 0, 18, 23), (230, yb + 6, 0, 18, 23), (236, yb + 6, 0, 16, 20)], 'metal_dark', axis='x', segs=18, squareness=0.5))
    W.add('body', extrude([(-60, 0), (140, 0), (140, 24), (-60, 24)], -14, 14, 'polymer', bevel=2))
    W.add('body', box(70, -30, -14, 125, 5, 14, 'metal_dark', bevel=2))
    straight_mag(W, 74, -28, 150, 44, 24, rake=4, mat='metal_dark')
    grip(W, 0, 4, angle=18, length=98, width=28, depth=34, mat='polymer')
    trigger_guard(W, 10, 62, 2, depth=22, thick=4, width=12)
    W.add('body', extrude(smooth_poly([(236, 12), (440, 16), (445, 24), (445, 70), (236, 70)], 1), -21, 21, 'polymer', bevel=5))
    for xx in range(260, 430, 30):
        W.add('body', box(xx, 48, -21.6, xx + 16, 62, 21.6, 'bore', bevel=0.8))
    barrel(W, 445, 700, yb, 10)
    flash_hider(W, 700, yb, 12, 45)
    W.add('body', cylinder((236, 70, 0), (300, 70, 0), 7, 'metal_dark', segs=12))
    W.add('bolt', cylinder((260, 70, 6), (250, 70, 30), 4, 'metal', segs=10, bevel=0.6))
    W.add('body', extrude(smooth_poly([(-60, 58), (-60, 8), (-140, -20), (-360, -58), (-378, -58), (-378, 56), (-360, 62), (-240, 70), (-120, 64)], 1), -18, 18, 'polymer', bevel=5))
    W.add('body', box(-386, -62, -19, -376, 60, 19, 'rubber', bevel=3))
    scope(W, -90, 190, yb + 60, r_tube=14, r_obj=25, r_eye=20, mounts=[-10, 110], mount_base=yb + 26)
    W.point('grip', 0, -2)
    W.point('support', 330, 16)
    W.point('muzzle', 745, yb)
    W.point('eject', 120, 50, 18)
    W.point('mag', 96, -28)
    W.point('bolt', 255, 70, 25)
    W.props.update(grip_angle=18)
    return W


def scar20():
    W = Model('scar20')
    yb = 40
    W.add('body', extrude([(-40, 20), (330, 20), (330, 62), (-40, 62)], -17, 17, 'polymer_tan', bevel=3))
    W.add('body', rail(-35, 325, 62, 11, 'metal_dark'))
    W.add('body', extrude([(-40, 0), (170, 0), (170, 20), (-40, 20)], -15, 15, 'polymer_tan', bevel=2))
    W.add('body', extrude([(70, 0), (150, 0), (150, -30), (70, -30)], -16, 16, 'polymer_tan', bevel=2))
    straight_mag(W, 76, -28, 130, 66, 25, rake=3, mat='metal_dark')
    grip(W, 0, 4, angle=20, length=100, width=28, depth=34, mat='polymer')
    trigger_guard(W, 12, 66, 2, depth=22, thick=4, width=12)
    barrel(W, 330, 640, yb, 10)
    muzzle_brake(W, 640, yb, 13, 48)
    W.add('body', extrude(smooth_poly([(-40, 56), (-40, 10), (-150, -10), (-330, -55), (-350, -55), (-350, 52), (-330, 60)], 1), -18, 18, 'polymer_tan', bevel=5))
    W.add('body', box(-358, -60, -19, -348, 58, 19, 'rubber', bevel=3))
    scope(W, -60, 210, yb + 68, r_tube=14, r_obj=26, r_eye=20, mounts=[20, 150], mount_base=yb + 30)
    W.add('bolt', cylinder((240, 50, -16), (240, 50, -34), 4.5, 'metal_dark', segs=10, bevel=0.6))
    W.point('grip', 0, -2)
    W.point('support', 260, 16)
    W.point('muzzle', 690, yb)
    W.point('eject', 110, 44, 17)
    W.point('mag', 108, -28)
    W.point('bolt', 240, 50, -30)
    W.props.update(grip_angle=20)
    return W


# ======================================================================== Shotguns
def nova():
    W = Model('nova')
    yb = 44
    W.add('body', extrude(smooth_poly([(-40, 10), (180, 10), (180, 62), (-40, 62)], 1), -18, 18, 'polymer', bevel=4))
    barrel(W, 180, 640, yb + 4, 12)
    shell_tube(W, 180, 560, 18, 11)
    # pump
    W.add('pump', loft([(260, 18, 0, 18, 20), (266, 18, 0, 20, 22), (400, 18, 0, 20, 22), (406, 18, 0, 18, 20)], 'polymer', axis='x', segs=18))
    for xx in range(280, 395, 14):
        W.add('pump', lathe([(xx, 22.3), (xx + 5, 22.3)], 'polymer', segs=18, axis='x', origin=(0, 18, 0)))
    W.add('body', ellipsoid((630, yb + 18, 0), 3, 5, 3, 'metal', segs=8, rings=6))
    bore(W, 640, yb + 4, 9)
    grip(W, 0, 12, angle=22, length=95, width=27, depth=33, mat='polymer')
    trigger_guard(W, 10, 70, 10, depth=24, thick=4, width=12)
    W.add('body', extrude(smooth_poly([(-40, 58), (-40, 14), (-120, -10), (-330, -60), (-350, -60), (-350, 50), (-330, 58)], 1), -18, 18, 'polymer', bevel=5))
    W.add('body', box(-358, -64, -19, -348, 56, 19, 'rubber', bevel=3))
    W.point('grip', 0, -2)
    W.point('support', 330, 0)
    W.point('muzzle', 640, yb + 4)
    W.point('eject', 90, 40, 18)
    W.point('mag', 150, 5)
    W.point('pump', 330, 18)
    W.props.update(grip_angle=22, shell_reload=True)
    return W


def xm1014():
    W = Model('xm1014')
    yb = 44
    W.add('body', extrude(smooth_poly([(-40, 10), (200, 10), (200, 64), (-40, 64)], 1), -18, 18, 'metal_dark', bevel=3))
    W.add('body', rail(-35, 195, 64, 11, 'metal_dark'))
    barrel(W, 200, 600, yb + 6, 11.5)
    shell_tube(W, 200, 540, 20, 11)
    W.add('body', loft([(210, 22, 0, 20, 22), (216, 22, 0, 22, 24), (380, 22, 0, 22, 24), (386, 22, 0, 20, 22)], 'polymer', axis='x', segs=18, squareness=0.4))
    W.add('body', extrude([(560, 40), (580, 40), (576, 72), (566, 72)], -6, 6, 'metal_dark', bevel=1))
    bore(W, 600, yb + 6, 9)
    grip(W, 0, 12, angle=22, length=100, width=28, depth=34, mat='polymer')
    trigger_guard(W, 10, 70, 10, depth=24, thick=4, width=12)
    stock_ar(W, -40, 46)
    W.add('bolt', box(80, 40, 18, 100, 50, 30, 'metal', bevel=1))
    W.point('grip', 0, -2)
    W.point('support', 300, 0)
    W.point('muzzle', 600, yb + 6)
    W.point('eject', 90, 44, 18)
    W.point('mag', 150, 5)
    W.point('bolt', 90, 45, 28)
    W.props.update(grip_angle=22, shell_reload=True)
    return W


def sawedoff():
    W = Model('sawedoff')
    yb = 42
    W.add('body', extrude(smooth_poly([(-30, 10), (170, 10), (170, 60), (-30, 60)], 1), -17, 17, 'metal_dark', bevel=3))
    barrel(W, 170, 420, yb, 13)
    shell_tube(W, 170, 380, 16, 10)
    W.add('pump', loft([(220, 16, 0, 17, 19), (226, 16, 0, 19, 21), (340, 16, 0, 19, 21), (346, 16, 0, 17, 19)], 'wood', axis='x', segs=18))
    bore(W, 420, yb, 10)
    # pistol grip stock (wood, sawn)
    W.add('body', extrude(smooth_poly([(-30, 58), (-30, 12), (-60, -30), (-100, -95), (-140, -100), (-150, -80), (-110, -20), (-100, 30), (-80, 58)], 1), -17, 17, 'wood', bevel=5))
    trigger_guard(W, 5, 65, 10, depth=24, thick=4, width=12)
    W.point('grip', -40, -10)
    W.point('support', 285, -2)
    W.point('muzzle', 420, yb)
    W.point('eject', 70, 40, 17)
    W.point('mag', 120, 5)
    W.point('pump', 285, 16)
    W.props.update(grip_angle=38, shell_reload=True)
    return W


def mag7():
    W = Model('mag7')
    yb = 46
    W.add('body', extrude(smooth_poly([(-60, 10), (260, 10), (260, 68), (-60, 68)], 1), -19, 19, 'metal_dark', bevel=3))
    W.add('body', rail(-50, 250, 68, 11, 'metal_dark'))
    barrel(W, 260, 440, yb, 13)
    shell_tube(W, 260, 400, 18, 11)
    bore(W, 440, yb, 10)
    # grip with mag inside
    W.add('body', extrude(smooth_poly([(-10, 10), (40, 10), (30, -95), (-26, -95)], 1), -17, 17, 'polymer', bevel=3.5))
    straight_mag(W, -22, -92, 55, 48, 30, rake=-6, mat='metal_dark')
    trigger_guard(W, 44, 110, 10, depth=28, thick=5, width=12)
    W.add('pump', extrude(smooth_poly([(300, 30), (380, 30), (380, 0), (365, -60), (330, -62), (320, 0)], 1), -14, 14, 'polymer', bevel=4))
    W.add('body', extrude(strip([(-60, 58), (-230, 50)], 12), -14, 14, 'metal_dark', bevel=2))
    W.add('body', extrude([(-245, -40), (-228, -40), (-228, 62), (-245, 62)], -17, 17, 'rubber', bevel=3))
    W.point('grip', 0, -2)
    W.point('support', 350, -30)
    W.point('muzzle', 440, yb)
    W.point('eject', 120, 46, 19)
    W.point('mag', 0, -95)
    W.point('pump', 345, 0)
    W.props.update(grip_angle=-4, support_kind='vertical')
    return W


# ======================================================================== Machine guns
def m249():
    W = Model('m249')
    yb = 46
    W.add('body', extrude([(-60, 10), (240, 10), (240, 72), (-60, 72)], -20, 20, 'metal_dark', bevel=3))
    W.add('body', extrude([(-60, 72), (220, 72), (215, 84), (-50, 84)], -18, 18, 'metal_dark', bevel=3))  # feed cover
    W.add('body', rail(-40, 210, 84, 11, 'metal_dark'))
    W.add('body', extrude(smooth_poly([(240, 14), (400, 18), (405, 26), (405, 64), (240, 66)], 1), -22, 22, 'polymer', bevel=5))
    barrel(W, 405, 700, yb, 12)
    W.add('body', cylinder((440, 20, -6), (650, 20, -14), 5, 'metal_dark', segs=8))  # bipod legs folded
    W.add('body', cylinder((440, 20, 6), (650, 20, 14), 5, 'metal_dark', segs=8))
    flash_hider(W, 700, yb, 13, 45)
    W.add('body', extrude(strip([(420, 72), (430, 110), (480, 118), (520, 105)], 8), -5, 5, 'polymer', bevel=2))  # carry handle
    grip(W, 0, 12, angle=20, length=100, width=28, depth=34, mat='polymer')
    trigger_guard(W, 12, 72, 10, depth=24, thick=4, width=12)
    W.add('body', extrude(smooth_poly([(-60, 66), (-60, 16), (-140, -10), (-330, -55), (-350, -55), (-350, 52), (-330, 60), (-200, 66)], 1), -20, 20, 'polymer', bevel=5))
    W.add('body', box(-358, -60, -21, -348, 58, 21, 'rubber', bevel=3))
    # box magazine on the left + belt
    W.add('mag', extrude(smooth_poly([(60, 20), (170, 20), (175, -90), (55, -90)], 1), -70, -22, 'polymer_od', bevel=6))
    W.add('mag', extrude(strip([(110, 20), (112, 40), (100, 50)], 10), -40, -20, 'brass', bevel=1))
    W.add('bolt', box(120, 40, 20, 150, 50, 36, 'metal_dark', bevel=1))
    W.point('grip', 0, -2)
    W.point('support', 320, 14)
    W.point('muzzle', 745, yb)
    W.point('eject', 80, 30, 0)
    W.point('mag', 115, 20, -46)
    W.point('bolt', 135, 45, 33)
    W.point('cover', 80, 84)
    W.props.update(grip_angle=20, mg=True)
    return W


def negev():
    W = Model('negev')
    yb = 46
    W.add('body', extrude([(-60, 10), (250, 10), (250, 74), (-60, 74)], -19, 19, 'metal_dark', bevel=3))
    W.add('body', rail(-50, 240, 74, 11, 'metal_dark'))
    W.add('body', extrude(smooth_poly([(250, 16), (420, 20), (425, 30), (425, 70), (250, 70)], 1), -20, 20, 'metal_dark', bevel=4))
    for xx in range(270, 410, 24):
        W.add('body', box(xx, 36, -20.6, xx + 12, 58, 20.6, 'bore', bevel=0.8))
    barrel(W, 425, 720, yb, 11)
    flash_hider(W, 720, yb, 12, 40)
    W.add('body', cylinder((440, 22, -6), (660, 22, -12), 5, 'metal_dark', segs=8))
    W.add('body', cylinder((440, 22, 6), (660, 22, 12), 5, 'metal_dark', segs=8))
    grip(W, 0, 12, angle=20, length=100, width=28, depth=34, mat='polymer')
    trigger_guard(W, 12, 72, 10, depth=24, thick=4, width=12)
    W.add('body', extrude(strip([(-60, 60), (-300, 50)], 16), -13, 13, 'metal_dark', bevel=3))
    W.add('body', extrude(strip([(-60, 20), (-300, -30)], 14), -13, 13, 'metal_dark', bevel=3))
    W.add('body', extrude([(-318, -52), (-298, -52), (-298, 60), (-318, 60)], -18, 18, 'rubber', bevel=3))
    W.add('mag', extrude(smooth_poly([(60, 20), (180, 20), (185, -100), (55, -100)], 1), -72, -20, 'polymer', bevel=6))
    W.add('mag', extrude(strip([(120, 20), (122, 40), (110, 50)], 10), -40, -18, 'brass', bevel=1))
    W.add('bolt', box(120, 40, 19, 150, 50, 34, 'metal_dark', bevel=1))
    W.point('grip', 0, -2)
    W.point('support', 330, 18)
    W.point('muzzle', 760, yb)
    W.point('eject', 80, 30, 0)
    W.point('mag', 120, 20, -46)
    W.point('bolt', 135, 45, 31)
    W.point('cover', 80, 74)
    W.props.update(grip_angle=20, mg=True)
    return W


RIFLES = [ak47, m4a4, m4a1s, galil, famas, aug, sg553,
          mac10, mp9, mp7, mp5sd, ump45, p90, bizon,
          awp, ssg08, g3sg1, scar20,
          nova, xm1014, sawedoff, mag7, m249, negev]
