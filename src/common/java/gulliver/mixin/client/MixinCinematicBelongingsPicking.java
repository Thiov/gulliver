package gulliver.mixin.client;

import gulliver.client.BelongingsScale;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Cinematic Belongings (#33): its mouse picking casts rays from the camera
 * into the bag. With the scene drawn at the player's size
 * (MixinCinematicBelongingsSize), the rays start where the camera would be
 * at normal size, so the cursor still lands on the item under it.
 * Pseudo: does nothing unless the mod is installed.
 */
@Pseudo
@Mixin(targets = "com.cinematicbelongings.render.Picking", remap = false)
public abstract class MixinCinematicBelongingsPicking {

    @ModifyVariable(method = "capture", at = @At("HEAD"), argsOnly = true, require = 0)
    private static Vec3 gulliver$normalCamera(Vec3 camera) {
        return BelongingsScale.normalCamera(camera);
    }
}
