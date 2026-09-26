package dev.csarsenal.client;

import dev.csarsenal.ballistics.Ballistics;
import dev.csarsenal.ballistics.BulletTrace;
import dev.csarsenal.client.fx.ClientFx;
import dev.csarsenal.client.fx.ClientSounds;
import dev.csarsenal.client.hud.CsHud;
import dev.csarsenal.client.render.AgentPose;
import dev.csarsenal.client.weapon.ClientWeapon;
import dev.csarsenal.network.CsDataPayload;
import dev.csarsenal.network.Net;
import dev.csarsenal.network.Packets;
import dev.csarsenal.weapon.WeaponDef;
import dev.csarsenal.weapon.Weapons;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class ClientPackets implements Net.ClientHandler {
    public static final ClientPackets INSTANCE = new ClientPackets();

    @Override
    public void shotFx(Packets.ShotFx p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity shooter = mc.level.getEntity(p.shooter());
        if (shooter == mc.player) return;
        WeaponDef d = Weapons.byIndex(p.weapon());
        if (d == null) return;
        Vec3 o = p.origin();
        if (shooter instanceof Player pl) AgentPose.onShot(pl, p.silenced());
        ClientSounds.fire(d, p.silenced(), o.x, o.y, o.z, false);
        double range = d.range * Ballistics.UNIT;
        boolean tracer = d.tracerFrequency > 0 && p.shotIndex() % Math.max(1, d.tracerFrequency) == 0 && !p.silenced();
        int i = 0;
        for (Vec3 dir : p.dirs()) {
            List<BulletTrace.Wall> walls = BulletTrace.walls(mc.level, o, dir, range, Ballistics.MAX_PENETRATIONS + 1);
            double stop = Ballistics.stopDistance(d, walls, range);
            ClientFx.bullet(mc.level, d, o, dir, walls, stop, null, false, tracer && i == 0, shooter);
            i++;
        }
        if (shooter != null && d.category != WeaponDef.Category.SHOTGUN && !d.boltAction && d.category != WeaponDef.Category.TASER) {
            Vec3 look = p.dirs().isEmpty() ? shooter.getLookAngle() : p.dirs().get(0);
            Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
            Vec3 up = right.cross(look).normalize();
            Vec3 at = o.add(look.scale(0.3)).add(right.scale(0.12)).add(up.scale(-0.15));
            ClientFx.ejectShell(d, at, right, up, look, shooter.getYRot());
        }
    }

    @Override
    public void tagged(Packets.Tagged p) {
        ClientWeapon.INSTANCE.onTagged(p);
    }

    @Override
    public void hitConfirm(Packets.HitConfirm p) {
        ClientWeapon.INSTANCE.lastHitConfirm = ClientWeapon.INSTANCE.now;
    }

    @Override
    public void killFeed(Packets.KillFeed p) {
        CsHud.onKill(p);
    }

    @Override
    public void flash(Packets.Flash p) {
        CsHud.onFlash(p);
    }

    @Override
    public void anim(Packets.Anim p) {
        AgentPose.onAnim(p);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(p.entity());
        if (e == null || e == mc.player) return;
        WeaponDef d = Weapons.byIndex(p.weapon());
        switch (p.anim()) {
            case Packets.Anim.RELOAD -> {
                String s = d != null && (d.reloadStyle == WeaponDef.ReloadStyle.PISTOL || d.reloadStyle == WeaponDef.ReloadStyle.REVOLVER) ? "reload.pistol_magout"
                        : d != null && d.reloadStyle == WeaponDef.ReloadStyle.SHOTGUN_SHELLS ? "reload.shell_insert" : "reload.rifle_magout";
                ClientSounds.at(s, e.getX(), e.getEyeY(), e.getZ(), 0.8f, 1f);
            }
            case Packets.Anim.SHELL -> ClientSounds.at("reload.shell_insert", e.getX(), e.getEyeY(), e.getZ(), 0.7f, 1f);
            case Packets.Anim.DRAW -> ClientSounds.at("weapon.draw", e.getX(), e.getEyeY(), e.getZ(), 0.5f, 1f);
            case Packets.Anim.KNIFE -> ClientSounds.at("knife.slash", e.getX(), e.getEyeY(), e.getZ(), 0.6f, 1f);
            case Packets.Anim.KNIFE_HEAVY -> ClientSounds.at("knife.stab", e.getX(), e.getEyeY(), e.getZ(), 0.6f, 1f);
            case Packets.Anim.THROW -> ClientSounds.at("grenade.throw", e.getX(), e.getEyeY(), e.getZ(), 0.6f, 1f);
            default -> {
            }
        }
    }

    @Override
    public void effect(Packets.Effect p) {
        ClientFx.effect(p);
    }

    @Override
    public void csData(CsDataPayload p) {
        ClientData.put(p);
    }

    @Override
    public void scoreboard(Packets.Scoreboard p) {
        dev.csarsenal.client.hud.CsScoreboard.update(p);
    }

    private ClientPackets() {
    }
}
