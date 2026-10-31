//#if MC >= 1.21.10
package gulliver.mixin.client;

import net.minecraft.client.renderer.WorldBorderRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The world border is drawn at the end of the weather pass, after the
 * water and the clouds; the breath gust goes right after it, so nothing
 * see-through behind the wind is painted over it (BreathRibbonRenderer).
 */
@Mixin(WorldBorderRenderer.class)
public abstract class MixinWorldBorderRendererBreath {

    //#if MC >= 26.3
    //$$ @Inject(method = "render", at = @At("RETURN"), require = 0)
    //$$ private void gulliver$windAfterWeather(net.minecraft.client.renderer.state.level.WorldBorderRenderState state,
    //$$                                        com.mojang.renderpearl.api.commands.RenderPass pass,
    //$$                                        net.minecraft.world.phys.Vec3 camera, double distance, CallbackInfo ci) {
    //$$     gulliver.client.BreathRibbonRenderer.drawLate(pass);
    //$$ }
    //$$
    //$$ @Inject(method = "renderOit", at = @At("RETURN"), require = 0)
    //$$ private void gulliver$windAfterWeatherOit(net.minecraft.client.renderer.state.level.WorldBorderRenderState state,
    //$$                                           net.minecraft.world.phys.Vec3 camera, double distance,
    //$$                                           net.minecraft.client.renderer.oit.OitStage stage,
    //$$                                           com.mojang.renderpearl.api.commands.RenderPass pass, CallbackInfo ci) {
    //$$     gulliver.client.BreathRibbonRenderer.drawLateOit(stage, pass);
    //$$ }
    //#else
    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private void gulliver$windAfterWeather(CallbackInfo ci) {
        gulliver.client.BreathRibbonRenderer.drawLate();
    }
    //#endif
}
//#endif
