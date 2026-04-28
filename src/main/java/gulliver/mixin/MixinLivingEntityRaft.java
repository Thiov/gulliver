package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.api.IResizeableLiving;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.6.4 of.java:3269-3337 raft physics — when isRafting (tiny holding
 * lily-pad above water), keep the entity at the water-air boundary by
 * sampling 3 sub-bboxes vertically and applying a y-delta proportional
 * to the water-fraction `da`.
 *
 * Verbatim port:
 *
 *   if (isRafting) {
 *     int i = 3; double da = 0;
 *     for (int j = 0; j < i; j++) {
 *       double y1 = bb.minY + (bb.maxY - bb.minY) * j / i + 0.23 * sizeroot;
 *       double y2 = bb.minY + (bb.maxY - bb.minY) * (j+1) / i + 0.23 * sizeroot;
 *       AABB sub = new AABB(bb.minX, y1, bb.minZ, bb.maxX, y2, bb.maxZ);
 *       if (level.containsAnyLiquid(sub) which is water) da += 1.0/i;
 *     }
 *     double horiz = sqrt(dx*dx + dz*dz);
 *     if (horiz > 0.15 * size) -> spawn splash particles
 *     if (da < 1.0) {
 *       double delta = da * 2 - 1;       // negative when mostly above water
 *       y += 0.04 * delta;
 *     } else {
 *       if (y < 0) y *= 0.4;             // dampen sinking
 *       y += 0.007;                      // slow rise to surface
 *     }
 *     if (onGround) y += 0.04;
 *     if (sneaking) y += 0.04 * sizeroot;
 *   }
 *
 * Net effect: tiny holding lily-pad in water hovers at the surface like
 * a tiny on a tiny boat.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityRaft {

    @Inject(method = "aiStep", at = @At("RETURN"))
    private void gulliver$applyRaft(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        IResizeableLiving sized = (IResizeableLiving) this;
        if (!sized.isRafting()) return;

        float sizemult = sized.getSizeMultiplier();
        float sizeroot = ((IResizeableEntity) self).getSizeMultiplierRoot();

        AABB bb = self.getBoundingBox();
        double rangeY = bb.maxY - bb.minY;
        double da = 0.0;
        for (int j = 0; j < 3; j++) {
            double y1 = bb.minY + rangeY * j / 3.0 + 0.23 * sizeroot;
            double y2 = bb.minY + rangeY * (j + 1) / 3.0 + 0.23 * sizeroot;
            if (gulliver$boxIntersectsWater(self, new AABB(bb.minX, y1, bb.minZ, bb.maxX, y2, bb.maxZ))) {
                da += 1.0 / 3.0;
            }
        }

        Vec3 dm = self.getDeltaMovement();
        double horiz = Math.sqrt(dm.x * dm.x + dm.z * dm.z);

        // splash particles when moving horizontally enough
        if (horiz > 0.15 * sizemult && self.level().isClientSide) {
            double angRad = self.getYRot() * Math.PI / 180.0;
            double cosA = Math.cos(angRad) * sizemult;
            double sinA = Math.sin(angRad) * sizemult;
            int n = (int) (1.0 + horiz * 60.0 * sizemult);
            java.util.Random rng = new java.util.Random();
            for (int i1 = 0; i1 < n; i1++) {
                double r1 = rng.nextFloat() * 2.0F - 1.0F;
                double r2 = (rng.nextInt(2) * 2 - 1) * 0.7 * sizemult;
                double px, pz;
                if (rng.nextBoolean()) {
                    px = self.getX() - cosA * r1 * 0.8 + sinA * r2;
                    pz = self.getZ() - sinA * r1 * 0.8 - cosA * r2;
                } else {
                    px = self.getX() + cosA + sinA * r1 * 0.7;
                    pz = self.getZ() + sinA - cosA * r1 * 0.7;
                }
                self.level().addParticle(ParticleTypes.SPLASH, px,
                        self.getY() - 0.125 * sizemult, pz, dm.x, dm.y, dm.z);
            }
        }

        double y = dm.y;
        if (da < 1.0) {
            // Partially submerged — pull toward surface (-1 if above, +1 if at)
            y += 0.04 * (da * 2.0 - 1.0);
        } else {
            // Fully submerged — dampen sink, slow rise.
            if (y < 0.0) y *= 0.4;
            y += 0.007;
        }
        if (self.onGround()) y += 0.04;
        if (self.isShiftKeyDown()) y += 0.04 * sizeroot;

        self.setDeltaMovement(dm.x, y, dm.z);
        self.fallDistance = 0.0;
    }

    private static boolean gulliver$boxIntersectsWater(LivingEntity self, AABB sub) {
        // Cheap surrogate for 1.6.4 `level.containsLiquid(box, Material.water)`:
        // sample the four bottom corners + center.
        net.minecraft.world.level.Level level = self.level();
        double[] xs = { sub.minX, sub.maxX, (sub.minX + sub.maxX) * 0.5 };
        double[] zs = { sub.minZ, sub.maxZ, (sub.minZ + sub.maxZ) * 0.5 };
        double midY = (sub.minY + sub.maxY) * 0.5;
        for (double x : xs) for (double z : zs) {
            net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(x, midY, z);
            FluidState fs = level.getFluidState(pos);
            if (fs.is(FluidTags.WATER)) return true;
        }
        return false;
    }
}
