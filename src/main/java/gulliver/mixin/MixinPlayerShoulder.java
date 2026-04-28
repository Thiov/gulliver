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

        // Use BODY yaw (yBodyRot) instead of head yaw — held entities
        // should track the body direction, not turn with head movement.
        double yaw = Math.toRadians(self.yBodyRot);
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);

        // Shoulder y ≈ top-third of body (just below head).
        double shoulderY = self.getY() + self.getBbHeight() * (24.0D / 32.0D);
        // Side offset = half-shoulder-width, scaled with body width.
        double sideUnit  = (5.0D / 16.0D) * self.getBbWidth() / 0.6D;
        // Hand: extended forward of body. Need to be PAST the carrier's
        // bbox so the held entity doesn't overlap the carrier.
        double frontUnit = self.getBbWidth() * 0.5D + 0.5D;
        // Hand y ≈ shoulder + a small lift (arm extends forward from
        // shoulder, hand is slightly above shoulder when arm raised).
        double handY     = self.getY() + self.getBbHeight() * 0.8D;

        if (hand != null) {
            Entity h = lookup(self, hand);
            if (h == null) {
                cs.gulliver$setHandEntity(null);
            } else {
                double px = self.getX() + (-sin) * frontUnit;
                double pz = self.getZ() + ( cos) * frontUnit;
                placePassenger(h, px, handY, pz);
            }
        }
        if (right != null) {
            Entity r = lookup(self, right);
            if (r == null) {
                cs.gulliver$setRightShoulder(null);
            } else {
                double px = self.getX() + cos * sideUnit;
                double pz = self.getZ() + sin * sideUnit;
                placePassenger(r, px, shoulderY, pz);
            }
        }
        if (left != null) {
            Entity l = lookup(self, left);
            if (l == null) {
                cs.gulliver$setLeftShoulder(null);
            } else {
                double px = self.getX() + (-cos) * sideUnit;
                double pz = self.getZ() + (-sin) * sideUnit;
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
