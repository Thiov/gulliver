package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 1.6.4 of.java:1667-1722 knockback scaling. The modern entry point is
 * LivingEntity.knockback(double strength, double x, double z). Vanilla
 * already multiplies strength by (1 - KNOCKBACK_RESISTANCE) internally,
 * so the size relation goes directly onto the input strength:
 *
 *   strength' = strength / size      (tinies fly farther, giants barely budge)
 *   if isSticky: strength' *= 0.25   (slime-ball / ladder anchors them)
 *
 * Linear ratio matches mass scaling: a tiny is so light any knockback is
 * disproportionately huge; a giant has so much mass that the same impulse
 * barely shifts them.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityKnockback {

    @ModifyVariable(method = "knockback(DDD)V", at = @At("HEAD"),
            argsOnly = true, ordinal = 0)
    private double gulliver$scaleKnockback(double strength) {
        LivingEntity self = (LivingEntity) (Object) this;
        IResizeableEntity sized = (IResizeableEntity) self;
        float size = sized.getSizeMultiplier();
        double scaled = strength;
        if (size != 1.0F) scaled = strength / size;
        if (sized.isSticky()) scaled *= 0.25D;
        return scaled;
    }
}
