package gulliver.mixin.client;

import gulliver.client.VisualSize;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Camera scaling with the viewer — all of it on the DRAWN size
 * (VisualSize), so a resize glides instead of snapping.
 *
 * Third-person distance, 1.6.4 bfe.java:597:
 *
 *     d3 *= pfactor;     // sizeMultiplier
 *     for (probe corners 0..7) f4/f5/f6 *= 0.1F * pfactor;
 *     // collision-clip then move(0,0,-d3)
 *
 * Modern Camera.alignWithEntity:
 *     this.move(-this.getMaxZoom(maxZoom_4F), 0, 0);
 * and, from 1.21.10, vanilla's own distance is
 *     max(riderScale * riderDistance, mountScale * mountDistance)
 * so a camera riding a big mount keeps clear of it. The mod's size takes
 * the place of that scale on both terms (a size-8 rider on a size-8 mount
 * keeps the 32-block view; a tiny on a giant sees the giant's whole body),
 * and the result is capped so the 1/32..32 range never asks for a view
 * from beyond the render distance.
 *
 * The probe corner-bias (the 0.1F in getMaxZoom) scales the same way.
 *
 * Eye height: vanilla eases the camera's eye height toward the entity's
 * (sneaking, swimming), and does it in the entity's own, already resized,
 * units — so after a resize the eye would slide on a second clock. The
 * mixin keeps its own easing of the UNIT eye height (the eye height at
 * size 1 for the current pose) and multiplies by the drawn size when the
 * position is built. While the drawn size runs ahead of the hitbox (the
 * tail of a shrink) the eye is held below the first block above the head,
 * so the camera never passes through a ceiling.
 */
@Mixin(Camera.class)
public abstract class MixinCamera {

    /** Third-person view distance never exceeds this (4 blocks x size 16). */
    @Unique private static final float GULLIVER$MAX_DISTANCE = 64.0F;

    @Unique private float gulliver$unitEye;
    @Unique private float gulliver$unitEyeOld;
    /** Weak: the camera outlives worlds, and must not keep the last one's entity alive. */
    @Unique private java.lang.ref.WeakReference<Entity> gulliver$unitEntity;

    // ---- third-person distance -----------------------------------------------

    //#if MC >= 1.20.5
    @ModifyArg(method = "alignWithEntity",
               at = @At(value = "INVOKE",
                        target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"),
               index = 0, require = 0)
    private float gulliver$scaleZoomInputDist(float maxDist) {
    //#else
    //$$ @ModifyArg(method = "alignWithEntity",
    //$$            at = @At(value = "INVOKE",
    //$$                     target = "Lnet/minecraft/client/Camera;getMaxZoom(D)D"),
    //$$            index = 0, require = 0)
    //$$ private double gulliver$scaleZoomInputDist(double maxDist) {
    //#endif
        Entity entity = ((Camera) (Object) this).entity();
        if (entity == null) return maxDist;
        float rider = gulliver$unscaledDistance(entity, (float) maxDist) * VisualSize.of(entity);
        float mount = rider;
        if (entity.isPassenger() && entity.getVehicle() instanceof LivingEntity vehicle) {
            mount = gulliver$unscaledDistance(vehicle, (float) maxDist) * VisualSize.of(vehicle);
        }
        return Math.min(Math.max(rider, mount), GULLIVER$MAX_DISTANCE);
    }

    /** Vanilla's distance for one entity before any Gulliver size: scale x the camera-distance attribute. */
    @Unique
    private static float gulliver$unscaledDistance(Entity entity, float vanillaDistance) {
        //#if MC >= 1.21.10
        if (entity instanceof LivingEntity living) {
            return living.getScale() * (float) living.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.CAMERA_DISTANCE);
        }
        return 4.0F;
        //#else
        //$$ return vanillaDistance;
        //#endif
    }

    @ModifyConstant(method = "getMaxZoom",
                    constant = @Constant(floatValue = 0.1F),
                    require = 0)
    private float gulliver$scaleProbeBias(float c) {
        Entity entity = ((Camera) (Object) this).entity();
        if (entity == null) return c;
        float size = VisualSize.of(entity);
        if (entity.isPassenger() && entity.getVehicle() instanceof LivingEntity vehicle) {
            size = Math.max(size, VisualSize.of(vehicle));
        }
        return size == 1.0F ? c : c * size;
    }

    // ---- eye height ----------------------------------------------------------

    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void gulliver$trackUnitEye(CallbackInfo ci) {
        Entity entity = ((Camera) (Object) this).entity();
        if (entity == null) return;
        float target = entity.getEyeHeight() / VisualSize.logical(entity);
        if (gulliver$unitEntity == null || gulliver$unitEntity.get() != entity) {
            gulliver$unitEntity = new java.lang.ref.WeakReference<>(entity);
            gulliver$unitEye = gulliver$unitEyeOld = target;
            return;
        }
        gulliver$unitEyeOld = gulliver$unitEye;
        gulliver$unitEye += (target - gulliver$unitEye) * 0.5F;
    }

    @ModifyArg(method = "alignWithEntity",
               at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;lerp(FFF)F"),
               index = 1, require = 0)
    private float gulliver$eyeHeightOld(float vanilla) {
        return gulliver$eyeHeight(vanilla, gulliver$unitEyeOld);
    }

    @ModifyArg(method = "alignWithEntity",
               at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;lerp(FFF)F"),
               index = 2, require = 0)
    private float gulliver$eyeHeightNew(float vanilla) {
        return gulliver$eyeHeight(vanilla, gulliver$unitEye);
    }

    @Unique
    private float gulliver$eyeHeight(float vanilla, float unitEye) {
        Entity entity = ((Camera) (Object) this).entity();
        if (entity == null || gulliver$unitEntity == null || gulliver$unitEntity.get() != entity) return vanilla;
        float logical = VisualSize.logical(entity);
        float drawn = VisualSize.of(entity);
        float eye = unitEye * drawn;
        float own = unitEye * logical;
        if (eye > own + 1.0E-3F) {
            // The drawn body is taller than the hitbox for a moment: stop short of any ceiling.
            Vec3 from = new Vec3(entity.getX(), entity.getY() + own, entity.getZ());
            Vec3 to = new Vec3(entity.getX(), entity.getY() + eye, entity.getZ());
            var hit = entity.level().clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity));
            if (hit.getType() != HitResult.Type.MISS) {
                eye = Math.max(own, (float) (hit.getLocation().y - entity.getY()) - 0.1F * logical);
            }
        }
        return eye;
    }

    // ---- sleeping ------------------------------------------------------------

    /**
     * Sleep camera Y offset above the bed surface. Vanilla's 0.3 puts the
     * eye 0.1 above the body top (the body is 0.2 high lying down); keeping
     * that rule for sized players,
     *
     *   eye_offset = SLEEPING_DIMENSIONS.height * size + 0.1  =  0.2 * size + 0.1
     *
     * is 0.3 at size 1 (vanilla unchanged), 1.7 at size 8 and 0.125 at 1/8,
     * i.e. 1.6.4 bfe.java:582-583: glTranslatef(0, 0.3F * hfactor, 0).
     * No pitch tilt, as in the original.
     */
    //#if MC >= 26.2
    @ModifyConstant(method = "alignWithEntity", constant = @Constant(floatValue = 0.15F), require = 0)
    private float gulliver$scaleSleepCameraY(float c) {
    //#elif MC >= 1.20.5
    //$$ @ModifyConstant(method = "alignWithEntity", constant = @Constant(floatValue = 0.3F), require = 0)
    //$$ private float gulliver$scaleSleepCameraY(float c) {
    //#else
    //$$ @ModifyConstant(method = "alignWithEntity", constant = @Constant(doubleValue = 0.3D), require = 0)
    //$$ private double gulliver$scaleSleepCameraY(double c) {
    //#endif
        Entity entity = ((Camera) (Object) this).entity();
        if (entity == null) return c;
        if (!(entity instanceof LivingEntity le)) return c;
        if (!le.isSleeping()) return c;
        float m = VisualSize.of(entity);
        if (m == 1.0F) return c;
        return c * m;
    }
}
