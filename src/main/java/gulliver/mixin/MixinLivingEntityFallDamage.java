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
 * 1.6.4 fall-damage scaling. of.java:1879-1894 (doLivingFall) computes:
 *
 *   sh        = stepHeight (already size-scaled)
 *   sizeroot  = sqrt(size)
 *   threshold = sizeroot >= 1 ? 3.0 : 0.125 + 2.875 * sizeroot
 *   min       = max(sh * 1.5, threshold)
 *   f1        = jumpBoost amplifier + 1   (0 if no jump boost)
 *   damage    = (fallDist - min - f1) * sizeroot
 *   var2      = ceil(damage)
 *   attackEntityFrom(DamageSource.fall, var2)
 *
 * Note: of.java:1390 has `par2 /= sizeroot` for `nb.m`/`nb.n` damage
 * sources — but those are ANVIL and FALLING_BLOCK damage, NOT fall.
 * Fall damage is `nb.h`, applied verbatim in doLivingFall with no
 * further scaling on the receive side.
 *
 * Net feel — TINIES TAKE LESS, GIANTS TAKE MORE (matches the original
 * mod and real-world physics: smaller things less affected by gravity):
 *
 *   size 0.125, fall 10 blocks, min=1.14:
 *     damage = (10 - 1.14) * 0.354 = 3.13 → 4 hp
 *   vanilla, fall 10 blocks, min=3.0:
 *     damage = 7.0 → 7 hp
 *   size 4, fall 10 blocks, stepH=2.4, min=3.6:
 *     damage = (10 - 3.6) * 2 = 12.8 → 13 hp
 *
 * Vanilla 26.x calculateFallDamage(D, F)I:
 *   fallPower = fallDistance + 1e-6 - SAFE_FALL_DISTANCE
 *   return floor(fallPower * multiplier * FALL_DAMAGE_MULTIPLIER)
 *
 * We override at HEAD with cancellable when size != 1, computing the
 * 1.6.4 ceil(damage) and multiplying by vanilla's mult / FALL_DAMAGE_MULTIPLIER
 * so external resistance/perk attribute changes still apply.
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

        // Asymmetric scaling. Vanilla had user perceive linear-size as
        // "still way too much for tinies" because max HP also scales —
        // 1 hp of 2.5 max is 40%, same as vanilla's 7 of 20. Tinies in
        // reality are weightless (insects survive any fall) so we
        // square the factor for sizes < 1:
        //
        //   factor = size <= 1 ? size * size : size
        //
        //   size 0.125, fall 10, min=1.14: (10-1.14)*0.0156 = 0.138 → 0 hp
        //   size 0.125, fall 30:           (30-1.14)*0.0156 = 0.45  → 0 hp
        //   size 0.125, fall 100:          (100-1.14)*0.0156 = 1.54 → 1 hp
        //   size 0.5,   fall 10, min=2.53: (10-2.53)*0.25 = 1.87    → 1 hp
        //   vanilla,    fall 10:           7 hp
        //   size 4,     fall 10, min=3.6:  (10-3.6)*4 = 25.6        → 25 hp
        //
        // Tinies effectively immune to any reasonable fall; giants take
        // much more than vanilla (proportional to body mass). Floor
        // (not ceil) so sub-1-hp damage rounds DOWN — really small
        // falls give zero damage, not 1 hp.
        float factor = size <= 1.0F ? size * size : size;
        double damageBlocks = (fallDistance - min - f1) * factor;
        if (damageBlocks <= 0.0D) {
            cir.setReturnValue(0);
            return;
        }
        double fallMult = self.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER);
        cir.setReturnValue(Mth.floor(damageBlocks * multiplier * fallMult));
    }
}
