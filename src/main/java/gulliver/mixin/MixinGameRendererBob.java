package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 1.6.4 bfe.java:559 view-bobbing scaling — the X-translate amplitude
 * was multiplied by getRenderViewbobFactor = getSizeMultiplierRoot().
 * Modern GameRenderer.bobView translates by `sin(f*pi) * bob * 0.5F`
 * (only X uses the 0.5F factor; Y uses cos * bob without 0.5F).
 *
 * @ModifyConstant on the literal 0.5F multiplies it by sqrt(size). At
 * sizeMultiplier=0.125 (extra-tiny) the amplitude shrinks by sqrt(0.125)
 * ~= 0.354 — large bob shrinks to a third, matching the 1.6.4 feel
 * where tinies don't bob through their own faces.
 *
 * Mirrors 1.6.4 bfe.java:251 `getRenderViewbobFactor = sizeMultiplierRoot`.
 */
@Mixin(GameRenderer.class)
public abstract class MixinGameRendererBob {

    @Shadow @Final private Minecraft minecraft;

    @ModifyConstant(method = "bobView", constant = @Constant(floatValue = 0.5F))
    private float gulliver$scaleBob(float c) {
        Entity cam = this.minecraft.getCameraEntity();
        if (cam == null) return c;
        // 1.6.4 bfe.java:251 used sqrt(size) directly. That AMPLIFIES bob
        // for hugies (sqrt(8)*0.5 = 1.41x amplitude — visible as huge
        // hand-side-to-side swing in 1st person). Clamp factor to 1.0
        // so only tinies dampen, hugies stay vanilla. Better feel.
        float root = ((IResizeableEntity) cam).getSizeMultiplierRoot();
        float factor = Math.min(root, 1.0F);
        if (factor == 1.0F) return c;
        return c * factor;
    }

    /**
     * View-bob FREQUENCY proportional compensation. Same intent as the
     * walkAnimationPos scaling in MixinLivingEntityRenderer — bob cycles
     * at body-stride frequency regardless of size.
     */
    @ModifyVariable(method = "bobView", at = @At(value = "STORE", ordinal = 0), index = 3)
    private float gulliver$boostBobFreq(float walkDist) {
        Entity cam = this.minecraft.getCameraEntity();
        if (cam == null) return walkDist;
        float size = ((IResizeableEntity) cam).getSizeMultiplier();
        if (size == 1.0F || size <= 0.0F) return walkDist;
        return walkDist / (float) Math.sqrt(size);
    }
}
