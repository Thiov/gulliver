package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.api.IResizeableLiving;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lily-pad raft: while a tiny holding a lily-pad floats on water, snap
 * their feet (bbox.minY) directly to the water surface and zero
 * vertical velocity. Splash particles still fire when moving.
 *
 * Earlier port was a verbatim 1.6.4 of.java:3269-3337 — sample the
 * bbox in 3 vertical sub-boxes, compute submerged-fraction `da`,
 * apply `+0.04 * (da*2 - 1)` velocity each tick. That bounces:
 * gravity drops the player below surface → da rises → upward kick →
 * overshoot above surface → da drops → downward kick → repeat. User
 * report: "very wobbly, fluctuating up and down".
 *
 * Snap-to-surface kills the oscillation — player Y matches the
 * surface every tick exactly, no integration error to compound. The
 * 1.6.4 behaviour is gone, but the user explicitly asked for smooth.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityRaft {

    @Inject(method = "aiStep", at = @At("RETURN"))
    private void gulliver$applyRaft(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        IResizeableLiving sized = (IResizeableLiving) this;
        if (!sized.isRafting()) return;

        float sizemult = sized.getSizeMultiplier();
        Vec3 dm = self.getDeltaMovement();

        // Splash particles when moving horizontally — kept verbatim
        // from 1.6.4. Cosmetic, doesn't affect the position.
        double horiz = Math.sqrt(dm.x * dm.x + dm.z * dm.z);
        if (horiz > 0.15 * sizemult && self.level().isClientSide()) {
            double angRad = self.getYRot() * Math.PI / 180.0;
            double cosA = Math.cos(angRad) * sizemult;
            double sinA = Math.sin(angRad) * sizemult;
            int n = (int) (1.0 + horiz * 60.0 * sizemult);
            var rng = self.getRandom();
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

        // Find the water surface at the player's column. If they're
        // not over water at all, skip — let normal physics run (they
        // walked off the lily pad).
        double surfaceY = gulliver$findWaterSurfaceY(self);
        if (Double.isNaN(surfaceY)) return;

        double currentY = self.getY();

        // Don't snap when the player is trying to jump off the raft:
        // both `jumping` (input held, jump impulse just applied
        // upstream in aiStep) and any clearly-upward velocity. Without
        // this, the snap zeroes dm.y and the jump never gets off the
        // ground.
        if (self.jumping) return;
        if (dm.y > 0.05) return;

        // Don't yank the player up from far underwater. Equipping a
        // lily-pad while submerged should let them swim up naturally;
        // the snap only takes over when they're already at/near the
        // surface line.
        if (currentY < surfaceY - 0.5) return;

        // Don't yank the player down from above the surface. Happens
        // mid-jump-arc and would also fire if the player is standing
        // on something other than the snapped surface (placed lily
        // pad, boat, edge of land). The rafting flag in
        // GulliverEnvoy.updateResizingFlags already excludes onGround,
        // but this is the belt-and-suspenders fence.
        if (currentY > surfaceY + 0.1) return;

        // We're at the surface — snap. Sit into the water by
        // `0.4 * sizemult` so the player visibly settles into the
        // surface rather than hovering on top of it. Scaled by size
        // so the offset is body-proportional: a 1.0× rider sinks 0.4
        // (calf-deep), a 0.125× tiny sinks 0.05 (proportional to
        // their tiny body so they aren't drowned). The disc lift in
        // both renderers is set to `0.4 * scale` to match — disc
        // center lands at the water-surface line regardless of size.
        // Setting position re-aligns the bbox; the tick has already
        // finished, so this is the final state.
        self.setPos(self.getX(), surfaceY - 0.4 * sizemult, self.getZ());
        self.setDeltaMovement(dm.x, 0.0, dm.z);
        self.fallDistance = 0.0F;
    }

    /**
     * Scan the column at (entity X, entity Z) for the topmost water
     * block (a water block with non-water above). Surface Y =
     * blockY + fluid own-height. NaN if no water in the search range.
     */
    private static double gulliver$findWaterSurfaceY(LivingEntity self) {
        Level level = self.level();
        int x = Mth.floor(self.getX());
        int z = Mth.floor(self.getZ());
        int top = Mth.floor(self.getY()) + 4;
        int bot = Mth.floor(self.getY()) - 4;
        BlockPos.MutableBlockPos cur = new BlockPos.MutableBlockPos();
        for (int y = top; y >= bot; y--) {
            cur.set(x, y, z);
            FluidState fs = level.getFluidState(cur);
            if (!fs.is(FluidTags.WATER)) continue;
            cur.set(x, y + 1, z);
            FluidState above = level.getFluidState(cur);
            if (above.is(FluidTags.WATER)) continue;
            return y + fs.getOwnHeight();
        }
        return Double.NaN;
    }
}
