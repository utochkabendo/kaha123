package dev.csarsenal.weapon;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Throwables. Pin pull / throw strength is handled client side, the throw is spawned by the server. */
public class GrenadeItem extends Item implements CsItem {
    public final GrenadeType type;
    private final WeaponDef def;

    public GrenadeItem(GrenadeType type, WeaponDef def, int maxStack) {
        super(new Item.Properties().stacksTo(maxStack));
        this.type = type;
        this.def = def;
    }

    @Override
    public WeaponDef def() {
        return def;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.fail(player.getItemInHand(hand));
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }
}
