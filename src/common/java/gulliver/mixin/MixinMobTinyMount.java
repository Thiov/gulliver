package gulliver.mixin;

import gulliver.common.TinyMounts;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A small animal with a tiny player on its back (TinyMounts) is steered by
 * that player like a saddled horse: the player is its controlling
 * passenger on both sides, so the rider's client simulates it and the
 * server follows the vehicle-move packets. None of the animal's own
 * business may get in the way, so its AI is switched off while ridden and
 * it never turns on its rider.
 */
@Mixin(Mob.class)
public abstract class MixinMobTinyMount {

    @Inject(method = "getControllingPassenger", at = @At("RETURN"), cancellable = true)
    private void gulliver$tinyRider(CallbackInfoReturnable<LivingEntity> cir) {
        if (cir.getReturnValue() != null) return;
        LivingEntity rider = TinyMounts.controllerOf((Mob) (Object) this);
        if (rider != null) cir.setReturnValue(rider);
    }

    /** Before 1.21.2 the server keeps running a ridden animal's goals; they must not pull it around. */
    @Inject(method = "isEffectiveAi", at = @At("RETURN"), cancellable = true)
    private void gulliver$noGoalsWhileRidden(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && TinyMounts.controllerOf((Mob) (Object) this) != null) {
            cir.setReturnValue(false);
        }
    }

    /** A wolf angry at its rider (or a fox that fears it) doesn't pick it as a target. */
    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void gulliver$notMyRider(LivingEntity target, CallbackInfo ci) {
        if (target != null && target == TinyMounts.controllerOf((Mob) (Object) this)) ci.cancel();
    }
}
