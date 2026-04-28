package gulliver.mixin;

import gulliver.api.IResizeableEntity;
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
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityGlide {

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void gulliver$applyGlide(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        IResizeableLiving sized = (IResizeableLiving) this;
        if (!sized.isGliding()) return;

        float size = sized.getSizeMultiplier();
        Vec3 dm = self.getDeltaMovement();
        double y = dm.y;
        double cap = 0.0625D + 0.1D * size;

        if (y < cap) {
            if (y < 0.0D) y = 0.0D;
            double draft = GulliverEnvoy.getRisingUpdraft(self) - 0.1D * size;
            if (draft > 0.0625D) draft = 0.0625D;
            y += draft;
            double minY = -0.1D * size;
            if (y < minY) y = minY;
            self.setDeltaMovement(dm.x, y, dm.z);
            // Reset fall distance — gliders don't take fall damage on landing.
            self.fallDistance = 0.0;
        }
    }
}
