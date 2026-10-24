package gulliver.mixin.client;

import gulliver.api.IResizeableEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Water splashes that match the body that makes them.
 *
 * Vanilla's splash draws 1 + 20 x width bubbles and as many droplets over
 * the body's width. Width already follows the size, so a tiny's splash is
 * small and a giant's wide; two things are not right at the extremes:
 *
 *  - the count has no limit (a size-32 player throws ~400 of each every time
 *    it touches water), and a creature at 1/8 still throws 1 + 1.5 of
 *    them, which is a lot for something the size of a fly. The 20 is
 *    scaled: the count is held to about 60, and below size 1 it shrinks
 *    with the size.
 *  - a giant's splash is not taller than anyone's: the droplets leave at
 *    the same speed. Giants add a crown of droplets thrown outward and up,
 *    harder the bigger the body (capped).
 *
 * The splash SOUND is not touched: Entity.playSound already scales its
 * volume by size (MixinEntitySound).
 */
@Mixin(Entity.class)
public abstract class MixinEntitySplash {

    @ModifyConstant(method = "doWaterSplashEffect", constant = @Constant(floatValue = 20.0F), require = 0)
    private float gulliver$particleDensity(float perBlock) {
        Entity self = (Entity) (Object) this;
        float size = ((IResizeableEntity) self).getSizeMultiplier();
        float width = self.getBbWidth();
        if (size < 1.0F) return perBlock * size;
        if (width * perBlock <= 60.0F) return perBlock;
        return 60.0F / width;
    }

    @Inject(method = "doWaterSplashEffect", at = @At("TAIL"), require = 0)
    private void gulliver$crown(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!self.level().isClientSide()) return;
        float size = ((IResizeableEntity) self).getSizeMultiplier();
        if (size < 2.0F) return;
        var random = self.level().getRandom();
        // A slow wade makes a small crown, a fall from height a full one.
        double impact = Mth.clamp(self.getDeltaMovement().length() * 2.0D, 0.35D, 1.0D);
        int count = Mth.clamp((int) (4.0D * size * impact), 6, 48);
        double radius = Math.max(0.3D, self.getBbWidth() * 0.5D);
        double speed = Mth.clamp(0.12D * Math.sqrt(size), 0.15D, 0.7D) * (0.6D + 0.4D * impact);
        double surface = Mth.floor(self.getY()) + 1.0D;
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double reach = radius * (0.6D + random.nextDouble() * 0.5D);
            double outward = speed * (0.5D + random.nextDouble() * 0.5D);
            self.level().addParticle(ParticleTypes.SPLASH,
                    self.getX() + Math.cos(angle) * reach, surface, self.getZ() + Math.sin(angle) * reach,
                    Math.cos(angle) * outward, speed * (0.7D + random.nextDouble() * 0.6D), Math.sin(angle) * outward);
        }
    }
}
