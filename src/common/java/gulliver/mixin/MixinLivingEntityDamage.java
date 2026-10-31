package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.common.AttackContext;
import gulliver.common.GulliverEnvoy;
import gulliver.common.SizeScaling;
import gulliver.init.GulliverDamageTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Size-scaled damage (1.6.4 of.java 1395-1432, softened to square roots
 * after playtesting):
 *
 *   - target side:   amount / sqrt(targetSize)  — small bodies take more
 *   - attacker side: amount * sqrt(attackerSize) — big bodies hit harder;
 *     a tiny wielding something pointy (either hand) gets cbrt instead
 *     (weak but real)
 *
 * The combined factor is kept within 1/8x..8x, which the default size
 * range (1/8..8) already stays inside; it only bites once the range is
 * opened up, so a size-32 giant against a size-1/32 tiny is a hit 8x as
 * hard, not 1000x.
 *
 * Two melee-only rules sit on top: a much larger mob attacker may whiff
 * over a tiny target (miss chance 1 - target/attacker, max 90%, unless it
 * holds something pointy), and an attacker 8x smaller than its target
 * without a pointy item can't hurt it at all. Projectiles, explosions and
 * other indirect damage keep the scaling but never miss and are never
 * blocked — an arrow doesn't care how tall the archer is.
 *
 * The amount is scaled in place (no re-dispatch), so subclass overrides
 * of hurtServer — Player's difficulty scaling, a Guardian's thorns — run
 * exactly once. Gulliver's own crushing damage is pre-scaled and skipped.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityDamage {

    //#if MC >= 1.21.2
    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void gulliver$missOrImmune(ServerLevel level, DamageSource source, float amount,
                                         CallbackInfoReturnable<Boolean> cir) {
    //#else
    //$$ @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    //$$ private void gulliver$missOrImmune(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
    //$$     if (((LivingEntity) (Object) this).level().isClientSide()) return;
    //#endif
        LivingEntity self = (LivingEntity) (Object) this;
        Entity attacker = source.getEntity();
        AttackContext.push(attacker, self);
        if (!gulliver$isMelee(source, attacker, self)) return;
        LivingEntity attackerLiv = (LivingEntity) attacker;
        float targetSize = ((IResizeableEntity) self).getSizeMultiplier();
        float attackerSize = ((IResizeableEntity) attacker).getSizeMultiplier();
        boolean pointy = GulliverEnvoy.holdingPointyItem(attackerLiv);

        // A mob swinging at something much smaller often whiffs over it.
        if (attackerLiv instanceof Mob && targetSize < attackerSize && !pointy) {
            float missChance = Math.min(0.9F, 1.0F - targetSize / attackerSize);
            if (self.getRandom().nextFloat() < missChance) {
                AttackContext.pop(self);
                cir.setReturnValue(false);
                return;
            }
        }
        // A microscopic attacker can't hurt a giant without a weapon.
        if (!pointy && attackerSize / targetSize <= 0.125F) {
            AttackContext.pop(self);
            cir.setReturnValue(false);
        }
    }

    /** A monster holding its prey up drops it when it gets hurt (Appetite). */
    //#if MC >= 1.21.2
    @Inject(method = "hurtServer", at = @At("RETURN"))
    private void gulliver$dropMealWhenHurt(ServerLevel level, DamageSource source, float amount,
                                           CallbackInfoReturnable<Boolean> cir) {
    //#else
    //$$ @Inject(method = "hurt", at = @At("RETURN"))
    //$$ private void gulliver$dropMealWhenHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
    //#endif
        if (cir.getReturnValueZ()) gulliver.common.Appetite.onHurt((LivingEntity) (Object) this);
    }

    //#if MC >= 1.21.2
    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float gulliver$scaleAmount(float amount, ServerLevel level, DamageSource source) {
    //#else
    //$$ @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    //$$ private float gulliver$scaleAmount(float amount, DamageSource source) {
    //$$     if (((LivingEntity) (Object) this).level().isClientSide()) return amount;
    //#endif
        LivingEntity self = (LivingEntity) (Object) this;
        Entity attacker = source.getEntity();
        if (!(attacker instanceof LivingEntity attackerLiv) || attacker == self) return amount;
        if (source.is(GulliverDamageTypes.PASSIVE)) return amount; // already size-scaled
        float targetSize = ((IResizeableEntity) self).getSizeMultiplier();
        float attackerSize = ((IResizeableEntity) attacker).getSizeMultiplier();
        float ratio = 1.0F;
        if (targetSize != 1.0F) ratio /= (float) Math.sqrt(targetSize);
        if (attackerSize != 1.0F) {
            if (attackerSize < 1.0F && GulliverEnvoy.holdingPointyItem(attackerLiv)
                    && gulliver$isMelee(source, attacker, self)) {
                ratio *= (float) Math.cbrt(attackerSize);
            } else {
                ratio *= (float) Math.sqrt(attackerSize);
            }
        }
        return amount * SizeScaling.clampRatio(ratio);
    }

    //#if MC >= 1.21.2
    @Inject(method = "hurtServer", at = @At("RETURN"))
    private void gulliver$popAttacker(ServerLevel level, DamageSource source, float amount,
                                       CallbackInfoReturnable<Boolean> cir) {
        AttackContext.pop((LivingEntity) (Object) this);
    }
    //#else
    //$$ @Inject(method = "hurt", at = @At("RETURN"))
    //$$ private void gulliver$popAttacker(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
    //$$     if (!((LivingEntity) (Object) this).level().isClientSide()) AttackContext.pop((LivingEntity) (Object) this);
    //$$ }
    //#endif

    private static boolean gulliver$isMelee(DamageSource source, Entity attacker, LivingEntity self) {
        return attacker instanceof LivingEntity
                && attacker != self
                && source.getDirectEntity() == attacker
                && !source.is(DamageTypeTags.IS_PROJECTILE)
                && !source.is(DamageTypeTags.IS_EXPLOSION)
                && !source.is(GulliverDamageTypes.PASSIVE)
                && !source.is(GulliverDamageTypes.EATEN);
    }
}
