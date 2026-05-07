package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.6.4 of.java:1879-1894 fall-damage scaling. Reproduces the verbatim
 * formula:
 *
 *   sh        = maxUpStep (already size-scaled by MixinLivingEntityStep)
 *   sizeroot  = sqrt(size)
 *   threshold = sizeroot >= 1.0 ? 3.0 : 0.125 + 2.875 * sizeroot
 *   min       = max(sh * 1.5, threshold)
 *   f1        = jumpBoost amplifier + 1   (0 if no jump boost)
 *   damage    = (fallDistance - min - f1) * sizeroot
 *
 * Net feel: tinies take much less fall damage (sizeroot factor), giants
 * take more. Tinies also start taking damage earlier (sub-3-block falls
 * can hurt at extra-tiny scale) but the overall damage is still small.
 *
 * Vanilla 26.x calculateFallDamage(D, F):
 *   fallPower = fallDistance + 1e-6 - SAFE_FALL_DISTANCE (default 3.0)
 *   return floor(fallPower * multiplier * FALL_DAMAGE_MULTIPLIER)
 *
 * We override at HEAD when size != 1.0, computing the 1.6.4 damage and
 * multiplying through by the modern multiplier and FALL_DAMAGE_MULTIPLIER
 * attribute so external resistances/perks still apply.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityFallDamage {

    @Inject(method = "calculateFallDamage", at = @At("HEAD"), cancellable = true)
    private void gulliver$scaleFallDamage(double fallDistance, float multiplier,
                                           CallbackInfoReturnable<Integer> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        IResizeableEntity sized = (IResizeableEntity) self;
        float size = sized.getSizeMultiplier();
        if (size == 1.0F) return;

        if (self.getType().builtInRegistryHolder().is(EntityTypeTags.FALL_DAMAGE_IMMUNE)) {
            cir.setReturnValue(0);
            return;
        }

        float sizeroot = sized.getSizeMultiplierRoot();
        float sh = self.maxUpStep();
        float threshold = sizeroot >= 1.0F ? 3.0F : 0.125F + 2.875F * sizeroot;
        float min = Math.max(sh * 1.5F, threshold);

        MobEffectInstance jb = self.getEffect(MobEffects.JUMP_BOOST);
        float f1 = jb != null ? jb.getAmplifier() + 1 : 0.0F;

        double damage = (fallDistance - min - f1) * sizeroot;
        if (damage <= 0.0D) {
            cir.setReturnValue(0);
            return;
        }

        double fallMult = self.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER);
        cir.setReturnValue(Mth.floor(damage * multiplier * fallMult));
    }
}
