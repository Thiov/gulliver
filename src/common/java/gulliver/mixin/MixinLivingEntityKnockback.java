package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.common.AttackContext;
import gulliver.common.SizeScaling;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 1.6.4 of.java:1667-1722 knockback scaling. Modern entry:
 * LivingEntity.knockback(double strength, double x, double z).
 *
 *   strength' = strength * clamp(attackerSize / targetSize, 1/8, 8)
 *   if isSticky: strength' *= 0.25
 *
 * Examples (base strength 0.4 from a vanilla bare-hands hit):
 *   tiny 0.125 hits vanilla: 0.4 * 0.125/1 = 0.05  (negligible)
 *   tiny 0.125 hits tiny:    0.4 * 1     = 0.4    (vanilla feel)
 *   vanilla hits tiny 0.125: 0.4 * 1/0.125 = 3.2  (sent flying)
 *   giant 4 hits vanilla:    0.4 * 4     = 1.6    (big hit)
 *   giant 4 hits tiny 0.125: 0.4 * 8 (capped from 32) = 3.2
 *
 * The cap keeps the full 1/32..32 size range sane: uncapped, a size-32
 * giant against a size-1/32 tiny would be a 1000x knockback.
 *
 * Attacker context: vanilla calls knockback(D,D,D) BEFORE setting
 * lastDamageSource (offset 460 vs 557 in hurtServer). We capture
 * the source via @Inject HEAD on hurtServer into a thread-local
 * stack, read it in the knockback ModifyVariable (matched to the
 * victim), and pop at RETURN; see AttackContext for how leaks are
 * contained.
 *
 * If knockback fires outside a hurtServer context (explosion, fall,
 * direct invoke) no frame matches and we fall back to /size only —
 * knockback still inverse-proportional to target's mass.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityKnockback {

    //#if MC >= 26.2
    @ModifyVariable(method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double gulliver$scaleKnockback(double strength, double strength2, double x, double z,
                                            net.minecraft.world.damagesource.DamageSource source) {
        net.minecraft.world.entity.Entity attacker = source != null && source.getEntity() != null
                ? source.getEntity() : AttackContext.get((LivingEntity) (Object) this);
        return gulliver$scaled(strength, attacker);
    }
    //#else
    //$$ @ModifyVariable(method = "knockback(DDD)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    //$$ private double gulliver$scaleKnockback(double strength) {
    //$$     return gulliver$scaled(strength, AttackContext.get((LivingEntity) (Object) this));
    //$$ }
    //#endif

    private double gulliver$scaled(double strength, net.minecraft.world.entity.Entity attacker) {
        LivingEntity self = (LivingEntity) (Object) this;
        IResizeableEntity sized = (IResizeableEntity) self;
        float targetSize = sized.getSizeMultiplier();
        double scaled = strength;
        if (attacker != null && attacker != self) {
            float attackerSize = ((IResizeableEntity) attacker).getSizeMultiplier();
            scaled = strength * SizeScaling.clampRatio(attackerSize / targetSize);
        } else if (targetSize != 1.0F) {
            // No attacker (explosion, environment): light targets are tossed
            // further, but capped — uncapped 1/size flung a 0.125 tiny 8x —
            // and heavy ones barely move.
            scaled = strength * Math.max(1.0D / SizeScaling.MAX_HIT_RATIO, Math.min(3.0D, 1.0D / targetSize));
        }
        if (sized.isSticky()) scaled *= 0.25D;
        return scaled;
    }
}
