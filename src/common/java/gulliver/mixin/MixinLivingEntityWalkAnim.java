package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 1.6.4 verbatim limb-swing rate compensation.
 *
 * 1.6.4 source (sf.java:574, of.java:2872, bey.java:77, rs.java:1437):
 *   float f4 = sqrt(d0*d0 + d1*d1) * 4.0F / getSizeMovementMultiplier();
 * where d0/d1 are the horizontal delta-position this tick. The DIVISION
 * by getSizeMovementMultiplier scales the limb-swing INCREMENT so that
 * a tiny moving sqrt(size)x slower in world units still increments
 * limb-swing at vanilla rate — animation cycles at vanilla frequency
 * AT EVERY SIZE.
 *
 * Modern equivalent: LivingEntity.updateWalkAnimation(distance) turns the
 * distance moved this tick into `min(distance * 4, 1)` and feeds that to
 * walkAnimation.update. We divide the DISTANCE by getSizeMovementMultiplier
 * on the way in, BEFORE vanilla caps it at 1. (Dividing the capped value
 * instead flattened a giant: at size 8 both a walk and a sprint hit the cap
 * and came out at 1/2.8, a slow-motion shuffle that looked the same at both
 * speeds.) The cumulative position then increments at vanilla rate per tick
 * regardless of size — no jump-on-size-change jitter (because we modify
 * rate-of-change, not the cumulative value).
 *
 * Replaces the previous walk-cycle override in MixinHumanoidModelPose /
 * MixinPlayerModelPose which divided walkAnimationPos at render time
 * (causing visible phase jumps when /doublesize-ing mid-walk).
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityWalkAnim {

    @ModifyVariable(method = "updateWalkAnimation", at = @At("HEAD"), argsOnly = true)
    private float gulliver$normalizeDistance(float distance) {
        IResizeableEntity sized = (IResizeableEntity) this;
        float m = sized.getSizeMultiplier();
        if (m == 1.0F) return distance;
        float mov = sized.getSizeMovementMultiplier();
        if (!(mov > 0.0F)) return distance;
        return distance / mov;
    }
}
