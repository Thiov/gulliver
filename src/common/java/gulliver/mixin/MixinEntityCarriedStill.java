package gulliver.mixin;

import gulliver.access.IGulliverShoulderInternal;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A carried entity is placed by its carrier on every client and the
 * server alike (ShoulderHelper.positionPassengers), so on a client it must
 * not also be eased towards the positions and rotations the server sends:
 * that tug-of-war made creatures flicker in the hand. The server copy
 * still sends them (other clients need them the moment it is let go).
 */
@Mixin(Entity.class)
public abstract class MixinEntityCarriedStill {

    private boolean gulliver$heldOnClient() {
        Entity self = (Entity) (Object) this;
        return self.level().isClientSide() && ((IGulliverShoulderInternal) self).gulliver$getHoldingEntity() != null;
    }

    //#if MC >= 26.3
    @Inject(method = "moveOrInterpolateTo(Lnet/minecraft/world/entity/PositionPath;FFZ)V", at = @At("HEAD"), cancellable = true)
    private void gulliver$noServerTug(net.minecraft.world.entity.PositionPath path, float yRot, float xRot,
                                      boolean interpolate, CallbackInfo ci) {
        if (gulliver$heldOnClient()) ci.cancel();
    }

    @Inject(method = "moveOrInterpolateTo(FF)V", at = @At("HEAD"), cancellable = true)
    private void gulliver$noServerTurn(float yRot, float xRot, CallbackInfo ci) {
        if (gulliver$heldOnClient()) ci.cancel();
    }
    //#elif MC >= 1.21.5
    //$$ @Inject(method = "moveOrInterpolateTo(Ljava/util/Optional;Ljava/util/Optional;Ljava/util/Optional;)V",
    //$$         at = @At("HEAD"), cancellable = true)
    //$$ private void gulliver$noServerTug(java.util.Optional<net.minecraft.world.phys.Vec3> pos,
    //$$                                   java.util.Optional<Float> yRot, java.util.Optional<Float> xRot, CallbackInfo ci) {
    //$$     if (gulliver$heldOnClient()) ci.cancel();
    //$$ }
    //#else
    //$$ @Inject(method = "lerpTo(DDDFFIZ)V", at = @At("HEAD"), cancellable = true)
    //$$ private void gulliver$noServerTug(double x, double y, double z, float yRot, float xRot, int steps,
    //$$                                   boolean teleport, CallbackInfo ci) {
    //$$     if (gulliver$heldOnClient()) ci.cancel();
    //$$ }
    //#endif
}
