package gulliver.mixin;

import gulliver.access.IGulliverTargeting;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Beneath notice means not even a glance: the conditions an idle mob picks
 * a player (or anything else) to look at with overlook what is far
 * smaller than the mob, the way hostile targeting does
 * (MixinTargetingConditionsTinyHide). A size-8 zombie no longer watches a
 * normal player it is never going to attack. (The other constructors
 * delegate to this one; InteractGoal is a LookAtPlayerGoal too.)
 */
@Mixin(LookAtPlayerGoal.class)
public abstract class MixinLookAtPlayerGoal {

    @Shadow @Final protected TargetingConditions lookAtContext;

    @Inject(method = "<init>(Lnet/minecraft/world/entity/Mob;Ljava/lang/Class;FFZ)V", at = @At("TAIL"))
    private void gulliver$overlookSmall(Mob mob, Class<?> lookAtType, float lookDistance, float probability,
                                        boolean onlyHorizontal, CallbackInfo ci) {
        ((IGulliverTargeting) lookAtContext).gulliver$setOverlooksSmall(true);
    }
}
