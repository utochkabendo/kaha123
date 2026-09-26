package dev.csarsenal.weapon;

import dev.csarsenal.entity.C4Entity;
import dev.csarsenal.registry.ModEntities;
import dev.csarsenal.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** The bomb: hold right click on the ground for 3.2 s to plant. */
public class C4Item extends Item implements CsItem {
    public static final int PLANT_TICKS = 64;
    private final WeaponDef def;

    public C4Item(WeaponDef def) {
        super(new Item.Properties().stacksTo(1));
        this.def = def;
    }

    @Override
    public WeaponDef def() {
        return def;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.onGround()) return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return PLANT_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        int used = PLANT_TICKS - remaining;
        if (used % 8 == 2 && used < PLANT_TICKS - 6) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), ModSounds.get("c4.press"), SoundSource.PLAYERS, 0.8f, 1f + (used % 3) * 0.08f);
        }
        if (!entity.onGround()) entity.stopUsingItem();
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity.onGround()) {
            C4Entity c4 = new C4Entity(ModEntities.C4.get(), level);
            c4.moveTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), 0);
            c4.setPlanter(entity);
            level.addFreshEntity(c4);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), ModSounds.get("c4.plant"), SoundSource.PLAYERS, 1f, 1f);
            if (entity instanceof ServerPlayer sp) {
                sp.server.getPlayerList().broadcastSystemMessage(Component.translatable("csarsenal.hud.bomb_planted"), true);
            }
            if (!(entity instanceof Player p && p.getAbilities().instabuild)) stack.shrink(1);
        }
        return stack;
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }
}
