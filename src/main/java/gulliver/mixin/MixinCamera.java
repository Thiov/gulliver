package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Third-person camera scaling — exact 1.6.4 bfe.java:597 behaviour:
 *
 *     d3 *= pfactor;     // sizeMultiplier
 *     for (probe corners 0..7) f4/f5/f6 *= 0.1F * pfactor;
 *     // collision-clip then move(0,0,-d3)
 *
 * Modern Camera.alignWithEntity:
 *     this.move(-this.getMaxZoom(maxZoom_4F), 0, 0);
 *
 * Strategy: scale the ARGUMENT to getMaxZoom by sizeMultiplier (so it
 * probes for distance 4*size), and scale the 0.1F probe corner-bias by
 * sizeMultiplier (matching 1.6.4 lines 626-628). getMaxZoom returns the
 * collision-clipped value, which is then passed unchanged to move().
 *
 * Previous version (4(165)) double-scaled by ALSO modifying move's arg
 * AND getMaxZoom's RETURN — that gave size² which is exactly the
 * "too far when large, too close when small" feedback. This version
 * scales once, at the input boundary.
 */
@Mixin(Camera.class)
public abstract class MixinCamera {

    @Shadow @Final public Entity entity;

    @ModifyArg(method = "alignWithEntity",
               at = @At(value = "INVOKE",
                        target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"),
               index = 0)
    private float gulliver$scaleZoomInputDist(float maxDist) {
        if (entity == null) return maxDist;
        float m = ((IResizeableEntity) entity).getSizeMultiplier();
        if (m == 1.0F) return maxDist;
        return maxDist * m;
    }

    @ModifyConstant(method = "getMaxZoom",
                    constant = @Constant(floatValue = 0.1F))
    private float gulliver$scaleProbeBias(float c) {
        if (entity == null) return c;
        float m = ((IResizeableEntity) entity).getSizeMultiplier();
        if (m == 1.0F) return c;
        return c * m;
    }
}
