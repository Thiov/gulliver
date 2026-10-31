//#if MC >= 26.1 && MC < 26.2
package gulliver.mixin.client;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.1: the breath gust is drawn with the translucent particles, after the water (BreathRibbonRenderer). */
@Mixin(FeatureRenderDispatcher.class)
public abstract class MixinFeatureRenderDispatcherBreath {

    @Shadow @Final private MultiBufferSource.BufferSource bufferSource;

    @Inject(method = "renderTranslucentParticles", at = @At("HEAD"), require = 0)
    private void gulliver$windAfterWater(CallbackInfo ci) {
        gulliver.client.BreathRibbonRenderer.draw(bufferSource);
    }
}
//#endif
