package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.common.AttackContext;
import gulliver.common.GulliverEnvoy;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.6.4 of.java line 1413-1432 damage scaling for melee attacks. Uses
 * a single @Inject HEAD cancellable that handles immunity, scaling, and
 * attacker-context capture. Recursive guard via thread-local prevents
 * infinite loop when re-calling hurtServer with scaled amount.
 *
 * Why not @ModifyVariable: in early attempts the modify ran BEFORE
 * @Inject HEAD callbacks, so AttackContext was empty when the modifier
 * fired. Trying multi-arg handler signatures was unreliable. The
 * recursive @Inject pattern is fully self-contained and binds without
 * surprises.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityDamage {

    private static final ThreadLocal<Boolean> gulliver$inHurt =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void gulliver$gulliverDamageHandler(net.minecraft.server.level.ServerLevel level,
                                                  DamageSource source, float amount,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if (gulliver$inHurt.get()) {
            // Recursive call from our own scaling. Set attacker context
            // for knockback and let vanilla run with the scaled amount.
            AttackContext.set(source.getEntity());
            return;
        }

        LivingEntity self = (LivingEntity) (Object) this;
        Entity attacker = source.getEntity();
        float targetSize = ((IResizeableEntity) self).getSizeMultiplier();

        if (attacker instanceof LivingEntity attackerLiv && attacker != self) {
            float attackerSize = ((IResizeableEntity) attacker).getSizeMultiplier();

            // Damage immunity gap: 8x size disparity in either direction →
            // no damage. A microscopic mob can't bite a giant; a giant
            // doesn't get hurt by a microscopic mob's hit either. The
            // user's case (size 0.125 hits size 1) returns false here.
            if (attackerSize / targetSize <= 0.125F) {
                cir.setReturnValue(false);
                return;
            }

            float scaled = amount;
            // Target-side: divide by target size (1.6.4 line 1414).
            if (targetSize != 1.0F) scaled = scaled / targetSize;

            // Attacker-side scaling.
            if (attackerSize != 1.0F) {
                net.minecraft.world.item.ItemStack hand = attackerLiv.getMainHandItem();
                boolean hasItem = hand != null && !hand.isEmpty();
                if (hasItem) {
                    if (attackerSize < 1.0F && GulliverEnvoy.isItemPointy(hand)) {
                        scaled *= (float) Math.cbrt(attackerSize);
                    } else {
                        scaled *= (float) Math.sqrt(attackerSize);
                    }
                } else {
                    scaled *= attackerSize;  // bare-hands linear (1.6.4 line 1432)
                }
            }

            if (scaled != amount) {
                // Recurse with scaled amount; inHurt guard makes the
                // re-entry skip this whole block and fall through to
                // vanilla logic.
                gulliver$inHurt.set(Boolean.TRUE);
                try {
                    AttackContext.set(attacker);
                    boolean result = self.hurtServer(level, source, scaled);
                    cir.setReturnValue(result);
                } finally {
                    gulliver$inHurt.set(Boolean.FALSE);
                    AttackContext.clear();
                }
                return;
            }
        }

        // No LivingEntity attacker (fall, drown, lava, cactus, etc.).
        // 1.6.4 line 1395 gates target-side scaling on `cause != null`,
        // so non-entity damage sources are NOT scaled by target size.
        // Fall damage in particular is already pre-scaled inside
        // MixinLivingEntityFallDamage's calculateFallDamage override —
        // dividing by target size again would 8x a tiny's fall damage.
        // No scaling here. Just set attacker context for any KB the
        // vanilla path may fire.
        AttackContext.set(attacker);
    }

    @Inject(method = "hurtServer", at = @At("RETURN"))
    private void gulliver$clearAttacker(net.minecraft.server.level.ServerLevel level,
                                          DamageSource source, float amount,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (!gulliver$inHurt.get()) AttackContext.clear();
    }
}
