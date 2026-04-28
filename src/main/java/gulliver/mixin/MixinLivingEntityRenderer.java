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
            g.gulliver$setSizeMultiplier(sized.getSizeMultiplier());
        }
        // Walk-cycle frequency compensation: a tiny moves at sqrt(size) of
        // vanilla world-speed but covers MORE body-lengths per second
        // (smaller body, same world distance). Vanilla walkAnimationPos
        // increments by world-distance, so tiny anim cycles slowly = the
        // "slow motion" the user reported. Scale walkAnimationPos by
        // 1/sqrt(size) to restore body-length-per-cycle frequency.
        if (state instanceof gulliver.access.IGlideRenderState g2) {
            float size = g2.gulliver$getSizeMultiplier();
            if (size < 1.0F && size > 0.0F) {
                float root = (float) Math.sqrt(size);
                state.walkAnimationPos /= root;
                // Also boost speed clamp so amplitude doesn't get
                // re-clamped to the smaller world-speed value.
                state.walkAnimationSpeed = Math.min(state.walkAnimationSpeed / root, 1.0F);
            }
        }
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
}
