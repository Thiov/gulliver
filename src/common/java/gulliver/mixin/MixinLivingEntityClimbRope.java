package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.common.Climb;
import gulliver.common.GulliverEnvoy;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.6.4 string/leash climb-rope mechanic — ASM-patched in the original,
 * not visible in JDCore source. Reconstructed from intent of the
 * isHoldingStringOrLeash predicate (GulliverEnvoy):
 *
 *   tiny + string-in-hand                 → can climb walls (rope)
 *   normal-or-medium + lead-in-hand       → can climb walls (rope)
 *
 * The 1.6.4 ASM patch gated rope-climb on pushing into the wall: the body
 * must touch a wall (any collision shape at its sides, see {@link Climb})
 * AND be pressing into it or holding jump. Then onClimbable returns true
 * and the vanilla climbable handler takes over — same lift-rate as a
 * ladder, same gravity damping. Standing or walking beside a wall with a
 * lead in hand is not a climb (it used to cancel fall damage and slow
 * wall-walking by a third).
 *
 * Climbing speed follows the walking gait (sqrt of the size), so a 1/8
 * body doesn't scale a wall eight times faster per body length than a
 * normal one; at the top the body is carried over the lip.
 *
 * Limited to LivingEntity since the predicate requires getMainHandItem.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityClimbRope {

    @Shadow protected boolean jumping;

    /** The latest onClimbable answer came from the rope, not from a ladder or a tiny's own climbing. */
    @Unique private boolean gulliver$ropeClimbing;

    @Inject(method = "onClimbable", at = @At("RETURN"), cancellable = true)
    private void gulliver$leashClimb(CallbackInfoReturnable<Boolean> cir) {
        gulliver$ropeClimbing = false;
        if (cir.getReturnValueZ()) return; // already climbable, no need to add
        LivingEntity self = (LivingEntity) (Object) this;
        if (!GulliverEnvoy.isHoldingStringOrLeash(self) || !Climb.canClimb(self)) return;
        if (!self.horizontalCollision && !this.jumping) return;
        if (Climb.touchingWall(self, Climb.reach(self))) {
            gulliver$ropeClimbing = true;
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "aiStep", at = @At("RETURN"))
    private void gulliver$ropeClimbTick(CallbackInfo ci) {
        if (!gulliver$ropeClimbing) return;
        LivingEntity self = (LivingEntity) (Object) this;
        if (!GulliverEnvoy.isHoldingStringOrLeash(self)) {
            gulliver$ropeClimbing = false;
            return;
        }
        double reach = Climb.reach(self);
        Climb.Contact wall = Climb.probe(self, reach, false);
        if (!wall.solid) return; // already off the wall: nothing to hold back
        Vec3 dm = self.getDeltaMovement();
        double cap = Climb.VANILLA_CLIMB * ((IResizeableEntity) self).getSizeMultiplierRoot();
        if (dm.y > cap + 1.0E-4D) {
            self.setDeltaMovement(dm.x, cap, dm.z);
        }
        Climb.hopOverLedge(self, wall, reach);
    }
}
