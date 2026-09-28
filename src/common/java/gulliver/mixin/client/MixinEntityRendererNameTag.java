package gulliver.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Name tags scale with their entity; see NameTagScale. */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRendererNameTag {

    //#if MC >= 26.1
    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
            at = @At("HEAD"))
    private void gulliver$pushScale(EntityRenderState state, PoseStack pose, SubmitNodeCollector buf,
                                      CameraRenderState cam, int offset, CallbackInfo ci) {
    //#else
    //$$ @Inject(method = "submitNameTag", at = @At("HEAD"))
    //$$ private void gulliver$pushScale(EntityRenderState state, PoseStack pose, SubmitNodeCollector buf,
    //$$                                   CameraRenderState cam, CallbackInfo ci) {
    //#endif
        gulliver.client.NameTagScale.push(state, pose);
    }

    //#if MC >= 26.1
    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
            at = @At("RETURN"))
    private void gulliver$popScale(EntityRenderState state, PoseStack pose, SubmitNodeCollector buf,
                                     CameraRenderState cam, int offset, CallbackInfo ci) {
    //#else
    //$$ @Inject(method = "submitNameTag", at = @At("RETURN"))
    //$$ private void gulliver$popScale(EntityRenderState state, PoseStack pose, SubmitNodeCollector buf,
    //$$                                  CameraRenderState cam, CallbackInfo ci) {
    //#endif
        gulliver.client.NameTagScale.pop(state, pose);
    }
}
