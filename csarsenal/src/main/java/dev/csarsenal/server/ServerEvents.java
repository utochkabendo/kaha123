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
        if (e.getEntity() instanceof ServerPlayer sp) CsPlayerData.sync(sp);
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
            CsPlayerData d = CsPlayerData.get(target);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(viewer,
                    new dev.csarsenal.network.CsDataPayload(target.getId(), d.armor(), d.helmet(), d.defuser(), d.team()));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) PlayerState.remove(sp);
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent e) {
        PlayerState.clear();
    }

    private ServerEvents() {
    }
}
