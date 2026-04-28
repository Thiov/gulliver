package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

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
        float root = ((IResizeableEntity) cam).getSizeMultiplierRoot();
        if (root == 1.0F) return c;
        return c * root;
    }
}
