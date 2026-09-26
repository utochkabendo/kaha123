package dev.csarsenal.server;

import dev.csarsenal.Config;
import dev.csarsenal.registry.ModAttachments;
import dev.csarsenal.weapon.CsItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.living.LivingSwapItemsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Game bus events that run on both sides or on the server. */
public final class ServerEvents {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer sp) ServerWeapons.tick(sp);
    }

    /** CS crouch: 54 units tall, eyes at 46 units (1.35 / 1.15 blocks) */
    @SubscribeEvent
    public static void onSize(EntityEvent.Size e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (e.getPose() != Pose.CROUCHING) return;
        if (!Config.get(Config.CS_CROUCH_HITBOX, false)) return;
        try {
            if (!CsMode.active(p)) return;
        } catch (Exception ex) {
            return; // entity not fully constructed yet
        }
        e.setNewSize(EntityDimensions.scalable(0.6f, 1.35f).withEyeHeight(1.15f));
    }

    /** F is "inspect" in CS: don't swap hands while holding a CS item */
    @SubscribeEvent
    public static void onSwap(LivingSwapItemsEvent.Hands e) {
        if (e.getEntity() instanceof Player p && CsItem.holding(p)) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone e) {
        if (e.isWasDeath() && e.getEntity() instanceof ServerPlayer np) {
            CsPlayerData old = e.getOriginal().getData(ModAttachments.CS_DATA);
            np.setData(ModAttachments.CS_DATA, old.afterDeath());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        CsStats.markDirty();
        CsPlayerData d = CsPlayerData.get(sp);
        if (d.money() < 0) sp.setData(ModAttachments.CS_DATA, d.withMoney(Config.startMoney()));
        CsPlayerData.sync(sp);
    }

    /** CS kill rewards: the weapon's reward for killing a player (or, scaled, a hostile mob); -$300 for a teammate */
    @SubscribeEvent
    public static void onKill(net.neoforged.neoforge.event.entity.living.LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer victimPlayer) CsStats.death(victimPlayer, e.getSource());
        if (!(e.getSource().getEntity() instanceof ServerPlayer killer) || e.getEntity() == killer) return;
        if (!Config.economy()) return;
        var victim = e.getEntity();
        var src = e.getSource();
        int reward;
        if (src.is(dev.csarsenal.registry.ModDamageTypes.GRENADE) || src.is(dev.csarsenal.registry.ModDamageTypes.INFERNO)) reward = 300;
        else if (src.is(dev.csarsenal.registry.ModDamageTypes.BOMB)) reward = 0;
        else {
            dev.csarsenal.weapon.WeaponDef d = CsItem.defOf(killer.getMainHandItem());
            if (d == null) return;
            reward = d.killReward;
        }
        if (victim instanceof ServerPlayer vp) {
            int kt = CsPlayerData.get(killer).team(), vt = CsPlayerData.get(vp).team();
            if (kt != 0 && kt == vt) reward = -300;
        } else if (victim instanceof net.minecraft.world.entity.monster.Enemy) {
            reward = (int) Math.round(reward * Config.mobRewardScale() / 50.0) * 50;
        } else {
            return;
        }
        if (reward != 0) CsPlayerData.addMoney(killer, reward);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) CsPlayerData.sync(sp);
    }

    @SubscribeEvent
    public static void onDim(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) CsPlayerData.sync(sp);
    }

    @SubscribeEvent
    public static void onTrack(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof ServerPlayer target && e.getEntity() instanceof ServerPlayer viewer) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(viewer, CsPlayerData.payload(target));
        }
    }

    /**
     * CS footsteps for everybody else: walking (shift) and crouching players are silent, running players are loud
     * enough to be heard like in CS (the moving player hears his own steps client side).
     */
    @SubscribeEvent
    public static void onSound(net.neoforged.neoforge.event.PlayLevelSoundEvent.AtPosition e) {
        if (e.getLevel().isClientSide() || e.getSound() == null) return;
        if (!e.getSound().value().getLocation().getPath().endsWith(".step")) return;
        var pos = e.getPosition();
        for (var pl : e.getLevel().players()) {
            if (!(pl instanceof ServerPlayer sp) || sp.distanceToSqr(pos) > 1.0 || !CsMode.active(sp)) continue;
            if (PlayerState.of(sp).walking || sp.isCrouching()) e.setCanceled(true);
            else e.setNewVolume(Math.max(e.getNewVolume(), 1.1f));
            return;
        }
    }

    @SubscribeEvent
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) {
        CsStats.tick(e.getServer());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        CsStats.markDirty();
        if (e.getEntity() instanceof ServerPlayer sp) PlayerState.remove(sp);
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent e) {
        PlayerState.clear();
        CsStats.reset();
    }

    private ServerEvents() {
    }
}
