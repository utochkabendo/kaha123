package dev.csarsenal.mixin;

import dev.csarsenal.server.CsMovementHook;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void csarsenal$jump(CallbackInfo ci) {
        if ((Object) this instanceof Player p && p.level().isClientSide && p.isLocalPlayer()) {
            CsMovementHook.Handler h = CsMovementHook.handler;
            if (h != null && h.jump(p)) ci.cancel();
        }
    }
}
