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
 * Per-tick passenger positioning for the 3-slot carry system:
 *   - hand:  in front of carrier at chest height
 *   - right shoulder: literally on the carrier's right shoulder bone
 *   - left shoulder:  literally on the carrier's left shoulder bone
 *
 * Plus drop-everything on death.
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

        double yaw = Math.toRadians(self.getYRot());
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);

        // The body bbox top is at carrier.getY() + carrier.bbHeight. Shoulder
        // bone is roughly 4/16 below the head (head occupies top 8/16). For
        // a vanilla 1.8 high body, shoulder y ≈ getY + 1.4. Scale by
        // sizeMultiplier (already baked into bbHeight by Phase 2).
        double shoulderY = self.getY() + self.getBbHeight() * (24.0D / 32.0D); // ~0.75 of height
        // Side offset = half shoulder width = 5/16 in vanilla, scaled by size.
        double sideUnit = (5.0D / 16.0D) * self.getBbWidth() / 0.6D;
        // Hand slot offset: directly in front of chest, slightly low.
        double frontUnit = self.getBbWidth() * 0.6D;
        double chestY    = self.getY() + self.getBbHeight() * 0.65D;

        if (hand != null) {
            Entity h = lookup(self, hand);
            if (h == null) {
                cs.gulliver$setHandEntity(null);
            } else {
                double px = self.getX() + (-sin) * frontUnit;
                double pz = self.getZ() + ( cos) * frontUnit;
                placePassenger(h, px, chestY, pz);
            }
        }
        if (right != null) {
            Entity r = lookup(self, right);
            if (r == null) {
                cs.gulliver$setRightShoulder(null);
            } else {
                // Right shoulder = carrier's right side. In MC, +X-of-yaw
                // direction on right side is `cos`. So right = +cos, +sin.
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
