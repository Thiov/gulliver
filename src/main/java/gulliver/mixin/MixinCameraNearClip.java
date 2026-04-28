package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 1.6.4 bfe.java line 743 (EntityRenderer level projection setup):
 *   Project.gluPerspective(fov, aspect, 0.05F * sizeMultiplier, far);
 *
 * The 1.6.4 mod scales the near clipping plane by sizeMultiplier so that
 * a tiny player can stand close to a block face without seeing through it.
 * Vanilla's fixed 0.05f near plane is half a typical 0.125x player's bbox
 * — the camera ends up inside neighbouring block geometry, which the GPU
 * then clips, producing the see-through-blocks bug at extreme small
 * sizes.
 *
 * Modern equivalent: Camera.setupPerspective(near, far, fov, w, h) is
 * called from Camera.update once per frame for the level projection.
 * Multiplying its near argument by the camera's entity sizeMultiplier
 * is byte-equivalent to the 1.6.4 patch.
 *
 * The HUD/hand projection uses GameRenderer.hudProjection.setupPerspective
 * directly (not via Camera) so this mixin doesn't affect it.
 */
@Mixin(Camera.class)
public abstract class MixinCameraNearClip {

    @Shadow @Final public Entity entity;

    @ModifyArg(
        method = "update",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/client/Camera;setupPerspective(FFFFF)V"),
        index = 0
    )
    private float gulliver$scaleNearPlane(float near) {
        if (entity == null) return near;
        float m = ((IResizeableEntity) entity).getSizeMultiplier();
        if (m == 1.0F) return near;
        return near * m;
    }
}
