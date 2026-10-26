//#if MC >= 1.20.5
package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.common.Movement;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.6.4 of.java:486-512 step-height — LivingEntity.maxUpStep in MC 26.x
 * reads from Attributes.STEP_HEIGHT and IGNORES the Entity-class mixin's
 * inject (LivingEntity overrides maxUpStep, doesn't call super). Apply
 * the 1.6.4 power-of-2 normalization to the LivingEntity return value
 * ({@link Movement#stepHeight}).
 *
 * (Before 1.20.5 LivingEntity.maxUpStep still calls super, which
 * MixinEntity already scales; scaling here too would apply it twice.)
 *
 * Examples (vanilla maxUpStep = 0.6):
 *   size 0.03125 (1/32):     f = 0.01875 (a third of its height)
 *   size 0.125 (extra-tiny): f = 0.6/8 = 0.075
 *   size 0.5  (small):       f = 0.6/2 * 0.5 = 0.15
 *   size 1.0  (vanilla):     f = 0.6 (untouched)
 *   size 2.0  (huge):        f = 0.6*2 = 1.2  (steps up 2 blocks)
 *   size 8.0:                f = 0.6*8 = 4.8
 *   size 32.0:               f = 19.2
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityStep {

    @Inject(method = "maxUpStep", at = @At("RETURN"), cancellable = true)
    private void gulliver$scaleStep(CallbackInfoReturnable<Float> cir) {
        float m = ((IResizeableEntity) this).getSizeMultiplier();
        if (m == 1.0F) return;
        cir.setReturnValue(Movement.stepHeight(cir.getReturnValue(), m));
    }
}
//#endif
