package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Cinematic Interactions (#33), server side: when a station opens its
 * normal window instead of a scene (always, for a giant: see
 * MixinCinematicSceneSize), the server only opens and keeps it open within
 * 6 blocks of the player's feet. A giant stands with its feet several
 * blocks off and reaches much further, so the window never opened; the
 * distance grows with the player like reach does (never below 6).
 * Pseudo: does nothing unless Cinematic Interactions is installed.
 */
@Pseudo
@Mixin(targets = "dev.dyslexic.cinematic.station.Sessions", remap = false)
public abstract class MixinCinematicStationReach {

    @ModifyConstant(method = {"openWindow", "valid"}, constant = @Constant(doubleValue = 36.0D), require = 0)
    private static double gulliver$reachWithSize(double distanceSqr, ServerPlayer player) {
        float size = ((IResizeableEntity) player).getSizeMultiplier();
        return size > 1.0F ? distanceSqr * size * size : distanceSqr;
    }
}
