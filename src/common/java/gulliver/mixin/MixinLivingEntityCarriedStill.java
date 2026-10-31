package gulliver.mixin;

import gulliver.access.IGulliverShoulderInternal;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A creature in someone's hand keeps still: its legs don't walk with the
 * carrier's stride, the server's head turns don't reach it on a client
 * (the carrier sets its rotation), and before 1.20.5 the server's position
 * updates don't ease it away from the hand (LivingEntity has its own
 * lerpTo there). Its AI is paused in MixinMobCarriedStill.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityCarriedStill {

    private boolean gulliver$held() {
        return ((IGulliverShoulderInternal) this).gulliver$getHoldingEntity() != null;
    }

    @Inject(method = "calculateEntityAnimation", at = @At("HEAD"), cancellable = true)
    private void gulliver$noWalkingInHand(boolean includeHeight, CallbackInfo ci) {
        if (gulliver$held()) {
            ((LivingEntity) (Object) this).walkAnimation.setSpeed(0.0F);
            ci.cancel();
        }
    }

    @Inject(method = "lerpHeadTo", at = @At("HEAD"), cancellable = true)
    private void gulliver$noServerHeadTurn(float yRot, int steps, CallbackInfo ci) {
        if (gulliver$held() && ((LivingEntity) (Object) this).level().isClientSide()) ci.cancel();
    }

    //#if MC < 1.20.5
    //$$ @Inject(method = "lerpTo(DDDFFIZ)V", at = @At("HEAD"), cancellable = true)
    //$$ private void gulliver$noServerTug(double x, double y, double z, float yRot, float xRot, int steps,
    //$$                                   boolean teleport, CallbackInfo ci) {
    //$$     if (gulliver$held() && ((LivingEntity) (Object) this).level().isClientSide()) ci.cancel();
    //$$ }
    //#endif
}
