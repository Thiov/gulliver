package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 1.6.4 of.java:1667-1722 knockback scaling. Modern entry:
 * LivingEntity.knockback(double strength, double x, double z).
 *
 *   strength' = strength * attackerSize / targetSize
 *   if isSticky: strength' *= 0.25
 *
 * Examples (base strength 0.4 from a vanilla bare-hands hit):
 *   tiny 0.125 hits vanilla: 0.4 * 0.125/1 = 0.05  (negligible)
 *   tiny 0.125 hits tiny:    0.4 * 1     = 0.4    (vanilla feel)
 *   vanilla hits tiny 0.125: 0.4 * 1/0.125 = 3.2  (sent flying)
 *   giant 4 hits vanilla:    0.4 * 4     = 1.6    (big hit)
 *   giant 4 hits tiny 0.125: 0.4 * 32    = 12.8   (catastrophic)
 *
 * Attacker context: vanilla calls knockback(D,D,D) BEFORE setting
 * lastDamageSource (offset 460 vs 557 in hurtServer). We capture
 * the source via @Inject HEAD on hurtServer into a thread-local,
 * read it in the knockback ModifyVariable, and clear at RETURN.
 *
 * If knockback fires outside a hurtServer context (explosion, fall,
 * direct invoke) the thread-local is null and we fall back to /size
 * only — knockback still inverse-proportional to target's mass.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityKnockback {

    @org.spongepowered.asm.mixin.Unique
    private static final ThreadLocal<net.minecraft.world.entity.Entity> gulliver$attackerCtx
            = new ThreadLocal<>();

    @org.spongepowered.asm.mixin.injection.Inject(method = "hurtServer", at = @At("HEAD"))
    private void gulliver$captureAttacker(net.minecraft.server.level.ServerLevel level,
                                            net.minecraft.world.damagesource.DamageSource source,
                                            float amount,
                                            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        net.minecraft.world.entity.Entity a = source.getEntity();
        if (a != null) gulliver$attackerCtx.set(a);
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "hurtServer", at = @At("RETURN"))
    private void gulliver$clearAttacker(net.minecraft.server.level.ServerLevel level,
                                          net.minecraft.world.damagesource.DamageSource source,
                                          float amount,
                                          org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        gulliver$attackerCtx.remove();
    }

    @ModifyVariable(method = "knockback(DDD)V", at = @At("HEAD"),
            argsOnly = true, ordinal = 0)
    private double gulliver$scaleKnockback(double strength) {
        LivingEntity self = (LivingEntity) (Object) this;
        IResizeableEntity sized = (IResizeableEntity) self;
        float targetSize = sized.getSizeMultiplier();
        double scaled = strength;

        net.minecraft.world.entity.Entity attacker = gulliver$attackerCtx.get();
        if (attacker != null && attacker != self) {
            float attackerSize = ((IResizeableEntity) attacker).getSizeMultiplier();
            scaled = strength * (attackerSize / targetSize);
        } else if (targetSize != 1.0F) {
            scaled = strength / targetSize;
        }

        if (sized.isSticky()) scaled *= 0.25D;
        return scaled;
    }
}
