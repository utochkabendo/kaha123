package dev.csarsenal.weapon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Per-stack weapon state stored as a data component.
 *
 * @param ammo     rounds in the magazine
 * @param reserve  reserve rounds
 * @param silencer silencer attached (M4A1-S / USP-S)
 * @param altMode  burst mode (Glock / FAMAS)
 * @param readyAt  game time when a recharging weapon (Zeus) is ready again, 0 if ready
 */
public record WeaponState(int ammo, int reserve, boolean silencer, boolean altMode, long readyAt) {
    public static final Codec<WeaponState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("ammo").forGetter(WeaponState::ammo),
            Codec.INT.fieldOf("reserve").forGetter(WeaponState::reserve),
            Codec.BOOL.optionalFieldOf("silencer", false).forGetter(WeaponState::silencer),
            Codec.BOOL.optionalFieldOf("alt", false).forGetter(WeaponState::altMode),
            Codec.LONG.optionalFieldOf("ready_at", 0L).forGetter(WeaponState::readyAt)
    ).apply(i, WeaponState::new));

    public static final StreamCodec<ByteBuf, WeaponState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WeaponState::ammo,
            ByteBufCodecs.VAR_INT, WeaponState::reserve,
            ByteBufCodecs.BOOL, WeaponState::silencer,
            ByteBufCodecs.BOOL, WeaponState::altMode,
            ByteBufCodecs.VAR_LONG, WeaponState::readyAt,
            WeaponState::new);

    public static WeaponState full(WeaponDef def) {
        return new WeaponState(def.magSize, def.reserve, def.silencer, false, 0L);
    }

    public WeaponState withAmmo(int a) {
        return new WeaponState(a, reserve, silencer, altMode, readyAt);
    }

    public WeaponState withAmmo(int a, int r) {
        return new WeaponState(a, r, silencer, altMode, readyAt);
    }

    public WeaponState withSilencer(boolean s) {
        return new WeaponState(ammo, reserve, s, altMode, readyAt);
    }

    public WeaponState withAlt(boolean a) {
        return new WeaponState(ammo, reserve, silencer, a, readyAt);
    }

    public WeaponState withReadyAt(long t) {
        return new WeaponState(ammo, reserve, silencer, altMode, t);
    }
}
