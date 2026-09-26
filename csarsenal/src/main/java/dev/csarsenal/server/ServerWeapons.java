package dev.csarsenal.server;

import dev.csarsenal.Config;
import dev.csarsenal.network.Packets;
import dev.csarsenal.registry.ModSounds;
import dev.csarsenal.weapon.CsItem;
import dev.csarsenal.weapon.WeaponDef;
import dev.csarsenal.weapon.WeaponItem;
import dev.csarsenal.weapon.WeaponState;
import dev.csarsenal.weapon.Weapons;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/** Server side weapon timers: reloads, zeus recharge. */
public final class ServerWeapons {
    /** time between the rounds of a burst (glock / famas) */
    public static final float BURST_INTERVAL = 0.055f;
    /** shotgun: time before the first shell goes in */
    public static final float SHELL_START = 0.45f;

    public static void startReload(ServerPlayer sp, PlayerState ps, int slot, WeaponDef d) {
        ps.reloading = true;
        ps.reloadSlot = slot;
        ps.reloadWeapon = d.index;
        long now = sp.serverLevel().getGameTime();
        float secs = d.reloadStyle == WeaponDef.ReloadStyle.SHOTGUN_SHELLS ? SHELL_START + d.reloadTime : d.reloadTime;
        ps.reloadEnd = now + Math.max(1, Math.round(secs * 20f));
        ServerNet.broadcastAnim(sp, Packets.Anim.RELOAD, d.index, 0);
    }

    public static void cancelReload(ServerPlayer sp, PlayerState ps) {
        if (!ps.reloading) return;
        ps.reloading = false;
        ServerNet.broadcastAnim(sp, Packets.Anim.RELOAD_CANCEL, ps.reloadWeapon, 0);
    }

    public static void tick(ServerPlayer sp) {
        PlayerState ps = PlayerState.of(sp);
        int sel = sp.getInventory().selected;
        ItemStack stack = sp.getInventory().getSelected();
        boolean cs = CsMode.active(sp);
        if (cs != ps.lastCsMode) {
            ps.lastCsMode = cs;
            sp.refreshDimensions();
        }
        if (sel != ps.lastSelected) {
            ps.lastSelected = sel;
            cancelReload(sp, ps);
        }
        long now = sp.serverLevel().getGameTime();
        if (ps.reloading) {
            if (!(stack.getItem() instanceof WeaponItem wi) || wi.def().index != ps.reloadWeapon || sel != ps.reloadSlot) {
                cancelReload(sp, ps);
            } else if (now >= ps.reloadEnd) {
                WeaponDef d = wi.def();
                WeaponState st = WeaponItem.state(stack);
                boolean infinite = Config.get(Config.INFINITE_RESERVE, false);
                if (d.reloadStyle == WeaponDef.ReloadStyle.SHOTGUN_SHELLS) {
                    if (st.ammo() < d.magSize && (st.reserve() > 0 || infinite)) {
                        st = st.withAmmo(st.ammo() + 1, infinite ? st.reserve() : st.reserve() - 1);
                        WeaponItem.setState(stack, st);
                        ServerNet.broadcastAnim(sp, Packets.Anim.SHELL, d.index, st.ammo());
                    }
                    if (st.ammo() < d.magSize && (st.reserve() > 0 || infinite)) {
                        ps.reloadEnd = now + Math.max(1, Math.round(d.reloadTime * 20f));
                    } else {
                        ps.reloading = false;
                        sp.serverLevel().playSound(null, sp.getX(), sp.getEyeY(), sp.getZ(), ModSounds.get("reload.pump"), SoundSource.PLAYERS, 0.7f, 1f);
                    }
                } else {
                    int need = d.magSize - st.ammo();
                    int take = infinite ? need : Math.min(need, st.reserve());
                    WeaponItem.setState(stack, st.withAmmo(st.ammo() + take, infinite ? st.reserve() : st.reserve() - take));
                    ps.reloading = false;
                }
            }
        }
        // zeus recharge
        if (stack.getItem() instanceof WeaponItem wi && wi.def() == Weapons.TASER) {
            WeaponState st = WeaponItem.state(stack);
            if (st.ammo() <= 0 && st.readyAt() > 0 && now >= st.readyAt()) {
                WeaponItem.setState(stack, st.withAmmo(1).withReadyAt(0));
            }
        }
        // make sure every carried CS gun has its state component (e.g. from /give)
        if (now % 20 == 0) {
            for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
                ItemStack s = sp.getInventory().getItem(i);
                if (s.getItem() instanceof WeaponItem && s.get(dev.csarsenal.registry.ModComponents.WEAPON_STATE.get()) == null) {
                    WeaponItem.setState(s, WeaponState.full(((CsItem) s.getItem()).def()));
                }
            }
        }
    }

    private ServerWeapons() {
    }
}
