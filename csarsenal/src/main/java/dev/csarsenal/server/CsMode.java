package dev.csarsenal.server;

import dev.csarsenal.Config;
import dev.csarsenal.weapon.CsItem;
import net.minecraft.world.entity.player.Player;

/** Whether the CS2 movement / crouch / controls are active for a player (identical on both sides). */
public final class CsMode {
    public static boolean active(Player p) {
        if (p.isSpectator() || p.getAbilities().flying || p.isPassenger() || p.isFallFlying()) return false;
        return switch (Config.movement()) {
            case ALWAYS -> true;
            case OFF -> false;
            case HOLDING_CS_ITEM -> CsItem.holding(p);
        };
    }

    private CsMode() {
    }
}
