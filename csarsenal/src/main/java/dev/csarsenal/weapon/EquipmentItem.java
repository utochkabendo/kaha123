package dev.csarsenal.weapon;

import dev.csarsenal.registry.ModAttachments;
import dev.csarsenal.registry.ModSounds;
import dev.csarsenal.server.CsPlayerData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Kevlar, Kevlar + Helmet and the defuse kit: right click to equip. */
public class EquipmentItem extends Item implements CsItem {
    public enum Kind { KEVLAR, ASSAULTSUIT, DEFUSER }

    public final Kind kind;
    private final WeaponDef def;

    public EquipmentItem(Kind kind, WeaponDef def) {
        super(new Item.Properties().stacksTo(16));
        this.kind = kind;
        this.def = def;
    }

    @Override
    public WeaponDef def() {
        return def;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (!apply(sp, kind)) return InteractionResultHolder.fail(stack);
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        level.playSound(player, player.getX(), player.getY(), player.getZ(), ModSounds.get("weapon.draw"), SoundSource.PLAYERS, 1f, 0.9f);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static boolean apply(ServerPlayer sp, Kind kind) {
        CsPlayerData d = sp.getData(ModAttachments.CS_DATA);
        CsPlayerData n = switch (kind) {
            case KEVLAR -> d.armor() >= 100 ? null : d.withArmor(100, d.helmet());
            case ASSAULTSUIT -> d.armor() >= 100 && d.helmet() ? null : d.withArmor(100, true);
            case DEFUSER -> d.defuser() ? null : d.withDefuser(true);
        };
        if (n == null) return false;
        CsPlayerData.set(sp, n);
        return true;
    }
}
