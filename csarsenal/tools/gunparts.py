"""Re-usable weapon building blocks (profile space: mm, x fwd, y up, z right)."""
import math
from meshlib import *


def barrel(W, x0, x1, y, r, mat='metal_dark', part='body', segs=16, r_end=None):
    W.add(part, cylinder((x0, y, 0), (x1, y, 0), r, mat, segs=segs, r1=r_end if r_end else r, bevel=0.6))


def bore(W, x, y, r, part='body'):
    """dark disc at the muzzle so the barrel looks hollow"""
    W.add(part, cylinder((x - 0.5, y, 0), (x + 0.3, y, 0), r, 'bore', segs=12))


def lathe_x(W, prof, x0, y, mat, part='body', segs=18, mats=None):
    W.add(part, lathe([(x0 + t, r) for (t, r) in prof], mat, segs=segs, axis='x', origin=(0, y, 0), mats=mats))


def muzzle_brake(W, x, y, r, L=40, part='body', mat='metal_dark', slots=2):
    lathe_x(W, [(0, r * 0.95), (1.5, r), (L - 1.5, r), (L, r * 0.85)], x, y, mat, part=part)
    for i in range(slots):
        sx = x + L * (0.25 + 0.4 * i)
        W.add(part, box(sx, y - r * 0.55, -r - 0.4, sx + L * 0.16, y + r * 0.55, r + 0.4, 'bore', bevel=0.2))
    bore(W, x + L, y, r * 0.45, part)


def flash_hider(W, x, y, r, L=45, part='body', mat='metal_dark'):
    lathe_x(W, [(0, r * 0.9), (3, r), (L, r * 1.05), (L, r * 0.6)], x, y, mat, part=part, segs=16)
    for a in range(0, 360, 60):
        W.add(part, box(x + L * 0.35, y - 1.2, -r - 0.5, x + L + 0.3, y + 1.2, r + 0.5, 'bore', bevel=0.1).rotate('x', a, origin=(0, y, 0)))
    bore(W, x + L, y, r * 0.55, part)


def suppressor(W, x, y, r, L, part='silencer', mat='metal_dark'):
    lathe_x(W, [(0, r * 0.55), (4, r * 0.8), (10, r), (L - 8, r), (L - 2, r * 0.85), (L, r * 0.5)], x, y, mat, part=part, segs=22)
    for i in range(3):
        xx = x + 12 + i * 3
        lathe_x(W, [(0, r + 0.2), (1.2, r + 0.2)], xx, y, 'metal', part=part, segs=22)
    bore(W, x + L, y, r * 0.3, part)


def grip(W, x, y, angle=18, length=105, width=30, depth=36, mat='polymer', part='body', bottom_flare=4, finger=True):
    """Pistol grip hanging below (x, y) raked backwards by angle degrees."""
    d = depth
    prof = []
    # front edge with finger grooves
    n = 6
    for i in range(n + 1):
        t = i / n
        yy = -t * length
        bump = (math.sin(t * math.pi * 3) * 2.2) if finger else 0
        prof.append((d * 0.5 + bump * (0.3 if i in (0, n) else 1.0), yy))
    prof.append((d * 0.5 + bottom_flare * 0.5, -length - 2))
    prof.append((-d * 0.5 - bottom_flare, -length - 3))
    prof.append((-d * 0.5 - 2, -length * 0.5))
    prof.append((-d * 0.5 - 4, -6))
    prof.append((-d * 0.5 + 4, 3))
    prof.append((d * 0.5, 3))
    g = extrude(smooth_poly(ensure_ccw(prof), 1), -width / 2, width / 2, mat, bevel=3.0)
    g.rotate('z', -angle).translate(x, y, 0)
    W.add(part, g)


def trigger_guard(W, x0, x1, y, depth=24, thick=4.0, mat='polymer', part='body', width=10):
    pts = [(x0, y), (x0 + 3, y - depth * 0.85), (x0 + 12, y - depth), ((x0 + x1) / 2, y - depth - 1), (x1 - 8, y - depth + 1), (x1, y - depth * 0.3), (x1, y)]
    W.add(part, extrude(strip(pts, thick), -width / 2, width / 2, mat, bevel=1.0))
    # trigger
    tx = x0 + (x1 - x0) * 0.42
    trig = [(tx, y), (tx + 3, y - depth * 0.35), (tx + 1, y - depth * 0.75), (tx - 1.5, y - depth * 0.78)]
    W.add(part, extrude(strip(trig, 3.4), -2.2, 2.2, 'metal_dark', bevel=0.5))


def curved_mag(W, x_top_rear, y_top, length, width_x, width_z, curve_deg=20, mat='metal_dark', part='mag', segs=10, taper=0.0, ribs=True, floor_mat=None):
    """Magazine hanging down from the mag well, bending forward by curve_deg degrees over its length."""
    pts_front, pts_rear = [], []
    if curve_deg > 0.5:
        R = length / math.radians(curve_deg)
        for i in range(segs + 1):
            t = i / segs
            a = math.radians(curve_deg) * t
            w = width_x * (1 + taper * t)
            # rear edge on a circle of radius R centred in front of the mag, front edge on radius R - w
            pts_rear.append((x_top_rear + R - R * math.cos(a), y_top - R * math.sin(a)))
            pts_front.append((x_top_rear + R - (R - w) * math.cos(a), y_top - (R - w) * math.sin(a)))
    else:
        for i in range(segs + 1):
            t = i / segs
            w = width_x * (1 + taper * t)
            pts_rear.append((x_top_rear, y_top - length * t))
            pts_front.append((x_top_rear + w, y_top - length * t))
    prof = pts_rear + pts_front[::-1]
    W.add(part, extrude(prof, -width_z / 2, width_z / 2, mat, bevel=1.6))
    # floor plate
    a = math.radians(curve_deg) if curve_deg > 0.5 else 0
    pr, pf = pts_rear[-1], pts_front[-1]
    fm = floor_mat or mat
    fp = [(pr[0] - 2 * math.cos(a), pr[1] - 2 * math.sin(a) * 0), (pf[0] + 2, pf[1]), (pf[0] + 2 + 5 * math.sin(a), pf[1] - 5 * math.cos(a)), (pr[0] - 2 + 5 * math.sin(a), pr[1] - 5 * math.cos(a))]
    W.add(part, extrude(fp, -width_z / 2 - 1.2, width_z / 2 + 1.2, fm, bevel=1.0))
    if ribs:
        for k in (0.35, 0.65):
            i = int(segs * k)
            p0, p1 = pts_rear[i], pts_front[i]
            W.add(part, extrude([(p0[0] + 3, p0[1] + 2), (p1[0] - 3, p1[1] + 2), (p1[0] - 3, p1[1] - 2), (p0[0] + 3, p0[1] - 2)], -width_z / 2 - 0.8, width_z / 2 + 0.8, fm, bevel=0.6))
    # top round visible
    W.add(part, cylinder((pts_rear[0][0] + 6, y_top + 2, 0), (pts_front[0][0] - 4, y_top + 2, 0), 4.2, 'brass', segs=10))
    return pts_rear, pts_front


def straight_mag(W, x_rear, y_top, length, width_x, width_z, rake=0, mat='polymer', part='mag', floor_mat=None, extended=0):
    prof = [(x_rear, y_top), (x_rear + width_x, y_top), (x_rear + width_x + math.tan(math.radians(rake)) * length, y_top - length),
            (x_rear + math.tan(math.radians(rake)) * length, y_top - length)]
    W.add(part, extrude(prof, -width_z / 2, width_z / 2, mat, bevel=1.4))
    bx = x_rear + math.tan(math.radians(rake)) * length
    W.add(part, box(bx - 2, y_top - length - 5 - extended, -width_z / 2 - 1.5, bx + width_x + 2, y_top - length + 1, width_z / 2 + 1.5, floor_mat or mat, bevel=1.5))


def front_post(W, x, y, h, part='body', mat='metal_dark', w=10):
    W.add(part, extrude([(x - 8, y), (x + 8, y), (x + 4, y + h * 0.7), (x + 1, y + h), (x - 1, y + h), (x - 4, y + h * 0.7)], -w / 2, w / 2, mat, bevel=0.8))


def rear_notch(W, x, y, h, part='body', mat='metal_dark', w=14):
    W.add(part, extrude([(x - 7, y), (x + 5, y), (x + 5, y + h), (x - 7, y + h)], -w / 2, -1.5, mat, bevel=0.6))
    W.add(part, extrude([(x - 7, y), (x + 5, y), (x + 5, y + h), (x - 7, y + h)], 1.5, w / 2, mat, bevel=0.6))
    W.add(part, extrude([(x - 7, y), (x + 5, y), (x + 5, y + h * 0.45), (x - 7, y + h * 0.45)], -w / 2, w / 2, mat, bevel=0.6))


def scope(W, x0, x1, y, r_tube=13, r_obj=24, r_eye=19, part='body', mat='metal_dark', mounts=None, mount_base=None, turret=True, lens='lens'):
    L = x1 - x0
    prof = [(0, r_eye * 0.8), (2, r_eye), (L * 0.14, r_eye), (L * 0.22, r_tube), (L * 0.66, r_tube), (L * 0.78, r_obj), (L - 2, r_obj), (L, r_obj * 0.9)]
    lathe_x(W, prof, x0, y, mat, part=part, segs=24)
    W.add(part, cylinder((x1 - 0.2, y, 0), (x1 + 0.6, y, 0), r_obj * 0.82, lens, segs=20))
    W.add(part, cylinder((x0 - 0.6, y, 0), (x0 + 0.2, y, 0), r_eye * 0.7, lens, segs=20))
    if turret:
        tx = x0 + L * 0.45
        W.add(part, cylinder((tx, y + r_tube - 2, 0), (tx, y + r_tube + 11, 0), 9, mat, segs=16, bevel=0.8))
        W.add(part, cylinder((tx, y, r_tube - 2), (tx, y, r_tube + 10), 8.5, mat, segs=16, bevel=0.8))
        W.add(part, cylinder((tx, y, -r_tube + 2), (tx, y, -r_tube - 7), 7, mat, segs=14, bevel=0.8))
    for mx in (mounts or []):
        base = mount_base if mount_base is not None else y - r_tube - 14
        lathe_x(W, [(0, r_tube + 3), (12, r_tube + 3)], mx - 6, y, mat, part=part, segs=20)
        W.add(part, box(mx - 7, base, -9, mx + 7, y - r_tube + 2, 9, mat, bevel=1.2))


def red_dot(W, x, y, part='body'):
    W.add(part, box(x - 20, y, -11, x + 20, y + 8, 11, 'metal_dark', bevel=1.5))
    lathe_x(W, [(0, 15), (32, 15), (32, 13)], x - 16, y + 23, 'metal_dark', part=part, segs=20)
    W.add(part, cylinder((x + 15.8, y + 23, 0), (x + 16.4, y + 23, 0), 12.8, 'lens', segs=18))


def stock_ar(W, x0, y, part='body', mat='polymer'):
    """AR-15 buffer tube + collapsible stock. x0 = receiver rear."""
    barrel(W, x0 - 190, x0 + 2, y, 15, 'metal_dark', part=part, segs=18)
    prof = [(x0 - 120, y + 18), (x0 - 115, y + 22), (x0 - 225, y + 30), (x0 - 245, y + 32), (x0 - 250, y + 20),
            (x0 - 252, y - 78), (x0 - 240, y - 80), (x0 - 200, y - 32), (x0 - 170, y - 20), (x0 - 122, y - 14)]
    W.add(part, extrude(prof, -18, 18, mat, bevel=3.0))
    W.add(part, box(x0 - 256, y - 82, -19, x0 - 248, y + 33, 19, 'rubber', bevel=2.0))


def handguard_quad(W, x0, x1, y, r=22, part='body', mat='metal_dark'):
    W.add(part, loft([(x0, y, 0, r, r), (x0 + 6, y, 0, r + 1, r + 1), (x1 - 4, y, 0, r + 1, r + 1), (x1, y, 0, r, r)], mat, segs=16, axis='x', squareness=0.55))
    for a in (0, 90, 180, 270):
        g = rail(x0 + 6, x1 - 6, r - 1, 7.5, mat)
        g.rotate('x', a, origin=(0, 0, 0)).translate(0, y, 0)
        W.add(part, g)


def handguard_round(W, x0, x1, y, r=20, part='body', mat='polymer', ribs=8):
    W.add(part, loft([(x0, y, 0, r - 1, r - 1), (x0 + 5, y, 0, r, r), (x1 - 5, y, 0, r, r), (x1, y, 0, r - 2, r - 2)], mat, segs=18, axis='x'))
    for i in range(ribs):
        xx = x0 + 12 + i * (x1 - x0 - 24) / max(1, ribs - 1)
        W.add(part, box(xx - 3, y - 3, -r - 0.6, xx + 3, y + 7, r + 0.6, 'bore', bevel=0.3))


def pistol_slide(W, x0, x1, y0, h, w, mat='metal_dark', part='bolt', serr=True, top_round=2.5, ejection=True, front_taper=8):
    prof = [(x0, y0), (x1, y0), (x1, y0 + h - front_taper * 0.4), (x1 - front_taper, y0 + h), (x0 + 2, y0 + h), (x0, y0 + h - 3)]
    W.add(part, extrude(prof, -w / 2, w / 2, mat, bevel=top_round))
    if serr:
        for i in range(7):
            xx = x0 + 6 + i * 4.2
            W.add(part, box(xx, y0 + 3, w / 2 - 0.6, xx + 1.6, y0 + h - 4, w / 2 + 0.3, 'bore', bevel=0.1))
            W.add(part, box(xx, y0 + 3, -w / 2 - 0.3, xx + 1.6, y0 + h - 4, -w / 2 + 0.6, 'bore', bevel=0.1))
    if ejection:
        ex = x0 + (x1 - x0) * 0.45
        W.add(part, box(ex, y0 + h * 0.45, w / 2 - 1.2, ex + 22, y0 + h - 1.5, w / 2 + 0.2, 'bore', bevel=0.2))
    # sights
    W.add(part, box(x1 - 14, y0 + h - 0.5, -2, x1 - 7, y0 + h + 5, 2, mat, bevel=0.6))
    W.add(part, box(x0 + 4, y0 + h - 0.5, -w / 2 + 2, x0 + 12, y0 + h + 5, -1.6, mat, bevel=0.6))
    W.add(part, box(x0 + 4, y0 + h - 0.5, 1.6, x0 + 12, y0 + h + 5, w / 2 - 2, mat, bevel=0.6))
    W.add(part, box(x1 - 12, y0 + h + 3.5, -1.0, x1 - 9, y0 + h + 5.3, 1.0, 'white', bevel=0.2))


def pistol_frame(W, x0, x1, y_top, depth=10, w=24, mat='polymer', part='body', rail_len=0):
    prof = [(x0, y_top), (x1, y_top), (x1, y_top - depth * 0.6), (x1 - 8, y_top - depth), (x0 + 20, y_top - depth), (x0, y_top - 4)]
    W.add(part, extrude(prof, -w / 2, w / 2, mat, bevel=1.6))
    if rail_len:
        for i in range(3):
            xx = x1 - 12 - i * 9
            W.add(part, box(xx - 3, y_top - depth - 2, -w / 2 + 2, xx + 3, y_top - depth + 0.5, w / 2 - 2, mat, bevel=0.5))


def hand_grip_metadata(W, grip_angle, support=None, support_kind='under'):
    W.props['grip_angle'] = grip_angle
    W.props['support_kind'] = support_kind


def charging_handle_ak(W, x, y, part='bolt'):
    W.add(part, box(x - 70, y - 5, 13, x + 10, y + 5, 16, 'metal', bevel=0.8))
    W.add(part, cylinder((x - 4, y, 16), (x - 4, y, 30), 4.2, 'metal', segs=10, r1=5.0, bevel=0.8))


def stamped_ribs(W, x0, x1, y, w, part='body', mat='metal_dark', n=4):
    for i in range(n):
        xx = x0 + (x1 - x0) * (i + 0.5) / n
        W.add(part, box(xx - 4, y - 5, w / 2 - 0.4, xx + 4, y + 5, w / 2 + 0.8, mat, bevel=0.8))
        W.add(part, box(xx - 4, y - 5, -w / 2 - 0.8, xx + 4, y + 5, -w / 2 + 0.4, mat, bevel=0.8))


def sling_swivel(W, x, y, part='body'):
    W.add(part, torus((x, y, 0), 5, 1.4, 'metal', segs=12, tube=6, axis='z'))


def shell_tube(W, x0, x1, y, r, part='body', mat='metal_dark'):
    barrel(W, x0, x1, y, r, mat, part=part, segs=16)
    lathe_x(W, [(0, r + 1.2), (4, r + 1.2)], x1 - 4, y, mat, part=part, segs=16)
