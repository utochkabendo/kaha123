package dev.csarsenal.server;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Transient per-player server state (not saved). */
public final class PlayerState {
    private static final Map<UUID, PlayerState> ALL = new HashMap<>();

    public long lastShotNs;
    public double fireBudget = 2;
    public boolean reloading;
    public int reloadSlot = -1;
    public int reloadWeapon = -1;
    public long reloadEnd;
    public long lastKnifeMs;
    public long flashedUntilMs;
    public int lastSelected = -1;
    public boolean lastCsMode;
    public boolean walking;
    public long lastConsecutiveKnifeMs;

    public static PlayerState of(ServerPlayer p) {
        return ALL.computeIfAbsent(p.getUUID(), u -> new PlayerState());
    }

    public static void remove(ServerPlayer p) {
        ALL.remove(p.getUUID());
    }

    public static void clear() {
        ALL.clear();
    }
}
