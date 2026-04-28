package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
 */
@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("RETURN"),
            require = 0)
    private void gulliver$applyScale(LivingEntity entity, LivingEntityRenderState state,
                                      float partialTick, CallbackInfo ci) {
        float m = ((IResizeableEntity) entity).getSizeMultiplier();
        if (m == 1.0F) return;
        state.scale *= m;
        state.ageScale *= m;
    }
}
