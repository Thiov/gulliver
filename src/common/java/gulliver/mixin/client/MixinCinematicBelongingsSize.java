package gulliver.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import gulliver.client.BelongingsScale;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cinematic Belongings (#33): the inventory as a 3D bag held in front of
 * the camera. Its scenes on the body (the inventory, a worn or held
 * backpack) follow the player's size (BelongingsScale): the scene camera
 * moves out from the eye by the size and everything is drawn that much
 * bigger, so a giant holds a giant's bag and a tiny a tiny one, instead of
 * a normal-size bag clipping through a giant's body. Scenes at a block in
 * the world (chests, placed backpacks) still only play near normal size;
 * elsewhere the normal screen opens.
 * Pseudo: does nothing unless the mod is installed.
 */
@Pseudo
@Mixin(targets = "com.cinematicbelongings.scene.PersonalScene", remap = false)
public abstract class MixinCinematicBelongingsSize {

    /** "May this screen become a scene?" (its own check, e.g. not in combat). */
    @Inject(method = "allowed", at = @At("HEAD"), cancellable = true, require = 0)
    private static void gulliver$sceneAtThisSize(@Coerce Object target, LocalPlayer player,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (!BelongingsScale.allowed(target)) cir.setReturnValue(false);
    }

    /** The scene camera, moved out from the eye by the player's size. */
    @Inject(method = "camera", at = @At("RETURN"), cancellable = true, require = 0)
    private static void gulliver$cameraAtSize(Vec3 eye, float yaw, float pitch, CallbackInfoReturnable<Object> cir) {
        Object scaled = BelongingsScale.camera(eye, cir.getReturnValue());
        if (scaled != null) cir.setReturnValue(scaled);
    }

    /** Everything the scene draws, that much bigger around the camera... */
    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private static void gulliver$drawAtSize(PoseStack pose, @Coerce Object buffers, Vec3 camera, float partialTick,
                                            CallbackInfo ci) {
        float s = BelongingsScale.drawScale();
        pose.pushPose();
        if (s != 1.0F) pose.scale(s, s, s);
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private static void gulliver$drawAtSizeEnd(PoseStack pose, @Coerce Object buffers, Vec3 camera, float partialTick,
                                               CallbackInfo ci) {
        pose.popPose();
    }

    /** ... while its renderer measures from where the camera would be at normal size. */
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, require = 0)
    private static Vec3 gulliver$normalCamera(Vec3 camera) {
        return BelongingsScale.normalCamera(camera);
    }
}
