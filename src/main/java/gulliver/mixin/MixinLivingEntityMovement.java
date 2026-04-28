package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Walk speed and jump power scale with sizeMultiplier so tinies move and
 * jump in proportion to their tiny size, and giants stride and bound in
 * proportion to theirs. The 1.6.4 mod did this through ASM patches into
 * EntityLivingBase that aren't visible in the JDCore decompile, but the
 * shape of the formula is necessarily the same: linear multiplication by
 * sizeMultiplier preserves "seconds per traversed body length" as a
 * constant — a tiny crosses a 1-block tile in the same wall-clock time
 * as a giant crosses a 32-block one (their stride is correspondingly
 * larger / smaller).
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityMovement {

    @Inject(method = "getSpeed", at = @At("RETURN"), cancellable = true)
    private void gulliver$scaleSpeed(CallbackInfoReturnable<Float> cir) {
        float m = ((IResizeableEntity) this).getSizeMultiplier();
        if (m == 1.0F) return;
        cir.setReturnValue(cir.getReturnValue() * m);
    }

    @Inject(method = "getJumpPower()F", at = @At("RETURN"), cancellable = true)
    private void gulliver$scaleJump(CallbackInfoReturnable<Float> cir) {
        float m = ((IResizeableEntity) this).getSizeMultiplier();
        if (m == 1.0F) return;
        cir.setReturnValue(cir.getReturnValue() * m);
    }
}
