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

    /**
     * Scale the bed-camera Y offset (vanilla 0.3F) by sizeMultiplier so
     * sleeping at a non-1 size shows the correct eye height. The 0.3F
     * lives in Camera.alignWithEntity (the sleep branch's move call).
     */
    /**
     * Sleep camera Y offset above bed surface. Vanilla 0.3 puts the eye
     * 0.1 above body-top (body height = 0.2 lying down). For sized players
     * we want the SAME relative offset (eye 0.1 above body top) so the
     * view always shows the body — keep vanilla's `0.1 above body` rule:
     *
     *   eye_offset = SLEEPING_DIMENSIONS.height * sizeMultiplier + 0.1
     *              = 0.2 * m + 0.1
     *
     * For m=1 this gives 0.3 (vanilla unchanged).
     * For m=8 gives 1.7 (eye at body-top + 0.1).
     * For m=0.125 gives 0.125 (eye still above tiny body).
     */
    @ModifyConstant(method = "alignWithEntity", constant = @Constant(floatValue = 0.3F))
    private float gulliver$scaleSleepCameraY(float c) {
        if (entity == null) return c;
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity le)) return c;
        if (!le.isSleeping()) return c;
        float m = ((IResizeableEntity) entity).getSizeMultiplier();
        if (m == 1.0F) return c;
        return 0.2F * m + 0.1F;
    }
}
