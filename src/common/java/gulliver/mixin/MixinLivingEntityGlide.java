package gulliver.mixin;

import gulliver.api.IResizeableLiving;
import gulliver.common.GulliverEnvoy;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.6.4 of.java line 2805-2839 glide mechanic: when isGlidingFlag is set
 * (tiny holding feather/paper/lily-pad/shears, airborne, not in plant/rain
 * /water/on-ladder), apply slow descent + lift from rising heat updrafts.
 *
 *   if (this.y < 0.0625 + 0.1 * size) {
 *     if (this.y < 0) this.y = 0;
 *     double draft = getRisingUpdraft(this) - 0.1 * size;
 *     if (draft > 0.0625) draft = 0.0625;
 *     this.y += draft;
 *     if (this.y < -0.1 * size) this.y = -0.1 * size;
 *   }
 *
 * The 1.6.4 patch ran inside EntityLivingBase.moveEntityWithHeading after
 * gravity was applied. Modern equivalent: post-aiStep / post-travel
 * delta-movement adjustment. We use a HEAD inject on aiStep so we run
 * once per tick before AI movement decisions are made — same lifecycle
 * timing as the 1.6.4 livingEntityBaseTick path.
 *
 * The glide runs before aiStep's own dead zone (vanilla zeroes any speed
 * under 0.003), so the sink rate never goes below 0.0035: at size 1/32
 * (0.1 * size = 0.003125) a trace of updraft would otherwise stop the
 * descent dead. The flag itself already gives way to flight, elytra, a
 * mount and spectating (GulliverEnvoy.isMovingFreely); checked again here
 * because the flag is only refreshed in baseTick.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityGlide {

    /** Smallest sink speed: just above vanilla's 0.003 velocity dead zone. */
    private static final double GULLIVER$MIN_SINK = 0.0035D;

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void gulliver$applyGlide(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        IResizeableLiving sized = (IResizeableLiving) this;
        if (!sized.isGliding() || !GulliverEnvoy.isMovingFreely(self)) return;

        float size = sized.getSizeMultiplier();
        Vec3 dm = self.getDeltaMovement();
        double y = dm.y;
        double sink = Math.max(0.1D * size, GULLIVER$MIN_SINK);
        double cap = 0.0625D + sink;

        if (y < cap) {
            if (y < 0.0D) y = 0.0D;
            double draft = GulliverEnvoy.getRisingUpdraft(self) - sink;
            if (draft > 0.0625D) draft = 0.0625D;
            y += draft;
            if (y < -sink) y = -sink;
            self.setDeltaMovement(dm.x, y, dm.z);
            // Reset fall distance — gliders don't take fall damage on landing.
            self.fallDistance = 0.0F;
        }
    }
}
