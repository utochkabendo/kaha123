package dev.csarsenal.server;

import dev.csarsenal.Config;
import dev.csarsenal.anim.HitShape;
import dev.csarsenal.anim.Hitboxes;
import dev.csarsenal.ballistics.Ballistics;
import dev.csarsenal.ballistics.BulletTrace;
import dev.csarsenal.ballistics.HitGroup;
import dev.csarsenal.ballistics.Material;
import dev.csarsenal.entity.GrenadeEntity;
import dev.csarsenal.network.Packets;
import dev.csarsenal.registry.ModDamageTypes;
import dev.csarsenal.registry.ModEntities;
import dev.csarsenal.registry.ModItems;
import dev.csarsenal.registry.ModSounds;
import dev.csarsenal.weapon.EquipmentItem;
import dev.csarsenal.weapon.GrenadeItem;
import dev.csarsenal.weapon.WeaponDef;
import dev.csarsenal.weapon.WeaponItem;
import dev.csarsenal.weapon.WeaponState;
import dev.csarsenal.weapon.Weapons;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Server side handling of the client weapon packets. */
public final class ServerNet {

    private static ItemStack selected(ServerPlayer sp, int slot) {
        if (slot < 0 || slot > 8 || sp.getInventory().selected != slot) return ItemStack.EMPTY;
        return sp.getInventory().getItem(slot);
    }

    // ================================================================================================= shoot
    public static void shoot(ServerPlayer sp, Packets.Shoot p) {
        ItemStack stack = selected(sp, p.slot());
        if (!(stack.getItem() instanceof WeaponItem wi)) return;
        WeaponDef d = wi.def();
        if (d.index != p.weapon() || !d.isGun()) return;
        ServerLevel level = sp.serverLevel();
        PlayerState ps = PlayerState.of(sp);
        WeaponState st = WeaponItem.state(stack);
        long gameTime = level.getGameTime();
        if (d.category == WeaponDef.Category.TASER && st.readyAt() > gameTime) return;
        if (st.ammo() <= 0) {
            WeaponItem.setState(stack, st); // resync
            return;
        }
        if (ps.reloading && ps.reloadSlot == p.slot()) {
            if (d.reloadStyle == WeaponDef.ReloadStyle.SHOTGUN_SHELLS) ServerWeapons.cancelReload(sp, ps);
            else return;
        }
        // fire rate budget (the client fires on exact frame times; allow jitter but not faster than the weapon)
        long now = System.nanoTime();
        double minInterval = Math.min(d.cycleTime, d.cycleTimeAlt);
        if (st.altMode() && d.altFireMode == WeaponDef.FireMode.BURST) minInterval = ServerWeapons.BURST_INTERVAL;
        ps.fireBudget = Math.min(2.5, ps.fireBudget + (now - ps.lastShotNs) / 1e9 / Math.max(0.03, minInterval * 0.92));
        if (ps.fireBudget < 1.0) return;
        ps.fireBudget -= 1.0;
        ps.lastShotNs = now;

        Vec3 eye = sp.getEyePosition();
        Vec3 origin = p.origin().distanceToSqr(eye) < 2.5 * 2.5 ? p.origin() : eye;

        WeaponState ns = st.withAmmo(st.ammo() - 1);
        if (d.category == WeaponDef.Category.TASER) ns = ns.withReadyAt(gameTime + (long) (d.reloadTime * 20));
        WeaponItem.setState(stack, ns);

        boolean silenced = d.integrallySuppressed || (d.silencer && st.silencer());
        Vec3 look = sp.getLookAngle();
        double range = d.range * Ballistics.UNIT;
        List<Vec3> dirs = new ArrayList<>();
        int pellets = Math.min(p.pellets().size(), Math.max(1, d.pellets));
        List<GrenadeEntity> smokes = GrenadeEntity.activeSmokes(level);
        for (int i = 0; i < pellets; i++) {
            Packets.Pellet pel = p.pellets().get(i);
            Vec3 dir = pel.dir().normalize();
            if (!Double.isFinite(dir.x) || dir.dot(look) < 0.2) dir = look;
            dirs.add(dir);
            List<BulletTrace.Wall> walls = BulletTrace.walls(level, origin, dir, range, Ballistics.MAX_PENETRATIONS + 1);
            double stop = Ballistics.stopDistance(d, walls, range);
            if (Config.get(Config.BREAK_GLASS, true)) breakGlass(level, walls, stop);
            if (d.category == WeaponDef.Category.TASER) {
                zeus(sp, d, origin, dir, pel, Math.min(stop, range));
                continue;
            }
            List<Packets.Hit> hits = new ArrayList<>(pel.hits());
            hits.sort(Comparator.comparingDouble(Packets.Hit::distance));
            float entityPenalty = 1f;
            int n = 0;
            for (Packets.Hit h : hits) {
                if (++n > 4) break;
                Entity e = level.getEntity(h.entity());
                if (!(e instanceof LivingEntity le) || !le.isAlive() || le == sp) continue;
                if (h.distance() > stop + 0.6 || h.distance() > range) continue;
                if (!validate(le, origin, dir, h.distance())) continue;
                float dmg = Ballistics.damageAt(d, walls, h.distance(), entityPenalty);
                if (dmg <= 0) continue;
                CsDamage.Context c = new CsDamage.Context();
                c.weapon = d;
                c.group = HitGroup.byOrdinal(h.group());
                c.scoped = p.scoped();
                c.hitPos = origin.add(dir.scale(h.distance()));
                for (BulletTrace.Wall w : walls) if (w.enter < h.distance()) c.wallbang = true;
                for (GrenadeEntity s : smokes) if (s.smokeBlocks(origin, c.hitPos)) c.throughSmoke = true;
                c.quietImpact = d.pellets > 1 && i > 0;
                CsDamage.apply(sp, le, dmg, c);
                entityPenalty *= 0.7f;
            }
        }
        PacketDistributor.sendToPlayersTrackingEntity(sp, new Packets.ShotFx(sp.getId(), d.index, origin, dirs, silenced, p.shotIndex()));
        if (!silenced) level.gameEvent(sp, GameEvent.PROJECTILE_SHOOT, origin);
    }

    /** accept a client reported hit if the ray passes the entity's (tolerance-inflated) box near the reported distance */
    private static boolean validate(LivingEntity e, Vec3 origin, Vec3 dir, double dist) {
        double tol = Config.SPEC.isLoaded() ? Config.HIT_TOLERANCE.get() : 1.0;
        Vec3 v = e.getDeltaMovement();
        double speed = Math.sqrt(v.x * v.x + v.z * v.z) * 20.0;
        AABB bb = e.getBoundingBox().inflate(0.35 + tol + Math.min(speed * 0.35, 3.0));
        Vec3 end = origin.add(dir.scale(dist + 3.0));
        var clip = bb.clip(origin, end);
        if (clip.isEmpty() && !bb.contains(origin)) return false;
        double d0 = clip.map(c -> c.distanceTo(origin)).orElse(0.0);
        return dist + 0.5 >= d0 - 0.5 && dist <= d0 + e.getBbWidth() + e.getBbHeight() + 2 * tol + 2.0;
    }

    private static void breakGlass(ServerLevel level, List<BulletTrace.Wall> walls, double stop) {
        for (BulletTrace.Wall w : walls) {
            if (w.enter > stop) break;
            if (w.material != Material.GLASS) continue;
            for (BlockPos bp : w.blocks) {
                BlockState bs = level.getBlockState(bp);
                if (bs.is(Tags.Blocks.GLASS_BLOCKS) || bs.is(Tags.Blocks.GLASS_PANES)) level.destroyBlock(bp, false);
            }
        }
    }

    private static void zeus(ServerPlayer sp, WeaponDef d, Vec3 origin, Vec3 dir, Packets.Pellet pel, double stop) {
        ServerLevel level = sp.serverLevel();
        for (Packets.Hit h : pel.hits()) {
            Entity e = level.getEntity(h.entity());
            if (!(e instanceof LivingEntity le) || !le.isAlive() || le == sp || h.distance() > stop + 0.3) continue;
            if (!validate(le, origin, dir, h.distance())) continue;
            CsDamage.Context c = new CsDamage.Context();
            c.weapon = d;
            c.group = HitGroup.CHEST;
            c.type = ModDamageTypes.TASER;
            c.hitPos = origin.add(dir.scale(h.distance()));
            // the zeus ignores armor in practice: 500 damage
            CsDamage.apply(sp, le, 500f, c);
            break;
        }
        Vec3 end = origin.add(dir.scale(Math.min(stop, d.range * Ballistics.UNIT)));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp, new Packets.Effect(Packets.Effect.TASER, end, 0));
    }

    // ================================================================================================= reload
    public static void reload(ServerPlayer sp, Packets.Reload p) {
        ItemStack stack = selected(sp, p.slot());
        if (!(stack.getItem() instanceof WeaponItem wi)) return;
        WeaponDef d = wi.def();
        if (!d.isGun() || d.category == WeaponDef.Category.TASER || d.reloadStyle == WeaponDef.ReloadStyle.NONE) return;
        WeaponState st = WeaponItem.state(stack);
        boolean infinite = Config.get(Config.INFINITE_RESERVE, false);
        if (st.ammo() >= d.magSize || (st.reserve() <= 0 && !infinite)) return;
        PlayerState ps = PlayerState.of(sp);
        if (ps.reloading) return;
        ServerWeapons.startReload(sp, ps, p.slot(), d);
    }

    // ================================================================================================= actions
    public static void action(ServerPlayer sp, Packets.Action p) {
        ItemStack stack = selected(sp, p.slot());
        WeaponDef d = stack.getItem() instanceof WeaponItem wi ? wi.def() : null;
        PlayerState ps = PlayerState.of(sp);
        switch (p.action()) {
            case Packets.Action.SILENCER -> {
                if (d == null || !d.silencer) return;
                WeaponState st = WeaponItem.state(stack);
                WeaponItem.setState(stack, st.withSilencer(!st.silencer()));
                ServerWeapons.cancelReload(sp, ps);
                broadcastAnim(sp, Packets.Anim.SILENCER, d.index, st.silencer() ? 0 : 1);
                sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(), ModSounds.get(st.silencer() ? "weapon.silencer_off" : "weapon.silencer_on"), SoundSource.PLAYERS, 0.8f, 1f);
            }
            case Packets.Action.MODE -> {
                if (d == null || d.altFireMode != WeaponDef.FireMode.BURST) return;
                WeaponState st = WeaponItem.state(stack);
                WeaponItem.setState(stack, st.withAlt(!st.altMode()));
                sp.displayClientMessage(Component.literal(!st.altMode() ? "Burst Fire" : (d.fireMode == WeaponDef.FireMode.AUTO ? "Automatic" : "Semi-Automatic")), true);
            }
            case Packets.Action.INSPECT -> { if (d != null) broadcastAnim(sp, Packets.Anim.INSPECT, d.index, 0); }
            case Packets.Action.DRAW -> {
                ServerWeapons.cancelReload(sp, ps);
                WeaponDef any = dev.csarsenal.weapon.CsItem.defOf(stack);
                if (any != null) broadcastAnim(sp, Packets.Anim.DRAW, any.index, 0);
            }
            case Packets.Action.WALK -> ps.walking = p.arg() != 0;
            case Packets.Action.DROP -> {
                ItemStack s = sp.getInventory().getSelected();
                if (!s.isEmpty() && s.getItem() instanceof dev.csarsenal.weapon.CsItem) {
                    ItemStack drop = sp.getInventory().removeFromSelected(true);
                    sp.drop(drop, false, true);
                }
            }
            default -> {
            }
        }
    }

    static void broadcastAnim(ServerPlayer sp, int anim, int weapon, int arg) {
        PacketDistributor.sendToPlayersTrackingEntity(sp, new Packets.Anim(sp.getId(), anim, weapon, arg));
    }

    // ================================================================================================= grenades
    public static void throwGrenade(ServerPlayer sp, Packets.Throw p) {
        ItemStack stack = selected(sp, p.slot());
        if (!(stack.getItem() instanceof GrenadeItem gi)) return;
        ServerLevel level = sp.serverLevel();
        float strength = Mth.clamp(p.strength(), 0f, 1f);
        Vec3 eye = sp.getEyePosition();
        Vec3 dir = p.dir().lengthSqr() > 0.5 ? p.dir().normalize() : sp.getLookAngle();
        // CS: throw angle is biased upwards: pitch -= (90 - |pitch|) * 10 / 90
        double pitch = -Math.toDegrees(Math.asin(Mth.clamp(dir.y, -1, 1)));
        double yaw = Math.toDegrees(Math.atan2(-dir.x, dir.z));
        pitch = pitch - (90 - Math.abs(pitch)) * 10.0 / 90.0;
        Vec3 tdir = Vec3.directionFromRotation((float) pitch, (float) yaw);
        double speedU = Mth.clamp(750.0 * 0.9, 15, 750) * (0.3 + 0.7 * strength);
        Vec3 pv = p.playerVel();
        double pvLen = pv.length();
        if (pvLen > 400 * Ballistics.UNIT / 20) pv = pv.scale(400 * Ballistics.UNIT / 20 / pvLen);
        Vec3 vel = tdir.scale(speedU * Ballistics.UNIT / 20.0).add(pv.scale(1.25));
        Vec3 start = eye.add(tdir.scale(0.4));
        var hit = level.clip(new net.minecraft.world.level.ClipContext(eye, start, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, sp));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) start = hit.getLocation().subtract(tdir.scale(0.12));
        GrenadeEntity g = new GrenadeEntity(ModEntities.GRENADE.get(), level);
        g.setup(gi.type, sp, start, vel);
        level.addFreshEntity(g);
        level.playSound(null, sp.getX(), sp.getEyeY(), sp.getZ(), ModSounds.get("grenade.throw"), SoundSource.PLAYERS, 0.8f, 1f);
        broadcastAnim(sp, Packets.Anim.THROW, gi.def().index, 0);
        if (!sp.getAbilities().instabuild) stack.shrink(1);
    }

    // ================================================================================================= knife
    public static void knife(ServerPlayer sp, Packets.Knife p) {
        ItemStack stack = selected(sp, p.slot());
        if (!(stack.getItem() instanceof WeaponItem wi) || wi.def().category != WeaponDef.Category.KNIFE) return;
        WeaponDef d = wi.def();
        PlayerState ps = PlayerState.of(sp);
        long now = System.currentTimeMillis();
        long cd = (long) ((p.heavy() ? d.cycleTimeAlt : d.cycleTime) * 1000 * 0.8);
        if (now - ps.lastKnifeMs < cd) return;
        boolean consecutive = now - ps.lastKnifeMs < 1200 && !p.heavy();
        ps.lastKnifeMs = now;
        ServerLevel level = sp.serverLevel();
        broadcastAnim(sp, p.heavy() ? Packets.Anim.KNIFE_HEAVY : Packets.Anim.KNIFE, d.index, 0);
        Vec3 eye = sp.getEyePosition();
        if (p.entity() < 0) {
            String snd = p.hitWall() ? "knife.hitwall" : (p.heavy() ? "knife.stab" : "knife.slash");
            level.playSound(null, eye.x, eye.y, eye.z, ModSounds.get(snd), SoundSource.PLAYERS, 0.8f, 1f);
            return;
        }
        Entity e = level.getEntity(p.entity());
        if (!(e instanceof LivingEntity le) || !le.isAlive() || le == sp) return;
        double reach = (p.heavy() ? 32 : 48) * Ballistics.UNIT + 1.2 + (Config.SPEC.isLoaded() ? Config.HIT_TOLERANCE.get() : 1.0);
        if (le.getBoundingBox().distanceToSqr(eye) > reach * reach) return;
        // backstab: attacker behind the victim (verified on the server)
        Vec3 toAttacker = sp.position().subtract(le.position());
        Vec3 victimFwd = Vec3.directionFromRotation(0, le.getYRot());
        boolean backstab = toAttacker.horizontalDistanceSqr() > 1e-4 && toAttacker.normalize().dot(victimFwd) < -0.3;
        float dmg = p.heavy() ? (backstab ? 180 : 65) : (backstab ? 90 : (consecutive ? 25 : 40));
        CsDamage.Context c = new CsDamage.Context();
        c.weapon = d;
        c.group = HitGroup.GENERIC;
        c.type = ModDamageTypes.KNIFE;
        c.quietImpact = true;
        CsDamage.apply(sp, le, dmg, c);
        level.playSound(null, le.getX(), le.getEyeY(), le.getZ(), ModSounds.get("knife.hit"), SoundSource.PLAYERS, 1f, 1f);
    }

    // ================================================================================================= buy menu
    public static void buy(ServerPlayer sp, Packets.Buy p) {
        Config.BuyMode mode = Config.SPEC.isLoaded() ? Config.BUY.get() : Config.BuyMode.FREE;
        if (mode == Config.BuyMode.DISABLED || (mode == Config.BuyMode.CREATIVE_ONLY && !sp.isCreative())) {
            sp.displayClientMessage(Component.translatable("csarsenal.buy.disabled"), true);
            return;
        }
        WeaponDef d = Weapons.byId(p.id());
        Item item = ModItems.get(p.id());
        if (d == null || item == null) return;
        if (item instanceof EquipmentItem eq) {
            EquipmentItem.apply(sp, eq.kind);
        } else {
            ItemStack s = item.getDefaultInstance();
            if (!sp.getInventory().add(s)) sp.drop(s, false);
        }
        sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(), ModSounds.get("ui.buy"), SoundSource.PLAYERS, 0.7f, 1f);
    }

    private ServerNet() {
    }

    @SuppressWarnings("unused")
    private static List<HitShape> shapes(Entity e) {
        return Hitboxes.forEntity(e, 1f);
    }
}
