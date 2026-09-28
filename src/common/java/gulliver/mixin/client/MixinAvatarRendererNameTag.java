//#if MC >= 1.21.9 && MC < 26.1
//$$ package gulliver.mixin.client;
//$$
//$$ import com.mojang.blaze3d.vertex.PoseStack;
//$$ import net.minecraft.client.renderer.SubmitNodeCollector;
//$$ import net.minecraft.client.renderer.entity.player.AvatarRenderer;
//$$ import net.minecraft.client.renderer.entity.state.AvatarRenderState;
//$$ import net.minecraft.client.renderer.state.CameraRenderState;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$
//$$ /** Players' name tags: AvatarRenderer draws its own and never calls up. */
//$$ @Mixin(AvatarRenderer.class)
//$$ public abstract class MixinAvatarRendererNameTag {
//$$
//$$     @Inject(method = "submitNameTag(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
//$$             at = @At("HEAD"))
//$$     private void gulliver$push(AvatarRenderState state, PoseStack pose, SubmitNodeCollector buf,
//$$                                CameraRenderState cam, CallbackInfo ci) {
//$$         gulliver.client.NameTagScale.push(state, pose);
//$$     }
//$$
//$$     @Inject(method = "submitNameTag(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
//$$             at = @At("RETURN"))
//$$     private void gulliver$pop(AvatarRenderState state, PoseStack pose, SubmitNodeCollector buf,
//$$                               CameraRenderState cam, CallbackInfo ci) {
//$$         gulliver.client.NameTagScale.pop(state, pose);
//$$     }
//$$ }
//#endif
