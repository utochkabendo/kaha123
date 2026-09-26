package dev.csarsenal.weapon;

import dev.csarsenal.registry.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Guns, knives and the Zeus. All input is handled by the client weapon controller and validated on the server. */
public class WeaponItem extends Item implements CsItem {
    private final WeaponDef def;

    public WeaponItem(WeaponDef def) {
        super(new Item.Properties().stacksTo(1));
        this.def = def;
    }

    @Override
    public WeaponDef def() {
        return def;
    }

    public static WeaponState state(ItemStack stack) {
        WeaponState s = stack.get(ModComponents.WEAPON_STATE.get());
        if (s != null) return s;
        WeaponDef d = CsItem.defOf(stack);
        return d == null ? new WeaponState(0, 0, false, false, 0) : WeaponState.full(d);
    }

    public static void setState(ItemStack stack, WeaponState s) {
        stack.set(ModComponents.WEAPON_STATE.get(), s);
    }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack s = super.getDefaultInstance();
        s.set(ModComponents.WEAPON_STATE.get(), WeaponState.full(def));
        return s;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // right click is scope / silencer / burst mode, handled by the weapon controller
        return InteractionResultHolder.fail(player.getItemInHand(hand));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return 0f;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
        return oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        if (def.isGun()) {
            WeaponState s = state(stack);
            String dmg = def.pellets > 1 ? (int) def.damage + " x" + def.pellets : String.valueOf((int) def.damage);
            tip.add(Component.translatable("csarsenal.tooltip.damage", dmg).withStyle(ChatFormatting.GRAY));
            tip.add(Component.translatable("csarsenal.tooltip.armor_pen", String.format("%.1f", def.armorPenetration * 100)).withStyle(ChatFormatting.GRAY));
            tip.add(Component.translatable("csarsenal.tooltip.rpm", Math.round(60f / def.cycleTime)).withStyle(ChatFormatting.GRAY));
            tip.add(Component.translatable("csarsenal.tooltip.magazine", s.ammo(), s.reserve()).withStyle(ChatFormatting.GRAY));
        }
        tip.add(Component.translatable("csarsenal.tooltip.speed", (int) def.maxSpeed).withStyle(ChatFormatting.DARK_GRAY));
        if (def.price > 0) tip.add(Component.translatable("csarsenal.tooltip.price", def.price).withStyle(ChatFormatting.DARK_GREEN));
    }
}
