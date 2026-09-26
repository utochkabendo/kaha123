package dev.csarsenal.mixin.client;

import dev.csarsenal.client.render.AgentPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies the CS weapon pose (the one the hitboxes are computed from) to the vanilla player model. */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin<T extends LivingEntity> {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void csarsenal$pose(T entity, float limbSwing, float limbAmount, float age, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!((Object) this instanceof PlayerModel<?>) || !(entity instanceof Player p)) return;
        AgentPose.applyToModel((HumanoidModel<?>) (Object) this, p);
    }
}
