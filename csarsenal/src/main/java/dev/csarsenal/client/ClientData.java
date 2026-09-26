package dev.csarsenal.client;

import dev.csarsenal.network.CsDataPayload;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/** Armor / helmet / defuser / team of players as synced by the server. */
public final class ClientData {
    private static final Map<Integer, CsDataPayload> DATA = new HashMap<>();

    public static void put(CsDataPayload p) {
        DATA.put(p.entity(), p);
    }

    public static CsDataPayload get(Player p) {
        return DATA.get(p.getId());
    }

    public static int armor(Player p) {
        CsDataPayload d = get(p);
        return d == null ? 0 : d.armor();
    }

    public static boolean helmet(Player p) {
        CsDataPayload d = get(p);
        return d != null && d.helmet() && d.armor() > 0;
    }

    public static boolean defuser(Player p) {
        CsDataPayload d = get(p);
        return d != null && d.defuser();
    }

    public static int team(Player p) {
        CsDataPayload d = get(p);
        return d == null ? 0 : d.team();
    }

    /** texture set for the operator model */
    public static String teamSkin(Player p) {
        int t = team(p);
        if (t == 2) return "ct";
        if (t == 1) return "t";
        return (p.getUUID().hashCode() & 1) == 0 ? "t" : "ct";
    }

    public static void clear() {
        DATA.clear();
    }

    private ClientData() {
    }
}
