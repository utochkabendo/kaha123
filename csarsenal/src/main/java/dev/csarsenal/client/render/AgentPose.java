package dev.csarsenal.client.render;

import dev.csarsenal.anim.PoseInput;
import dev.csarsenal.anim.Skeleton;
import dev.csarsenal.anim.WeaponGeometry;
import dev.csarsenal.anim.Hitboxes;
import dev.csarsenal.client.move.CsMovement;
import dev.csarsenal.client.weapon.ClientWeapon;
import dev.csarsenal.network.Packets;
import dev.csarsenal.server.ServerWeapons;
import dev.csarsenal.weapon.CsItem;
import dev.csarsenal.weapon.WeaponDef;
import dev.csarsenal.weapon.Weapons;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/** Builds (and caches per frame) the skeleton pose of every player; shared by the renderer and the hit detection. */
public final class AgentPose implements Hitboxes.PlayerPoser {
    public static final AgentPose INSTANCE = new AgentPose();

    public static final class State {
        public double reloadStart = -100, reloadDur = 1, lastShot = -100, drawStart = -100, drawDur = 1, throwStart = -100, meleeStart = -100, inspectStart = -100;
        public boolean meleeHeavy, silenced;
        public float crouch, air;
        public Item lastItem;
        public int weaponIndex = -1;
        long frame = -1;
        float pt = -1;
        public final Skeleton sk = new Skeleton();
        final PoseInput in = new PoseInput();
        double lastUpdate;
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
        if (mc.level == null) return;
        if (!(mc.level.getEntity(a.entity()) instanceof Player p)) return;
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
            case Packets.Anim.INSPECT -> s.inspectStart = t;
            default -> {
            }
        }
    }

    public static void onShot(Player p, boolean silenced) {
        State s = state(p);
        s.lastShot = now();
        s.silenced = silenced;
    }

    @Override
    public Skeleton pose(Player p, float pt) {
        return compute(p, pt);
    }

    public static Skeleton compute(Player p, float pt) {
        State s = state(p);
        if (s.frame == frameId && s.pt == pt) return s.sk;
        double t = now();
        double dt = Mth.clamp(t - s.lastUpdate, 0, 0.1);
        s.lastUpdate = t;
        s.frame = frameId;
        s.pt = pt;
        PoseInput in = s.in.reset();
        float bodyYaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
        float headYaw = Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot);
        in.yawRel = Mth.wrapDegrees(headYaw - bodyYaw);
        in.pitch = Mth.lerp(pt, p.xRotO, p.getXRot());
        boolean crouching = p.getPose() == Pose.CROUCHING;
        s.crouch = (float) Mth.approach(s.crouch, crouching ? 1f : 0f, (float) (dt * 6));
        s.air = (float) Mth.approach(s.air, p.onGround() ? 0f : 1f, (float) (dt * 5));
        in.crouch = s.crouch;
        in.air = s.air;
        in.walkPos = p.walkAnimation.position(pt);
        in.walkAmount = Math.min(1f, p.walkAnimation.speed(pt));
        double dx = p.getX() - p.xo, dz = p.getZ() - p.zo;
        if (dx * dx + dz * dz > 1e-6) {
            float moveYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            in.moveAngle = Mth.wrapDegrees(moveYaw - bodyYaw);
        }
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
        if (def != null) {
            in.hold = def.hold;
            in.geo = WeaponGeometry.get(def.mesh);
            if (local) {
                ClientWeapon w = ClientWeapon.INSTANCE;
                in.reload = (float) w.reloadProgress();
                in.fire = (float) Math.exp(-(w.now - w.lastShot) / 0.08);
                in.deploy = (float) Mth.clamp((w.now - w.drawStart) / w.drawDuration, 0, 1);
                in.throwAnim = w.now - w.throwStart < ClientWeapon.THROW_TIME ? (float) ((w.now - w.throwStart) / ClientWeapon.THROW_TIME) : (w.pinPulled ? 0.2f : -1);
                in.melee = w.now - w.knifeStart < (w.knifeHeavy ? 1.0 : 0.4) ? (float) ((w.now - w.knifeStart) / (w.knifeHeavy ? 1.0 : 0.4)) : -1;
                in.scoped = w.isScoped();
                in.crouch = Math.max(in.crouch, CsMovement.INSTANCE.crouchAmount * 0.99f);
            } else {
                in.reload = t - s.reloadStart < s.reloadDur ? (float) ((t - s.reloadStart) / s.reloadDur) : -1;
                in.fire = (float) Math.exp(-(t - s.lastShot) / 0.08);
                in.deploy = (float) Mth.clamp((t - s.drawStart) / s.drawDur, 0, 1);
                in.throwAnim = t - s.throwStart < ClientWeapon.THROW_TIME ? (float) ((t - s.throwStart) / ClientWeapon.THROW_TIME) : -1;
                double md = s.meleeHeavy ? 1.0 : 0.4;
                in.melee = t - s.meleeStart < md ? (float) ((t - s.meleeStart) / md) : -1;
            }
        }
        s.sk.compute(in);
        return s.sk;
    }

    /** world position of the muzzle of a player's gun (third person) */
    public static Vec3 muzzleWorld(Player p, float pt) {
        WeaponDef def = CsItem.defOf(p.getMainHandItem());
        if (def == null || !def.isGun()) return null;
        Skeleton sk = compute(p, pt);
        if (!sk.hasGun) return null;
        WeaponGeometry g = WeaponGeometry.get(def.mesh);
        Vector3f m = sk.gun.transformPosition(new Vector3f(g.muzzle));
        float by = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
        float c = Mth.cos(-by), sn = Mth.sin(-by);
        double wx = m.x * c + m.z * sn, wz = -m.x * sn + m.z * c;
        return new Vec3(Mth.lerp(pt, p.xo, p.getX()) + wx, Mth.lerp(pt, p.yo, p.getY()) + m.y, Mth.lerp(pt, p.zo, p.getZ()) + wz);
    }

    public static void reset() {
        STATES.clear();
    }

    private AgentPose() {
    }
}
