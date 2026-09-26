package dev.csarsenal.entity;

import dev.csarsenal.ballistics.Ballistics;
import dev.csarsenal.ballistics.HitGroup;
import dev.csarsenal.network.Packets;
import dev.csarsenal.registry.ModDamageTypes;
import dev.csarsenal.registry.ModEntities;
import dev.csarsenal.registry.ModSounds;
import dev.csarsenal.server.CsDamage;
import dev.csarsenal.server.PlayerState;
import dev.csarsenal.weapon.GrenadeType;
import dev.csarsenal.weapon.WeaponDef;
import dev.csarsenal.weapon.WeaponItem;
import dev.csarsenal.weapon.Weapons;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/** A thrown CS grenade with CS bounce physics. */
public class GrenadeEntity extends Projectile {
    private static final EntityDataAccessor<Byte> TYPE = SynchedEntityData.defineId(GrenadeEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DETONATED_AT = SynchedEntityData.defineId(GrenadeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DECOY_WEAPON = SynchedEntityData.defineId(GrenadeEntity.class, EntityDataSerializers.INT);

    /** CS: sv_gravity 800 * grenade gravity 0.4 -> blocks / tick^2 */
    public static final double GRAVITY = 800 * 0.4 * Ballistics.UNIT / 400.0;
    public static final double ELASTICITY = 0.45;
    public static final int SMOKE_TICKS = 18 * 20, SMOKE_FADE = 2 * 20;
    public static final float SMOKE_RADIUS = 144 * 0.025f + 0.2f;

    private static final Set<GrenadeEntity> SMOKES = Collections.newSetFromMap(new WeakHashMap<>());

    public int age;
    private int restTicks;
    public boolean resting;
    /** client visual spin */
    public float spin, spinO;
    private int nextDecoyShot;
    private int decoyShots;

    public GrenadeEntity(EntityType<? extends GrenadeEntity> type, Level level) {
        super(type, level);
    }

    public void setup(GrenadeType t, LivingEntity owner, Vec3 pos, Vec3 vel) {
        entityData.set(TYPE, (byte) t.ordinal());
        setOwner(owner);
        setPos(pos);
        setDeltaMovement(vel);
        if (t == GrenadeType.DECOY && owner instanceof Player p) {
            int w = Weapons.USP_S.index;
            for (ItemStack s : p.getInventory().items) {
                if (s.getItem() instanceof WeaponItem wi && wi.def().isPrimary()) {
                    w = wi.def().index;
                    break;
                }
            }
            entityData.set(DECOY_WEAPON, w);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(TYPE, (byte) 0);
        b.define(DETONATED_AT, -1);
        b.define(DECOY_WEAPON, 0);
    }

    public GrenadeType grenadeType() {
        return GrenadeType.byOrdinal(entityData.get(TYPE));
    }

    public int detonatedAt() {
        return entityData.get(DETONATED_AT);
    }

    public boolean detonated() {
        return detonatedAt() >= 0;
    }

    public int decoyWeapon() {
        return entityData.get(DECOY_WEAPON);
    }

    @Override
    public void tick() {
        super.tick();
        spinO = spin;
        Vec3 v = getDeltaMovement();
        if (!resting) {
            v = v.add(0, -GRAVITY, 0);
            Vec3 pos = position();
            Vec3 next = pos.add(v);
            BlockHitResult hit = level().clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (hit.getType() == HitResult.Type.BLOCK) {
                Vec3 n = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
                double vn = v.dot(n);
                Vec3 vt = v.subtract(n.scale(vn));
                boolean floor = n.y > 0.7;
                v = vt.scale(floor ? 0.7 : 0.85).subtract(n.scale(vn * ELASTICITY));
                setPos(hit.getLocation().add(n.scale(0.07)));
                if (Math.abs(vn) > 0.04) {
                    level().playSound(null, getX(), getY(), getZ(), ModSounds.get(grenadeType() == GrenadeType.MOLOTOV ? "grenade.bounce" : "grenade.bounce"),
                            SoundSource.PLAYERS, (float) Mth.clamp(Math.abs(vn) * 4, 0.2, 1.0), 0.9f + random.nextFloat() * 0.2f);
                }
                if (floor && grenadeType().isFire() && !level().isClientSide && !detonated()) {
                    detonate();
                    return;
                }
                if (floor && v.lengthSqr() < 0.0009) {
                    resting = true;
                    v = Vec3.ZERO;
                }
            } else {
                setPos(next);
                if (hit.getType() == HitResult.Type.MISS && onGroundCheck()) {
                    v = new Vec3(v.x * 0.9, v.y, v.z * 0.9);
                }
            }
            if (level().isClientSide) spin += (float) (v.length() * 90);
        } else if (!level().getBlockState(BlockPos.containing(position().add(0, -0.12, 0))).isSolid()) {
            resting = false;
        }
        setDeltaMovement(v);
        age++;
        if (!level().isClientSide) serverTick();
    }

    private boolean onGroundCheck() {
        return !level().getBlockState(BlockPos.containing(position().add(0, -0.1, 0))).getCollisionShape(level(), BlockPos.containing(position().add(0, -0.1, 0))).isEmpty();
    }

    private void serverTick() {
        GrenadeType t = grenadeType();
        if (!detonated()) {
            if (resting) restTicks++;
            boolean go = switch (t) {
                case HE, FLASH -> age >= t.fuse * 20;
                case MOLOTOV, INCENDIARY -> age >= t.fuse * 20;
                case SMOKE -> (resting && restTicks >= 2 && age >= 20) || age >= 20 * 12;
                case DECOY -> (resting && age >= 20) || age >= 20 * 10;
            };
            if (go) detonate();
            if (age > 20 * 60) discard();
            return;
        }
        int since = age - detonatedAt();
        if (t == GrenadeType.SMOKE) {
            if (since == 0) extinguishNearby();
            if (since > SMOKE_TICKS + SMOKE_FADE) {
                SMOKES.remove(this);
                discard();
            }
        } else if (t == GrenadeType.DECOY) {
            if (since >= nextDecoyShot && since < 15 * 20) {
                WeaponDef d = Weapons.byIndex(decoyWeapon());
                if (d != null) {
                    PacketDistributor.sendToPlayersTrackingEntity(this, new Packets.Effect(Packets.Effect.DECOY_SHOT, position(), d.index));
                }
                decoyShots++;
                boolean burst = decoyShots % 4 != 0;
                nextDecoyShot = since + (burst ? Math.max(2, Math.round(d != null ? d.cycleTime * 20 : 3)) : 12 + random.nextInt(25));
            }
            if (since >= 15 * 20) {
                explodeHE(5f, 2f);
                discard();
            }
        }
    }

    private void detonate() {
        GrenadeType t = grenadeType();
        entityData.set(DETONATED_AT, age);
        ServerLevel level = (ServerLevel) level();
        switch (t) {
            case HE -> {
                explodeHE(98f, 350 * 0.025f);
                discard();
            }
            case FLASH -> {
                flash();
                discard();
            }
            case SMOKE -> {
                resting = true;
                setDeltaMovement(Vec3.ZERO);
                SMOKES.add(this);
                level.playSound(null, getX(), getY(), getZ(), ModSounds.get("grenade.smoke_emit"), SoundSource.PLAYERS, 1.2f, 1f);
                PacketDistributor.sendToPlayersTrackingEntity(this, new Packets.Effect(Packets.Effect.SMOKE_POP, position(), getId()));
            }
            case MOLOTOV, INCENDIARY -> {
                Vec3 ground = findGround(4.5);
                level.playSound(null, getX(), getY(), getZ(), ModSounds.get("grenade.molotov_shatter"), SoundSource.PLAYERS, 1.3f, t == GrenadeType.INCENDIARY ? 1.1f : 1f);
                if (ground != null) {
                    if (inSmoke(ground)) {
                        PacketDistributor.sendToPlayersTrackingEntity(this, new Packets.Effect(Packets.Effect.EXTINGUISH, ground, 0));
                        level.playSound(null, ground.x, ground.y, ground.z, ModSounds.get("grenade.inferno_extinguish"), SoundSource.PLAYERS, 1f, 1f);
                    } else {
                        InfernoEntity inf = new InfernoEntity(ModEntities.INFERNO.get(), level);
                        inf.setup(ground, getOwner() instanceof LivingEntity le ? le : null, t == GrenadeType.INCENDIARY);
                        level.addFreshEntity(inf);
                    }
                }
                PacketDistributor.sendToPlayersTrackingEntity(this, new Packets.Effect(Packets.Effect.MOLOTOV, position(), t == GrenadeType.INCENDIARY ? 1 : 0));
                discard();
            }
            case DECOY -> {
                nextDecoyShot = 10;
                resting = true;
            }
        }
    }

    private Vec3 findGround(double maxDown) {
        Vec3 p = position();
        BlockHitResult hit = level().clip(new ClipContext(p.add(0, 0.2, 0), p.add(0, -maxDown, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        if (!level().getFluidState(hit.getBlockPos()).isEmpty()) return null; // fire goes out in water
        return hit.getLocation().add(0, 0.05, 0);
    }

    /** gaussian falloff radius damage, blocked by walls (CS RadiusDamage) */
    private void explodeHE(float maxDamage, float radius) {
        ServerLevel level = (ServerLevel) level();
        Vec3 c = position().add(0, 0.1, 0);
        level.playSound(null, c.x, c.y, c.z, ModSounds.get(maxDamage > 20 ? "grenade.he_explode" : "impact.metal"), SoundSource.PLAYERS, maxDamage > 20 ? 4f : 1f, 1f);
        PacketDistributor.sendToPlayersTrackingEntity(this, new Packets.Effect(Packets.Effect.HE, c, maxDamage > 20 ? 1 : 0));
        level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, maxDamage > 20 ? 3 : 1, 0.3, 0.3, 0.3, 0);
        double sigma = radius / 3.0;
        Entity owner = getOwner();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(radius))) {
            Vec3 target = e.getBoundingBox().getCenter();
            double dist = target.distanceTo(c);
            if (dist > radius) continue;
            if (!visible(c, target, e) && !visible(c, e.getEyePosition(), e) && !visible(c, e.position().add(0, 0.1, 0), e)) continue;
            float dmg = (float) (maxDamage * Math.exp(-(dist * dist) / (2 * sigma * sigma)));
            if (dmg < 1f) continue;
            CsDamage.Context ctx = new CsDamage.Context();
            ctx.weapon = Weapons.HEGRENADE;
            ctx.group = HitGroup.GENERIC;
            ctx.type = ModDamageTypes.GRENADE;
            ctx.quietImpact = true;
            CsDamage.apply(owner instanceof LivingEntity le ? le : null, e, dmg, ctx);
            Vec3 push = target.subtract(c).normalize().scale(0.25 * dmg / maxDamage);
            e.push(push.x, push.y * 0.5 + 0.05, push.z);
        }
        // HE clears smoke around it for a moment (CS2)
    }

    private boolean visible(Vec3 from, Vec3 to, Entity target) {
        BlockHitResult hit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(to) < 0.04;
    }

    private void flash() {
        ServerLevel level = (ServerLevel) level();
        Vec3 c = position().add(0, 0.1, 0);
        level.playSound(null, c.x, c.y, c.z, ModSounds.get("grenade.flash_explode"), SoundSource.PLAYERS, 3.5f, 1f);
        PacketDistributor.sendToPlayersTrackingEntity(this, new Packets.Effect(Packets.Effect.FLASH, c, 0));
        for (ServerPlayer p : level.players()) {
            Vec3 eye = p.getEyePosition();
            double dist = eye.distanceTo(c);
            if (dist > 60 || !visible(c, eye, p)) continue;
            Vec3 look = p.getLookAngle();
            double dot = look.dot(c.subtract(eye).normalize());
            double facing = Mth.clamp((dot + 0.25) / 0.95, 0, 1);
            double strength = 0.22 + 0.78 * facing * facing;
            double distFactor = Mth.clamp(1.0 - (dist - 4.0) / 45.0, 0.08, 1.0);
            float duration = (float) (4.87 * strength * distFactor);
            if (duration < 0.2f) continue;
            PacketDistributor.sendToPlayer(p, new Packets.Flash((float) (strength * distFactor), duration, c));
            PlayerState ps = PlayerState.of(p);
            ps.flashedUntilMs = Math.max(ps.flashedUntilMs, System.currentTimeMillis() + (long) (duration * 700));
        }
        for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(c, c).inflate(20))) {
            if (!visible(c, m.getEyePosition(), m)) continue;
            m.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
            m.setTarget(null);
        }
    }

    // ------------------------------------------------------------------------------------------ smoke helpers
    public static List<GrenadeEntity> activeSmokes(Level level) {
        List<GrenadeEntity> out = new ArrayList<>();
        for (GrenadeEntity g : SMOKES) {
            if (g.isAlive() && g.level() == level && g.detonated()) out.add(g);
        }
        return out;
    }

    public Vec3 smokeCenter() {
        return position().add(0, 1.2, 0);
    }

    /** does the segment a-b pass through this smoke? */
    public boolean smokeBlocks(Vec3 a, Vec3 b) {
        Vec3 c = smokeCenter();
        Vec3 ab = b.subtract(a);
        double t = Mth.clamp(c.subtract(a).dot(ab) / Math.max(1e-6, ab.lengthSqr()), 0, 1);
        return a.add(ab.scale(t)).distanceToSqr(c) < SMOKE_RADIUS * SMOKE_RADIUS * 0.8;
    }

    public boolean inSmoke(Vec3 p) {
        for (GrenadeEntity g : activeSmokes(level())) {
            if (g.smokeCenter().distanceToSqr(p) < (SMOKE_RADIUS + 0.5) * (SMOKE_RADIUS + 0.5)) return true;
        }
        return false;
    }

    private void extinguishNearby() {
        for (InfernoEntity inf : level().getEntitiesOfClass(InfernoEntity.class, getBoundingBox().inflate(SMOKE_RADIUS + 3))) {
            if (inf.overlaps(smokeCenter(), SMOKE_RADIUS + 0.5)) inf.extinguish();
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        SMOKES.remove(this);
        super.remove(reason);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("GType", entityData.get(TYPE));
        tag.putInt("Age", age);
        tag.putInt("Det", detonatedAt());
        tag.putBoolean("Rest", resting);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(TYPE, tag.getByte("GType"));
        age = tag.getInt("Age");
        entityData.set(DETONATED_AT, tag.contains("Det") ? tag.getInt("Det") : -1);
        resting = tag.getBoolean("Rest");
        if (detonated() && grenadeType() == GrenadeType.SMOKE) SMOKES.add(this);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 128 * 128;
    }
}
