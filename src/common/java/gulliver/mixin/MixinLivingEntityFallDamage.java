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

        // Asymmetric scaling:
        //   size <= 1: factor = size² (tinies near-immune to any fall)
        //   size  > 1: factor = sqrt(size) (giants take more, but not
        //              ridiculous — linear scaling combined with the
        //              already-scaled stepHeight*1.5 min was hitting
        //              22 hp for size 8 fall 10, way too much)
        //
        //   size 0.125, fall 10, min=1.14: (10-1.14)*0.0156 = 0.14 → 0 hp
        //   size 0.5,   fall 10, min=2.53: (10-2.53)*0.25  = 1.87 → 1 hp
        //   vanilla,    fall 10:           7 hp
        //   size 4,     fall 10, min=3.6:  (10-3.6)*2     = 12.8 → 12 hp
        //   size 8,     fall 10, min=7.2:  (10-7.2)*2.83  = 7.9  → 7 hp
        //
        // Giant scaling now MILDLY exceeds vanilla. Combined with their
        // larger MAX_HEALTH (size 8 = 160 hp), 7 hp is ~4% of HP, less
        // dangerous than vanilla 7/20 = 35%.
        float factor = size <= 1.0F ? size * size : (float) Math.sqrt(size);
        double damageBlocks = (fallDistance - min - f1) * factor;
        if (damageBlocks <= 0.0D) {
            cir.setReturnValue(0);
            return;
        }
        double fallMult = self.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER);
        cir.setReturnValue(Mth.floor(damageBlocks * multiplier * fallMult));
    }
}
