//#if MC < 26.1
//$$ package gulliver.mixin.client;
//$$
//$$ import gulliver.client.PickBoost;
//$$ import net.minecraft.client.renderer.GameRenderer;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$
//$$ /** Opens the window in which small creatures are easier to pick (see PickBoost): the crosshair pick. */
//$$ @Mixin(GameRenderer.class)
//$$ public abstract class MixinGameRendererPick {
//$$
//$$     @Inject(method = "pick(F)V", at = @At("HEAD"), require = 0)
//$$     private void gulliver$pickBegin(float partialTick, CallbackInfo ci) {
//$$         PickBoost.begin();
//$$     }
//$$
//$$     @Inject(method = "pick(F)V", at = @At("RETURN"), require = 0)
//$$     private void gulliver$pickEnd(float partialTick, CallbackInfo ci) {
//$$         PickBoost.end();
//$$     }
//$$ }
//#endif
