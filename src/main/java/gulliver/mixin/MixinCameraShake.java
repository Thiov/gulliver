package gulliver.mixin;

import gulliver.client.TremorHandler;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies TremorHandler's impulse pool as a camera wobble at the very
 * end of Camera.update — after vanilla has fully positioned and
 * rotated the camera for the frame.
 *
 * Rotation-only (no position offset): the camera can never be shaken
 * into a wall, and small angular motion reads as "the ground kicks
 * under you" instead of a screen displacement. Two incommensurate
 * sine frequencies per axis give an organic rumble instead of a
 * metronome; pitch runs at 70% of yaw so the wobble feels grounded
 * rather than nodding. Max amplitude ≈ 1.4° for a point-blank giant
 * slam; a distant footstep is a barely-perceptible ~0.1° bump.
 */
@Mixin(Camera.class)
public abstract class MixinCameraShake {

    @Shadow protected abstract void setRotation(float yRot, float xRot);
    @Shadow public abstract float xRot();
    @Shadow public abstract float yRot();

    @Inject(method = "update", at = @At("TAIL"))
    private void gulliver$applyTremor(DeltaTracker delta, CallbackInfo ci) {
        float pt = delta.getGameTimeDeltaPartialTick(false);
        float s = TremorHandler.shakeAmount(pt);
        if (s <= 0.0F) return;
        float t = TremorHandler.time(pt);
        float yaw   = (Mth.sin(t * 3.1F) * 0.6F + Mth.sin(t * 5.3F) * 0.4F) * s;
        float pitch = (Mth.cos(t * 3.7F) * 0.6F + Mth.sin(t * 6.1F) * 0.4F) * s * 0.7F;
        setRotation(yRot() + yaw, xRot() + pitch);
    }
}
