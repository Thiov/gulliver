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

            // 4(338) miss chance: a mob attacker much larger than its
            // target rolls a miss with probability 1 - (target/attacker),
            // capped at 90%. Models "swing whiffs over the tiny's head" —
            // a size-1 zombie hitting a size-0.125 player misses 87.5%
            // of the time. Only applies to Mob attackers; Player vs Player
            // hits always land. Skipped if attacker holds a pointy item
            // (precision strike) — sword users always land.
            if (attackerLiv instanceof net.minecraft.world.entity.Mob
                    && targetSize < attackerSize
                    && !GulliverEnvoy.holdingPointyItem(attackerLiv)) {
                float ratio = targetSize / attackerSize;
                float missChance = Math.min(0.9F, 1.0F - ratio);
                if (self.level().getRandom().nextFloat() < missChance) {
                    cir.setReturnValue(false);
                    return;
                }
            }

            // Damage immunity gap: 8x size disparity → no damage when
            // ATTACKER is far smaller than target. A microscopic mob
            // can't bite a giant. EXCEPT when the attacker is wielding a
            // pointy item — sword/stick gives them the precision to land
            // a (cbrt-scaled, much-reduced) hit on a vastly larger foe.
            // The user's case (size 0.125 + stick hits size 1) goes
            // through the cbrt branch below.
            boolean attackerPointy = GulliverEnvoy.holdingPointyItem(attackerLiv);
            if (!attackerPointy && attackerSize / targetSize <= 0.125F) {
                cir.setReturnValue(false);
                return;
            }

            float scaled = amount;
            // 1.6.4 used LINEAR divide-by-targetSize (line 1414) and
            // LINEAR multiply-by-attackerSize for bare-hands (line 1432).
            // At an 8x size disparity (size-1 zombie vs size-0.125 tiny,
            // or size-8 zombie vs size-1 player) that produced an 8x
            // damage multiplier — instant-kill. User feedback 4(349):
            // "way too much".
            //
            // Switch to sqrt for both directions: same direction of
            // scaling (smaller takes more, larger hits harder) but the
            // 8x cases compress to ~2.83x. The pointy-tiny bonus stays
            // at cbrt — a tiny+stick already does very little damage,
            // and weakening it further would make the immunity-bypass
            // path useless.
            if (targetSize != 1.0F) scaled = scaled / (float) Math.sqrt(targetSize);

            if (attackerSize != 1.0F) {
                net.minecraft.world.item.ItemStack hand = attackerLiv.getMainHandItem();
                boolean hasItem = hand != null && !hand.isEmpty();
                if (hasItem && attackerSize < 1.0F && GulliverEnvoy.isItemPointy(hand)) {
                    scaled *= (float) Math.cbrt(attackerSize);
                } else {
                    scaled *= (float) Math.sqrt(attackerSize);
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
