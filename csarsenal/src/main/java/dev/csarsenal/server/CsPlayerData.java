package dev.csarsenal.server;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.csarsenal.network.CsDataPayload;
import dev.csarsenal.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * CS specific player state: kevlar (0..100), helmet, defuse kit and team (0 = none, 1 = T, 2 = CT).
 */
public record CsPlayerData(int armor, boolean helmet, boolean defuser, int team) {
    public static final CsPlayerData EMPTY = new CsPlayerData(0, false, false, 0);
    public static final int TEAM_NONE = 0, TEAM_T = 1, TEAM_CT = 2;

    public static final Codec<CsPlayerData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("armor", 0).forGetter(CsPlayerData::armor),
            Codec.BOOL.optionalFieldOf("helmet", false).forGetter(CsPlayerData::helmet),
            Codec.BOOL.optionalFieldOf("defuser", false).forGetter(CsPlayerData::defuser),
            Codec.INT.optionalFieldOf("team", 0).forGetter(CsPlayerData::team)
    ).apply(i, CsPlayerData::new));

    public CsPlayerData withArmor(int a, boolean h) {
        return new CsPlayerData(Math.max(0, Math.min(100, a)), h, defuser, team);
    }

    public CsPlayerData withDefuser(boolean d) {
        return new CsPlayerData(armor, helmet, d, team);
    }

    public CsPlayerData withTeam(int t) {
        return new CsPlayerData(armor, helmet, defuser, t);
    }

    public CsPlayerData afterDeath() {
        return new CsPlayerData(0, false, false, team);
    }

    public static CsPlayerData get(ServerPlayer p) {
        return p.getData(ModAttachments.CS_DATA);
    }

    /** Store and broadcast to the player and everybody tracking him. */
    public static void set(ServerPlayer p, CsPlayerData d) {
        p.setData(ModAttachments.CS_DATA, d);
        sync(p);
    }

    public static void sync(ServerPlayer p) {
        CsPlayerData d = p.getData(ModAttachments.CS_DATA);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new CsDataPayload(p.getId(), d.armor(), d.helmet(), d.defuser(), d.team()));
    }
}
