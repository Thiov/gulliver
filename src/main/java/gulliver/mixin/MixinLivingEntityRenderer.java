package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.common.GulliverEnvoy;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Scale the rendered model by sizeMultiplier. Vanilla's pose-stack scale
 * comes from LivingEntityRenderState.scale which is populated each frame
 * by extractRenderState. Multiplying that field at @Inject RETURN means
 * every layer + the base model picks up the new scale uniformly without
 * needing to mixin the scale() method itself.
 *
 * The 1.6.4 mod's RenderPlayer override scaled the model by
 * getSizeMultiplier() at the start of doRender; the modern equivalent is
 * exactly this state.scale multiply.
 *
 * Also: 1.6.4 hide-in-flower behaviour. When an extra-tiny entity is
 * fully inside a flower bbox (per GulliverEnvoy.isEntityIntersectingPlant)
 * we skip the entire render. Mirrors the 1.6.4 RenderPlayer
 * `if (entity.isHidingInPlant()) return;` short-circuit.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("RETURN"),
            require = 0)
    private void gulliver$applyScale(LivingEntity entity, LivingEntityRenderState state,
                                      float partialTick, CallbackInfo ci) {
        float m = ((IResizeableEntity) entity).getSizeMultiplier();
        if (m != 1.0F) {
            state.scale *= m;
            state.ageScale *= m;
        }
        // Carry glide / umbrella flags onto the state for HumanoidModel
        // setupAnim to read (state pattern hides entity from model path).
        if (state instanceof gulliver.access.IGlideRenderState g) {
            gulliver.api.IResizeableLiving sized = (gulliver.api.IResizeableLiving) entity;
            g.gulliver$setGliding(sized.isGliding());
            g.gulliver$setDoesUmbrella(sized.doesUmbrella());
            g.gulliver$setRafting(sized.isRafting());
            g.gulliver$setSizeMultiplier(sized.getSizeMultiplier());
        }
        // (Walk-frequency compensation is done in MixinHumanoidModelPose,
        // gated on attackTime==0 to avoid composing with attack pose and
        // making the arms cross into the body during punch.)
    }

    /**
     * Skip rendering the entity entirely when it's an extra-tiny inside a
     * plant. shouldRender is the canonical "should we draw this?" gate
     * that all LivingEntityRenderer subclasses inherit; setting return
     * value to false visually hides the model AND its layers, matching
     * the 1.6.4 hide-in-flower visual.
     */
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
    private void gulliver$hideInFlower(LivingEntity entity,
                                        net.minecraft.client.renderer.culling.Frustum frustum,
                                        double cameraX, double cameraY, double cameraZ,
                                        CallbackInfoReturnable<Boolean> cir) {
        IResizeableEntity sized = (IResizeableEntity) entity;
        if (!sized.isExtraTiny()) return;
        if (GulliverEnvoy.isEntityIntersectingPlant(entity)) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Render a lily-pad block under the player when rafting (1.6.4
     * visual: tiny holding lily-pad in water sits ON TOP of a lily-pad
     * block, like a tiny boat).
     */
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("RETURN"),
            require = 0)
    private void gulliver$drawRaftLilypad(LivingEntityRenderState state,
                                           com.mojang.blaze3d.vertex.PoseStack pose,
                                           net.minecraft.client.renderer.SubmitNodeCollector buf,
                                           net.minecraft.client.renderer.state.level.CameraRenderState cam,
                                           CallbackInfo ci) {
        if (!(state instanceof gulliver.access.IGlideRenderState g)) return;
        if (!g.gulliver$isRafting()) return;
        pose.pushPose();
        // The pose-stack at this point is at entity origin. Translate
        // down to feet height (state.scale * 0.0 — the entity's bbox
        // bottom is at y=0 in entity-local space). Rotate Y 180° to match
        // entity facing.
        pose.translate(0.0F, 0.05F, 0.0F);
        pose.scale(1.5F * state.scale, 0.1F * state.scale, 1.5F * state.scale);
        net.minecraft.world.item.ItemStack stack =
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LILY_PAD);
        net.minecraft.client.renderer.item.ItemStackRenderState itemState =
                new net.minecraft.client.renderer.item.ItemStackRenderState();
        net.minecraft.client.Minecraft.getInstance().getItemModelResolver().updateForTopItem(
                itemState, stack, net.minecraft.world.item.ItemDisplayContext.GROUND,
                null, null, 0);
        itemState.submit(pose, buf, 15728880,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }
}
