package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.Inject;

/**
 * Third-person camera scaling — exact 1.6.4 bfe.java line 597 behaviour:
 *
 *     double d3 = baseCameraDist;
 *     d3 *= sizeMultiplier;     // ← actual distance, not just the cap
 *
 * The 1.6.4 mod multiplied the live third-person camera distance by
 * sizeMultiplier so the framing stays consistent: a giant has the camera
 * 16 blocks behind their model (4 × 4), a tiny 0.5 blocks back (4 × 0.125).
 *
 * In modern Camera, the third-person path computes
 *   move(-getMaxZoom(scale * fovModifier * camDistanceAttr), 0, 0)
 * scaling by sizeMultiplier on the FIRST arg of move(...) is byte-
 * equivalent to the original's d3 *= pfactor.
 *
 * Also keeps a safety cap on getMaxZoom — when the entity is huge the
 * unscaled vanilla cap (4) would clip the camera against the model, so
 * scaling the cap matches the 1.6.4 behaviour where the collision-probe
 * "stop here" distance was relative to a sizeMultiplier'd 0.1 step.
 */
@Mixin(Camera.class)
public abstract class MixinCamera {

    @Shadow @Final public Entity entity;

    @Inject(method = "getMaxZoom", at = @At("RETURN"), cancellable = true)
    private void gulliver$scaleMaxZoom(float current, CallbackInfoReturnable<Float> cir) {
        if (entity == null) return;
        float m = ((IResizeableEntity) entity).getSizeMultiplier();
        if (m == 1.0F) return;
        cir.setReturnValue(cir.getReturnValue() * m);
    }

    /**
     * Scale the third-person camera-pull-back distance directly.
     * The Camera.update third-person branch calls move(-distance, 0, 0)
     * with the negated max-zoom result; we multiply the (negative)
     * argument by sizeMultiplier to match 1.6.4's d3 *= pfactor.
     */
    @ModifyArg(
        method = "update",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/client/Camera;move(FFF)V",
                 ordinal = 0),
        index = 0
    )
    private float gulliver$scaleThirdPersonDist(float distance) {
        if (entity == null) return distance;
        float m = ((IResizeableEntity) entity).getSizeMultiplier();
        if (m == 1.0F) return distance;
        return distance * m;
    }
}
