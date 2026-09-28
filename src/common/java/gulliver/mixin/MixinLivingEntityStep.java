package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.6.4 of.java:486-512 step-height — LivingEntity.maxUpStep in MC 26.x
 * reads from Attributes.STEP_HEIGHT and IGNORES the Entity-class mixin's
 * inject (LivingEntity overrides maxUpStep, doesn't call super). Apply
 * the 1.6.4 power-of-2 normalization to the LivingEntity return value.
 *
 * Formula:
 *   f = vanilla maxUpStep
 *   norm = 1.0
 *   while (norm * size < 0.5) { norm *= 2;  f /= 2; }
 *   while (norm * size > 1.0) { norm /= 2;  f *= 2; }
 *   if (norm * size < 0.65) f *= norm * size;
 *
 * Examples (vanilla maxUpStep = 0.6):
 *   size 0.125 (extra-tiny): f = 0.6/8 = 0.075
 *   size 0.5  (small):       f = 0.6/2 * 0.5 = 0.15
 *   size 1.0  (vanilla):     f = 0.6 (untouched)
 *   size 2.0  (huge):        f = 0.6*2 = 1.2  (steps up 2 blocks)
 *   size 4.0  (large huge):  f = 0.6*4 = 2.4
 *   size 8.0  (max):         f = 0.6*8 = 4.8
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityStep {

    @Inject(method = "maxUpStep", at = @At("RETURN"), cancellable = true)
    private void gulliver$scaleStep(CallbackInfoReturnable<Float> cir) {
        float m = ((IResizeableEntity) this).getSizeMultiplier();
        if (m == 1.0F) return;
        float f = cir.getReturnValue();
        float norm = 1.0F;
        while (norm * m < 0.5F) { norm *= 2.0F; f /= 2.0F; }
        while (norm * m > 1.0F) { norm /= 2.0F; f *= 2.0F; }
        if (norm * m < 0.65F) { f *= norm * m; }
        cir.setReturnValue(f);
    }
}
