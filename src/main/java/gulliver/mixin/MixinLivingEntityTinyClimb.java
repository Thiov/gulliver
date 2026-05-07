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
 * 1.6.4 climb mechanics for tinies:
 *
 *  - blockClimbingRateForTiny: tinies climb soft surfaces (dirt, grass,
 *    wool, leaves, sand, etc.) at per-material rates 0.3-0.7.
 *  - Slime-ball (or any isSticky() condition): tinies can climb ANY
 *    solid surface — 1.6.4 of.java:2730 enters the ladder-equivalent
 *    branch when (isSticky && onGround), and line 2843 gives an upward
 *    push of 0.08 + 0.12*sizeroot when sticky.
 *
 * Two hooks:
 *   1. onClimbable RETURN: true when (rate > 0) OR (isSticky &&
 *      adjacent to any solid). Vanilla's climb-input handling activates.
 *   2. aiStep RETURN: clamp upward velocity to the per-material rate
 *      (rate * vanilla-ladder-speed * sticky-factor). For sticky-only
 *      (slime ball, no soft rate) use rate=1.0 with the sticky 0.5
 *      factor so the cap = 0.5 * 0.5 * sizeroot * 0.2 = 0.05*sizeroot.
 *      Boost with the 1.6.4 line-2843 push when on ground + sticky.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityTinyClimb {

    @Inject(method = "onClimbable", at = @At("RETURN"), cancellable = true)
    private void gulliver$tinyClimbSoft(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;
        LivingEntity self = (LivingEntity) (Object) this;
        IResizeableEntity sized = (IResizeableEntity) self;
        if (!sized.isTiny()) return;
        if (gulliver$adjacentClimbRate(self) > 0.0F) {
            cir.setReturnValue(true);
            return;
        }
        if (sized.isSticky() && gulliver$hasAdjacentSolid(self)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "aiStep", at = @At("RETURN"))
    private void gulliver$dampClimbSpeed(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        IResizeableEntity sized = (IResizeableEntity) self;
        if (!sized.isTiny()) return;
        float rate = gulliver$adjacentClimbRate(self);
        boolean stickyClimb = sized.isSticky() && rate <= 0.0F
                && gulliver$hasAdjacentSolid(self);
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
        // sticky-climbable surface so the player rises off the ground.
        if (stickyClimb && self.onGround()) {
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

    private static boolean gulliver$hasAdjacentSolid(LivingEntity self) {
        AABB bb = self.getBoundingBox().inflate(0.1D, 0.0D, 0.1D);
        int x1 = (int) Math.floor(bb.minX);
        int x2 = (int) Math.floor(bb.maxX);
        int y1 = (int) Math.floor(bb.minY);
        int y2 = (int) Math.floor(bb.maxY);
        int z1 = (int) Math.floor(bb.minZ);
        int z2 = (int) Math.floor(bb.maxZ);
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    mp.set(x, y, z);
                    var st = self.level().getBlockState(mp);
                    if (st.isAir()) continue;
                    if (st.isFaceSturdy(self.level(), mp,
                            net.minecraft.core.Direction.NORTH)
                        || st.isFaceSturdy(self.level(), mp,
                            net.minecraft.core.Direction.SOUTH)
                        || st.isFaceSturdy(self.level(), mp,
                            net.minecraft.core.Direction.EAST)
                        || st.isFaceSturdy(self.level(), mp,
                            net.minecraft.core.Direction.WEST)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
