package dev.csarsenal.client.fx;

import dev.csarsenal.entity.GrenadeEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CS2 style volumetric smoke: the smoke flood-fills the reachable air around the grenade (it flows around corners
 * and does not go through walls), is built from big lit puffs, and can be shot through (bullets punch
 * short-lived holes) or blown away for a moment by an HE grenade.
 */
public final class SmokeManager {
    public static final class Cloud {
        final int entity;
        final Vec3 center;
        final List<BlockPos> cells;
        final List<CsParticles.Smoke> particles = new ArrayList<>();
        int spawned;
        int age;
        boolean dead;

        Cloud(int entity, Vec3 center, List<BlockPos> cells) {
            this.entity = entity;
            this.center = center;
            this.cells = cells;
        }
    }

    private record Hole(Vec3 a, Vec3 b, long until) {
    }

    private record Clear(Vec3 c, double r, long until, long start) {
    }

    private static final Map<Integer, Cloud> CLOUDS = new HashMap<>();
    private static final List<Hole> HOLES = new ArrayList<>();
    private static final List<Clear> CLEARS = new ArrayList<>();
    private static final int MAX_CELLS = 300;

    public static void start(GrenadeEntity g) {
        if (CLOUDS.containsKey(g.getId())) return;
        ClientLevel level = (ClientLevel) g.level();
        Vec3 c = g.position().add(0, 0.3, 0);
        BlockPos start = BlockPos.containing(c);
        if (!level.getBlockState(start).getCollisionShape(level, start).isEmpty()) start = start.above();
        List<BlockPos> cells = new ArrayList<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        q.add(start);
        seen.add(start);
        double r = GrenadeEntity.SMOKE_RADIUS;
        Vec3 center = Vec3.atCenterOf(start);
        while (!q.isEmpty() && cells.size() < MAX_CELLS) {
            BlockPos p = q.poll();
            cells.add(p);
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (seen.contains(n)) continue;
                seen.add(n);
                Vec3 nc = Vec3.atCenterOf(n);
                double dy = (nc.y - center.y) * (nc.y > center.y ? 1.35 : 1.8);
                double dist = Math.sqrt(Math.pow(nc.x - center.x, 2) + dy * dy + Math.pow(nc.z - center.z, 2));
                if (dist > r) continue;
                if (!level.getBlockState(n).getCollisionShape(level, n).isEmpty()) continue;
                q.add(n);
            }
        }
        // spawn order: inner cells first (the smoke blooms outwards)
        cells.sort((a, b) -> Double.compare(Vec3.atCenterOf(a).distanceToSqr(center), Vec3.atCenterOf(b).distanceToSqr(center)));
        CLOUDS.put(g.getId(), new Cloud(g.getId(), center, cells));
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null) {
            CLOUDS.clear();
            return;
        }
        long now = System.currentTimeMillis();
        HOLES.removeIf(h -> h.until < now);
        CLEARS.removeIf(c -> c.until < now);
        Iterator<Cloud> it = CLOUDS.values().iterator();
        while (it.hasNext()) {
            Cloud c = it.next();
            Entity e = level.getEntity(c.entity);
            c.age++;
            if (e == null || !e.isAlive()) {
                c.dead = true;
            }
            if (c.dead && c.age > 20 * 3) {
                boolean alive = false;
                for (CsParticles.Smoke s : c.particles) alive |= s.isAlive();
                if (!alive) it.remove();
                continue;
            }
            if (c.dead) continue;
            int remaining = GrenadeEntity.SMOKE_TICKS + GrenadeEntity.SMOKE_FADE / 2 - c.age;
            // bloom over ~0.8 s
            int target = Math.min(c.cells.size(), (int) (c.cells.size() * Mth.clamp(c.age / 16f, 0.08f, 1f)) + 1);
            while (c.spawned < target) {
                BlockPos p = c.cells.get(c.spawned++);
                RandomSourceHolder.spawn(level, c, p, remaining);
            }
        }
    }

    private static final class RandomSourceHolder {
        static final net.minecraft.util.RandomSource R = net.minecraft.util.RandomSource.create();

        static void spawn(ClientLevel level, Cloud c, BlockPos p, int life) {
            int n = 1 + (R.nextFloat() < 0.35f ? 1 : 0);
            for (int i = 0; i < n; i++) {
                double x = p.getX() + 0.15 + R.nextDouble() * 0.7, y = p.getY() + 0.15 + R.nextDouble() * 0.7, z = p.getZ() + 0.15 + R.nextDouble() * 0.7;
                CsParticles.Smoke s = new CsParticles.Smoke(level, x, y, z, c, Math.max(40, life + R.nextInt(30)), 1.0f + R.nextFloat() * 0.45f);
                Minecraft.getInstance().particleEngine.add(s);
                c.particles.add(s);
            }
        }
    }

    /** bullets punch a hole through smoke for a short moment */
    public static void addRay(Vec3 a, Vec3 b) {
        if (CLOUDS.isEmpty()) return;
        for (Cloud c : CLOUDS.values()) {
            if (c.dead) continue;
            Vec3 ab = b.subtract(a);
            double t = Mth.clamp(c.center.subtract(a).dot(ab) / Math.max(1e-6, ab.lengthSqr()), 0, 1);
            if (a.add(ab.scale(t)).distanceToSqr(c.center) < (GrenadeEntity.SMOKE_RADIUS + 1) * (GrenadeEntity.SMOKE_RADIUS + 1)) {
                HOLES.add(new Hole(a, b, System.currentTimeMillis() + 900));
                if (HOLES.size() > 64) HOLES.remove(0);
                return;
            }
        }
    }

    /** HE grenade blows the smoke away for ~1.5 s */
    public static void clear(Vec3 pos, double radius) {
        CLEARS.add(new Clear(pos, radius, System.currentTimeMillis() + 1800, System.currentTimeMillis()));
        for (Cloud c : CLOUDS.values()) {
            for (CsParticles.Smoke s : c.particles) {
                double dx = s.homeX - pos.x, dy = s.homeY - pos.y, dz = s.homeZ - pos.z;
                double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (d < radius && d > 1e-3) s.push(dx / d, dy / d * 0.5, dz / d, 0.35 * (1 - d / radius));
            }
        }
    }

    public static float alphaAt(double x, double y, double z) {
        float a = 1f;
        long now = System.currentTimeMillis();
        for (Hole h : HOLES) {
            Vec3 ab = h.b.subtract(h.a);
            Vec3 p = new Vec3(x, y, z);
            double t = Mth.clamp(p.subtract(h.a).dot(ab) / Math.max(1e-6, ab.lengthSqr()), 0, 1);
            double d = h.a.add(ab.scale(t)).distanceTo(p);
            if (d < 0.9) {
                float rec = 1f - Mth.clamp((h.until - now) / 900f, 0f, 1f);
                float edge = (float) (d / 0.9);
                a = Math.min(a, edge + (1f - edge) * rec * rec);
            }
        }
        for (Clear c : CLEARS) {
            double d = Math.sqrt((x - c.c.x) * (x - c.c.x) + (y - c.c.y) * (y - c.c.y) + (z - c.c.z) * (z - c.c.z));
            if (d < c.r) {
                float life = (c.until - now) / 1800f;
                a = Math.min(a, 1f - (float) Mth.clamp(life * 1.4, 0, 1) * (float) (1 - d / c.r));
            }
        }
        return Mth.clamp(a, 0f, 1f);
    }

    public static boolean inAnySmoke(Vec3 p) {
        for (Cloud c : CLOUDS.values()) {
            if (!c.dead && c.center.distanceToSqr(p) < GrenadeEntity.SMOKE_RADIUS * GrenadeEntity.SMOKE_RADIUS) return true;
        }
        return false;
    }

    public static void reset() {
        CLOUDS.clear();
        HOLES.clear();
        CLEARS.clear();
    }

    private SmokeManager() {
    }
}
