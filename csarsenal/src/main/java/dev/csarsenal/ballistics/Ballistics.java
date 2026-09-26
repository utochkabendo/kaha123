package dev.csarsenal.ballistics;

import dev.csarsenal.weapon.WeaponDef;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** CS2 gunplay math shared by client and server. */
public final class Ballistics {
    /** blocks per Source unit (72 unit player == 1.8 block player) */
    public static final double UNIT = 0.025;
    public static final double JUMP_IMPULSE = 301.993377;   // sqrt(2 * 800 * 57)
    public static final int MAX_PENETRATIONS = 4;

    /**
     * CS weapon inaccuracy (radians of spread radius).
     *
     * @param speed       horizontal speed (units/s)
     * @param vertical    vertical speed (units/s)
     * @param crouch      0..1
     * @param penalty     accumulated firing inaccuracy (weapon script units)
     */
    public static float inaccuracy(WeaponDef d, boolean alt, boolean scoped, float speed, boolean onGround, float vertical, float crouch, float penalty) {
        float stand = alt ? d.inaccStandAlt : d.inaccStand;
        float crch = alt ? d.inaccCrouchAlt : d.inaccCrouch;
        float move = alt ? d.inaccMoveAlt : d.inaccMove;
        float acc = Mth.lerp(crouch, stand, crch);
        if (!onGround) {
            float sqrtMax = (float) Math.sqrt(JUMP_IMPULSE);
            float t = Mth.clamp((Mth.sqrt(Math.abs(vertical)) - sqrtMax * 0.25f) / (sqrtMax * 0.75f), 0f, 1f);
            acc += Mth.lerp(t, d.inaccJumpApex, d.inaccJump);
        } else {
            float max = d.maxSpeed(scoped);
            float t = Mth.clamp((speed - max * 0.34f) / (max * 0.95f - max * 0.34f), 0f, 1f);
            acc += t * move;
        }
        return (acc + penalty) * 0.001f;
    }

    public static float spread(WeaponDef d, boolean alt) {
        return (alt ? d.spreadAlt : d.spread) * 0.001f;
    }

    /** Forward vector for Minecraft yaw/pitch. */
    public static Vec3 forward(float yaw, float pitch) {
        return Vec3.directionFromRotation(pitch, yaw);
    }

    /**
     * CS spread: two random rings (inaccuracy + intrinsic spread) around the aim direction.
     * Deterministic per (seed, pellet) so the server can reproduce the pattern.
     */
    public static Vec3 spreadDir(float yaw, float pitch, float inaccuracy, float spread, long seed, int pellet, int pellets) {
        long s = seed * 0x9E3779B97F4A7C15L + pellet * 0xBF58476D1CE4E5B9L;
        float theta0 = rnd(s) * (float) (Math.PI * 2);
        float r0 = rnd(s + 1) * inaccuracy;
        float theta1 = rnd(s + 2) * (float) (Math.PI * 2);
        float r1 = rnd(s + 3) * spread;
        if (pellets > 1) {
            // shotguns: pellets fill the spread disc more evenly (CS uses a per-pellet ring)
            float ring = (pellet + rnd(s + 4)) / pellets;
            r1 = Mth.sqrt(ring) * spread;
            theta1 = (pellet * 2.39996323f) + rnd(s + 5) * 0.6f;
        }
        float x = Mth.cos(theta0) * r0 + Mth.cos(theta1) * r1;
        float y = Mth.sin(theta0) * r0 + Mth.sin(theta1) * r1;
        Vec3 f = forward(yaw, pitch);
        float yr = yaw * Mth.DEG_TO_RAD, pr = pitch * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yr), 0, -Mth.sin(yr));
        Vec3 up = right.cross(f).normalize();
        return f.add(right.scale(x)).add(up.scale(y)).normalize();
    }

    private static float rnd(long s) {
        s ^= s >>> 33;
        s *= 0xff51afd7ed558ccdL;
        s ^= s >>> 33;
        s *= 0xc4ceb9fe1a85ec53L;
        s ^= s >>> 33;
        return (s >>> 40) / (float) (1L << 24);
    }

    /** CS:GO HandleBulletPenetration: damage left after crossing one wall. */
    public static float damageAfterWall(float damage, BulletTrace.Wall w, float power) {
        if (w.material == Material.IMPENETRABLE) return 0;
        double thickness = w.thickness() / UNIT;
        if (thickness > 180) return 0;
        float penMod = 1f / Math.max(0.05f, w.material.penetration);
        float lostPct = w.material == Material.GLASS || w.material == Material.SOFT ? 0.05f : 0.16f;
        float lost = (float) Math.max(0, (penMod * thickness) / 24.0 + damage * lostPct + Math.max(3.75f / power, 0f) * 3f * penMod);
        return damage - lost;
    }

    /** range falloff: damage * rangeModifier ^ (distance / 500 units) */
    public static float rangeFalloff(WeaponDef d, double distanceBlocks) {
        return (float) Math.pow(d.rangeModifier, (distanceBlocks / UNIT) / 500.0);
    }

    /** damage carried by the bullet at distance t along the ray (walls crossed before t), 0 if stopped */
    public static float damageAt(WeaponDef d, java.util.List<BulletTrace.Wall> walls, double t, float entityPenalty) {
        float dmg = d.damage * entityPenalty;
        int n = 0;
        for (BulletTrace.Wall w : walls) {
            if (w.enter >= t) break;
            dmg = damageAfterWall(dmg, w, d.penetration);
            if (++n > MAX_PENETRATIONS || dmg < 1f) return 0;
        }
        return dmg * rangeFalloff(d, t);
    }

    /** distance at which the bullet stops (impenetrable wall / damage gone / range) */
    public static double stopDistance(WeaponDef d, java.util.List<BulletTrace.Wall> walls, double range) {
        float dmg = d.damage;
        int n = 0;
        for (BulletTrace.Wall w : walls) {
            if (w.enter > range) break;
            float after = damageAfterWall(dmg, w, d.penetration);
            if (after < 1f || ++n > MAX_PENETRATIONS) return w.enter;
            dmg = after;
        }
        return range;
    }

    /**
     * CS armor: kevlar absorbs part of the damage.
     *
     * @return {healthDamage, armorDamage}
     */
    public static float[] applyArmor(float damage, float armorPenetration, int armor, boolean covered) {
        if (armor <= 0 || !covered) return new float[]{damage, 0};
        float ratio = armorPenetration;         // CS: armor_ratio * 0.5
        float bonus = 0.5f;
        float health = damage * ratio;
        float armorDmg = (damage - health) * bonus;
        if (armorDmg > armor) {
            armorDmg = armor;
            health = damage - armorDmg / bonus;
        }
        return new float[]{health, armorDmg};
    }

    private Ballistics() {
    }
}
