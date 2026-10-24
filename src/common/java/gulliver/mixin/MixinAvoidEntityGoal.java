package gulliver.mixin;

import gulliver.access.IGulliverTargeting;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Predicate;

/**
 * Fleeing is not hunting. AvoidEntityGoal builds its scan from combat
 * TargetingConditions, so the rule that hides small creatures from hostile
 * AI (MixinTargetingConditionsTinyHide) also hid them from the creeper
 * that is meant to run from a cat: a tiny cat did not scare it. Mark the
 * goal's conditions so the hiding leaves them alone. (The other
 * constructors delegate to this one.)
 */
@Mixin(AvoidEntityGoal.class)
public abstract class MixinAvoidEntityGoal {

    @Shadow @Final private TargetingConditions avoidEntityTargeting;

    @Inject(method = "<init>(Lnet/minecraft/world/entity/PathfinderMob;Ljava/lang/Class;Ljava/util/function/Predicate;FDDLjava/util/function/Predicate;)V",
            at = @At("TAIL"))
    private void gulliver$seeSmallThreats(PathfinderMob mob, Class<?> avoidClass, Predicate<?> avoidPredicate,
                                          float maxDist, double walkSpeed, double sprintSpeed,
                                          Predicate<?> predicateOnAvoidEntity, CallbackInfo ci) {
        ((IGulliverTargeting) avoidEntityTargeting).gulliver$setSeesSmall(true);
    }
}
