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
 * entity a valid candidate?" Returning false here makes the target
 * invisible to that mob's AI scan.
 *
 * 4(338) initial rule: target.isTiny() (absolute size < 0.3) → invisible.
 * 4(350) per user feedback: switched to RELATIVE size — a target whose
 * size is less than 0.3× the attacker's size is invisible. So a
 * size-1 player hides from a size-8 zombie the same way a size-0.125
 * player hides from a size-1 zombie. The "tiny" threshold becomes
 * "much smaller than the attacker" — symmetric across all size pairs.
 *
 * Spider / cave spider / silverfish / endermite / bee attackers are
 * exempt — these small-creature predators still notice prey at any
 * size disparity, matching 1.6.4 og.java line 108-114 (arthropods get
 * minTargetSize=0.1 instead of 0.3).
 *
 * HurtByTargetGoal compat: when the attacker has been hit by this
 * target (attacker.lastHurtByMob == target), retaliation is allowed
 * regardless of size. Without this branch, a tiny attacking a zombie
 * would face a zombie that can't fight back — the goal calls
 * TargetingConditions.test on the lastHurtBy target, and we'd block it.
 */
@Mixin(TargetingConditions.class)
public abstract class MixinTargetingConditionsTinyHide {

    @Inject(method = "test", at = @At("HEAD"), cancellable = true)
    private void gulliver$tinyInvisibleToMobs(ServerLevel level,
                                                LivingEntity attacker,
                                                LivingEntity target,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (target == null || attacker == null) return;
        float targetSize = ((IResizeableEntity) target).getSizeMultiplier();
        float attackerSize = ((IResizeableEntity) attacker).getSizeMultiplier();
        // Relative size threshold: target is "invisible" to this attacker
        // when it's less than 0.3× the attacker's size. Symmetric across
        // size pairs — works for tiny-vs-normal AND normal-vs-giant.
        if (targetSize >= attackerSize * 0.3F) return;
        // Spider/insect attackers ignore the rule.
        if (gulliver$isSmallCreaturePredator(attacker.getType())) return;
        // Retaliation: the goal that's calling test() is validating an
        // already-set HurtByTarget; allow it.
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
