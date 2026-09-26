package dev.csarsenal.entity;

import dev.csarsenal.ballistics.HitGroup;
import dev.csarsenal.network.Packets;
import dev.csarsenal.registry.ModDamageTypes;
import dev.csarsenal.registry.ModSounds;
import dev.csarsenal.server.CsDamage;
import dev.csarsenal.weapon.Weapons;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Molotov / incendiary fire spreading over the floor (CS inferno). */
public class InfernoEntity extends Entity implements IEntityWithComplexSpawn {
    public static final int LIFETIME = 7 * 20;
    public static final double MAX_RADIUS = 3.3;
    public static final int MAX_CELLS = 48;

    public final List<BlockPos> cells = new ArrayList<>();
    public int age;
    public boolean incendiary;
    private UUID ownerId;

    public InfernoEntity(EntityType<? extends InfernoEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public void setup(Vec3 ground, LivingEntity owner, boolean incendiary) {
        setPos(ground);
        this.incendiary = incendiary;
        this.ownerId = owner != null ? owner.getUUID() : null;
        cells.clear();
        cells.addAll(spread(level(), BlockPos.containing(ground)));
    }

    /** flood fill over walkable floor cells */
    public static List<BlockPos> spread(Level level, BlockPos start) {
        List<BlockPos> out = new ArrayList<>();
        if (!level.getBlockState(start).getCollisionShape(level, start).isEmpty()) start = start.above();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        q.add(start);
        seen.add(start);
        Vec3 c = Vec3.atBottomCenterOf(start);
        while (!q.isEmpty() && out.size() < MAX_CELLS) {
            BlockPos p = q.poll();
            if (!canBurn(level, p)) continue;
            out.add(p);
            for (Direction d : Direction.Plane.HORIZONTAL) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos n = p.relative(d).above(dy);
                    if (seen.contains(n)) continue;
                    if (Vec3.atBottomCenterOf(n).distanceTo(c) > MAX_RADIUS) continue;
                    // cannot climb through a wall: stepping up needs headroom above the current cell
                    if (dy == 1 && !level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) continue;
                    if (dy == -1 && !level.getBlockState(p.relative(d)).getCollisionShape(level, p.relative(d)).isEmpty()) continue;
                    seen.add(n);
                    q.add(n);
                }
            }
        }
        return out;
    }

    private static boolean canBurn(Level level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (!s.getCollisionShape(level, p).isEmpty()) return false;
        if (!level.getFluidState(p).isEmpty()) return false;
        BlockPos below = p.below();
        BlockState b = level.getBlockState(below);
        return b.isFaceSturdy(level, below, Direction.UP) || !b.getCollisionShape(level, below).isEmpty();
    }

    public int visibleCells() {
        return Math.min(cells.size(), 1 + age * 3);
    }

    public boolean overlaps(Vec3 center, double r) {
        for (BlockPos p : cells) {
            if (Vec3.atCenterOf(p).distanceToSqr(center) < r * r) return true;
        }
        return false;
    }

    public void extinguish() {
        if (level() instanceof ServerLevel sl) {
            sl.playSound(null, getX(), getY(), getZ(), ModSounds.get("grenade.inferno_extinguish"), SoundSource.PLAYERS, 1.2f, 1f);
            PacketDistributor.sendToPlayersTrackingEntity(this, new Packets.Effect(Packets.Effect.EXTINGUISH, position(), 0));
        }
        discard();
    }

    @Override
    public void tick() {
        super.tick();
        age++;
        if (level().isClientSide) return;
        if (age == 1) {
            level().playSound(null, getX(), getY(), getZ(), ModSounds.get("grenade.inferno_start"), SoundSource.PLAYERS, 1.2f, 1f);
        }
        if (age % 10 == 0) {
            for (GrenadeEntity s : GrenadeEntity.activeSmokes(level())) {
                if (overlaps(s.smokeCenter(), GrenadeEntity.SMOKE_RADIUS + 0.4)) {
                    extinguish();
                    return;
                }
            }
        }
        if (age % 5 == 0) {
            Entity owner = ownerId != null && level() instanceof ServerLevel sl ? sl.getEntity(ownerId) : null;
            int n = visibleCells();
            Set<LivingEntity> hurt = new HashSet<>();
            AABB all = null;
            for (int i = 0; i < n; i++) {
                AABB cell = new AABB(cells.get(i)).setMaxY(cells.get(i).getY() + 1.2);
                all = all == null ? cell : all.minmax(cell);
            }
            if (all == null) return;
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, all)) {
                AABB bb = e.getBoundingBox();
                for (int i = 0; i < n; i++) {
                    BlockPos p = cells.get(i);
                    if (bb.intersects(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 0.9, p.getZ() + 1)) {
                        hurt.add(e);
                        break;
                    }
                }
            }
            for (LivingEntity e : hurt) {
                CsDamage.Context c = new CsDamage.Context();
                c.weapon = incendiary ? Weapons.INCGRENADE : Weapons.MOLOTOV;
                c.group = HitGroup.LEFT_LEG;
                c.type = ModDamageTypes.INFERNO;
                c.quietImpact = true;
                // 40 damage per second (CS inferno), leg group multiplier compensated
                CsDamage.apply(owner instanceof LivingEntity le ? le : null, e, 10f / HitGroup.LEFT_LEG.multiplier, c);
            }
        }
        if (age >= LIFETIME || cells.isEmpty()) discard();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        age = tag.getInt("Age");
        incendiary = tag.getBoolean("Inc");
        cells.clear();
        for (long l : tag.getLongArray("Cells")) cells.add(BlockPos.of(l));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", age);
        tag.putBoolean("Inc", incendiary);
        tag.putLongArray("Cells", cells.stream().mapToLong(BlockPos::asLong).toArray());
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(age);
        buf.writeBoolean(incendiary);
        buf.writeVarInt(cells.size());
        for (BlockPos p : cells) buf.writeLong(p.asLong());
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buf) {
        age = buf.readVarInt();
        incendiary = buf.readBoolean();
        int n = buf.readVarInt();
        cells.clear();
        for (int i = 0; i < n; i++) cells.add(BlockPos.of(buf.readLong()));
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
