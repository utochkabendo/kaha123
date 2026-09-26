package dev.csarsenal.registry;

import dev.csarsenal.CsArsenal;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> REGISTER = DeferredRegister.create(Registries.PARTICLE_TYPE, CsArsenal.MODID);

    /** volumetric smoke grenade puff */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SMOKE = REGISTER.register("smoke", () -> new SimpleParticleType(true));
    /** bullet impact dust / debris */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> IMPACT = REGISTER.register("impact", () -> new SimpleParticleType(false));
    /** sparks (metal impacts, zeus) */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPARK = REGISTER.register("spark", () -> new SimpleParticleType(false));
    /** blood mist */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLOOD = REGISTER.register("blood", () -> new SimpleParticleType(false));
    /** inferno flame */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FLAME = REGISTER.register("flame", () -> new SimpleParticleType(true));
    /** explosion fireball */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FIREBALL = REGISTER.register("fireball", () -> new SimpleParticleType(true));
    /** muzzle smoke wisp */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MUZZLE_SMOKE = REGISTER.register("muzzle_smoke", () -> new SimpleParticleType(false));

    private ModParticles() {
    }
}
