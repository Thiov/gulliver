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
 * View-bob scaling.
 *
 * AMPLITUDE: 1.6.4 bfe.java:559 multiplies the X-translate by sqrt(size)
 * — but that AMPLIFIES bob for hugies (sqrt(8)=2.83x). Clamp factor
 * to 1.0 so only tinies dampen.
 *
 * FREQUENCY: bob phase comes from ClientAvatarState.getBackwardsInter-
 * polatedWalkDistance — a separate source from walkAnimation. Divide
 * the local by sizeMovementMultiplier so bob cycles at vanilla rate
 * for ALL sizes (matches 1.6.4 feel of "anim is size-invariant").
 */
@Mixin(GameRenderer.class)
public abstract class MixinGameRendererBob {

    @Shadow @Final private Minecraft minecraft;

    @ModifyConstant(method = "bobView", constant = @Constant(floatValue = 0.5F))
    private float gulliver$scaleBobAmplitude(float c) {
        Entity cam = this.minecraft.getCameraEntity();
        if (cam == null) return c;
        float root = ((IResizeableEntity) cam).getSizeMultiplierRoot();
        float factor = Math.min(root, 1.0F);
        if (factor == 1.0F) return c;
        return c * factor;
    }

    @ModifyVariable(method = "bobView", at = @At(value = "STORE"), index = 3)
    private float gulliver$normalizeBobFreq(float walkDist) {
        Entity cam = this.minecraft.getCameraEntity();
        if (cam == null) return walkDist;
        IResizeableEntity sized = (IResizeableEntity) cam;
        float m = sized.getSizeMultiplier();
        if (m == 1.0F) return walkDist;
        float mov = sized.getSizeMovementMultiplier();
        if (mov <= 0.0F) return walkDist;
        return walkDist / mov;
    }
}
