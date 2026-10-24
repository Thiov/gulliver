package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.common.Climb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.6.4 climb mechanics for tinies:
 *
 *  - blockClimbingRateForTiny: tinies climb soft surfaces (dirt, grass,
 *    wool, leaves, sand, etc.) at per-material rates 0.3-0.7.
 *  - Slime-ball (or any isSticky() condition): tinies can climb ANY
 *    solid surface — 1.6.4 of.java:2730 enters the ladder-equivalent
 *    branch when (isSticky && onGround), and line 2843 gives an upward
 *    push of 0.08 + 0.12*sizeroot when sticky.
 *
 * "Solid surface" is read from the real collision shapes at the body's
 * sides (see {@link Climb}), so a bottom slab, a carpet or a snow layer
 * is a wall, and the floor under the feet never is.
 *
 * Two hooks:
 *   1. onClimbable RETURN: true when (soft rate > 0, sneaking) OR (isSticky
 *      && touching any wall). Vanilla's climb-input handling activates.
 *   2. aiStep RETURN: clamp upward velocity to the per-material rate
 *      (rate * vanilla-ladder-speed * sticky-factor). For sticky-only
 *      (slime ball, no soft rate) use rate=1.0 with the sticky 0.5
 *      factor so the cap = 0.5 * 0.5 * sizeroot * 0.2 = 0.05*sizeroot.
 *      Boost with the 1.6.4 line-2843 push when on ground + sticky, but
 *      only while pressing into the wall or holding jump (vanilla's own
 *      ladder input), and carry the body over the lip at the top.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityTinyClimb {

    @Shadow protected boolean jumping;

    @Inject(method = "onClimbable", at = @At("RETURN"), cancellable = true)
    private void gulliver$tinyClimbSoft(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;
        LivingEntity self = (LivingEntity) (Object) this;
        IResizeableEntity sized = (IResizeableEntity) self;
        if (!sized.isTiny() || !Climb.canClimb(self)) return;
        // Soft-block climb is opt-in via shift (per user 4(338)): a tiny
        // walking past a dirt wall shouldn't pause to climb it. Slime-ball
        // ("sticky") climb stays automatic — that's the explicit-item path.
        boolean shifted = self.isShiftKeyDown();
        Climb.Contact wall = Climb.probe(self, Climb.reach(self), shifted);
        if (!wall.solid) return;
        if (shifted && wall.softRate > 0.0F) {
            cir.setReturnValue(true);
            return;
        }
        if (sized.isSticky()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "aiStep", at = @At("RETURN"))
    private void gulliver$dampClimbSpeed(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        IResizeableEntity sized = (IResizeableEntity) self;
        if (!sized.isTiny() || !Climb.canClimb(self)) return;
        // Match the onClimbable gate: soft-block path requires shift,
        // sticky-block path doesn't.
        boolean shifted = self.isShiftKeyDown();
        double reach = Climb.reach(self);
        Climb.Contact wall = Climb.probe(self, reach, shifted);
        if (!wall.solid) return;
        float rate = shifted ? wall.softRate : 0.0F;
        boolean stickyClimb = rate <= 0.0F && sized.isSticky();
        if (rate <= 0.0F && !stickyClimb) return;

        // Effective rate: if sticky-only (no soft material), use 1.0 (full
        // ladder rate) — line 2843's sticky branch ignores ladderRate.
        float effRate = stickyClimb ? 1.0F : rate;
        Vec3 dm = self.getDeltaMovement();
        // 1.6.4 of.java:2574 verbatim:
        //   y *= ladderRate * 0.5 * sqrt(size) * (isSticky ? 0.5 : 1.0)
        // Expressed as a CAP on upward velocity.
        float root = sized.getSizeMultiplierRoot();
        float stickyFactor = sized.isSticky() ? 0.5F : 1.0F;
        if (dm.y > 0.0D) {
            double cap = 0.2D * effRate * 0.5D * root * stickyFactor;
            if (dm.y > cap) {
                self.setDeltaMovement(dm.x, cap, dm.z);
            }
        }
        // 1.6.4 of.java:2843 ground-push: y = 0.08 + 0.12 * sizeroot when
        // sticky+grounded. Apply once per tick when grounded against a
        // sticky-climbable surface so the player rises off the ground — but
        // only when actually pushing into the wall or holding jump, like
        // vanilla's ladder (standing beside a wall must not hop).
        boolean pushing = self.horizontalCollision || this.jumping;
        if (stickyClimb && pushing && self.onGround()) {
            Vec3 cur = self.getDeltaMovement();
            double push = 0.08D + 0.12D * root;
            if (cur.y < push) {
                self.setDeltaMovement(cur.x, push, cur.z);
            }
        }
        // Wall-cling slow descent at vanilla ladder rate.
        Vec3 cur2 = self.getDeltaMovement();
        if (cur2.y < -0.15D) {
            self.setDeltaMovement(cur2.x, -0.15D, cur2.z);
        }
        // Over the top: the wall's lip (a slab, a carpet, a snow layer) is
        // about to drop out from under the climb.
        Climb.hopOverLedge(self, wall, reach);
    }
}
