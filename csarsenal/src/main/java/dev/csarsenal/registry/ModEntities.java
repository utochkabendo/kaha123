package dev.csarsenal.registry;

import dev.csarsenal.CsArsenal;
import dev.csarsenal.entity.C4Entity;
import dev.csarsenal.entity.GrenadeEntity;
import dev.csarsenal.entity.InfernoEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> REGISTER = DeferredRegister.create(Registries.ENTITY_TYPE, CsArsenal.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<GrenadeEntity>> GRENADE = REGISTER.register("grenade",
            () -> EntityType.Builder.<GrenadeEntity>of(GrenadeEntity::new, MobCategory.MISC).sized(0.2f, 0.2f)
                    .clientTrackingRange(10).updateInterval(1).build("grenade"));
    public static final DeferredHolder<EntityType<?>, EntityType<InfernoEntity>> INFERNO = REGISTER.register("inferno",
            () -> EntityType.Builder.<InfernoEntity>of(InfernoEntity::new, MobCategory.MISC).sized(0.5f, 0.5f).noSummon()
                    .clientTrackingRange(10).updateInterval(20).fireImmune().build("inferno"));
    public static final DeferredHolder<EntityType<?>, EntityType<C4Entity>> C4 = REGISTER.register("planted_c4",
            () -> EntityType.Builder.<C4Entity>of(C4Entity::new, MobCategory.MISC).sized(0.45f, 0.2f)
                    .clientTrackingRange(16).updateInterval(10).fireImmune().build("planted_c4"));

    private ModEntities() {
    }
}
