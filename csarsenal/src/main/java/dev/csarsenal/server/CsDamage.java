package dev.csarsenal.server;

import dev.csarsenal.Config;
import dev.csarsenal.ballistics.Ballistics;
import dev.csarsenal.ballistics.HitGroup;
import dev.csarsenal.network.Packets;
import dev.csarsenal.registry.ModDamageTypes;
import dev.csarsenal.registry.ModParticles;
import dev.csarsenal.registry.ModSounds;
import dev.csarsenal.weapon.WeaponDef;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Applies CS damage (hit group multiplier, kevlar/helmet, damage scale) and produces kill feed entries. */
public final class CsDamage {
    public static final class Context {
        public WeaponDef weapon;
        public HitGroup group = HitGroup.GENERIC;
        public boolean wallbang, scoped, throughSmoke;
        public ResourceKey<DamageType> type = ModDamageTypes.BULLET;
        public Vec3 hitPos;
        public boolean quietImpact;
    }

    public static boolean sameTeam(Entity a, Entity b) {
        if (!(a instanceof ServerPlayer pa) || !(b instanceof ServerPlayer pb) || a == b) return false;
        int ta = CsPlayerData.get(pa).team(), tb = CsPlayerData.get(pb).team();
        return ta != 0 && ta == tb;
    }

    /**
     * @param csDamage damage before the hit group multiplier / armor (CS units, 100 = full health)
     * @return health damage dealt (CS units)
     */
    public static float apply(LivingEntity attacker, LivingEntity victim, float csDamage, Context c) {
        if (!(victim.level() instanceof ServerLevel level)) return 0;
        if (attacker != null && attacker != victim && sameTeam(attacker, victim) && !Config.get(Config.FRIENDLY_FIRE, true)) return 0;
        float mult = c.group == HitGroup.HEAD ? (c.weapon != null ? c.weapon.headMultiplier : 4f) : c.group.multiplier;
        float dmg = csDamage * mult;
        boolean helmetHit = false;
        boolean kevlarHit = false;
        if (victim instanceof ServerPlayer vp) {
            CsPlayerData d = CsPlayerData.get(vp);
            boolean covered = c.group == HitGroup.HEAD ? d.helmet() : (c.group.kevlar || c.group == HitGroup.GENERIC);
            if (d.armor() > 0 && covered) {
                float ap = c.weapon != null ? c.weapon.armorPenetration : 0.5f;
                float[] r = Ballistics.applyArmor(dmg, ap, d.armor(), true);
                dmg = r[0];
                int newArmor = Math.max(0, d.armor() - Math.round(r[1]));
                CsPlayerData.set(vp, new CsPlayerData(newArmor, d.helmet(), d.defuser(), d.team()));
                helmetHit = c.group == HitGroup.HEAD;
                kevlarHit = !helmetHit;
            }
        }
        if (dmg <= 0) return 0;
        ResourceKey<DamageType> type = c.type;
        if (type == ModDamageTypes.BULLET && c.group == HitGroup.HEAD) type = ModDamageTypes.HEADSHOT;
        DamageSource src = ModDamageTypes.source(level, type, attacker, attacker);
        boolean wasAlive = victim.isAlive();
        float before = victim.getHealth();
        victim.invulnerableTime = 0;
        boolean hurt = victim.hurt(src, dmg * Config.damageScale());
        victim.invulnerableTime = 0;
        if (!hurt) return 0;
        boolean killed = wasAlive && (victim.isDeadOrDying() || !victim.isAlive());

        Vec3 at = c.hitPos != null ? c.hitPos : victim.position().add(0, victim.getBbHeight() * 0.6, 0);
        if (!c.quietImpact) {
            String snd = helmetHit ? "impact.helmet" : c.group == HitGroup.HEAD ? "impact.headshot" : kevlarHit ? "impact.kevlar" : "impact.flesh";
            level.playSound(null, at.x, at.y, at.z, ModSounds.get(snd), SoundSource.PLAYERS, helmetHit ? 1.4f : 1.0f, 0.95f + level.random.nextFloat() * 0.1f);
            if (!helmetHit) level.sendParticles(ModParticles.BLOOD.get(), at.x, at.y, at.z, c.group == HitGroup.HEAD ? 10 : 5, 0.05, 0.05, 0.05, 0.02);
        }
        if (victim instanceof ServerPlayer vp && c.weapon != null) {
            float yawFrom = attacker != null ? (float) Math.toDegrees(Math.atan2(attacker.getX() - vp.getX(), vp.getZ() - attacker.getZ())) : 0;
            PacketDistributor.sendToPlayer(vp, new Packets.Tagged(c.weapon.tagging, dmg, yawFrom, c.group == HitGroup.HEAD));
        }
        if (attacker instanceof ServerPlayer ap && ap != victim) {
            PacketDistributor.sendToPlayer(ap, new Packets.HitConfirm(victim.getId(), dmg, c.group.ordinal(), killed));
        }
        if (killed) killFeed(level, attacker, victim, c);
        return Math.min(dmg, before / Config.damageScale());
    }

    public static void killFeed(ServerLevel level, LivingEntity attacker, LivingEntity victim, Context c) {
        int flags = 0;
        if (c.group == HitGroup.HEAD && c.type == ModDamageTypes.BULLET) flags |= Packets.KillFeed.HEADSHOT;
        if (c.wallbang) flags |= Packets.KillFeed.WALLBANG;
        if (c.weapon != null && c.weapon.hasScope() && c.weapon.category == WeaponDef.Category.SNIPER && !c.scoped) flags |= Packets.KillFeed.NOSCOPE;
        if (c.throughSmoke) flags |= Packets.KillFeed.SMOKE;
        if (attacker instanceof ServerPlayer ap) {
            if (PlayerState.of(ap).flashedUntilMs > System.currentTimeMillis()) flags |= Packets.KillFeed.BLIND;
            if (!ap.onGround()) flags |= Packets.KillFeed.AIR;
        }
        String killer = attacker == null ? "" : attacker.getDisplayName().getString();
        String vict = victim.getDisplayName().getString();
        int kt = attacker instanceof ServerPlayer p ? CsPlayerData.get(p).team() : 0;
        int vt = victim instanceof ServerPlayer p ? CsPlayerData.get(p).team() : 0;
        int w = c.weapon != null ? c.weapon.index : -1;
        Packets.KillFeed kf = new Packets.KillFeed(killer, kt, vict, vt, w, flags);
        for (ServerPlayer p : level.players()) PacketDistributor.sendToPlayer(p, kf);
    }

    private CsDamage() {
    }
}
