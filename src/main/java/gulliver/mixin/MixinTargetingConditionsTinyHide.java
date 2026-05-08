package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mob AI scanning predicate: {@link TargetingConditions#test} is the
 * single choke point all goals (NearestAttackableTargetGoal,
 * AvoidEntityGoal, FollowOwnerGoal, etc.) route through to ask "is this
 * entity a valid candidate?" If we return false for tinies here, no
 * mob will spontaneously notice them.
 *
 * Per user request 4(338), tinies (size < 0.3) are invisible to ALL mob
 * AI EXCEPT spiders, cave spiders, silverfish, endermites, and bees.
 * Those small-creature types still see and chase tinies — narratively,
 * they're roughly the same scale and not bothered by it.
 *
 * NOTE: this gates spontaneous targeting only. HurtByTargetGoal sets
 * the attacker as last-hurt-by directly, bypassing TargetingConditions,
 * so mobs CAN retaliate when a tiny attacks them — which is what we
 * want. The miss-chance hook (MixinLivingEntityDamage 4(339)) still
 * makes those retaliations difficult to land.
 */
@Mixin(TargetingConditions.class)
public abstract class MixinTargetingConditionsTinyHide {

    @Inject(method = "test", at = @At("HEAD"), cancellable = true)
    private void gulliver$tinyInvisibleToMobs(ServerLevel level,
                                                LivingEntity attacker,
                                                LivingEntity target,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (target == null) return;
        if (!((IResizeableEntity) target).isTiny()) return;
        if (attacker == null) return;
        // Spider/insect attackers still notice tinies actively (passive
        // scanning works for them).
        if (gulliver$isSmallCreaturePredator(attacker.getType())) return;
        // HurtByTargetGoal context: when the attacker has been hit by
        // this target, retaliation is allowed regardless of size. The
        // goal calls TargetingConditions.test with HURT_BY_TARGETING and
        // target=lastHurtByMob; without this branch the AI would be
        // unable to fight back when a tiny hits a zombie/skeleton/etc.
        if (attacker.getLastHurtByMob() == target) return;
        cir.setReturnValue(false);
    }

    private static boolean gulliver$isSmallCreaturePredator(EntityType<?> type) {
        return type == EntityType.SPIDER
            || type == EntityType.CAVE_SPIDER
            || type == EntityType.SILVERFISH
            || type == EntityType.ENDERMITE
            || type == EntityType.BEE;
    }
}
