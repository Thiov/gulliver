package gulliver.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import gulliver.client.GliderPaperWorldRenderer;
import gulliver.client.LilyRaftWorldRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.20.1: first-person props right after the level's entities are drawn. */
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRendererSubmit {

    @Inject(method = "renderLevel",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/renderer/LevelRenderer;checkPoseStack(Lcom/mojang/blaze3d/vertex/PoseStack;)V",
                     ordinal = 0),
            require = 0)
    //#if MC >= 1.21
    //$$ private void gulliver$submitProps(net.minecraft.client.DeltaTracker delta, boolean outline, Camera camera,
    //$$                                   GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustum,
    //$$                                   Matrix4f projection, CallbackInfo ci) {
    //$$     // From 1.21 the camera's turn is on the model-view stack and the entity pose stack starts empty.
    //$$     PoseStack pose = new PoseStack();
    //$$     float partialTick = delta.getGameTimeDeltaPartialTick(false);
    //#else
    private void gulliver$submitProps(PoseStack pose, float partialTick, long nanos, boolean outline, Camera camera,
                                      GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projection,
                                      CallbackInfo ci) {
    //#endif
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        GliderPaperWorldRenderer.submit(pose, buffers, camera, partialTick);
        LilyRaftWorldRenderer.submit(pose, buffers, camera, partialTick);
    }

    /** The wind after the water, clouds and weather, so nothing see-through behind it covers it (BreathRibbonRenderer). */
    @Inject(method = "renderLevel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderWorldBorder(Lnet/minecraft/client/Camera;)V"),
            require = 0)
    //#if MC >= 1.21
    //$$ private void gulliver$drawWind(net.minecraft.client.DeltaTracker delta, boolean outline, Camera camera,
    //$$                                GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustum,
    //$$                                Matrix4f projection, CallbackInfo ci) {
    //$$     PoseStack pose = new PoseStack();
    //$$     pose.mulPose(frustum);
    //$$     float partialTick = delta.getGameTimeDeltaPartialTick(false);
    //#else
    private void gulliver$drawWind(PoseStack pose, float partialTick, long nanos, boolean outline, Camera camera,
                                   GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projection,
                                   CallbackInfo ci) {
    //#endif
        gulliver.client.BreathRibbonRenderer.drawLate(pose, Minecraft.getInstance().renderBuffers().bufferSource(),
                camera, partialTick);
    }
}
