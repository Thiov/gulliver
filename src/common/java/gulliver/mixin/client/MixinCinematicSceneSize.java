package gulliver.mixin.client;

import gulliver.client.CinematicCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cinematic Interactions (#33): its scenes put
 * the camera and the 3D arms at fixed distances in blocks around the
 * workstation, made for a normal-size body. At a giant's eye the camera
 * hangs high over the table while the arms stretch metres down to it; a
 * tiny's view is jammed into the table top. Settings.scene(kind) is its own
 * "use the normal window instead" switch: answering no while the player is
 * far from normal size opens the station's vanilla screen, which works at
 * any size (client.cinematicMinSize / cinematicMaxSize).
 * Pseudo: does nothing unless Cinematic Interactions is installed.
 */
@Pseudo
@Mixin(targets = "dev.dyslexic.cinematic.client.Settings", remap = false)
public abstract class MixinCinematicSceneSize {

    @Inject(method = "scene", at = @At("HEAD"), cancellable = true, require = 0)
    private static void gulliver$noSceneAtThisSize(CallbackInfoReturnable<Boolean> cir) {
        if (!CinematicCompat.sceneFitsPlayer()) cir.setReturnValue(false);
    }
}
