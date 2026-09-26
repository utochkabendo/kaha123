package dev.csarsenal.registry;

import dev.csarsenal.CsArsenal;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public final class ModDamageTypes {
    public static final ResourceKey<DamageType> BULLET = key("bullet");
    public static final ResourceKey<DamageType> HEADSHOT = key("headshot");
    public static final ResourceKey<DamageType> KNIFE = key("knife");
    public static final ResourceKey<DamageType> GRENADE = key("grenade");
    public static final ResourceKey<DamageType> INFERNO = key("inferno");
    public static final ResourceKey<DamageType> BOMB = key("bomb");
    public static final ResourceKey<DamageType> TASER = key("taser");

    private static ResourceKey<DamageType> key(String n) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, CsArsenal.id(n));
    }

    public static DamageSource source(Level level, ResourceKey<DamageType> type, Entity direct, Entity causing) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), direct, causing);
    }

    private ModDamageTypes() {
    }
}
