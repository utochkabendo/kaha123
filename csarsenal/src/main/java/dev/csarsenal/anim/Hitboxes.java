package dev.csarsenal.anim;

import dev.csarsenal.ballistics.HitGroup;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
    public interface PlayerShapes {
        List<HitShape> shapes(Player p, float partialTick);
    }

    public static PlayerShapes poser;
    private static final ThreadLocal<McPose> SERVER_POSE = ThreadLocal.withInitial(McPose::new);
    private static final ThreadLocal<PoseInput> SERVER_INPUT = ThreadLocal.withInitial(PoseInput::new);

    public static Matrix4f world(double x, double y, double z, float bodyYaw) {
        return new Matrix4f().translate((float) x, (float) y, (float) z).rotateY(-bodyYaw * Mth.DEG_TO_RAD);
    }

    public static List<HitShape> forEntity(Entity e, float pt) {
        double x = Mth.lerp(pt, e.xo, e.getX()), y = Mth.lerp(pt, e.yo, e.getY()), z = Mth.lerp(pt, e.zo, e.getZ());
        if (e instanceof Player p) {
            if (poser != null && p.level().isClientSide) return poser.shapes(p, pt);
            PoseInput in = SERVER_INPUT.get().reset();
            McPose pose = SERVER_POSE.get().compute(in, p.isCrouching(), Mth.wrapDegrees(p.getYHeadRot() - p.yBodyRot), p.getXRot());
            return pose.shapes(McPose.modelToWorld(x, y, z, Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot), p.isCrouching(), 0.9375f));
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
