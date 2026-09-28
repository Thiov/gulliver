package gulliver.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import gulliver.api.IResizeableEntity;
import gulliver.api.IResizeableLiving;
import gulliver.client.PropRender;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.20.1: scale the model right after the renderer's own scale() step
 * (so subclasses that override scale() are covered too), and draw the
 * lily-pad raft/umbrella disc in third person.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer {

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;scale(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;F)V",
                     shift = At.Shift.AFTER))
    private void gulliver$applyScale(LivingEntity entity, float yaw, float partialTick, PoseStack pose,
                                      MultiBufferSource buffers, int light, CallbackInfo ci) {
        float m = ((IResizeableEntity) entity).getSizeMultiplier();
        if (m != 1.0F) pose.scale(m, m, m);
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN"))
    private void gulliver$drawRaftLilypad(LivingEntity entity, float yaw, float partialTick, PoseStack pose,
                                           MultiBufferSource buffers, int light, CallbackInfo ci) {
        IResizeableLiving sized = (IResizeableLiving) entity;
        boolean raft = sized.isRafting();
        if (!raft && !sized.doesUmbrella()) return;
        PropRender.lilyDisc(pose, buffers, entity, PropRender.bodyYaw(entity, partialTick), raft, light);
    }
}
