package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Health regen scaling — tinies regen faster, giants regen slower.
 * Modify the healAmount argument so the same heal call yields more for
 * tinies and less for giants.
 *
 *   amount' = amount / sizeroot
 *
 *   size 0.125 (root 0.354): heal *= 2.83  (tinies regen ~3x faster)
 *   size 0.5  (root 0.707):  heal *= 1.41
 *   size 1                  : heal *= 1
 *   size 2  (root 1.414):    heal *= 0.71
 *   size 4  (root 2.0):      heal *= 0.5
 *   size 8  (root 2.83):     heal *= 0.35   (giants take ~3x longer)
 *
 * Combined with linear max-HP scaling, time-to-full-HP scales as
 * size * sizeroot = size^1.5 — giants take roughly size^1.5 times
 * longer than vanilla, tinies regen size^1.5 times faster.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityHeal {

    @ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float gulliver$scaleHeal(float healAmount) {
        IResizeableEntity sized = (IResizeableEntity) this;
        float root = sized.getSizeMultiplierRoot();
        if (root == 1.0F) return healAmount;
        return healAmount / root;
    }
}
