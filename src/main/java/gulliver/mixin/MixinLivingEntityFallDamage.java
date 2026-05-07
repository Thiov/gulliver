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
 * 1.6.4 fall-damage scaling. Verbatim from of.java:1879-1894 (doLivingFall)
 * combined with the receive-damage divide at of.java:1390-1395:
 *
 *   doLivingFall:
 *     sh        = stepHeight (already size-scaled)
 *     sizeroot  = sqrt(size)
 *     threshold = sizeroot >= 1 ? 3.0 : 0.125 + 2.875 * sizeroot
 *     min       = max(sh * 1.5, threshold)
 *     f1        = jumpBoost amplifier + 1   (0 if no jump boost)
 *     damage    = (fallDist - min - f1) * sizeroot
 *     var2      = ceil(damage)
 *     attackEntityFrom(DamageSource.fall, var2)
 *
 *   damageEntity (when source is fall):
 *     par2 /= sizeroot
 *
 *   final  = ceil((fallDist - min - jb) * sizeroot) / sizeroot
 *
 * Net feel:
 *   Tinies take MORE fall damage (their sizeroot < 1, so /sizeroot > 1).
 *     size 0.125, fall 5 blocks: damage_blocks=(5-1.14)*0.354=1.37,
 *     ceil=2, /0.354=5.65 hp  (vs vanilla 2 hp)
 *   Giants take LESS fall damage (sizeroot > 1).
 *     size 4, fall 10 blocks, stepH=2.4, min=3.6:
 *     damage_blocks=(10-3.6)*2=12.8, ceil=13, /2=6.5 hp (vs vanilla 7)
 *
 * Vanilla 26.x calculateFallDamage(D, F)I:
 *   fallPower = fallDistance + 1e-6 - SAFE_FALL_DISTANCE
 *   return floor(fallPower * multiplier * FALL_DAMAGE_MULTIPLIER)
 *
 * We override at HEAD with cancellable when size != 1, computing the
 * 1.6.4 result and multiplying by vanilla's mult / FALL_DAMAGE_MULTIPLIER
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

        double damageBlocks = (fallDistance - min - f1) * sizeroot;
        if (damageBlocks <= 0.0D) {
            cir.setReturnValue(0);
            return;
        }
        double var2 = Math.ceil(damageBlocks);
        double finalHp = var2 / sizeroot;
        double fallMult = self.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER);
        cir.setReturnValue(Mth.floor(finalHp * multiplier * fallMult));
    }
}
