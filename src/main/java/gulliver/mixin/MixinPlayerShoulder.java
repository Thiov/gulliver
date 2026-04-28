package gulliver.mixin;

import gulliver.access.IGulliverShoulderInternal;
import gulliver.common.ShoulderHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Per-tick passenger positioning for the 3-slot carry system. Hand
 * sits in front of carrier extended-arm position; shoulders sit on the
 * shoulder bones. Each carried entity is given noPhysics=true while
 * carried so it doesn't push the carrier or block their movement; flag
 * is cleared on drop.
 */
@Mixin(Player.class)
public abstract class MixinPlayerShoulder {

    @Inject(method = "aiStep", at = @At("RETURN"))
    private void gulliver$positionPassengers(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        IGulliverShoulderInternal cs = (IGulliverShoulderInternal) self;

        UUID hand  = cs.gulliver$getHandEntity();
        UUID right = cs.gulliver$getRightShoulder();
        UUID left  = cs.gulliver$getLeftShoulder();
        if (hand == null && right == null && left == null) return;

        double yaw = Math.toRadians(self.yBodyRot);
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);

        // MC yaw conventions: yaw=0 -> facing +Z (south), so forward
        // direction = (-sin, +cos). Player model faces +Z by default;
        // their RIGHT-arm bone is at body-x = -5, i.e. on the -X side of
        // the model. World-space right direction = (-cos, -sin) at yaw=0
        // gives (-1, 0) = -X. Negation matters — without it the held
        // mob ends up on the LEFT side.
        double fwdX   = -sin;
        double fwdZ   =  cos;
        double rightX = -cos;
        double rightZ = -sin;

        // Vanilla model: shoulder at body-x = ±5/16, arm length 12/16
        // when extended forward. Both scale linearly with bbWidth/0.6
        // (which already reflects sizeMultiplier via Phase 2 dimensions).
        double widthScale = self.getBbWidth() / 0.6D;
        double sideUnit   = (5.0D  / 16.0D) * widthScale;       // shoulder offset
        double armLength  = (8.0D  / 16.0D) * widthScale;       // arm extended forward (closer to body)
        // Shoulder height ≈ 24/32 of body height (just below head).
        double shoulderY  = self.getY() + self.getBbHeight() * (24.0D / 32.0D);

        if (hand != null) {
            Entity h = lookup(self, hand);
            if (h == null) {
                cs.gulliver$setHandEntity(null);
            } else {
                // Hand tip = shoulder position + forward arm length, on
                // the carrier's RIGHT SIDE.
                double px = self.getX() + rightX * sideUnit + fwdX * armLength;
                double pz = self.getZ() + rightZ * sideUnit + fwdZ * armLength;
                // Y at shoulder height — arm is horizontal forward, hand
                // tip stays at shoulder Y.
                placePassenger(h, px, shoulderY, pz);
            }
        }
        if (right != null) {
            Entity r = lookup(self, right);
            if (r == null) {
                cs.gulliver$setRightShoulder(null);
            } else {
                double px = self.getX() + rightX * sideUnit;
                double pz = self.getZ() + rightZ * sideUnit;
                placePassenger(r, px, shoulderY, pz);
            }
        }
        if (left != null) {
            Entity l = lookup(self, left);
            if (l == null) {
                cs.gulliver$setLeftShoulder(null);
            } else {
                double px = self.getX() - rightX * sideUnit;
                double pz = self.getZ() - rightZ * sideUnit;
                placePassenger(l, px, shoulderY, pz);
            }
        }
    }

    private static void placePassenger(Entity p, double x, double y, double z) {
        p.setPos(x, y, z);
        p.setDeltaMovement(0.0D, 0.0D, 0.0D);
        p.fallDistance = 0.0F;
        p.noPhysics = true; // disable block + entity collision while carried
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void gulliver$dropOnDeath(DamageSource source, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self instanceof ServerPlayer sp
                && ((IGulliverShoulderInternal) sp).gulliver$hasAnyCarry()) {
            ShoulderHelper.drop(sp);
        }
    }

    private static Entity lookup(Player self, UUID id) {
        if (self.level() instanceof ServerLevel sl) {
            return sl.getEntity(id);
        }
        for (Entity e : self.level().getEntities(self, self.getBoundingBox().inflate(64.0D))) {
            if (e.getUUID().equals(id)) return e;
        }
        return null;
    }
}
