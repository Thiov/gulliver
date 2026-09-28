package gulliver.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import gulliver.api.IResizeableEntity;
import gulliver.api.IResizeableLiving;
import gulliver.client.PropRender;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.20.1: scale the model where 1.21 applies LivingEntityRenderState.scale,
 * right before setupRotations, so every offset the renderer adds after
 * that (swimming, squids, shulkers, phantoms, scale() overrides) grows
 * with the body; the upside-down lift is divided back like 1.21 does.
 * Also draws the lily-pad raft/umbrella disc in third person.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer {

    @Unique private float gulliver$rotationScale = 1.0F;

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;setupRotations(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V"))
    private void gulliver$applyScale(LivingEntity entity, float yaw, float partialTick, PoseStack pose,
                                      MultiBufferSource buffers, int light, CallbackInfo ci) {
        float m = ((IResizeableEntity) entity).getSizeMultiplier();
        if (m != 1.0F) pose.scale(m, m, m);
    }

    @Inject(method = "setupRotations(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V",
            at = @At("HEAD"))
    private void gulliver$rememberScale(LivingEntity entity, PoseStack pose, float bob, float bodyRot,
                                         float partialTick, CallbackInfo ci) {
        gulliver$rotationScale = ((IResizeableEntity) entity).getSizeMultiplier();
    }

    /** Upside-down entities lift by their (already scaled) height: undo the model scale. */
    @ModifyArg(method = "setupRotations(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V",
               at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"),
               index = 1)
    private float gulliver$upsideDownLift(float y) {
        return gulliver$rotationScale > 0.0F ? y / gulliver$rotationScale : y;
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
