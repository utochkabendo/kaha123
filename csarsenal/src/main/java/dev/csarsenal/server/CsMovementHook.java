package dev.csarsenal.server;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Bridge from the (common) mixins to the client side CS movement implementation. */
public final class CsMovementHook {
    public interface Handler {
        /** @return true if the CS movement handled this travel tick */
        boolean travel(Player p, Vec3 input);

        /** @return true if the CS jump was applied */
        boolean jump(Player p);
    }

    public static volatile Handler handler;

    private CsMovementHook() {
    }
}
