"""
Tiny procedural modelling library used to generate every 3D asset of CS Arsenal.

Coordinates
-----------
Weapon "profile space" (what the weapon scripts are written in): millimetres,
  x = forward (towards the muzzle), y = up, z = right (thickness axis).
Model space (what the game loads): metres, X = right, Y = up, -Z = forward,
  i.e. (X, Y, Z) = (z, y, -x) * 0.001.  This is identical to camera space,
  so a weapon model can be dropped straight into the first-person view.

Every primitive builds a closed, consistently wound (CCW = outward) piece.
"""
import math
import struct
import json
import numpy as np

# --------------------------------------------------------------------------------------
# 2D helpers
# --------------------------------------------------------------------------------------

def poly_area(pts):
    a = 0.0
    n = len(pts)
    for i in range(n):
        x0, y0 = pts[i]
        x1, y1 = pts[(i + 1) % n]
        a += x0 * y1 - x1 * y0
    return a * 0.5


def ensure_ccw(pts):
    pts = [tuple(map(float, p)) for p in pts]
    # drop consecutive duplicates
    out = []
    for p in pts:
        if not out or (abs(out[-1][0] - p[0]) > 1e-6 or abs(out[-1][1] - p[1]) > 1e-6):
            out.append(p)
    if len(out) > 2 and abs(out[0][0] - out[-1][0]) < 1e-6 and abs(out[0][1] - out[-1][1]) < 1e-6:
        out.pop()
    if poly_area(out) < 0:
        out.reverse()
    return out


def _point_in_tri(p, a, b, c):
    def s(p1, p2, p3):
        return (p1[0] - p3[0]) * (p2[1] - p3[1]) - (p2[0] - p3[0]) * (p1[1] - p3[1])
    d1 = s(p, a, b)
    d2 = s(p, b, c)
    d3 = s(p, c, a)
    neg = (d1 < -1e-12) or (d2 < -1e-12) or (d3 < -1e-12)
    pos = (d1 > 1e-12) or (d2 > 1e-12) or (d3 > 1e-12)
    return not (neg and pos)


def triangulate(pts):
    """Ear clipping for a simple CCW polygon. Returns index triples."""
    n = len(pts)
    idx = list(range(n))
    tris = []
    guard = 0
    while len(idx) > 3 and guard < 10000:
        guard += 1
        found = False
        m = len(idx)
        for k in range(m):
            i0, i1, i2 = idx[(k - 1) % m], idx[k], idx[(k + 1) % m]
            a, b, c = pts[i0], pts[i1], pts[i2]
            cross = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
            if cross <= 1e-10:
                continue
            ok = True
            for j in idx:
                if j in (i0, i1, i2):
                    continue
                if _point_in_tri(pts[j], a, b, c):
                    ok = False
                    break
            if ok:
                tris.append((i0, i1, i2))
                idx.pop(k)
                found = True
                break
        if not found:
            # degenerate: clip the flattest vertex anyway
            m = len(idx)
            tris.append((idx[-1], idx[0], idx[1]))
            idx.pop(0)
    if len(idx) == 3:
        tris.append((idx[0], idx[1], idx[2]))
    return tris


def inset(pts, d):
    """Miter offset of a CCW polygon towards its inside by d (negative = outwards)."""
    n = len(pts)
    out = []
    for i in range(n):
        p0 = np.array(pts[i - 1])
        p1 = np.array(pts[i])
        p2 = np.array(pts[(i + 1) % n])
        e0 = p1 - p0
        e1 = p2 - p1
        l0 = np.linalg.norm(e0) or 1.0
        l1 = np.linalg.norm(e1) or 1.0
        n0 = np.array([-e0[1], e0[0]]) / l0
        n1 = np.array([-e1[1], e1[0]]) / l1
        m = n0 + n1
        lm = np.linalg.norm(m)
        if lm < 1e-6:
            m = n0
            k = 1.0
        else:
            m = m / lm
            k = 1.0 / max(0.35, float(np.dot(m, n0)))
        k = min(k, 2.5)
        out.append(tuple(p1 + m * d * k))
    return out


def resample_closed(pts, max_len):
    out = []
    n = len(pts)
    for i in range(n):
        a = np.array(pts[i])
        b = np.array(pts[(i + 1) % n])
        L = np.linalg.norm(b - a)
        steps = max(1, int(math.ceil(L / max_len)))
        for s in range(steps):
            out.append(tuple(a + (b - a) * (s / steps)))
    return out


def arc(cx, cy, r, a0, a1, segs):
    """Points on a circular arc, angles in degrees, inclusive."""
    return [(cx + r * math.cos(math.radians(a0 + (a1 - a0) * i / segs)),
             cy + r * math.sin(math.radians(a0 + (a1 - a0) * i / segs))) for i in range(segs + 1)]


def rounded_rect(x0, y0, x1, y1, r, segs=4):
    r = min(r, (x1 - x0) / 2, (y1 - y0) / 2)
    pts = []
    pts += arc(x1 - r, y0 + r, r, -90, 0, segs)
    pts += arc(x1 - r, y1 - r, r, 0, 90, segs)
    pts += arc(x0 + r, y1 - r, r, 90, 180, segs)
    pts += arc(x0 + r, y0 + r, r, 180, 270, segs)
    return pts


def strip(polyline, width_a, width_b=None):
    """Closed polygon made by thickening an open polyline (for guards, handles, ...)."""
    if width_b is None:
        width_b = width_a
    P = [np.array(p, dtype=float) for p in polyline]
    n = len(P)
    left, right = [], []
    for i in range(n):
        if i == 0:
            t = P[1] - P[0]
        elif i == n - 1:
            t = P[-1] - P[-2]
        else:
            t = (P[i + 1] - P[i]) / (np.linalg.norm(P[i + 1] - P[i]) + 1e-9) + (P[i] - P[i - 1]) / (np.linalg.norm(P[i] - P[i - 1]) + 1e-9)
        t = t / (np.linalg.norm(t) + 1e-9)
        nrm = np.array([-t[1], t[0]])
        w = (width_a + (width_b - width_a) * i / max(1, n - 1)) * 0.5
        left.append(tuple(P[i] + nrm * w))
        right.append(tuple(P[i] - nrm * w))
    return right + left[::-1]


def smooth_poly(pts, iterations=1):
    """Chaikin corner cutting for a closed polygon."""
    for _ in range(iterations):
        out = []
        n = len(pts)
        for i in range(n):
            p = np.array(pts[i])
            q = np.array(pts[(i + 1) % n])
            out.append(tuple(p * 0.75 + q * 0.25))
            out.append(tuple(p * 0.25 + q * 0.75))
        pts = out
    return pts


# --------------------------------------------------------------------------------------
# 3D geometry container
# --------------------------------------------------------------------------------------

class Geo:
    """A soup of triangles with a material per triangle (profile space, mm)."""

    def __init__(self):
        self.v = []      # list of np.array(3)
        self.f = []      # list of (a, b, c, material, smooth_group_id)

    _group_counter = [0]

    @staticmethod
    def new_group():
        Geo._group_counter[0] += 1
        return Geo._group_counter[0]

    def add_v(self, p):
        self.v.append(np.array(p, dtype=float))
        return len(self.v) - 1

    def tri(self, a, b, c, mat, g):
        self.f.append((a, b, c, mat, g))

    def quad(self, a, b, c, d, mat, g):
        self.f.append((a, b, c, mat, g))
        self.f.append((a, c, d, mat, g))

    def merge(self, other):
        off = len(self.v)
        self.v.extend([p.copy() for p in other.v])
        self.f.extend([(a + off, b + off, c + off, m, g) for (a, b, c, m, g) in other.f])
        return self

    def copy(self):
        g = Geo()
        g.merge(self)
        return g

    # ---- transforms (profile space) ----
    def translate(self, dx, dy, dz):
        d = np.array([dx, dy, dz], dtype=float)
        self.v = [p + d for p in self.v]
        return self

    def scale(self, sx, sy=None, sz=None):
        if sy is None:
            sy = sx
        if sz is None:
            sz = sx
        s = np.array([sx, sy, sz], dtype=float)
        self.v = [p * s for p in self.v]
        if sx * sy * sz < 0:
            self.f = [(a, c, b, m, g) for (a, b, c, m, g) in self.f]
        return self

    def rotate(self, axis, deg, origin=(0, 0, 0)):
        o = np.array(origin, dtype=float)
        R = rot_matrix(axis, deg)
        self.v = [R @ (p - o) + o for p in self.v]
        return self

    def mirror_z(self):
        return self.scale(1, 1, -1)

    def set_material(self, mat):
        self.f = [(a, b, c, mat, g) for (a, b, c, m, g) in self.f]
        return self

    def signed_volume(self):
        vol = 0.0
        for (a, b, c, m, g) in self.f:
            vol += np.dot(self.v[a], np.cross(self.v[b], self.v[c]))
        return vol / 6.0

    def fix_orientation(self):
        if self.signed_volume() < 0:
            self.f = [(a, c, b, m, g) for (a, b, c, m, g) in self.f]
        return self

    def bounds(self):
        A = np.array(self.v)
        return A.min(axis=0), A.max(axis=0)


def rot_matrix(axis, deg):
    t = math.radians(deg)
    c, s = math.cos(t), math.sin(t)
    if isinstance(axis, str) and axis == 'x':
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if isinstance(axis, str) and axis == 'y':
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    if isinstance(axis, str) and axis == 'z':
        return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])
    ax = np.array(axis, dtype=float)
    ax /= np.linalg.norm(ax)
    x, y, z = ax
    C = 1 - c
    return np.array([[c + x * x * C, x * y * C - z * s, x * z * C + y * s],
                     [y * x * C + z * s, c + y * y * C, y * z * C - x * s],
                     [z * x * C - y * s, z * y * C + x * s, c + z * z * C]])


# --------------------------------------------------------------------------------------
# primitives
# --------------------------------------------------------------------------------------

def extrude(profile, z0, z1, mat, bevel=0.8, side_mat=None, cap_mat=None, resample=None):
    """Extrude a 2D side profile (x fwd, y up) between z0 and z1 (right axis) with chamfered edges."""
    pts = ensure_ccw(profile)
    if resample:
        pts = resample_closed(pts, resample)
    side_mat = side_mat or mat
    cap_mat = cap_mat or mat
    g = Geo()
    thick = z1 - z0
    b = min(bevel, thick * 0.3)
    gid_side = Geo.new_group()
    gid_cap0 = Geo.new_group()
    gid_cap1 = Geo.new_group()
    if b > 1e-6:
        ins = inset(pts, b)
        rings = [(ins, z0), (pts, z0 + b), (pts, z1 - b), (ins, z1)]
    else:
        ins = pts
        rings = [(pts, z0), (pts, z1)]
    n = len(pts)
    ring_idx = []
    for (rp, z) in rings:
        ring_idx.append([g.add_v((p[0], p[1], z)) for p in rp])
    for r in range(len(rings) - 1):
        A = ring_idx[r]
        B = ring_idx[r + 1]
        for i in range(n):
            j = (i + 1) % n
            g.quad(A[i], A[j], B[j], B[i], side_mat, gid_side)
    tris = triangulate(ins)
    first = ring_idx[0]
    last = ring_idx[-1]
    for (a, b2, c) in tris:
        g.tri(last[a], last[b2], last[c], cap_mat, gid_cap1)
        g.tri(first[a], first[c], first[b2], cap_mat, gid_cap0)
    return g.fix_orientation()


def box(x0, y0, z0, x1, y1, z1, mat, bevel=0.6):
    return extrude([(x0, y0), (x1, y0), (x1, y1), (x0, y1)], z0, z1, mat, bevel=bevel)


def lathe(profile, mat, segs=20, axis='x', origin=(0, 0, 0), cap_start=True, cap_end=True, mats=None, phase=0.0):
    """Revolve [(t, r), ...] around an axis ('x' default = weapon forward axis).
    mats: optional list of materials per profile segment."""
    g = Geo()
    rings = []
    ox, oy, oz = origin
    for (t, r) in profile:
        ring = []
        for s in range(segs):
            a = 2 * math.pi * s / segs + phase
            cy, cz = math.cos(a) * r, math.sin(a) * r
            if axis == 'x':
                p = (ox + t, oy + cy, oz + cz)
            elif axis == 'y':
                p = (ox + cz, oy + t, oz + cy)
            else:
                p = (ox + cy, oy + cz, oz + t)
            ring.append(g.add_v(p))
        rings.append(ring)
    for k in range(len(rings) - 1):
        m = mats[k] if mats else mat
        gid = Geo.new_group()
        A, B = rings[k], rings[k + 1]
        for s in range(segs):
            s2 = (s + 1) % segs
            g.quad(A[s], A[s2], B[s2], B[s], m, gid)
    if cap_start and profile[0][1] > 1e-6:
        c = g.add_v(_axis_point(axis, origin, profile[0][0]))
        gid = Geo.new_group()
        A = rings[0]
        for s in range(segs):
            g.tri(c, A[(s + 1) % segs], A[s], mats[0] if mats else mat, gid)
    if cap_end and profile[-1][1] > 1e-6:
        c = g.add_v(_axis_point(axis, origin, profile[-1][0]))
        gid = Geo.new_group()
        A = rings[-1]
        for s in range(segs):
            g.tri(c, A[s], A[(s + 1) % segs], mats[-1] if mats else mat, gid)
    return _orient_lathe(g)


def _axis_point(axis, origin, t):
    ox, oy, oz = origin
    if axis == 'x':
        return (ox + t, oy, oz)
    if axis == 'y':
        return (ox, oy + t, oz)
    return (ox, oy, oz + t)


def _orient_lathe(g):
    # lathe pieces may be open (no caps) -> orient by radial direction of first face
    if not g.f:
        return g
    vol = g.signed_volume()
    if abs(vol) > 1e-9:
        return g.fix_orientation()
    return g


def cylinder(p0, p1, r, mat, segs=16, r1=None, bevel=0.0):
    """Cylinder between two arbitrary points (profile space)."""
    if r1 is None:
        r1 = r
    p0 = np.array(p0, dtype=float)
    p1 = np.array(p1, dtype=float)
    L = np.linalg.norm(p1 - p0)
    if bevel > 0:
        prof = [(0, max(r - bevel, 0.01)), (bevel, r), (L - bevel, r1), (L, max(r1 - bevel, 0.01))]
    else:
        prof = [(0, r), (L, r1)]
    g = lathe(prof, mat, segs=segs, axis='x')
    # orient +x to direction
    d = (p1 - p0) / L
    _align_x(g, d)
    g.translate(*p0)
    return g


def _align_x(g, d):
    x = np.array([1.0, 0, 0])
    v = np.cross(x, d)
    s = np.linalg.norm(v)
    c = float(np.dot(x, d))
    if s < 1e-9:
        if c < 0:
            g.rotate('y', 180)
        return
    ang = math.degrees(math.atan2(s, c))
    g.rotate(v / s, ang)


def ellipsoid(c, rx, ry, rz, mat, segs=16, rings=10):
    g = Geo()
    gid = Geo.new_group()
    top = g.add_v((c[0], c[1] + ry, c[2]))
    bot = g.add_v((c[0], c[1] - ry, c[2]))
    R = []
    for i in range(1, rings):
        phi = math.pi * i / rings
        ring = []
        for s in range(segs):
            th = 2 * math.pi * s / segs
            ring.append(g.add_v((c[0] + rx * math.sin(phi) * math.cos(th), c[1] + ry * math.cos(phi), c[2] + rz * math.sin(phi) * math.sin(th))))
        R.append(ring)
    for s in range(segs):
        s2 = (s + 1) % segs
        g.tri(top, R[0][s2], R[0][s], mat, gid)
        g.tri(bot, R[-1][s], R[-1][s2], mat, gid)
    for i in range(len(R) - 1):
        for s in range(segs):
            s2 = (s + 1) % segs
            g.quad(R[i][s], R[i][s2], R[i + 1][s2], R[i + 1][s], mat, gid)
    return g.fix_orientation()


def loft(sections, mat, segs=16, axis='y', cap_start=True, cap_end=True, mats=None, squareness=0.0):
    """Loft through elliptical sections [(t, cx, cz, rx, rz), ...] along axis (default y).
    squareness in [0,1) makes the section a superellipse (boxier)."""
    g = Geo()
    rings = []
    expo = 2.0 / (1.0 + squareness * 3.0)
    for sec in sections:
        t, ca, cb, ra, rb = sec[:5]
        ring = []
        for s in range(segs):
            th = 2 * math.pi * s / segs
            ct, st = math.cos(th), math.sin(th)
            a = ra * math.copysign(abs(ct) ** expo, ct)
            b = rb * math.copysign(abs(st) ** expo, st)
            if axis == 'y':
                p = (ca + a, t, cb + b)
            elif axis == 'x':
                p = (t, ca + a, cb + b)
            else:
                p = (ca + a, cb + b, t)
            ring.append(g.add_v(p))
        rings.append(ring)
    for k in range(len(rings) - 1):
        gid = Geo.new_group()
        A, B = rings[k], rings[k + 1]
        m = mats[k] if mats else mat
        for s in range(segs):
            s2 = (s + 1) % segs
            g.quad(A[s], B[s], B[s2], A[s2], m, gid)
    def cap(ring, sec, flip, m):
        t, ca, cb = sec[:3]
        if axis == 'y':
            cp = (ca, t, cb)
        elif axis == 'x':
            cp = (t, ca, cb)
        else:
            cp = (ca, cb, t)
        c = g.add_v(cp)
        gid = Geo.new_group()
        for s in range(segs):
            s2 = (s + 1) % segs
            if flip:
                g.tri(c, ring[s], ring[s2], m, gid)
            else:
                g.tri(c, ring[s2], ring[s], m, gid)
    if cap_start:
        cap(rings[0], sections[0], False, mats[0] if mats else mat)
    if cap_end:
        cap(rings[-1], sections[-1], True, mats[-1] if mats else mat)
    return g.fix_orientation()


def torus(c, R, r, mat, segs=24, tube=8, axis='x', arc_deg=360.0):
    g = Geo()
    gid = Geo.new_group()
    rings = []
    full = abs(arc_deg - 360.0) < 1e-6
    n_major = segs if full else segs + 1
    for i in range(n_major):
        a = math.radians(arc_deg) * i / segs
        ring = []
        for j in range(tube):
            b = 2 * math.pi * j / tube
            rr = R + r * math.cos(b)
            u, w = rr * math.cos(a), rr * math.sin(a)
            h = r * math.sin(b)
            if axis == 'x':
                p = (c[0] + h, c[1] + u, c[2] + w)
            elif axis == 'y':
                p = (c[0] + u, c[1] + h, c[2] + w)
            else:
                p = (c[0] + u, c[1] + w, c[2] + h)
            ring.append(g.add_v(p))
        rings.append(ring)
    for i in range(n_major if full else n_major - 1):
        A = rings[i]
        B = rings[(i + 1) % n_major]
        for j in range(tube):
            j2 = (j + 1) % tube
            g.quad(A[j], B[j], B[j2], A[j2], mat, gid)
    if abs(g.signed_volume()) > 1e-9:
        g.fix_orientation()
    return g


def rail(x0, x1, y, z_half, mat, slot=10.0, height=6.0):
    """Picatinny rail on top of y, spanning x0..x1."""
    g = Geo()
    g.merge(box(x0, y, -z_half * 0.6, x1, y + height * 0.45, z_half * 0.6, mat, bevel=0.4))
    x = x0 + slot * 0.25
    while x + slot * 0.5 < x1:
        g.merge(box(x, y + height * 0.4, -z_half, x + slot * 0.55, y + height, z_half, mat, bevel=0.5))
        x += slot
    return g


# --------------------------------------------------------------------------------------
# model = collection of parts, export
# --------------------------------------------------------------------------------------

class Model:
    def __init__(self, name):
        self.name = name
        self.parts = {}
        self.points = {}
        self.pivots = {}
        self.props = {}

    def part(self, name):
        if name not in self.parts:
            self.parts[name] = Geo()
        return self.parts[name]

    def add(self, part, geo):
        self.part(part).merge(geo)
        return self

    def point(self, name, x, y, z=0.0):
        self.points[name] = (x, y, z)

    def all_geo(self):
        g = Geo()
        for p in self.parts.values():
            g.merge(p)
        return g


def to_model_space(p):
    """profile (mm; x fwd, y up, z right) -> model (m; X right, Y up, -Z fwd)"""
    return (p[2] * 0.001, p[1] * 0.001, -p[0] * 0.001)


UV_SCALE = {  # metres per texture repeat
    'default': 0.08,
}


def build_part_buffers(geo, space='profile', smooth_angle=38.0, uv_scale=None, material_uv=None):
    """Return dict material -> (positions, normals, uvs, indices) in model space."""
    uv_scale = uv_scale or {}
    material_uv = material_uv or {}
    if space == 'profile':
        V = np.array([to_model_space(p) for p in geo.v]) if geo.v else np.zeros((0, 3))
    else:
        V = np.array(geo.v) if geo.v else np.zeros((0, 3))
    F = geo.f
    if not F:
        return {}
    tri = np.array([[a, b, c] for (a, b, c, m, g) in F])
    P0, P1, P2 = V[tri[:, 0]], V[tri[:, 1]], V[tri[:, 2]]
    fn = np.cross(P1 - P0, P2 - P0)
    area2 = np.linalg.norm(fn, axis=1)
    keep = area2 > 1e-14
    fnu = fn / np.maximum(area2[:, None], 1e-20)

    # vertex -> faces (by quantised position so seams between pieces sharing verts smooth too,
    # but only within the same smoothing group)
    key = [tuple(np.round(p * 1e5).astype(np.int64)) for p in V]
    pos_faces = {}
    for fi, (a, b, c, m, g) in enumerate(F):
        if not keep[fi]:
            continue
        for vi in (a, b, c):
            pos_faces.setdefault((key[vi], g), []).append(fi)
    cos_t = math.cos(math.radians(smooth_angle))

    out = {}
    cache = {}
    for fi, (a, b, c, m, g) in enumerate(F):
        if not keep[fi]:
            continue
        n_face = fnu[fi]
        mbuf = out.setdefault(m, ([], [], [], [], {}))
        pos_l, nrm_l, uv_l, idx_l, dedup = mbuf
        # uv projection axis from face normal
        ax = int(np.argmax(np.abs(n_face)))
        sc = uv_scale.get(m, UV_SCALE['default'])
        corner_ids = []
        for vi in (a, b, c):
            acc = np.zeros(3)
            for fj in pos_faces.get((key[vi], g), []):
                if np.dot(fnu[fj], n_face) >= cos_t:
                    acc += fn[fj]
            ln = np.linalg.norm(acc)
            nrm = acc / ln if ln > 1e-12 else n_face
            p = V[vi]
            if ax == 0:
                uv = (p[2] / sc, p[1] / sc)
            elif ax == 1:
                uv = (p[0] / sc, p[2] / sc)
            else:
                uv = (p[0] / sc, p[1] / sc)
            if m in material_uv:
                uv = material_uv[m](p, nrm, uv)
            k = (tuple(np.round(p * 1e5).astype(np.int64)), tuple(np.round(nrm * 1000).astype(np.int64)), (round(uv[0], 4), round(uv[1], 4)))
            if k in dedup:
                corner_ids.append(dedup[k])
            else:
                dedup[k] = len(pos_l)
                pos_l.append(p)
                nrm_l.append(nrm)
                uv_l.append(uv)
                corner_ids.append(dedup[k])
        idx_l.append(corner_ids)
    res = {}
    for m, (pos_l, nrm_l, uv_l, idx_l, _) in out.items():
        res[m] = (np.array(pos_l, dtype=np.float32), np.array(nrm_l, dtype=np.float32),
                  np.array(uv_l, dtype=np.float32), np.array(idx_l, dtype=np.int32))
    return res


def write_csm(path, parts_buffers):
    """Binary format read by dev.csarsenal.client.mesh.MeshLoader (big endian).
    'CSM1', int nParts, { utf name, int nChunks, { utf material, int nVerts,
    nVerts*(3f pos, 3b normal*127, 2f uv), int nTris, nTris*3 (short|int) } }"""
    with open(path, 'wb') as fp:
        fp.write(b'CSM1')
        fp.write(struct.pack('>i', len(parts_buffers)))
        for pname, bufs in parts_buffers.items():
            _wutf(fp, pname)
            fp.write(struct.pack('>i', len(bufs)))
            for mat, (P, N, UV, I) in bufs.items():
                _wutf(fp, mat)
                nv = len(P)
                fp.write(struct.pack('>i', nv))
                Nb = np.clip(np.round(N * 127), -127, 127).astype(np.int8)
                rec = bytearray()
                for i in range(nv):
                    rec += struct.pack('>fffbbbff', P[i][0], P[i][1], P[i][2], int(Nb[i][0]), int(Nb[i][1]), int(Nb[i][2]), UV[i][0], UV[i][1])
                fp.write(rec)
                fp.write(struct.pack('>i', len(I)))
                if nv < 32768:
                    fp.write(np.asarray(I, dtype='>i2').tobytes())
                else:
                    fp.write(np.asarray(I, dtype='>i4').tobytes())


def _wutf(fp, s):
    b = s.encode('utf-8')
    fp.write(struct.pack('>H', len(b)))
    fp.write(b)


def export_model(model, mesh_path, meta_path, space='profile', smooth_angle=38.0, uv_scale=None, extra_meta=None):
    bufs = {}
    tri_count = 0
    for pname, geo in model.parts.items():
        b = build_part_buffers(geo, space=space, smooth_angle=smooth_angle, uv_scale=uv_scale)
        if b:
            bufs[pname] = b
            tri_count += sum(len(v[3]) for v in b.values())
    write_csm(mesh_path, bufs)
    conv = to_model_space if space == 'profile' else (lambda p: p)
    meta = {
        'points': {k: [round(c, 5) for c in conv(v)] for k, v in model.points.items()},
        'pivots': {k: [round(c, 5) for c in conv(v)] for k, v in model.pivots.items()},
    }
    meta.update(model.props)
    if extra_meta:
        meta.update(extra_meta)
    with open(meta_path, 'w') as fp:
        json.dump(meta, fp, indent=1)
    return tri_count


# --------------------------------------------------------------------------------------
# preview renderer (QA only)
# --------------------------------------------------------------------------------------

PREVIEW_COLORS = {}


def render_preview(model_or_geo, path, size=(900, 420), view='side', colors=None, space='profile', yaw=25.0, pitch=18.0):
    from PIL import Image, ImageDraw
    colors = colors or PREVIEW_COLORS
    geos = model_or_geo.parts.values() if isinstance(model_or_geo, Model) else [model_or_geo]
    tris = []
    for g in geos:
        V = np.array([to_model_space(p) for p in g.v]) if space == 'profile' else np.array(g.v)
        for (a, b, c, m, gg) in g.f:
            tris.append((V[a], V[b], V[c], m))
    if not tris:
        return
    allp = np.array([t[i] for t in tris for i in range(3)])
    center = (allp.min(0) + allp.max(0)) / 2
    if view == 'side':
        R = rot_matrix('y', -90 + yaw) @ np.eye(3)
        R = rot_matrix('x', pitch) @ R
    elif view == 'front':
        R = rot_matrix('x', pitch) @ rot_matrix('y', yaw)
    else:
        R = rot_matrix('x', 90)
    W, H = size
    pts = (allp - center) @ R.T
    ext = max(pts[:, 0].max() - pts[:, 0].min(), (pts[:, 1].max() - pts[:, 1].min()) * W / H)
    sc = W * 0.9 / max(ext, 1e-6)
    img = Image.new('RGB', size, (38, 42, 48))
    dr = ImageDraw.Draw(img)
    light = np.array([0.4, 0.8, 0.45])
    light /= np.linalg.norm(light)
    items = []
    for (p0, p1, p2, m) in tris:
        q = [(p - center) @ R.T for p in (p0, p1, p2)]
        n = np.cross(q[1] - q[0], q[2] - q[0])
        ln = np.linalg.norm(n)
        if ln < 1e-12:
            continue
        n /= ln
        if n[2] < 0:
            continue  # back face (camera looks down -z; we view from +z)
        depth = (q[0][2] + q[1][2] + q[2][2]) / 3
        items.append((depth, q, n, m))
    items.sort(key=lambda t: t[0])
    for depth, q, n, m in items:
        base = np.array(colors.get(m, (160, 160, 160)), dtype=float)
        shade = 0.35 + 0.65 * max(0.0, float(np.dot(n, light)))
        col = tuple(int(min(255, c * shade)) for c in base)
        poly = [(W / 2 + p[0] * sc, H / 2 - p[1] * sc) for p in q]
        dr.polygon(poly, fill=col)
    img.save(path)


def tube(path, radius, mat, segs=10, radius_end=None, cap=True):
    """Sweep a circle along a polyline (parallel transport frames). radius may taper to radius_end."""
    P = [np.array(p, dtype=float) for p in path]
    n = len(P)
    if radius_end is None:
        radius_end = radius
    T = []
    for i in range(n):
        if i == 0:
            t = P[1] - P[0]
        elif i == n - 1:
            t = P[-1] - P[-2]
        else:
            t = (P[i + 1] - P[i - 1])
        T.append(t / (np.linalg.norm(t) + 1e-12))
    ref = np.array([0, 0, 1.0]) if abs(T[0][2]) < 0.9 else np.array([1.0, 0, 0])
    N = np.cross(T[0], ref)
    N /= np.linalg.norm(N)
    frames = []
    for i in range(n):
        if i > 0:
            N = N - T[i] * np.dot(N, T[i])
            ln = np.linalg.norm(N)
            N = N / ln if ln > 1e-9 else frames[-1][0]
        B = np.cross(T[i], N)
        frames.append((N, B))
    g = Geo()
    gid = Geo.new_group()
    rings = []
    for i in range(n):
        r = radius + (radius_end - radius) * i / max(1, n - 1)
        Nn, Bb = frames[i]
        ring = []
        for s in range(segs):
            a = 2 * math.pi * s / segs
            ring.append(g.add_v(P[i] + (Nn * math.cos(a) + Bb * math.sin(a)) * r))
        rings.append(ring)
    for i in range(n - 1):
        A, Bq = rings[i], rings[i + 1]
        for s in range(segs):
            s2 = (s + 1) % segs
            g.quad(A[s], A[s2], Bq[s2], Bq[s], mat, gid)
    if cap:
        for (i, flip) in ((0, True), (n - 1, False)):
            c = g.add_v(P[i] + (-T[i] if flip else T[i]) * (radius if i == 0 else radius_end) * 0.5)
            gc = Geo.new_group()
            R = rings[i]
            for s in range(segs):
                s2 = (s + 1) % segs
                if flip:
                    g.tri(c, R[s2], R[s], mat, gc)
                else:
                    g.tri(c, R[s], R[s2], mat, gc)
    if abs(g.signed_volume()) > 1e-15:
        g.fix_orientation()
    return g
