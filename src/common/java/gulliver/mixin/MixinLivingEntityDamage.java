package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.common.AttackContext;
import gulliver.common.GulliverEnvoy;
import gulliver.init.GulliverDamageTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
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
 *     a tiny wielding something pointy gets cbrt instead (weak but real)
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
        AttackContext.push(attacker);
        if (!gulliver$isMelee(source, attacker, self)) return;
        LivingEntity attackerLiv = (LivingEntity) attacker;
        float targetSize = ((IResizeableEntity) self).getSizeMultiplier();
        float attackerSize = ((IResizeableEntity) attacker).getSizeMultiplier();
        boolean pointy = GulliverEnvoy.holdingPointyItem(attackerLiv);

        // A mob swinging at something much smaller often whiffs over it.
        if (attackerLiv instanceof Mob && targetSize < attackerSize && !pointy) {
            float missChance = Math.min(0.9F, 1.0F - targetSize / attackerSize);
            if (self.getRandom().nextFloat() < missChance) {
                AttackContext.pop();
                cir.setReturnValue(false);
                return;
            }
        }
        // A microscopic attacker can't hurt a giant without a weapon.
        if (!pointy && attackerSize / targetSize <= 0.125F) {
            AttackContext.pop();
            cir.setReturnValue(false);
        }
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
        float scaled = amount;
        if (targetSize != 1.0F) scaled /= (float) Math.sqrt(targetSize);
        if (attackerSize != 1.0F) {
            ItemStack hand = attackerLiv.getMainHandItem();
            if (attackerSize < 1.0F && !hand.isEmpty() && GulliverEnvoy.isItemPointy(hand)
                    && gulliver$isMelee(source, attacker, self)) {
                scaled *= (float) Math.cbrt(attackerSize);
            } else {
                scaled *= (float) Math.sqrt(attackerSize);
            }
        }
        return scaled;
    }

    //#if MC >= 1.21.2
    @Inject(method = "hurtServer", at = @At("RETURN"))
    private void gulliver$popAttacker(ServerLevel level, DamageSource source, float amount,
                                       CallbackInfoReturnable<Boolean> cir) {
        AttackContext.pop();
    }
    //#else
    //$$ @Inject(method = "hurt", at = @At("RETURN"))
    //$$ private void gulliver$popAttacker(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
    //$$     if (!((LivingEntity) (Object) this).level().isClientSide()) AttackContext.pop();
    //$$ }
    //#endif

    private static boolean gulliver$isMelee(DamageSource source, Entity attacker, LivingEntity self) {
        return attacker instanceof LivingEntity
                && attacker != self
                && source.getDirectEntity() == attacker
                && !source.is(DamageTypeTags.IS_PROJECTILE)
                && !source.is(DamageTypeTags.IS_EXPLOSION)
                && !source.is(GulliverDamageTypes.PASSIVE);
    }
}
