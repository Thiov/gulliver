//#if MC >= 1.21.10 && MC < 26.1
package gulliver.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.state.ParticlesRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.10-1.21.11: the breath gust is submitted in the particles pass,
 * which is drawn after the water (BreathRibbonRenderer).
 */
@Mixin(ParticlesRenderState.class)
public abstract class MixinParticlesRenderStateBreath {

    @Inject(method = "submit", at = @At("TAIL"), require = 0)
    private void gulliver$windAfterWater(SubmitNodeStorage storage, CameraRenderState camera, CallbackInfo ci) {
        gulliver.client.BreathRibbonRenderer.submit(new PoseStack(), storage);
    }
}
//#endif
