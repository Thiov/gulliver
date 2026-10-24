package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.common.Movement;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Port of the 1.6.4 walk-speed + jump-power scaling.
 *
 * Walk speed — 1.6.4 of.java line 2638 multiplies the friction-adjusted
 * forward speed by getSizeMovementMultiplier(): sqrt(size) when
 * !isWeighted, else linear size (MixinEntity.moveRelative). Sqrt scaling
 * preserves "stride per body length per second" — a 0.25x player crosses
 * 0.5 vanilla-blocks per second instead of 0.25, which feels like the same
 * relative gait. This mixin scales the two other places where vanilla
 * hands out horizontal speed and lift: the jump (curve in
 * {@link Movement#scaledJump}) and the sprint-jump boost.
 *
 * Jump — 1.6.4 of.java lines 2451-2489. The hook sits on getJumpPower(float)
 * from 1.21.2 on, the one every jumper goes through (the player jump, a
 * horse's rider-charged jump, a rabbit's hop); older versions have only the
 * no-argument getJumpPower, and the animals that override it get their own
 * hooks (MixinRabbitJumpLegacy, MixinHorseJumpLegacy).
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityMovement {

    @Unique private double gulliver$jumpStartX;
    @Unique private double gulliver$jumpStartZ;

    //#if MC >= 1.21.2
    @Inject(method = "getJumpPower(F)F", at = @At("RETURN"), cancellable = true)
    //#else
    //$$ @Inject(method = "getJumpPower()F", at = @At("RETURN"), cancellable = true)
    //#endif
    private void gulliver$scaleJump(
                                     //#if MC >= 1.21.2
                                     float multiplier,
                                     //#endif
                                     CallbackInfoReturnable<Float> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        float vanilla = cir.getReturnValueF();
        float scaled = Movement.scaledJump(self, vanilla, self.getJumpBoostPower());
        if (scaled != vanilla) cir.setReturnValue(scaled);
    }

    @Inject(method = "jumpFromGround", at = @At("HEAD"))
    private void gulliver$rememberJumpStart(CallbackInfo ci) {
        Vec3 dm = ((LivingEntity) (Object) this).getDeltaMovement();
        gulliver$jumpStartX = dm.x;
        gulliver$jumpStartZ = dm.z;
    }

    /**
     * Vanilla's sprint-jump boost (+0.2 along the look direction) is a
     * fixed number, so a 1/8 body bunny-hopped about twice as fast as it
     * sprints. Scale what the jump added by the same factor as walking.
     */
    @Inject(method = "jumpFromGround", at = @At("RETURN"))
    private void gulliver$scaleSprintBoost(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.isSprinting()) return;
        float mov = ((IResizeableEntity) self).getSizeMovementMultiplier();
        if (!(mov > 0.0F) || mov == 1.0F) return;
        Vec3 dm = self.getDeltaMovement();
        self.setDeltaMovement(gulliver$jumpStartX + (dm.x - gulliver$jumpStartX) * mov, dm.y,
                gulliver$jumpStartZ + (dm.z - gulliver$jumpStartZ) * mov);
    }
}
