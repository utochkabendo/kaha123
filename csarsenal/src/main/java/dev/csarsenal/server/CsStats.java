package dev.csarsenal.server;

import dev.csarsenal.network.Packets;
import dev.csarsenal.registry.ModDamageTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Scoreboard statistics of the running server: kills, assists (41+ damage to a victim someone else killed, like CS),
 * deaths, headshot kills and damage dealt. Broadcast to every player when they change.
 */
public final class CsStats {
    public static final class Stats {
        public int kills, assists, deaths, headshots, damage;
    }

    private static final Map<UUID, Stats> STATS = new HashMap<>();
    /** victim -> attacker -> damage dealt during the victim's current life */
    private static final Map<UUID, Map<UUID, Float>> DAMAGE = new HashMap<>();
    private static boolean dirty;

    public static Stats of(UUID id) {
        return STATS.computeIfAbsent(id, k -> new Stats());
    }

    /** CS damage (health units) dealt by one player to another */
    public static void damage(ServerPlayer attacker, ServerPlayer victim, float hp) {
        if (attacker == victim || hp <= 0) return;
        DAMAGE.computeIfAbsent(victim.getUUID(), k -> new HashMap<>()).merge(attacker.getUUID(), hp, Float::sum);
        if (!CsDamage.sameTeam(attacker, victim)) of(attacker.getUUID()).damage += Math.round(hp);
        dirty = true;
    }

    public static void death(ServerPlayer victim, DamageSource src) {
        of(victim.getUUID()).deaths++;
        Entity k = src.getEntity();
        if (k instanceof ServerPlayer killer && killer != victim) {
            Stats s = of(killer.getUUID());
            if (CsDamage.sameTeam(killer, victim)) s.kills--;
            else {
                s.kills++;
                if (src.is(ModDamageTypes.HEADSHOT)) s.headshots++;
            }
        }
        Map<UUID, Float> dmg = DAMAGE.remove(victim.getUUID());
        if (dmg != null) {
            for (var e : dmg.entrySet()) {
                if (k != null && e.getKey().equals(k.getUUID())) continue;
                if (e.getValue() >= 41f) of(e.getKey()).assists++;
            }
        }
        dirty = true;
    }

    public static void markDirty() {
        dirty = true;
    }

    public static void reset() {
        STATS.clear();
        DAMAGE.clear();
        dirty = true;
    }

    public static void tick(MinecraftServer server) {
        if (!dirty || server.getTickCount() % 10 != 0) return;
        dirty = false;
        PacketDistributor.sendToAllPlayers(packet(server));
    }

    public static Packets.Scoreboard packet(MinecraftServer server) {
        List<Packets.Scoreboard.Row> rows = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Stats s = of(p.getUUID());
            CsPlayerData d = CsPlayerData.get(p);
            rows.add(new Packets.Scoreboard.Row(p.getUUID(), p.getGameProfile().getName(), d.team(), s.kills, s.assists, s.deaths, s.headshots, s.damage,
                    Math.max(0, d.money())));
        }
        return new Packets.Scoreboard(rows);
    }

    private CsStats() {
    }
}
