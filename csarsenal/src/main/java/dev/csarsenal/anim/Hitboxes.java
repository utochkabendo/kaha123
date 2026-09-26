package dev.csarsenal.anim;

import dev.csarsenal.ballistics.HitGroup;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Per body part hit volumes for any entity.
 * Players: taken from the same {@link Skeleton} that the operator model is rendered with.
 * Humanoid mobs: the Minecraft humanoid model boxes. Anything else: a head sphere around the eyes + body.
 */
public final class Hitboxes {
    /** Supplied by the client so hit detection uses the exact rendered pose (anim state lives client side). */
    public interface PlayerPoser {
        Skeleton pose(Player p, float partialTick);
    }

    public static PlayerPoser poser;
    private static final ThreadLocal<Skeleton> SERVER_SKELETON = ThreadLocal.withInitial(Skeleton::new);
    private static final ThreadLocal<PoseInput> SERVER_INPUT = ThreadLocal.withInitial(PoseInput::new);

    public static Matrix4f world(double x, double y, double z, float bodyYaw) {
        return new Matrix4f().translate((float) x, (float) y, (float) z).rotateY(-bodyYaw * Mth.DEG_TO_RAD);
    }

    public static List<HitShape> forEntity(Entity e, float pt) {
        double x = Mth.lerp(pt, e.xo, e.getX()), y = Mth.lerp(pt, e.yo, e.getY()), z = Mth.lerp(pt, e.zo, e.getZ());
        if (e instanceof Player p) {
            Skeleton sk;
            if (poser != null && p.level().isClientSide) {
                sk = poser.pose(p, pt);
            } else {
                PoseInput in = SERVER_INPUT.get().reset();
                in.pitch = p.getXRot();
                in.yawRel = Mth.wrapDegrees(p.getYHeadRot() - p.yBodyRot);
                in.crouch = p.getPose() == Pose.CROUCHING ? 1 : 0;
                sk = SERVER_SKELETON.get().compute(in);
            }
            float by = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
            return fromSkeleton(sk, world(x, y, z, by), p.getBbHeight() / (p.getPose() == Pose.CROUCHING ? 1.35f : 1.8f));
        }
        if (e instanceof LivingEntity le && isHumanoid(le)) {
            return humanoid(le, x, y, z, pt);
        }
        return generic(e, x, y, z, pt);
    }

    public static boolean isHumanoid(LivingEntity e) {
        return e instanceof Zombie || e instanceof AbstractSkeleton || e instanceof AbstractIllager || e instanceof AbstractPiglin
                || e instanceof AbstractVillager || e instanceof Witch || e instanceof ArmorStand;
    }

    public static List<HitShape> fromSkeleton(Skeleton sk, Matrix4f world, float scaleHint) {
        List<HitShape> out = new ArrayList<>(20);
        Matrix4f m = new Matrix4f();
        mul(world, sk.bones[Skeleton.HEAD], m);
        out.add(HitShape.capsule(HitGroup.HEAD, m, 0, 0.06f, 0.015f, 0, 0.155f, 0.0f, 0.088f));
        mul(world, sk.bones[Skeleton.NECK], m);
        out.add(HitShape.capsule(HitGroup.HEAD, m, 0, -0.01f, 0, 0, 0.05f, 0.005f, 0.052f));
        mul(world, sk.bones[Skeleton.CHEST], m);
        out.add(HitShape.box(HitGroup.CHEST, m, 0, 0.10f, 0.012f, 0.175f, 0.12f, 0.12f));
        mul(world, sk.bones[Skeleton.SPINE], m);
        out.add(HitShape.box(HitGroup.STOMACH, m, 0, 0.10f, 0.004f, 0.155f, 0.105f, 0.105f));
        mul(world, sk.bones[Skeleton.PELVIS], m);
        out.add(HitShape.box(HitGroup.STOMACH, m, 0, -0.03f, 0, 0.165f, 0.105f, 0.105f));
        for (int side = 0; side < 2; side++) {
            boolean l = side == 0;
            HitGroup arm = l ? HitGroup.LEFT_ARM : HitGroup.RIGHT_ARM;
            HitGroup leg = l ? HitGroup.LEFT_LEG : HitGroup.RIGHT_LEG;
            mul(world, sk.bones[l ? Skeleton.UPPERARM_L : Skeleton.UPPERARM_R], m);
            out.add(HitShape.capsule(arm, m, 0, 0, 0, 0, -Skeleton.UPPER_ARM, 0, 0.055f));
            mul(world, sk.bones[l ? Skeleton.FOREARM_L : Skeleton.FOREARM_R], m);
            out.add(HitShape.capsule(arm, m, 0, 0, 0, 0, -Skeleton.FOREARM, 0, 0.043f));
            mul(world, sk.bones[l ? Skeleton.HAND_L : Skeleton.HAND_R], m);
            out.add(HitShape.capsule(arm, m, 0, -0.03f, 0, 0, -0.085f, 0, 0.04f));
            mul(world, sk.bones[l ? Skeleton.THIGH_L : Skeleton.THIGH_R], m);
            out.add(HitShape.capsule(leg, m, 0, 0, 0, 0, -Skeleton.THIGH, 0, 0.08f));
            mul(world, sk.bones[l ? Skeleton.SHIN_L : Skeleton.SHIN_R], m);
            out.add(HitShape.capsule(leg, m, 0, 0, 0, 0, -Skeleton.SHIN, 0, 0.058f));
            mul(world, sk.bones[l ? Skeleton.FOOT_L : Skeleton.FOOT_R], m);
            out.add(HitShape.box(leg, m, 0, -0.04f, 0.06f, 0.05f, 0.04f, 0.12f));
        }
        return out;
    }

    private static void mul(Matrix4f a, Matrix4f b, Matrix4f out) {
        a.mul(b, out);
    }

    /** Minecraft HumanoidModel proportions, scaled to the entity height. */
    public static List<HitShape> humanoid(LivingEntity e, double x, double y, double z, float pt) {
        float s = e.getBbHeight() / 1.95f;
        float by = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot);
        float hy = Mth.rotLerp(pt, e.yHeadRotO, e.yHeadRot);
        float hp = Mth.lerp(pt, e.xRotO, e.getXRot());
        Matrix4f body = world(x, y, z, by).scale(s);
        Matrix4f head = world(x, y, z, hy).scale(s).translate(0, 1.5f, 0).rotateX(hp * Mth.DEG_TO_RAD);
        List<HitShape> out = new ArrayList<>(8);
        out.add(HitShape.box(HitGroup.HEAD, head, 0, 0.25f, 0, 0.25f * s, 0.25f * s, 0.25f * s));
        out.add(HitShape.box(HitGroup.CHEST, body, 0, 1.3125f, 0, 0.25f * s, 0.1875f * s, 0.125f * s));
        out.add(HitShape.box(HitGroup.STOMACH, body, 0, 0.9375f, 0, 0.25f * s, 0.1875f * s, 0.125f * s));
        out.add(HitShape.box(HitGroup.LEFT_ARM, body, 0.375f, 1.125f, 0, 0.125f * s, 0.375f * s, 0.125f * s));
        out.add(HitShape.box(HitGroup.RIGHT_ARM, body, -0.375f, 1.125f, 0, 0.125f * s, 0.375f * s, 0.125f * s));
        out.add(HitShape.box(HitGroup.LEFT_LEG, body, 0.125f, 0.375f, 0, 0.125f * s, 0.375f * s, 0.125f * s));
        out.add(HitShape.box(HitGroup.RIGHT_LEG, body, -0.125f, 0.375f, 0, 0.125f * s, 0.375f * s, 0.125f * s));
        return out;
    }

    public static List<HitShape> generic(Entity e, double x, double y, double z, float pt) {
        List<HitShape> out = new ArrayList<>(2);
        AABB bb = e.getBoundingBox().move(x - e.getX(), y - e.getY(), z - e.getZ());
        if (e instanceof LivingEntity le) {
            float w = e.getBbWidth(), h = e.getBbHeight();
            double r = Mth.clamp(Math.min(w, h) * 0.32, 0.1, 0.9);
            float hy = Mth.rotLerp(pt, le.yHeadRotO, le.yHeadRot);
            float hp = Mth.lerp(pt, le.xRotO, le.getXRot());
            Vec3 look = Vec3.directionFromRotation(hp, hy);
            Vec3 eye = new Vec3(x, y + e.getEyeHeight(), z);
            Vec3 c = eye.add(look.x * w * 0.3, 0, look.z * w * 0.3);
            out.add(HitShape.capsule(HitGroup.HEAD, c, c.add(0, 0.01, 0), r));
            out.add(HitShape.aabb(HitGroup.CHEST, bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ));
        } else {
            out.add(HitShape.aabb(HitGroup.GENERIC, bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ));
        }
        return out;
    }

    /** nearest hit of a ray with an entity's shapes: {distance, group ordinal} or null */
    public static double[] raycast(List<HitShape> shapes, Vec3 o, Vec3 d, double max) {
        double best = -1;
        int grp = 0;
        for (HitShape s : shapes) {
            double t = s.intersect(o, d);
            if (t >= 0 && t <= max && (best < 0 || t < best)) {
                best = t;
                grp = s.group.ordinal();
            }
        }
        return best < 0 ? null : new double[]{best, grp};
    }

    private Hitboxes() {
    }
}
