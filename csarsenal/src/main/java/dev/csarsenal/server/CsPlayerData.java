package dev.csarsenal.server;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.csarsenal.Config;
import dev.csarsenal.network.CsDataPayload;
import dev.csarsenal.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * CS specific player state: kevlar (0..100), helmet, defuse kit, team (0 = none, 1 = T, 2 = CT) and money
 * (-1 = never joined, gets the start money on the first login).
 */
public record CsPlayerData(int armor, boolean helmet, boolean defuser, int team, int money) {
    public static final CsPlayerData EMPTY = new CsPlayerData(0, false, false, 0, -1);
    public static final int TEAM_NONE = 0, TEAM_T = 1, TEAM_CT = 2;

    public static final Codec<CsPlayerData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("armor", 0).forGetter(CsPlayerData::armor),
            Codec.BOOL.optionalFieldOf("helmet", false).forGetter(CsPlayerData::helmet),
            Codec.BOOL.optionalFieldOf("defuser", false).forGetter(CsPlayerData::defuser),
            Codec.INT.optionalFieldOf("team", 0).forGetter(CsPlayerData::team),
            Codec.INT.optionalFieldOf("money", -1).forGetter(CsPlayerData::money)
    ).apply(i, CsPlayerData::new));

    public CsPlayerData withArmor(int a, boolean h) {
        return new CsPlayerData(Math.max(0, Math.min(100, a)), h, defuser, team, money);
    }

    public CsPlayerData withDefuser(boolean d) {
        return new CsPlayerData(armor, helmet, d, team, money);
    }

    public CsPlayerData withTeam(int t) {
        return new CsPlayerData(armor, helmet, defuser, t, money);
    }

    public CsPlayerData withMoney(int m) {
        return new CsPlayerData(armor, helmet, defuser, team, Math.max(0, Math.min(Config.maxMoney(), m)));
    }

    public CsPlayerData afterDeath() {
        return new CsPlayerData(0, false, false, team, money);
    }

    public static CsPlayerData get(ServerPlayer p) {
        return p.getData(ModAttachments.CS_DATA);
    }

    /** Store and broadcast to the player and everybody tracking him. */
    public static void set(ServerPlayer p, CsPlayerData d) {
        p.setData(ModAttachments.CS_DATA, d);
        sync(p);
    }

    public static CsDataPayload payload(ServerPlayer p) {
        CsPlayerData d = p.getData(ModAttachments.CS_DATA);
        return new CsDataPayload(p.getId(), d.armor(), d.helmet(), d.defuser(), d.team(), Math.max(0, d.money()));
    }

    public static void sync(ServerPlayer p) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, payload(p));
    }

    /** add (or with a negative amount take) money, clamped to 0..max; returns the amount actually applied */
    public static int addMoney(ServerPlayer p, int amount) {
        CsPlayerData d = get(p);
        int before = Math.max(0, d.money());
        CsPlayerData n = d.withMoney(before + amount);
        set(p, n);
        return n.money() - before;
    }
}
