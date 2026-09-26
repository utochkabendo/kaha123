package dev.csarsenal.ballistics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Walks a bullet ray through the voxel grid (Amanatides &amp; Woo) and collects the solid "walls" it crosses,
 * with the exact entry/exit distances inside every block's collision shape. Shared by client and server so both
 * compute identical penetration.
 */
public final class BulletTrace {
    public static final class Wall {
        public double enter, exit;
        public BlockPos pos;
        public Direction face;
        public Vec3 enterPoint;
        public Material material;
        public final List<BlockPos> blocks = new ArrayList<>(2);
        public BlockState state;

        public double thickness() {
            return exit - enter;
        }
    }

    /**
     * @param maxWalls stop after this many walls (CS: 4 penetrations)
     */
    public static List<Wall> walls(BlockGetter level, Vec3 from, Vec3 dir, double maxDist, int maxWalls) {
        List<Wall> out = new ArrayList<>(4);
        double dx = dir.x, dy = dir.y, dz = dir.z;
        int x = floor(from.x), y = floor(from.y), z = floor(from.z);
        int sx = dx > 0 ? 1 : (dx < 0 ? -1 : 0), sy = dy > 0 ? 1 : (dy < 0 ? -1 : 0), sz = dz > 0 ? 1 : (dz < 0 ? -1 : 0);
        double tdx = sx != 0 ? Math.abs(1.0 / dx) : Double.MAX_VALUE;
        double tdy = sy != 0 ? Math.abs(1.0 / dy) : Double.MAX_VALUE;
        double tdz = sz != 0 ? Math.abs(1.0 / dz) : Double.MAX_VALUE;
        double tmx = sx > 0 ? (x + 1 - from.x) * tdx : sx < 0 ? (from.x - x) * tdx : Double.MAX_VALUE;
        double tmy = sy > 0 ? (y + 1 - from.y) * tdy : sy < 0 ? (from.y - y) * tdy : Double.MAX_VALUE;
        double tmz = sz > 0 ? (z + 1 - from.z) * tdz : sz < 0 ? (from.z - z) * tdz : Double.MAX_VALUE;
        double t = 0;
        Wall cur = null;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        int guard = 0;
        while (t <= maxDist && guard++ < 4096) {
            mp.set(x, y, z);
            boolean skip = false;
            if (level instanceof Level l) {
                if (l.isOutsideBuildHeight(mp)) {
                    if ((sy >= 0 && y >= l.getMaxBuildHeight()) || (sy <= 0 && y < l.getMinBuildHeight())) break;
                    skip = true;
                } else if (!l.isLoaded(mp)) {
                    break;
                }
            }
            if (!skip) {
                BlockState st = level.getBlockState(mp);
                if (!st.isAir()) {
                    VoxelShape shape = st.getCollisionShape(level, mp, CollisionContext.empty());
                    if (!shape.isEmpty()) {
                        double best0 = Double.MAX_VALUE, best1 = -1;
                        Direction bestFace = null;
                        for (AABB box : shape.toAabbs()) {
                            double[] hit = slab(from, dx, dy, dz, box.minX + x, box.minY + y, box.minZ + z, box.maxX + x, box.maxY + y, box.maxZ + z);
                            if (hit != null && hit[1] >= 0) {
                                if (hit[0] < best0) {
                                    best0 = hit[0];
                                    bestFace = faceOf((int) hit[2], sx, sy, sz);
                                }
                                best1 = Math.max(best1, hit[1]);
                            }
                        }
                        if (best1 >= 0 && best0 <= maxDist) {
                            best0 = Math.max(0, best0);
                            if (cur != null && best0 - cur.exit < 0.02) {
                                cur.exit = Math.max(cur.exit, best1);
                                cur.blocks.add(mp.immutable());
                                Material m = Material.of(level, mp, st);
                                if (m == Material.IMPENETRABLE) cur.material = m;
                            } else {
                                if (cur != null) {
                                    out.add(cur);
                                    if (out.size() >= maxWalls || cur.material == Material.IMPENETRABLE) return out;
                                }
                                cur = new Wall();
                                cur.enter = best0;
                                cur.exit = best1;
                                cur.pos = mp.immutable();
                                cur.face = bestFace;
                                cur.enterPoint = from.add(dx * best0, dy * best0, dz * best0);
                                cur.material = Material.of(level, mp, st);
                                cur.state = st;
                                cur.blocks.add(cur.pos);
                            }
                        }
                    }
                }
            }
            // step
            if (tmx < tmy && tmx < tmz) {
                t = tmx;
                tmx += tdx;
                x += sx;
            } else if (tmy < tmz) {
                t = tmy;
                tmy += tdy;
                y += sy;
            } else {
                t = tmz;
                tmz += tdz;
                z += sz;
            }
            if (cur != null && t - cur.exit > 0.02) {
                out.add(cur);
                if (out.size() >= maxWalls || cur.material == Material.IMPENETRABLE) return out;
                cur = null;
            }
        }
        if (cur != null) out.add(cur);
        return out;
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    private static Direction faceOf(int axis, int sx, int sy, int sz) {
        return switch (axis) {
            case 0 -> sx > 0 ? Direction.WEST : Direction.EAST;
            case 1 -> sy > 0 ? Direction.DOWN : Direction.UP;
            default -> sz > 0 ? Direction.NORTH : Direction.SOUTH;
        };
    }

    /** ray vs AABB: {tEnter, tExit, axisOfEntry} or null */
    private static double[] slab(Vec3 o, double dx, double dy, double dz, double x0, double y0, double z0, double x1, double y1, double z1) {
        double tmin = -1e30, tmax = 1e30;
        int axis = 0;
        double[] os = {o.x, o.y, o.z}, ds = {dx, dy, dz}, mn = {x0, y0, z0}, mx = {x1, y1, z1};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(ds[i]) < 1e-12) {
                if (os[i] < mn[i] || os[i] > mx[i]) return null;
                continue;
            }
            double a = (mn[i] - os[i]) / ds[i], b = (mx[i] - os[i]) / ds[i];
            if (a > b) {
                double tt = a;
                a = b;
                b = tt;
            }
            if (a > tmin) {
                tmin = a;
                axis = i;
            }
            if (b < tmax) tmax = b;
            if (tmin > tmax) return null;
        }
        if (tmax < 0) return null;
        return new double[]{tmin, tmax, axis};
    }

    private BulletTrace() {
    }
}
