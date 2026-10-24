package gulliver.mixin.client;

import gulliver.client.TremorHandler;
import gulliver.client.VisualSize;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies TremorHandler's impulse pool as a camera wobble at the very
 * end of the camera's alignment with its entity — after vanilla has fully
 * positioned and rotated the camera for the frame, and (from 26.1, where
 * the camera builds its own culling frustum right after) before the
 * frustum is prepared, so the chunks at the screen edge are culled for the
 * view that is actually drawn.
 *
 * Rotation-only (no position offset): the camera can never be shaken
 * into a wall, and small angular motion reads as "the ground kicks
 * under you" instead of a screen displacement. Two incommensurate
 * sine frequencies per axis give an organic rumble instead of a
 * metronome; pitch runs at 70% of yaw so the wobble feels grounded
 * rather than nodding. Max amplitude ≈ 1.4° for a point-blank giant
 * slam; a distant footstep is a barely-perceptible ~0.1° bump.
 *
 * The oscillators run on doubles: the time base grows for as long as the
 * game stays open, and a float sine silently stops working once its
 * argument is large.
 */
@Mixin(Camera.class)
public abstract class MixinCameraShake {

    @Shadow protected abstract void setRotation(float yRot, float xRot);
    @Shadow public abstract float xRot();
    @Shadow public abstract float yRot();

    //#if MC >= 26.1
    @Inject(method = "alignWithEntity", at = @At("TAIL"), require = 0)
    private void gulliver$applyTremor(float pt, CallbackInfo ci) {
    //#elif MC >= 1.21.11
    //$$ @Inject(method = "setup", at = @At("TAIL"), require = 0)
    //$$ private void gulliver$applyTremor(net.minecraft.world.level.Level level, net.minecraft.world.entity.Entity entity,
    //$$                                   boolean detached, boolean mirrored, float pt, CallbackInfo ci) {
    //#else
    //$$ @Inject(method = "setup", at = @At("TAIL"), require = 0)
    //$$ private void gulliver$applyTremor(net.minecraft.world.level.BlockGetter level, net.minecraft.world.entity.Entity entity,
    //$$                                   boolean detached, boolean mirrored, float pt, CallbackInfo ci) {
    //#endif
        VisualSize.setViewPartial(pt);
        float s = TremorHandler.shakeAmount(pt);
        if (s <= 0.0F) return;
        double t = TremorHandler.time(pt);
        float yaw   = (float) ((Math.sin(t * 3.1D) * 0.6D + Math.sin(t * 5.3D) * 0.4D) * s);
        float pitch = (float) ((Math.cos(t * 3.7D) * 0.6D + Math.sin(t * 6.1D) * 0.4D) * s * 0.7D);
        setRotation(yRot() + yaw, xRot() + pitch);
    }
}
