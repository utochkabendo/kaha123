package dev.csarsenal.client.render;

import dev.csarsenal.anim.HitShape;
import dev.csarsenal.anim.Hitboxes;
import dev.csarsenal.anim.McPose;
import dev.csarsenal.anim.PoseInput;
import dev.csarsenal.anim.WeaponGeometry;
import dev.csarsenal.client.weapon.ClientWeapon;
import dev.csarsenal.network.Packets;
import dev.csarsenal.server.ServerWeapons;
import dev.csarsenal.weapon.CsItem;
import dev.csarsenal.weapon.WeaponDef;
import dev.csarsenal.weapon.Weapons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per player CS pose state (reload / shot / draw / throw / knife timers) and the {@link McPose} built from it.
 * The same pose drives the vanilla player model (mixin), the weapon render layer and the hitboxes.
 */
public final class AgentPose implements Hitboxes.PlayerShapes {
    public static final AgentPose INSTANCE = new AgentPose();

    public static final class State {
        public double reloadStart = -100, reloadDur = 1, lastShot = -100, drawStart = -100, drawDur = 1, throwStart = -100, meleeStart = -100;
        public boolean meleeHeavy, silenced;
        public Item lastItem;
        long frame = -1;
        float pt = -1;
        public final McPose pose = new McPose();
        final PoseInput in = new PoseInput();
    }

    private static final Map<Integer, State> STATES = new HashMap<>();
    private static long frameId;

    public static void newFrame() {
        frameId++;
        if (frameId % 600 == 0) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) STATES.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        }
    }

    public static State state(Player p) {
        return STATES.computeIfAbsent(p.getId(), k -> new State());
    }

    static double now() {
        return System.nanoTime() / 1e9;
    }

    public static void onAnim(Packets.Anim a) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(a.entity()) instanceof Player p)) return;
        State s = state(p);
        WeaponDef d = Weapons.byIndex(a.weapon());
        double t = now();
        switch (a.anim()) {
            case Packets.Anim.RELOAD -> {
                s.reloadStart = t;
                s.reloadDur = d == null ? 2 : d.reloadStyle == WeaponDef.ReloadStyle.SHOTGUN_SHELLS ? ServerWeapons.SHELL_START + d.reloadTime * Math.max(1, d.magSize / 2) : d.reloadTime;
            }
            case Packets.Anim.RELOAD_CANCEL -> s.reloadStart = -100;
            case Packets.Anim.DRAW -> {
                s.drawStart = t;
                s.drawDur = d != null ? Math.max(0.3, d.deployTime) : 0.8;
            }
            case Packets.Anim.THROW -> s.throwStart = t;
            case Packets.Anim.KNIFE, Packets.Anim.KNIFE_HEAVY -> {
                s.meleeStart = t;
                s.meleeHeavy = a.anim() == Packets.Anim.KNIFE_HEAVY;
            }
            default -> {
            }
        }
    }

    public static void onShot(Player p, boolean silenced) {
        State s = state(p);
        s.lastShot = now();
        s.silenced = silenced;
    }

    /** vanilla poses we do not override */
    public static boolean special(Player p) {
        return p.isSleeping() || p.isFallFlying() || p.isVisuallySwimming() || p.isAutoSpinAttack() || p.isPassenger() || p.getPose() == Pose.SWIMMING;
    }

    public static float partialTick() {
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
    }

    public static McPose compute(Player p, float pt) {
        State s = state(p);
        if (s.frame == frameId && s.pt == pt) return s.pose;
        double t = now();
        s.frame = frameId;
        s.pt = pt;
        PoseInput in = s.in.reset();
        float bodyYaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
        float headYaw = Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot);
        float yawRel = Mth.wrapDegrees(headYaw - bodyYaw);
        float pitch = Mth.lerp(pt, p.xRotO, p.getXRot());
        in.walkPos = p.walkAnimation.position(pt);
        in.walkAmount = Math.min(1f, p.walkAnimation.speed(pt));
        in.air = p.onGround() ? 0 : 1;
        WeaponDef def = CsItem.defOf(p.getMainHandItem());
        Item item = p.getMainHandItem().getItem();
        boolean local = p == Minecraft.getInstance().player;
        if (item != s.lastItem) {
            s.lastItem = item;
            if (!local && def != null) {
                s.drawStart = t;
                s.drawDur = Math.max(0.3, def.deployTime);
            }
            s.reloadStart = -100;
        }
        if (def != null && !special(p)) {
            in.hold = def.hold;
            in.geo = WeaponGeometry.get(def.mesh);
            if (local) {
                ClientWeapon w = ClientWeapon.INSTANCE;
                in.reload = (float) w.reloadProgress();
                in.fire = (float) Math.exp(-(w.now - w.lastShot) / 0.08);
                in.deploy = (float) Mth.clamp((w.now - w.drawStart) / w.drawDuration, 0, 1);
                in.throwAnim = w.now - w.throwStart < ClientWeapon.THROW_TIME ? (float) ((w.now - w.throwStart) / ClientWeapon.THROW_TIME) : -1;
                in.melee = w.now - w.knifeStart < (w.knifeHeavy ? 1.0 : 0.4) ? (float) ((w.now - w.knifeStart) / (w.knifeHeavy ? 1.0 : 0.4)) : -1;
                in.meleeHeavy = w.knifeHeavy;
                in.scoped = w.isScoped();
            } else {
                in.reload = t - s.reloadStart < s.reloadDur ? (float) ((t - s.reloadStart) / s.reloadDur) : -1;
                in.fire = (float) Math.exp(-(t - s.lastShot) / 0.08);
                in.deploy = (float) Mth.clamp((t - s.drawStart) / s.drawDur, 0, 1);
                in.throwAnim = t - s.throwStart < ClientWeapon.THROW_TIME ? (float) ((t - s.throwStart) / ClientWeapon.THROW_TIME) : -1;
                double md = s.meleeHeavy ? 1.0 : 0.4;
                in.melee = t - s.meleeStart < md ? (float) ((t - s.meleeStart) / md) : -1;
                in.meleeHeavy = s.meleeHeavy;
            }
        }
        s.pose.compute(in, p.isCrouching(), Mth.clamp(yawRel, -85, 85), pitch);
        return s.pose;
    }

    /** called from the model mixin after vanilla setupAnim */
    public static void applyToModel(HumanoidModel<?> m, Player p) {
        m.rightArm.yScale = 1;
        m.leftArm.yScale = 1;
        if (m instanceof net.minecraft.client.model.PlayerModel<?> pm0) {
            pm0.rightSleeve.yScale = 1;
            pm0.leftSleeve.yScale = 1;
        }
        if (special(p) || !(p.getMainHandItem().getItem() instanceof CsItem)) return;
        McPose pose = compute(p, partialTick());
        if (!pose.armsPosed) return;
        copy(pose.head, m.head);
        copy(pose.body, m.body);
        copy(pose.rightArm, m.rightArm);
        copy(pose.leftArm, m.leftArm);
        copy(pose.rightLeg, m.rightLeg);
        copy(pose.leftLeg, m.leftLeg);
        m.hat.copyFrom(m.head);
        if (m instanceof net.minecraft.client.model.PlayerModel<?> pm) {
            pm.rightSleeve.copyFrom(m.rightArm);
            pm.leftSleeve.copyFrom(m.leftArm);
            pm.rightPants.copyFrom(m.rightLeg);
            pm.leftPants.copyFrom(m.leftLeg);
            pm.jacket.copyFrom(m.body);
        }
    }

    private static void copy(McPose.Part a, ModelPart b) {
        b.x = a.x;
        b.y = a.y;
        b.z = a.z;
        b.xRot = a.xRot;
        b.yRot = a.yRot;
        b.zRot = a.zRot;
        b.yScale = a.yScale;
    }

    public static Matrix4f modelToWorld(Player p, float pt) {
        return McPose.modelToWorld(Mth.lerp(pt, p.xo, p.getX()), Mth.lerp(pt, p.yo, p.getY()), Mth.lerp(pt, p.zo, p.getZ()),
                Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot), p.isCrouching(), 0.9375f);
    }

    @Override
    public List<HitShape> shapes(Player p, float pt) {
        McPose pose = compute(p, pt);
        if (!pose.armsPosed) {
            // vanilla pose (no CS item): still use the part boxes
            return pose.shapes(modelToWorld(p, pt));
        }
        return pose.shapes(modelToWorld(p, pt));
    }

    /** world position of the muzzle of a player's gun (third person) */
    public static Vec3 muzzleWorld(Player p, float pt) {
        WeaponDef def = CsItem.defOf(p.getMainHandItem());
        if (def == null || !def.isGun()) return null;
        McPose pose = compute(p, pt);
        if (!pose.hasGun) return null;
        WeaponGeometry g = WeaponGeometry.get(def.mesh);
        Vector3f m = modelToWorld(p, pt).mul(pose.gun, new Matrix4f()).transformPosition(new Vector3f(g.muzzle));
        return new Vec3(m.x, m.y, m.z);
    }

    public static void reset() {
        STATES.clear();
    }

    private AgentPose() {
    }
}
