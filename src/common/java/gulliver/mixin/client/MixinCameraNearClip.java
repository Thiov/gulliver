//#if MC >= 26.1
package gulliver.mixin.client;

import gulliver.client.VisualSize;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
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
 * The near argument follows the viewer's drawn size (VisualSize.nearScale
 * bounds it: not below 1/16 of vanilla's, which a 32-bit depth buffer
 * could no longer tell apart at distance, and not above 0.4, so a giant
 * neither cuts away nearby blocks nor its own hands).
 *
 * The HUD/hand projection uses GameRenderer.hudProjection.setupPerspective
 * directly (not via Camera) so this mixin doesn't affect it.
 */
@Mixin(Camera.class)
public abstract class MixinCameraNearClip {

    @ModifyArg(
        method = "update",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/client/Camera;setupPerspective(FFFFF)V"),
        index = 0,
        require = 0
    )
    private float gulliver$scaleNearPlane(float near) {
        Entity entity = ((net.minecraft.client.Camera) (Object) this).entity();
        if (entity == null) return near;
        float size = VisualSize.of(entity);
        return size == 1.0F ? near : near * VisualSize.nearScale(size);
    }
}
//#endif
