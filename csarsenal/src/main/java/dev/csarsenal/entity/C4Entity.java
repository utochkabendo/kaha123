package dev.csarsenal.entity;

import dev.csarsenal.Config;
import dev.csarsenal.ballistics.HitGroup;
import dev.csarsenal.network.Packets;
import dev.csarsenal.registry.ModDamageTypes;
import dev.csarsenal.registry.ModSounds;
import dev.csarsenal.server.CsDamage;
import dev.csarsenal.server.CsPlayerData;
import dev.csarsenal.weapon.Weapons;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/** A planted C4. Explodes after the bomb timer unless defused (hold right click: 10 s, 5 s with a kit). */
public class C4Entity extends Entity {
    private static final EntityDataAccessor<Integer> TICKS_LEFT = SynchedEntityData.defineId(C4Entity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DEFUSE = SynchedEntityData.defineId(C4Entity.class, EntityDataSerializers.FLOAT);

    private UUID planter;
    private UUID defuser;
    private int lastDefuseTick = -100;
    private int defuseTicks;
    private int nextBeep;
    private boolean defused;
    public int clientBeepFlash;

    public C4Entity(EntityType<? extends C4Entity> type, Level level) {
        super(type, level);
        entityData.set(TICKS_LEFT, Config.SPEC.isLoaded() ? Config.BOMB_TIMER.get() * 20 : 800);
    }

    public void setPlanter(LivingEntity e) {
        planter = e.getUUID();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(TICKS_LEFT, 800);
        b.define(DEFUSE, 0f);
    }

    public int ticksLeft() {
        return entityData.get(TICKS_LEFT);
    }

    public float defuseProgress() {
        return entityData.get(DEFUSE);
    }

    @Override
    public void tick() {
        super.tick();
        if (!onGround()) {
            setDeltaMovement(getDeltaMovement().add(0, -0.04, 0));
            move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().multiply(0.6, 0.98, 0.6));
        }
        if (level().isClientSide) {
            if (clientBeepFlash > 0) clientBeepFlash--;
            return;
        }
        if (defused) return;
        int left = ticksLeft() - 1;
        entityData.set(TICKS_LEFT, left);
        if (left <= 0) {
            explode();
            return;
        }
        // beeps speed up as the timer runs out
        if (--nextBeep <= 0) {
            level().playSound(null, getX(), getY(), getZ(), ModSounds.get("c4.beep"), SoundSource.BLOCKS, 1.5f, 1f);
            float frac = left / (float) (Config.SPEC.isLoaded() ? Config.BOMB_TIMER.get() * 20 : 800);
            nextBeep = Math.max(2, Math.round(3 + 17 * frac));
        }
        // defusing
        if (defuser != null) {
            Entity d = ((ServerLevel) level()).getEntity(defuser);
            if (!(d instanceof ServerPlayer p) || tickCount - lastDefuseTick > 6 || p.distanceToSqr(this) > 3.5 * 3.5 || !p.isAlive()) {
                defuser = null;
                defuseTicks = 0;
                entityData.set(DEFUSE, 0f);
            } else {
                defuseTicks++;
                int need = CsPlayerData.get(p).defuser() ? 100 : 200;
                entityData.set(DEFUSE, defuseTicks / (float) need);
                if (defuseTicks % 10 == 0) {
                    int pct = defuseTicks * 100 / need;
                    StringBuilder bar = new StringBuilder();
                    for (int i = 0; i < 20; i++) bar.append(i < pct / 5 ? '|' : '.');
                    p.displayClientMessage(Component.translatable("csarsenal.hud.defusing").append(" [" + bar + "] " + String.format("%.1fs", (need - defuseTicks) / 20f)).withStyle(ChatFormatting.AQUA), true);
                }
                if (defuseTicks >= need) {
                    defused = true;
                    level().playSound(null, getX(), getY(), getZ(), ModSounds.get("c4.defused"), SoundSource.BLOCKS, 2f, 1f);
                    ((ServerLevel) level()).getServer().getPlayerList().broadcastSystemMessage(Component.translatable("csarsenal.hud.bomb_defused").withStyle(ChatFormatting.BLUE), true);
                    discard();
                }
            }
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp && CsPlayerData.get(sp).team() == CsPlayerData.TEAM_T) return InteractionResult.PASS;
        if (defuser == null || defuser.equals(player.getUUID())) {
            if (defuser == null) {
                level().playSound(null, getX(), getY(), getZ(), ModSounds.get("c4.defuse"), SoundSource.BLOCKS, 1f, 1f);
            }
            defuser = player.getUUID();
            lastDefuseTick = tickCount;
        }
        return InteractionResult.CONSUME;
    }

    private void explode() {
        ServerLevel level = (ServerLevel) level();
        Vec3 c = position().add(0, 0.3, 0);
        level.playSound(null, c.x, c.y, c.z, ModSounds.get("c4.explode"), SoundSource.BLOCKS, 8f, 1f);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(c) < 400 * 400) PacketDistributor.sendToPlayer(p, new Packets.Effect(Packets.Effect.C4, c, 0));
        }
        double radius = 1750 * 0.025;
        double sigma = radius / 3.0;
        Entity owner = planter != null ? level.getEntity(planter) : null;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(radius))) {
            double dist = e.getBoundingBox().getCenter().distanceTo(c);
            float dmg = (float) (500 * Math.exp(-(dist * dist) / (2 * sigma * sigma)));
            if (dmg < 1) continue;
            CsDamage.Context ctx = new CsDamage.Context();
            ctx.weapon = Weapons.C4;
            ctx.group = HitGroup.GENERIC;
            ctx.type = ModDamageTypes.BOMB;
            ctx.quietImpact = true;
            CsDamage.apply(owner instanceof LivingEntity le ? le : null, e, dmg, ctx);
        }
        if (Config.get(Config.C4_BREAKS_BLOCKS, false)) {
            level.explode(this, c.x, c.y, c.z, 8f, Level.ExplosionInteraction.TNT);
        }
        discard();
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(TICKS_LEFT, tag.getInt("Left"));
        if (tag.hasUUID("Planter")) planter = tag.getUUID("Planter");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Left", ticksLeft());
        if (planter != null) tag.putUUID("Planter", planter);
    }
}
