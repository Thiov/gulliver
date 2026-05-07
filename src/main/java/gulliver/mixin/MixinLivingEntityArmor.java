package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Giants get bonus armor that scales with size — additional damage
 * reduction beyond worn-armor pieces. Tinies don't get this benefit
 * (their armor stays whatever they're actually wearing).
 *
 *   bonusArmor = max(0, (size - 1) * 2)   (capped at 30 to avoid the
 *   armor cap behavior at >30 going weird)
 *
 *   size 1     → +0
 *   size 2     → +2
 *   size 4     → +6
 *   size 8     → +14
 *
 * Vanilla armor formula: 4% reduction per point, soft-capped — bonus
 * +14 means giant size 8 reduces incoming damage by ~56% on top of
 * worn armor, BEFORE the size-based damage division (which already
 * divides incoming damage by size). Combined effect: huge giants take
 * tiny fractions of damage from anything not also size-matched.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityArmor {

    @Inject(method = "getArmorValue", at = @At("RETURN"), cancellable = true)
    private void gulliver$bonusArmorForGiants(CallbackInfoReturnable<Integer> cir) {
        float size = ((IResizeableEntity) this).getSizeMultiplier();
        if (size <= 1.0F) return;
        int bonus = (int) Math.min(30.0F, (size - 1.0F) * 2.0F);
        cir.setReturnValue(cir.getReturnValueI() + bonus);
    }
}
