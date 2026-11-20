package gulliver.mixin.client;

import gulliver.client.CinematicCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cinematic Cookery and Cinematic Delight (#33): cooking scenes for
 * Kaleidoscope Cookery and Farmer's Delight, standalone mods in the style
 * of Cinematic Interactions, with the same fixed-distance camera and 3D
 * arms made for a normal-size body. Their Config.enabled() is asked only
 * when a scene would start (CookScene.tryBegin); answering no while the
 * player is far from normal size lets the click through, so the pot,
 * board or skillet works as it does without the mod.
 * Pseudo: does nothing unless the mod is installed.
 */
@Pseudo
@Mixin(targets = {"com.example.cinematiccookery.Config", "com.arbuzobr1.cinematicdelight.Config"}, remap = false)
public abstract class MixinCinematicCookingSize {

    @Inject(method = "enabled", at = @At("HEAD"), cancellable = true, require = 0)
    private static void gulliver$noSceneAtThisSize(CallbackInfoReturnable<Boolean> cir) {
        if (!CinematicCompat.sceneFitsPlayer()) cir.setReturnValue(false);
    }
}
