//#if MC < 26.1
//$$ package gulliver.mixin.client;
//$$
//$$ import gulliver.client.VisualSize;
//$$ import net.minecraft.client.Minecraft;
//$$ import net.minecraft.client.renderer.GameRenderer;
//$$ import net.minecraft.world.entity.Entity;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.Unique;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Constant;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.ModifyConstant;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$
//$$ /**
//$$  * Near clip plane scaled with the viewer (before 26.1 it lives in the
//$$  * projection matrix): a tiny's camera would otherwise clip into walls
//$$  * it is standing right against. VisualSize.nearScale bounds the factor
//$$  * (not below 1/16, not above 8).
//$$  *
//$$  * On 1.20.1 the first-person hand is drawn through the same projection
//$$  * method: it keeps vanilla's near plane, or a giant's hands (and what
//$$  * they hold) would be cut away by the scaled one.
//$$  */
//$$ @Mixin(GameRenderer.class)
//$$ public abstract class MixinGameRendererNearClip {
//$$
//#if MC < 1.21
//$$     @Unique private boolean gulliver$handPass;
//$$
//$$     @Inject(method = "renderItemInHand", at = @At("HEAD"), require = 0)
//$$     private void gulliver$handBegin(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.Camera camera,
//$$                                     float partialTick, CallbackInfo ci) {
//$$         gulliver$handPass = true;
//$$     }
//$$
//$$     @Inject(method = "renderItemInHand", at = @At("RETURN"), require = 0)
//$$     private void gulliver$handEnd(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.Camera camera,
//$$                                   float partialTick, CallbackInfo ci) {
//$$         gulliver$handPass = false;
//$$     }
//$$
//#endif
//$$     @ModifyConstant(method = "getProjectionMatrix", constant = @Constant(floatValue = 0.05F), require = 0)
//$$     private float gulliver$scaleNearPlane(float near) {
//#if MC < 1.21
//$$         if (gulliver$handPass) return near;
//#endif
//$$         Entity cam = Minecraft.getInstance().getCameraEntity();
//$$         if (cam == null) return near;
//$$         float m = VisualSize.of(cam);
//$$         return m == 1.0F ? near : near * VisualSize.nearScale(m);
//$$     }
//$$ }
//#endif
