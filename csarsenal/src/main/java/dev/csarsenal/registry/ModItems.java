package dev.csarsenal.registry;

import dev.csarsenal.CsArsenal;
import dev.csarsenal.weapon.C4Item;
import dev.csarsenal.weapon.EquipmentItem;
import dev.csarsenal.weapon.GrenadeItem;
import dev.csarsenal.weapon.GrenadeType;
import dev.csarsenal.weapon.WeaponDef;
import dev.csarsenal.weapon.WeaponItem;
import dev.csarsenal.weapon.Weapons;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CsArsenal.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CsArsenal.MODID);

    /** in CS buy-menu order */
    public static final List<DeferredItem<? extends Item>> ORDERED = new ArrayList<>();
    private static final Map<String, DeferredItem<? extends Item>> BY_ID = new HashMap<>();

    static {
        for (WeaponDef d : Weapons.all()) {
            DeferredItem<? extends Item> item = switch (d.category) {
                case GRENADE -> {
                    GrenadeType t = switch (d.id) {
                        case "hegrenade" -> GrenadeType.HE;
                        case "flashbang" -> GrenadeType.FLASH;
                        case "smokegrenade" -> GrenadeType.SMOKE;
                        case "molotov" -> GrenadeType.MOLOTOV;
                        case "incgrenade" -> GrenadeType.INCENDIARY;
                        default -> GrenadeType.DECOY;
                    };
                    int max = t == GrenadeType.FLASH ? 2 : 1;
                    yield ITEMS.register(d.id, () -> new GrenadeItem(t, d, max));
                }
                case C4 -> ITEMS.register(d.id, () -> new C4Item(d));
                case EQUIPMENT -> {
                    EquipmentItem.Kind k = switch (d.id) {
                        case "kevlar" -> EquipmentItem.Kind.KEVLAR;
                        case "assaultsuit" -> EquipmentItem.Kind.ASSAULTSUIT;
                        default -> EquipmentItem.Kind.DEFUSER;
                    };
                    yield ITEMS.register(d.id, () -> new EquipmentItem(k, d));
                }
                default -> ITEMS.register(d.id, () -> new WeaponItem(d));
            };
            ORDERED.add(item);
            BY_ID.put(d.id, item);
        }
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.csarsenal"))
            .icon(() -> get("ak47").getDefaultInstance())
            .displayItems((params, out) -> {
                for (DeferredItem<? extends Item> i : ORDERED) out.accept(i.get().getDefaultInstance());
            })
            .build());

    public static Item get(String id) {
        DeferredItem<? extends Item> i = BY_ID.get(id);
        return i == null ? null : i.get();
    }

    public static ItemStack stack(String id) {
        Item i = get(id);
        return i == null ? ItemStack.EMPTY : i.getDefaultInstance();
    }

    private ModItems() {
    }
}
