package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

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
 * Modern equivalent: LivingEntity.updateWalkAnimation invokes
 * walkAnimation.update(speed, ...) where speed comes from
 * `min(distance * 4, 1)`. We divide that speed by getSizeMovementMultiplier
 * BEFORE update consumes it. The cumulative position then increments at
 * vanilla rate per tick regardless of size — no jump-on-size-change
 * jitter (because we modify rate-of-change, not the cumulative value).
 *
 * Replaces the previous walk-cycle override in MixinHumanoidModelPose /
 * MixinPlayerModelPose which divided walkAnimationPos at render time
 * (causing visible phase jumps when /doublesize-ing mid-walk).
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityWalkAnim {

    @ModifyArg(method = "updateWalkAnimation",
               at = @At(value = "INVOKE",
                        target = "Lnet/minecraft/world/entity/WalkAnimationState;update(FFF)V"),
               index = 0)
    private float gulliver$normalizeSpeed(float speed) {
        IResizeableEntity sized = (IResizeableEntity) this;
        float m = sized.getSizeMultiplier();
        if (m == 1.0F) return speed;
        float mov = sized.getSizeMovementMultiplier();
        if (mov <= 0.0F) return speed;
        return Math.min(speed / mov, 1.0F);
    }
}
