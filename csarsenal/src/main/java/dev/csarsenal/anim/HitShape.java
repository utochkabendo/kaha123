package dev.csarsenal.anim;

import dev.csarsenal.ballistics.HitGroup;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** A world space hit volume: capsule (a-b, radius) or oriented box. */
public final class HitShape {
    public final HitGroup group;
    public final boolean box;
    // capsule
    public double ax, ay, az, bx, by, bz, r;
    // box: centre, axes (unit), half extents
    public double cx, cy, cz;
    public final double[] axes = new double[9];
    public double hx, hy, hz;

    private HitShape(HitGroup g, boolean box) {
        this.group = g;
        this.box = box;
    }

    public static HitShape capsule(HitGroup g, Vec3 a, Vec3 b, double r) {
        HitShape s = new HitShape(g, false);
        s.ax = a.x; s.ay = a.y; s.az = a.z;
        s.bx = b.x; s.by = b.y; s.bz = b.z;
        s.r = r;
        return s;
    }

    /** capsule defined in a bone frame, transformed by world matrix m */
    public static HitShape capsule(HitGroup g, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, float r) {
        Vector3f a = m.transformPosition(new Vector3f(x0, y0, z0));
        Vector3f b = m.transformPosition(new Vector3f(x1, y1, z1));
        return capsule(g, new Vec3(a.x, a.y, a.z), new Vec3(b.x, b.y, b.z), r);
    }

    /** oriented box in a bone frame: centre + half extents, transformed by m (rotation+translation only) */
    public static HitShape box(HitGroup g, Matrix4f m, float cx, float cy, float cz, float hx, float hy, float hz) {
        HitShape s = new HitShape(g, true);
        Vector3f c = m.transformPosition(new Vector3f(cx, cy, cz));
        s.cx = c.x; s.cy = c.y; s.cz = c.z;
        Vector3f x = m.transformDirection(new Vector3f(1, 0, 0)).normalize();
        Vector3f y = m.transformDirection(new Vector3f(0, 1, 0)).normalize();
        Vector3f z = m.transformDirection(new Vector3f(0, 0, 1)).normalize();
        s.axes[0] = x.x; s.axes[1] = x.y; s.axes[2] = x.z;
        s.axes[3] = y.x; s.axes[4] = y.y; s.axes[5] = y.z;
        s.axes[6] = z.x; s.axes[7] = z.y; s.axes[8] = z.z;
        s.hx = hx; s.hy = hy; s.hz = hz;
        return s;
    }

    /** axis aligned box (for simple mobs) */
    public static HitShape aabb(HitGroup g, double x0, double y0, double z0, double x1, double y1, double z1) {
        HitShape s = new HitShape(g, true);
        s.cx = (x0 + x1) / 2; s.cy = (y0 + y1) / 2; s.cz = (z0 + z1) / 2;
        s.axes[0] = 1; s.axes[4] = 1; s.axes[8] = 1;
        s.hx = (x1 - x0) / 2; s.hy = (y1 - y0) / 2; s.hz = (z1 - z0) / 2;
        return s;
    }

    /** Ray intersection. d must be normalised. Returns distance or -1. */
    public double intersect(Vec3 o, Vec3 d) {
        return box ? rayBox(o, d) : rayCapsule(o, d);
    }

    private double rayCapsule(Vec3 o, Vec3 d) {
        double bax = bx - ax, bay = by - ay, baz = bz - az;
        double oax = o.x - ax, oay = o.y - ay, oaz = o.z - az;
        double baba = bax * bax + bay * bay + baz * baz;
        double bard = bax * d.x + bay * d.y + baz * d.z;
        double baoa = bax * oax + bay * oay + baz * oaz;
        double rdoa = d.x * oax + d.y * oay + d.z * oaz;
        double oaoa = oax * oax + oay * oay + oaz * oaz;
        double a = baba - bard * bard;
        double b = baba * rdoa - baoa * bard;
        double c = baba * oaoa - baoa * baoa - r * r * baba;
        double h = b * b - a * c;
        if (h >= 0 && a > 1e-12) {
            double t = (-b - Math.sqrt(h)) / a;
            double y = baoa + t * bard;
            if (y > 0 && y < baba) return t >= 0 ? t : (inside(o) ? 0 : -1);
            // caps
            double ocx, ocy, ocz;
            if (y <= 0) { ocx = oax; ocy = oay; ocz = oaz; } else { ocx = o.x - bx; ocy = o.y - by; ocz = o.z - bz; }
            double bb = d.x * ocx + d.y * ocy + d.z * ocz;
            double cc = ocx * ocx + ocy * ocy + ocz * ocz - r * r;
            double hh = bb * bb - cc;
            if (hh > 0) {
                double t2 = -bb - Math.sqrt(hh);
                return t2 >= 0 ? t2 : (inside(o) ? 0 : -1);
            }
        } else {
            // parallel to the axis: test both caps as spheres
            double best = -1;
            for (int i = 0; i < 2; i++) {
                double px = i == 0 ? ax : bx, py = i == 0 ? ay : by, pz = i == 0 ? az : bz;
                double ocx = o.x - px, ocy = o.y - py, ocz = o.z - pz;
                double bb = d.x * ocx + d.y * ocy + d.z * ocz;
                double cc = ocx * ocx + ocy * ocy + ocz * ocz - r * r;
                double hh = bb * bb - cc;
                if (hh > 0) {
                    double t2 = -bb - Math.sqrt(hh);
                    if (t2 >= 0 && (best < 0 || t2 < best)) best = t2;
                }
            }
            return best;
        }
        return -1;
    }

    private boolean inside(Vec3 p) {
        if (box) return false;
        double bax = bx - ax, bay = by - ay, baz = bz - az;
        double pax = p.x - ax, pay = p.y - ay, paz = p.z - az;
        double baba = bax * bax + bay * bay + baz * baz;
        double t = baba > 1e-12 ? Math.max(0, Math.min(1, (pax * bax + pay * bay + paz * baz) / baba)) : 0;
        double dx = pax - bax * t, dy = pay - bay * t, dz = paz - baz * t;
        return dx * dx + dy * dy + dz * dz < r * r;
    }

    private double rayBox(Vec3 o, Vec3 d) {
        double px = o.x - cx, py = o.y - cy, pz = o.z - cz;
        double tmin = -1e30, tmax = 1e30;
        double[] half = {hx, hy, hz};
        for (int i = 0; i < 3; i++) {
            double ex = axes[i * 3], ey = axes[i * 3 + 1], ez = axes[i * 3 + 2];
            double e = ex * px + ey * py + ez * pz;
            double f = ex * d.x + ey * d.y + ez * d.z;
            if (Math.abs(f) > 1e-12) {
                double t1 = (-half[i] - e) / f, t2 = (half[i] - e) / f;
                if (t1 > t2) { double tt = t1; t1 = t2; t2 = tt; }
                if (t1 > tmin) tmin = t1;
                if (t2 < tmax) tmax = t2;
                if (tmin > tmax || tmax < 0) return -1;
            } else if (-e - half[i] > 0 || -e + half[i] < 0) {
                return -1;
            }
        }
        return tmin >= 0 ? tmin : 0;
    }

    /** a representative point (for effects) */
    public Vec3 center() {
        return box ? new Vec3(cx, cy, cz) : new Vec3((ax + bx) / 2, (ay + by) / 2, (az + bz) / 2);
    }
}
