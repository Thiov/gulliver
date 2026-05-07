package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.common.GulliverEnvoy;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.6.4 blockClimbingRateForTiny — tinies climb soft surfaces (dirt,
 * grass, wool, leaves, sand, etc.) at material-typed rates. Vanilla
 * climbable gives a uniform fast climb (like ladder); 1.6.4's rates
 * were 0.3-0.7 of that — matching the 1.6.4 feel.
 *
 * Two hooks:
 *   1. onClimbable RETURN: report true when adjacent to a soft block
 *      so vanilla's climb-input handling activates.
 *   2. aiStep RETURN: clamp upward Y velocity to the per-material rate
 *      (rate * vanilla-ladder-speed) to slow the climb to 1.6.4 cadence.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityTinyClimb {

    @Inject(method = "onClimbable", at = @At("RETURN"), cancellable = true)
    private void gulliver$tinyClimbSoft(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;
        LivingEntity self = (LivingEntity) (Object) this;
        if (!((IResizeableEntity) self).isTiny()) return;
        if (gulliver$adjacentClimbRate(self) > 0.0F) cir.setReturnValue(true);
    }

    @Inject(method = "aiStep", at = @At("RETURN"))
    private void gulliver$dampClimbSpeed(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        IResizeableEntity sized = (IResizeableEntity) self;
        if (!sized.isTiny()) return;
        float rate = gulliver$adjacentClimbRate(self);
        if (rate <= 0.0F) return;
        Vec3 dm = self.getDeltaMovement();
        // 1.6.4 of.java:2574 verbatim:
        //   y *= ladderRate * 0.5 * sqrt(size) * (isSticky ? 0.5 : 1.0)
        // Express as a CAP on upward velocity (rather than multiply, to
        // be safe against zero current velocity).
        float root = sized.getSizeMultiplierRoot();
        float stickyFactor = sized.isSticky() ? 0.5F : 1.0F;
        if (dm.y > 0.0D) {
            double cap = 0.2D * rate * 0.5D * root * stickyFactor;
            if (dm.y > cap) {
                self.setDeltaMovement(dm.x, cap, dm.z);
            }
        }
        // Wall-cling slow descent at vanilla ladder rate.
        if (dm.y < -0.15D) {
            self.setDeltaMovement(dm.x, -0.15D, dm.z);
        }
    }

    private static float gulliver$adjacentClimbRate(LivingEntity self) {
        AABB bb = self.getBoundingBox().inflate(0.05D, 0.0D, 0.05D);
        int x1 = (int) Math.floor(bb.minX);
        int x2 = (int) Math.floor(bb.maxX);
        int y1 = (int) Math.floor(bb.minY);
        int y2 = (int) Math.floor(bb.maxY);
        int z1 = (int) Math.floor(bb.minZ);
        int z2 = (int) Math.floor(bb.maxZ);
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        float best = 0.0F;
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    mp.set(x, y, z);
                    float r = GulliverEnvoy.blockClimbingRateForTiny(self.level(), mp);
                    if (r > best) best = r;
                }
            }
        }
        return best;
    }
}
