package gulliver.mixin;

import gulliver.common.ShoulderHelper;
import gulliver.mixin.iface.IGulliverShoulderInternal;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Per-tick shoulder-passenger position + drop-on-key-events. Mirrors the
 * 1.6.4 EntityResizeablePlayerMP.setPositionAndUpdate dropping the held
 * entity on teleport (covered by direct setPos check below), plus the
 * implicit drop on death / removal.
 */
@Mixin(Player.class)
public abstract class MixinPlayerShoulder {

    @Inject(method = "aiStep", at = @At("RETURN"))
    private void gulliver$positionShoulderPassenger(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        UUID heldId = ((IGulliverShoulderInternal) self).gulliver$getHeldEntity();
        if (heldId == null) return;
        Entity held = lookup(self, heldId);
        if (held == null) {
            // Held entity vanished (despawn, dimension change). Clear our side.
            ((IGulliverShoulderInternal) self).gulliver$setHeldEntity(null);
            return;
        }
        // Shoulder position: just above head, slightly behind, scaled by
        // carrier's eye height (which already accounts for sizeMultiplier
        // via the Phase 2 dimension mixin — so a giant's shoulder is up
        // where a giant's shoulder should be).
        double yaw = Math.toRadians(self.getYRot());
        double offX = -Math.sin(yaw) * 0.0D;   // no forward push
        double offZ =  Math.cos(yaw) * 0.0D;
        // Slight side offset (left shoulder for the carrier)
        double sideX = -Math.cos(yaw) * 0.25D;
        double sideZ = -Math.sin(yaw) * 0.25D;
        held.setPos(self.getX() + offX + sideX,
                    self.getY() + self.getEyeHeight() - 0.4D,
                    self.getZ() + offZ + sideZ);
        held.setDeltaMovement(0.0D, 0.0D, 0.0D);
        held.fallDistance = 0.0F;
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void gulliver$dropOnDeath(DamageSource source, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self instanceof ServerPlayer sp
                && ((IGulliverShoulderInternal) sp).gulliver$getHeldEntity() != null) {
            ShoulderHelper.drop(sp);
        }
    }

    private static Entity lookup(Player self, UUID id) {
        if (self.level() instanceof ServerLevel sl) {
            return sl.getEntity(id);
        }
        // Client: getEntities iterates all loaded entities; cheaper than
        // walking but rare path (only useful for prediction display).
        for (Entity e : self.level().getEntities(self,
                self.getBoundingBox().inflate(64.0D))) {
            if (e.getUUID().equals(id)) return e;
        }
        return null;
    }
}
