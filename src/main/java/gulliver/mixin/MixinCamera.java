package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Scale the third-person camera distance by the focused entity's size.
 * Vanilla returns a fixed 4-block max zoom; multiplying by sizeMultiplier
 * gives a giant a far-back camera and a tiny one snug to the model so
 * the framing stays consistent regardless of size. Same shape as the
 * 1.6.4 RenderHelperLightingDecorationsRenderer scale code.
 *
 * Camera.getMaxZoom is also called to determine how far to push the
 * camera away from the eye position before stopping at a wall — a giant
 * has more room behind their model so the camera can pull further back.
 */
@Mixin(Camera.class)
public abstract class MixinCamera {

    @Shadow private Entity entity;

    @Inject(method = "getMaxZoom", at = @At("RETURN"), cancellable = true)
    private void gulliver$scaleMaxZoom(float current, CallbackInfoReturnable<Float> cir) {
        if (entity == null) return;
        float m = ((IResizeableEntity) entity).getSizeMultiplier();
        if (m == 1.0F) return;
        cir.setReturnValue(cir.getReturnValue() * m);
    }
}
