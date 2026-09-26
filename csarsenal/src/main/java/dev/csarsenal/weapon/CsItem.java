package dev.csarsenal.weapon;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Marker for every item of the mod that puts the player into "CS mode". */
public interface CsItem {
    WeaponDef def();

    static WeaponDef defOf(ItemStack stack) {
        return stack.getItem() instanceof CsItem c ? c.def() : null;
    }

    static boolean holding(LivingEntity e) {
        return e.getMainHandItem().getItem() instanceof CsItem;
    }
}
