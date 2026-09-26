package dev.csarsenal.registry;

import dev.csarsenal.CsArsenal;
import dev.csarsenal.weapon.WeaponState;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModComponents {
    public static final DeferredRegister<DataComponentType<?>> REGISTER = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, CsArsenal.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WeaponState>> WEAPON_STATE = REGISTER.register("weapon_state",
            () -> DataComponentType.<WeaponState>builder().persistent(WeaponState.CODEC).networkSynchronized(WeaponState.STREAM_CODEC).build());

    private ModComponents() {
    }
}
