package gulliver.mixin;

import gulliver.access.IGulliverShoulderInternal;
import gulliver.common.ShoulderHelper;
import gulliver.common.TinyMounts;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Two server-side jobs that run from an entity's own tick or its ride
 * calls, so they reach every kind of entity:
 *
 *  - a carried entity checks that its carrier still holds it (mobs without
 *    AI never move, so the old check in move() never ran for them);
 *  - riding by size: a rider more than twice its vehicle's size is refused
 *    (TinyMounts), and dismounted if it grows past that. Rides that were
 *    forced (/ride, string, a reload) are left alone.
 */
@Mixin(Entity.class)
public abstract class MixinEntityCarryRide {

    /** The current ride was forced, so the size rules don't apply to it. */
    @Unique private boolean gulliver$forcedRide;

    @Inject(method = "baseTick", at = @At("HEAD"))
    private void gulliver$carryAndRideTick(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide()) return;
        if (((IGulliverShoulderInternal) self).gulliver$getHoldingEntity() != null) {
            ShoulderHelper.validateCarried(self);
        }
        Entity vehicle = self.getVehicle();
        if (vehicle != null && !gulliver$forcedRide && TinyMounts.tooBigFor(self, vehicle)) {
            self.stopRiding();
        }
    }

    //#if MC >= 1.21.5
    @Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z", at = @At("HEAD"), cancellable = true)
    private void gulliver$sizeRules(Entity vehicle, boolean force, boolean sendEvents,
                                    CallbackInfoReturnable<Boolean> cir) {
    //#else
    //$$ @Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;Z)Z", at = @At("HEAD"), cancellable = true)
    //$$ private void gulliver$sizeRules(Entity vehicle, boolean force, CallbackInfoReturnable<Boolean> cir) {
    //#endif
        if (!force && TinyMounts.refusesRide((Entity) (Object) this, vehicle)) cir.setReturnValue(false);
    }

    //#if MC >= 1.21.5
    @Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z", at = @At("RETURN"))
    private void gulliver$noteForced(Entity vehicle, boolean force, boolean sendEvents,
                                     CallbackInfoReturnable<Boolean> cir) {
    //#else
    //$$ @Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;Z)Z", at = @At("RETURN"))
    //$$ private void gulliver$noteForced(Entity vehicle, boolean force, CallbackInfoReturnable<Boolean> cir) {
    //#endif
        if (cir.getReturnValueZ()) gulliver$forcedRide = force;
    }

    @Inject(method = "removeVehicle", at = @At("HEAD"))
    private void gulliver$clearForced(CallbackInfo ci) {
        gulliver$forcedRide = false;
    }
}
