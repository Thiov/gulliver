package gulliver.mixin.client;

import gulliver.client.VisualSize;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cinematic Belongings (#33): the mirror shard on the bag shows the player
 * in a frame made for a normal-size body, from the player's own render
 * state, so a giant filled it with its face and a tiny was lost in it. The
 * body is drawn at size 1 there; the bag around it already follows the
 * player's size (MixinCinematicBelongingsSize).
 * Pseudo: does nothing unless the mod is installed.
 */
@Pseudo
@Mixin(targets = "com.cinematicbelongings.bag.Bag", remap = false)
public abstract class MixinCinematicBelongingsMirror {

    @Inject(method = "renderMirror", at = @At("HEAD"), require = 0)
    private static void gulliver$mirrorBegin(CallbackInfo ci) {
        VisualSize.beginNormal();
    }

    @Inject(method = "renderMirror", at = @At("RETURN"), require = 0)
    private static void gulliver$mirrorEnd(CallbackInfo ci) {
        VisualSize.endNormal();
    }
}
