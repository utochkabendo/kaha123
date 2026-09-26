package dev.csarsenal.mixin;

import dev.csarsenal.server.CsMode;
import dev.csarsenal.server.CsMovementHook;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void csarsenal$travel(Vec3 input, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        CsMovementHook.Handler h = CsMovementHook.handler;
        if (h != null && self.level().isClientSide && self.isLocalPlayer() && h.travel(self, input)) ci.cancel();
    }

    /** CS crouching does not stop you at ledges */
    @Inject(method = "isStayingOnGroundSurface", at = @At("HEAD"), cancellable = true)
    private void csarsenal$edge(CallbackInfoReturnable<Boolean> cir) {
        if (CsMode.active((Player) (Object) this)) cir.setReturnValue(false);
    }
}
