package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.common.GulliverEnvoy;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.6.4 of.java line 1413-1432 damage scaling for melee attacks:
 *
 *   par2 /= (squish ? sqrt(targetSize) : targetSize);
 *   if (squish || (cause is LivingEntity holding item)) {
 *     if (causeSize < 1 && holdingPointyItem):
 *       par2 *= cbrt(causeSize);     // softer penalty for tiny w/ pointy
 *     else:
 *       par2 *= sqrt(causeSize);     // sqrt scale for everything else
 *   } else {
 *     par2 *= causeSize;             // linear (e.g. arrow damage)
 *   }
 *
 * The "softer penalty" branch lets a tiny-but-armed-with-pointy-thing
 * still hit harder than their pure size would suggest — a 0.125x player
 * with a sword does 0.5x damage instead of 0.125x, because cbrt(0.125)=0.5.
 *
 * Modern hurtServer is the canonical "apply damage from source X for
 * amount Y" entrypoint. We ModifyVariable on the float damage argument.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityDamage {

    /**
     * Damage immunity gap: if attacker is way too small relative to
     * target (ratio <= 0.125, i.e. 8x size diff), short-circuit
     * hurtServer and return false so no damage / hurt-flash / KB applies.
     * Asymmetric — a giant CAN still squish a tiny (inverse ratio
     * doesn't trigger immunity).
     */
    @org.spongepowered.asm.mixin.injection.Inject(method = "hurtServer",
            at = @At("HEAD"), cancellable = true)
    private void gulliver$immuneAtLargeGap(net.minecraft.server.level.ServerLevel level,
                                            DamageSource source, float amount,
                                            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        Entity attacker = source.getEntity();
        if (!(attacker instanceof LivingEntity) || attacker == self) return;
        float attackerSize = ((IResizeableEntity) attacker).getSizeMultiplier();
        float targetSize = ((IResizeableEntity) self).getSizeMultiplier();
        if (attackerSize / targetSize <= 0.125F) {
            cir.setReturnValue(false);
        }
    }

    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float gulliver$scaleDamage(float amount) {
        LivingEntity self = (LivingEntity) (Object) this;

        float targetSize = ((IResizeableEntity) self).getSizeMultiplier();
        float result = amount;
        // Target-side: damage divided by target's size (1.6.4 line 1414).
        if (targetSize != 1.0F) result = result / targetSize;

        DamageSource src = self.getLastDamageSource();
        Entity attacker = src != null ? src.getEntity() : null;
        if (attacker instanceof LivingEntity attackerLiv && attacker != self) {
            float attackerSize = ((IResizeableEntity) attacker).getSizeMultiplier();
            if (attackerSize == 1.0F) return result;

            net.minecraft.world.item.ItemStack hand = attackerLiv.getMainHandItem();
            boolean hasItem = hand != null && !hand.isEmpty();
            if (hasItem) {
                if (attackerSize < 1.0F && GulliverEnvoy.isItemPointy(hand)) {
                    result *= (float) Math.cbrt(attackerSize);
                } else {
                    result *= (float) Math.sqrt(attackerSize);
                }
            } else {
                result *= attackerSize;
            }
        }
        return result;
    }
}
