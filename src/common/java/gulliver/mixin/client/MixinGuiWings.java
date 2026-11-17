package gulliver.mixin.client;

import gulliver.client.WingHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The wings' stamina bar (WingHud), drawn over the rest of the HUD. */
//#if MC >= 26.2
@Mixin(net.minecraft.client.gui.Hud.class)
public abstract class MixinGuiWings {

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("TAIL"), require = 0)
    private void gulliver$wingStamina(net.minecraft.client.gui.GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker delta,
                                      CallbackInfo ci) {
        WingHud.draw(g, delta.getGameTimeDeltaPartialTick(false));
    }
}
//#elif MC >= 26.1
//$$ @Mixin(net.minecraft.client.gui.Gui.class)
//$$ public abstract class MixinGuiWings {
//$$
//$$     @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
//$$             at = @At("TAIL"), require = 0)
//$$     private void gulliver$wingStamina(net.minecraft.client.gui.GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker delta,
//$$                                       CallbackInfo ci) {
//$$         WingHud.draw(g, delta.getGameTimeDeltaPartialTick(false));
//$$     }
//$$ }
//#elif MC >= 1.21
//$$ @Mixin(net.minecraft.client.gui.Gui.class)
//$$ public abstract class MixinGuiWings {
//$$
//$$     @Inject(method = "render", at = @At("TAIL"), require = 0)
//$$     private void gulliver$wingStamina(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.DeltaTracker delta,
//$$                                       CallbackInfo ci) {
//$$         WingHud.draw(g, delta.getGameTimeDeltaPartialTick(false));
//$$     }
//$$ }
//#else
//$$ @Mixin(net.minecraft.client.gui.Gui.class)
//$$ public abstract class MixinGuiWings {
//$$
//$$     @Inject(method = "render", at = @At("TAIL"), require = 0)
//$$     private void gulliver$wingStamina(net.minecraft.client.gui.GuiGraphics g, float partialTick, CallbackInfo ci) {
//$$         WingHud.draw(g, partialTick);
//$$     }
//$$ }
//#endif
