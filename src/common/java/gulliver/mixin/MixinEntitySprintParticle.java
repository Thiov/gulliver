package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 1.6.4 nn.java:672-680 sprint-dust ("tilecrack") scaling. Vanilla
 * spawnSprintParticle ends with one Level.addParticle call; the 1.6.4
 * mod wraps that call with three changes:
 *
 *   - stochastic skip when sizeMultiplier < 0.5
 *       ab.nextInt((int)(1/size)) != 0 -> skip
 *   - extra copies for huge entities
 *       cnt = sizeRoot <= 1 ? 1 : (int) sizeRoot
 *   - Y-offset and Y-velocity scaled by sizeMultiplier
 *       y_off  = 0.1 * size  (vanilla 0.1)
 *       y_vel  = 1.5 * size  (vanilla 1.5)
 *
 * X/Z position and velocity unchanged. Each extra copy regenerates fresh
 * random X/Z so the dust spreads instead of stacking on one point
 * (1.6.4 calls ab.nextFloat() per loop iteration).
 */
@Mixin(Entity.class)
public abstract class MixinEntitySprintParticle {

    @Redirect(
        method = "spawnSprintParticle",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"
        )
    )
    private void gulliver$scaleSprintParticle(Level level, ParticleOptions opts,
                                               double x, double y, double z,
                                               double dx, double dy, double dz) {
        Entity self = (Entity) (Object) this;
        float size = ((IResizeableEntity) self).getSizeMultiplier();

        if (size < 0.5F) {
            int slots = Mth.ceil(1.0F / size);
            if (slots > 1 && self.getRandom().nextInt(slots) != 0) {
                return;
            }
        }

        float root = ((IResizeableEntity) self).getSizeMultiplierRoot();
        int cnt = root <= 1.0F ? 1 : (int) root;

        double scaledY = y - 0.1 + 0.1 * size;
        double scaledDy = dy * size;
        float width = self.getDimensions(self.getPose()).width();

        level.addParticle(opts, x, scaledY, z, dx, scaledDy, dz);
        for (int p = 1; p < cnt; p++) {
            double px = self.getX() + (self.getRandom().nextDouble() - 0.5) * width;
            double pz = self.getZ() + (self.getRandom().nextDouble() - 0.5) * width;
            level.addParticle(opts, px, scaledY, pz, dx, scaledDy, dz);
        }
    }
}
