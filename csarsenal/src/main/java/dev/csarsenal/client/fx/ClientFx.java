package dev.csarsenal.client.fx;

import dev.csarsenal.anim.WeaponGeometry;
import dev.csarsenal.ballistics.Ballistics;
import dev.csarsenal.ballistics.BulletTrace;
import dev.csarsenal.ballistics.Material;
import dev.csarsenal.client.render.AgentPose;
import dev.csarsenal.network.Packets;
import dev.csarsenal.registry.ModParticles;
import dev.csarsenal.weapon.WeaponDef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Spawns the client side effects of shots and explosions. */
public final class ClientFx {
    private static final RandomSource R = RandomSource.create();
    public static double muzzleFlashTime = -100;
    public static double cameraShake;

    /** visual + audio effects of one bullet path (walls precomputed by the caller) */
    public static void bullet(Level level, WeaponDef d, Vec3 eye, Vec3 dir, List<BulletTrace.Wall> walls, double stop, List<Packets.Hit> hits, boolean local,
                              boolean tracer, Entity shooter) {
        double range = d.range * Ballistics.UNIT;
        int n = 0;
        for (BulletTrace.Wall w : walls) {
            if (w.enter > stop + 1e-3 || n++ > Ballistics.MAX_PENETRATIONS) break;
            impact(level, w, w.enterPoint, w.face, true);
            if (w.exit < stop - 1e-3 && w.face != null) {
                Vec3 out = eye.add(dir.scale(w.exit));
                impact(level, w, out, w.face.getOpposite(), false);
            }
        }
        double end = stop;
        if (hits != null && !hits.isEmpty() && d.pellets <= 1) end = Math.min(end, hits.get(0).distance());
        Vec3 endPos = eye.add(dir.scale(Math.min(end, range)));
        SmokeManager.addRay(eye, endPos);
        if (tracer) {
            Vec3 muzzle = muzzleWorld(shooter, local, dir);
            WorldFx.tracer(muzzle, endPos, d.category == WeaponDef.Category.MACHINEGUN || d.category == WeaponDef.Category.SNIPER);
        }
        if (!local) whiz(eye, dir, end, shooter);
    }

    private static void impact(Level level, BulletTrace.Wall w, Vec3 pos, Direction face, boolean entry) {
        if (face == null) return;
        Vec3 n = Vec3.atLowerCornerOf(face.getNormal());
        String kind = switch (w.material) {
            case WOOD -> "wood";
            case METAL -> "metal";
            case GLASS -> "glass";
            case DIRT, SOFT -> "dirt";
            default -> "concrete";
        };
        WorldFx.decal(pos, face, kind, w.material == Material.GLASS ? 0.1f : 0.07f + R.nextFloat() * 0.015f, w.pos);
        if (w.state != null) {
            for (int i = 0; i < (entry ? 6 : 3); i++) {
                Vec3 v = n.scale(0.05 + R.nextDouble() * 0.12).add(R.nextGaussian() * 0.05, R.nextDouble() * 0.06, R.nextGaussian() * 0.05);
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, w.state), pos.x, pos.y, pos.z, v.x, v.y, v.z);
            }
        }
        for (int i = 0; i < 2; i++) {
            Vec3 v = n.scale(0.02 + R.nextDouble() * 0.03);
            level.addParticle(ModParticles.IMPACT.get(), pos.x + n.x * 0.05, pos.y + n.y * 0.05, pos.z + n.z * 0.05, v.x, v.y + 0.005, v.z);
        }
        if (w.material == Material.METAL || w.material == Material.IMPENETRABLE) {
            for (int i = 0; i < 6; i++) {
                Vec3 v = n.scale(0.1 + R.nextDouble() * 0.2).add(R.nextGaussian() * 0.12, R.nextGaussian() * 0.12, R.nextGaussian() * 0.12);
                level.addParticle(ModParticles.SPARK.get(), pos.x, pos.y, pos.z, v.x, v.y, v.z);
            }
        }
        if (entry) ClientSounds.at("impact." + (w.material == Material.IMPENETRABLE ? "concrete" : w.material.impactSound.equals("dirt") && w.material == Material.SOFT ? "dirt" : w.material.impactSound),
                pos.x, pos.y, pos.z, 0.55f, 1f);
    }

    /** world position of the muzzle of a shooter (first person approximated from the camera) */
    public static Vec3 muzzleWorld(Entity shooter, boolean local, Vec3 dir) {
        Minecraft mc = Minecraft.getInstance();
        if (shooter instanceof Player p) {
            if (local && mc.options.getCameraType().isFirstPerson()) {
                Vec3 eye = p.getEyePosition(mc.getTimer().getGameTimeDeltaPartialTick(true));
                Vec3 look = dir;
                Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
                Vec3 up = right.cross(look).normalize();
                return eye.add(look.scale(0.55)).add(right.scale(0.1)).add(up.scale(-0.09));
            }
            Vec3 m = AgentPose.muzzleWorld(p, mc.getTimer().getGameTimeDeltaPartialTick(true));
            if (m != null) return m;
            return p.getEyePosition().add(dir.scale(0.8)).add(0, -0.15, 0);
        }
        return shooter != null ? shooter.getEyePosition() : Vec3.ZERO;
    }

    /** near miss crack when a bullet passes close to the local player's head */
    private static void whiz(Vec3 o, Vec3 d, double len, Entity shooter) {
        LocalPlayer me = Minecraft.getInstance().player;
        if (me == null || me == shooter) return;
        Vec3 head = me.getEyePosition();
        double t = Mth.clamp(head.subtract(o).dot(d), 0, len);
        Vec3 cp = o.add(d.scale(t));
        double dist = cp.distanceTo(head);
        if (dist < 1.6 && t > 3 && t < len - 0.3) {
            ClientSounds.at("bullet.whiz", cp.x, cp.y, cp.z, 0.9f, 1f);
        }
    }

    /** local shot extras: viewmodel muzzle flash, shell ejection, smoke wisp */
    public static void localShot(WeaponDef d, boolean silenced) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        muzzleFlashTime = System.nanoTime() / 1e9;
        Vec3 look = p.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 up = right.cross(look).normalize();
        Vec3 eye = p.getEyePosition();
        if (d.category != WeaponDef.Category.TASER && !d.boltAction && d.category != WeaponDef.Category.SHOTGUN) {
            ejectShell(d, eye.add(look.scale(0.25)).add(right.scale(0.14)).add(up.scale(-0.1)), right, up, look, p.getYRot());
        }
        Vec3 muzzle = eye.add(look.scale(0.7)).add(right.scale(0.1)).add(up.scale(-0.08));
        if (!silenced) {
            for (int i = 0; i < 2; i++)
                p.level().addParticle(ModParticles.MUZZLE_SMOKE.get(), muzzle.x, muzzle.y, muzzle.z, look.x * 0.02, 0.01, look.z * 0.02);
        }
    }

    public static void ejectShell(WeaponDef d, Vec3 at, Vec3 right, Vec3 up, Vec3 fwd, float yaw) {
        String mesh = d.category == WeaponDef.Category.PISTOL || d.category == WeaponDef.Category.SMG ? "shell_pistol"
                : d.category == WeaponDef.Category.SHOTGUN ? "shell_shotgun" : "shell_rifle";
        Vec3 v = right.scale(0.09 + R.nextDouble() * 0.04).add(up.scale(0.07 + R.nextDouble() * 0.04)).add(fwd.scale(-0.02));
        Player p = Minecraft.getInstance().player;
        if (p != null) v = v.add(p.getDeltaMovement());
        WorldFx.shell(mesh, at, v, yaw + 90);
    }

    public static void knifeWall(Level level, BlockHitResult bh) {
        Vec3 n = Vec3.atLowerCornerOf(bh.getDirection().getNormal());
        Vec3 p = bh.getLocation();
        for (int i = 0; i < 5; i++) {
            Vec3 v = n.scale(0.1).add(R.nextGaussian() * 0.08, R.nextGaussian() * 0.08, R.nextGaussian() * 0.08);
            level.addParticle(ModParticles.SPARK.get(), p.x, p.y, p.z, v.x, v.y, v.z);
        }
        WorldFx.decal(p, bh.getDirection(), "metal", 0.03f, bh.getBlockPos());
    }

    /** explosions and other world effects broadcast by the server */
    public static void effect(Packets.Effect e) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return;
        Vec3 p = e.pos();
        double distToMe = mc.player != null ? mc.player.position().distanceTo(p) : 999;
        switch (e.kind()) {
            case Packets.Effect.HE -> {
                boolean big = e.arg() > 0;
                int n = big ? 26 : 6;
                for (int i = 0; i < n; i++) {
                    Vec3 v = new Vec3(R.nextGaussian(), R.nextDouble() * 0.8 + 0.2, R.nextGaussian()).normalize().scale(0.05 + R.nextDouble() * (big ? 0.35 : 0.1));
                    level.addParticle(ModParticles.FIREBALL.get(), p.x, p.y + 0.2, p.z, v.x, v.y, v.z);
                }
                for (int i = 0; i < (big ? 30 : 6); i++) {
                    Vec3 v = new Vec3(R.nextGaussian(), R.nextDouble(), R.nextGaussian()).normalize().scale(0.3 + R.nextDouble() * 0.5);
                    level.addParticle(ModParticles.SPARK.get(), p.x, p.y + 0.1, p.z, v.x, v.y, v.z);
                }
                for (int i = 0; i < (big ? 14 : 3); i++) {
                    Vec3 v = new Vec3(R.nextGaussian(), R.nextDouble() * 0.5, R.nextGaussian()).scale(0.06);
                    level.addParticle(ModParticles.SMOKE.get(), p.x, p.y + 0.4, p.z, v.x, v.y, v.z);
                }
                if (big) {
                    SmokeManager.clear(p, 5.5);
                }
            }
            case Packets.Effect.FLASH -> {
                for (int i = 0; i < 20; i++) {
                    Vec3 v = new Vec3(R.nextGaussian(), R.nextGaussian(), R.nextGaussian()).normalize().scale(0.3);
                    level.addParticle(ModParticles.SPARK.get(), p.x, p.y, p.z, v.x, v.y, v.z);
                }
                level.addParticle(ModParticles.FIREBALL.get(), p.x, p.y, p.z, 0, 0, 0);
            }
            case Packets.Effect.MOLOTOV -> {
                for (int i = 0; i < 18; i++) {
                    Vec3 v = new Vec3(R.nextGaussian(), R.nextDouble() * 0.6, R.nextGaussian()).scale(0.12);
                    level.addParticle(ModParticles.FLAME.get(), p.x, p.y + 0.1, p.z, v.x, v.y, v.z);
                }
                for (int i = 0; i < 12; i++) {
                    Vec3 v = new Vec3(R.nextGaussian(), R.nextDouble() * 0.6, R.nextGaussian()).scale(0.15);
                    level.addParticle(ParticleTypes.CRIT, p.x, p.y + 0.1, p.z, v.x, v.y, v.z);
                }
            }
            case Packets.Effect.EXTINGUISH -> {
                for (int i = 0; i < 30; i++) {
                    level.addParticle(ParticleTypes.CLOUD, p.x + R.nextGaussian() * 1.2, p.y + R.nextDouble() * 0.6, p.z + R.nextGaussian() * 1.2, 0, 0.05, 0);
                }
            }
            case Packets.Effect.C4 -> {
                for (int i = 0; i < 80; i++) {
                    Vec3 v = new Vec3(R.nextGaussian(), R.nextDouble() * 1.2, R.nextGaussian()).normalize().scale(0.2 + R.nextDouble() * 0.9);
                    level.addParticle(ModParticles.FIREBALL.get(), p.x, p.y + 0.3, p.z, v.x, v.y, v.z);
                }
                for (int i = 0; i < 50; i++) {
                    Vec3 v = new Vec3(R.nextGaussian(), R.nextDouble() * 0.8, R.nextGaussian()).scale(0.15);
                    level.addParticle(ModParticles.SMOKE.get(), p.x, p.y + 1, p.z, v.x, v.y, v.z);
                }
                level.addParticle(ParticleTypes.EXPLOSION_EMITTER, p.x, p.y + 0.5, p.z, 0, 0, 0);
                SmokeManager.clear(p, 12);
            }
            case Packets.Effect.TASER -> {
                Player shooter = mc.player;
                Vec3 from = p;
                if (mc.level != null) {
                    // find the closest player to draw the beam from (the shooter)
                    double best = 1e9;
                    for (Player pl : mc.level.players()) {
                        double dd = pl.getEyePosition().distanceToSqr(p);
                        if (dd < best && dd < 12 * 12) {
                            best = dd;
                            shooter = pl;
                        }
                    }
                }
                if (shooter != null) from = muzzleWorld(shooter, shooter == mc.player, p.subtract(shooter.getEyePosition()).normalize());
                WorldFx.beam(from, p);
                for (int i = 0; i < 10; i++) {
                    level.addParticle(ModParticles.SPARK.get(), p.x, p.y, p.z, R.nextGaussian() * 0.1, R.nextDouble() * 0.1, R.nextGaussian() * 0.1);
                }
            }
            case Packets.Effect.SMOKE_POP -> {
                Entity ent = level.getEntity((int) e.arg());
                if (ent instanceof dev.csarsenal.entity.GrenadeEntity g) SmokeManager.start(g);
            }
            case Packets.Effect.DECOY_SHOT -> {
                WeaponDef d = dev.csarsenal.weapon.Weapons.byIndex((int) e.arg());
                ClientSounds.fire(d, false, p.x, p.y, p.z, false);
                level.addParticle(ModParticles.SPARK.get(), p.x, p.y + 0.1, p.z, 0, 0.1, 0);
            }
            default -> {
            }
        }
    }

    private ClientFx() {
    }

    @SuppressWarnings("unused")
    private static WeaponGeometry g() {
        return null;
    }
}
