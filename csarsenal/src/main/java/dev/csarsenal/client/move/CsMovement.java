package dev.csarsenal.client.move;

import dev.csarsenal.ballistics.Ballistics;
import dev.csarsenal.client.weapon.ClientWeapon;
import dev.csarsenal.server.CsMode;
import dev.csarsenal.server.CsMovementHook;
import dev.csarsenal.weapon.CsItem;
import dev.csarsenal.weapon.WeaponDef;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;

/**
 * Source / CS2 player movement for the local player: ground friction + acceleration (sv_friction 5.2,
 * sv_accelerate 5.5, sv_stopspeed 80), air acceleration with the 30 u/s wish cap (air strafing / bhop),
 * weapon dependent max speed, shift walk (52%), crouch (34%), stamina after jumps/landings, tagging,
 * 800 u/s^2 gravity and the 57 unit jump. Integrated in 3 sub-steps per tick with sub-tick input.
 */
public final class CsMovement implements CsMovementHook.Handler {
    public static final CsMovement INSTANCE = new CsMovement();

    public static final float SV_ACCELERATE = 5.5f, SV_AIRACCELERATE = 12f, SV_FRICTION = 5.2f, SV_STOPSPEED = 80f;
    public static final float AIR_WISH_CAP = 30f, GRAVITY = 800f, WALK_MUL = 0.52f, DUCK_MUL = 0.34f, MAX_VELOCITY = 3500f;
    public static final float STAMINA_MAX = 80f, STAMINA_JUMP = 0.08f, STAMINA_LAND = 0.05f, STAMINA_RECOVER = 60f;
    private static final double U = Ballistics.UNIT;
    private static final int SUBSTEPS = 3;

    /** current velocity in units/second (for inaccuracy / HUD) */
    public double vx, vy, vz;
    public boolean onGround = true;
    public float stamina;
    private float tagSlow;          // 0..1 slowdown from being shot, recovers
    private boolean wasOnGround = true;
    private double lastFallSpeed;
    public float crouchAmount;
    public boolean walking;
    public boolean active;
    public float landDip;

    public float horizontalSpeed() {
        return (float) Math.sqrt(vx * vx + vz * vz);
    }

    public void tag(float amount) {
        tagSlow = Math.max(tagSlow, Mth.clamp(amount, 0f, 0.9f));
    }

    private float maxSpeed(Player p) {
        WeaponDef d = CsItem.defOf(p.getMainHandItem());
        float max = d != null ? d.maxSpeed(ClientWeapon.INSTANCE.isScoped()) : 250f;
        double attr = p.getAttributeValue(Attributes.MOVEMENT_SPEED) / 0.1;
        max *= (float) Mth.clamp(attr, 0.1, 3.0);
        BlockPos below = BlockPos.containing(p.getX(), p.getY() - 0.5, p.getZ());
        float factor = p.level().getBlockState(below).getBlock().getSpeedFactor();
        float inside = p.level().getBlockState(p.blockPosition()).getBlock().getSpeedFactor();
        max *= Math.min(factor, inside);
        return Math.min(max, 320f);
    }

    @Override
    public boolean travel(Player p, Vec3 input) {
        active = false;
        if (!CsMode.active(p) || p.isInWater() || p.isInLava() || p.onClimbable() || p.isSwimming() || p.isFallFlying() || p.getAbilities().flying || p.isPassenger()) {
            Vec3 d = p.getDeltaMovement();
            vx = d.x * 20 / U;
            vy = d.y * 20 / U;
            vz = d.z * 20 / U;
            onGround = p.onGround();
            SubtickInput.steps(1, p.getYRot());
            return false;
        }
        active = true;
        Vec3 dm = p.getDeltaMovement();
        vx = dm.x * 20 / U;
        vy = dm.y * 20 / U;
        vz = dm.z * 20 / U;
        boolean ground = p.onGround() && vy <= 1e-3;
        if (ground && !wasOnGround) {
            // landing: stamina penalty proportional to fall speed
            stamina = Math.min(STAMINA_MAX, stamina + (float) (lastFallSpeed * STAMINA_LAND));
            landDip = (float) Mth.clamp(lastFallSpeed / 600.0, 0, 1);
        }
        if (!ground) lastFallSpeed = Math.max(0, -vy);
        float maxSpeed = maxSpeed(p);
        float dt = 0.05f / SUBSTEPS;
        SubtickInput.Step[] steps = SubtickInput.steps(SUBSTEPS, p.getYRot());
        double dispX = 0, dispY = 0, dispZ = 0;
        float frictionScale = 1f;
        {
            BlockPos below = BlockPos.containing(p.getX(), p.getY() - 0.2, p.getZ());
            float mcFriction = p.level().getBlockState(below).getFriction(p.level(), below, p);
            // MC 0.6 = normal ground, 0.98 = ice -> CS surface friction
            frictionScale = Mth.clamp((1f - mcFriction) / 0.4f, 0.05f, 2f);
        }
        boolean crouched = p.isCrouching();
        for (SubtickInput.Step s : steps) {
            float yr = s.yaw * Mth.DEG_TO_RAD;
            // forward (-sin yaw, cos yaw), right (-cos yaw, -sin yaw)
            double fx = -Mth.sin(yr), fz = Mth.cos(yr);
            double rx = -Mth.cos(yr), rz = -Mth.sin(yr);
            double wx = fx * s.forward * 450 + rx * s.side * 450;
            double wz = fz * s.forward * 450 + rz * s.side * 450;
            double wishspeed = Math.sqrt(wx * wx + wz * wz);
            double dirx = 0, dirz = 0;
            if (wishspeed > 1e-4) {
                dirx = wx / wishspeed;
                dirz = wz / wishspeed;
            }
            float cap = maxSpeed;
            if (crouched) cap *= DUCK_MUL;
            else if (s.walk > 0.5f) cap *= WALK_MUL;
            if (stamina > 0) cap *= Mth.clamp((100f - stamina) / 100f, 0.2f, 1f);
            cap *= 1f - tagSlow;
            wishspeed = Math.min(wishspeed, cap);

            if (ground) {
                // friction
                double speed = Math.sqrt(vx * vx + vz * vz);
                if (speed > 0.1) {
                    double control = Math.max(speed, SV_STOPSPEED);
                    double drop = control * SV_FRICTION * frictionScale * dt;
                    double ns = Math.max(0, speed - drop) / speed;
                    vx *= ns;
                    vz *= ns;
                } else {
                    vx = 0;
                    vz = 0;
                }
                // accelerate
                double cur = vx * dirx + vz * dirz;
                double add = wishspeed - cur;
                if (add > 0) {
                    double acc = Math.min(SV_ACCELERATE * dt * wishspeed * frictionScale, add);
                    vx += acc * dirx;
                    vz += acc * dirz;
                }
                vy = Math.min(vy, -2.0);
            } else {
                double wishspd = Math.min(wishspeed, AIR_WISH_CAP);
                double cur = vx * dirx + vz * dirz;
                double add = wishspd - cur;
                if (add > 0) {
                    double acc = Math.min(SV_AIRACCELERATE * wishspeed * dt, add);
                    vx += acc * dirx;
                    vz += acc * dirz;
                }
                vy -= GRAVITY * dt;
            }
            double hs = Math.sqrt(vx * vx + vz * vz);
            if (hs > MAX_VELOCITY) {
                vx *= MAX_VELOCITY / hs;
                vz *= MAX_VELOCITY / hs;
            }
            vy = Mth.clamp(vy, -MAX_VELOCITY, MAX_VELOCITY);
            dispX += vx * dt;
            dispY += vy * dt;
            dispZ += vz * dt;
            if (stamina > 0) stamina = Math.max(0, stamina - STAMINA_RECOVER * dt);
            tagSlow = Math.max(0, tagSlow - dt * 1.6f);
        }
        crouchAmount = Mth.approach(crouchAmount, crouched ? 1f : 0f, 0.05f / 0.2f);
        walking = steps[steps.length - 1].walk > 0.5f;
        if (landDip > 0) landDip = Math.max(0, landDip - 0.08f);

        p.setDeltaMovement(vx * U / 20, vy * U / 20, vz * U / 20);
        p.move(MoverType.SELF, new Vec3(dispX * U, dispY * U, dispZ * U));
        // collisions may have stopped us: resync velocity from the entity
        Vec3 after = p.getDeltaMovement();
        vx = after.x * 20 / U;
        vy = after.y * 20 / U;
        vz = after.z * 20 / U;
        if (p.onGround() && vy < 0) vy = 0;
        onGround = p.onGround();
        wasOnGround = onGround;
        float moved = (float) Math.sqrt(dispX * dispX + dispZ * dispZ) * (float) U;
        p.walkAnimation.update(Math.min(moved * 4f, 1f), 0.4f);
        return true;
    }

    @Override
    public boolean jump(Player p) {
        if (!CsMode.active(p) || p.isInWater() || p.isInLava()) return false;
        float scale = Mth.clamp((100f - stamina) / 100f, 0.3f, 1f);
        Vec3 d = p.getDeltaMovement();
        double jumpU = Ballistics.JUMP_IMPULSE * scale;
        p.setDeltaMovement(d.x, jumpU * U / 20, d.z);
        stamina = Math.min(STAMINA_MAX, stamina + (float) (Ballistics.JUMP_IMPULSE * STAMINA_JUMP));
        p.hasImpulse = true;
        CommonHooks.onLivingJump(p);
        wasOnGround = false;
        return true;
    }

    public void reset() {
        vx = vy = vz = 0;
        stamina = 0;
        tagSlow = 0;
    }
}
