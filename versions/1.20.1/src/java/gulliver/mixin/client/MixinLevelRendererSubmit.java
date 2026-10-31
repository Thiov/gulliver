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
    private void gulliver$submitProps(PoseStack pose, float partialTick, long nanos, boolean outline, Camera camera,
                                      GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projection,
                                      CallbackInfo ci) {
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        GliderPaperWorldRenderer.submit(pose, buffers, camera, partialTick);
        LilyRaftWorldRenderer.submit(pose, buffers, camera, partialTick);
    }

    /** The wind after the translucent terrain, like particles, so water behind it doesn't cover it. */
    @Inject(method = "renderLevel",
            at = {
                @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleEngine;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;F)V"),
                // loader patch: Forge and NeoForge pass the frustum along
                @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleEngine;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;)V")
            },
            require = 0)
    private void gulliver$drawWind(PoseStack pose, float partialTick, long nanos, boolean outline, Camera camera,
                                   GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projection,
                                   CallbackInfo ci) {
        gulliver.client.BreathRibbonRenderer.submit(pose, Minecraft.getInstance().renderBuffers().bufferSource(),
                camera, partialTick);
    }
}
