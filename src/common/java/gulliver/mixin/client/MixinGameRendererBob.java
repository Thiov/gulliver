package gulliver.mixin.client;

import gulliver.api.IResizeableEntity;
import gulliver.client.VisualSize;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
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
 * polatedWalkDistance — a separate source from walkAnimation. The phase
 * is accumulated from the per-frame change of that distance, divided by
 * the movement multiplier of the DRAWN size, so bob cycles at vanilla
 * rate for ALL sizes (matches 1.6.4 feel of "anim is size-invariant")
 * and, unlike dividing the running total, a size change in mid-stride
 * only changes the speed of the phase from then on instead of jumping it
 * (the old jitter while resizing). Bob is periodic in 2, so only the
 * remainder is handed back; a double keeps it exact over a long walk.
 */
@Mixin(GameRenderer.class)
public abstract class MixinGameRendererBob {

    @Shadow @Final private Minecraft minecraft;

    @Unique private boolean gulliver$bobStarted;
    @Unique private int gulliver$bobEntityId;
    @Unique private float gulliver$bobLastDist;
    @Unique private double gulliver$bobPhase;

    @ModifyConstant(method = "bobView", constant = @Constant(floatValue = 0.5F), require = 0)
    private float gulliver$scaleBobAmplitude(float c) {
        Entity cam = this.minecraft.getCameraEntity();
        if (cam == null) return c;
        float root = (float) Math.sqrt(VisualSize.of(cam));
        float factor = Math.min(root, 1.0F);
        if (factor == 1.0F) return c;
        return c * factor;
    }

    // The walk-distance local: slot 3 in bobView(CameraRenderState, PoseStack);
    // before 26.1, bobView(PoseStack, float) keeps two more locals first.
    //#if MC >= 26.1
    @ModifyVariable(method = "bobView", at = @At(value = "STORE"), index = 3, require = 0)
    //#else
    //$$ @ModifyVariable(method = "bobView", at = @At(value = "STORE"), index = 5, require = 0)
    //#endif
    private float gulliver$normalizeBobFreq(float walkDist) {
        Entity cam = this.minecraft.getCameraEntity();
        if (cam == null) {
            gulliver$bobStarted = false;
            return walkDist;
        }
        float size = VisualSize.of(cam);
        float mov = ((IResizeableEntity) cam).isWeighted() ? size : (float) Math.sqrt(size);
        if (!(mov > 0.0F)) mov = 1.0F;
        if (!gulliver$bobStarted || cam.getId() != gulliver$bobEntityId
                || Math.abs(walkDist - gulliver$bobLastDist) > 8.0F) {
            gulliver$bobStarted = true;
            gulliver$bobEntityId = cam.getId();
            gulliver$bobPhase = walkDist / (double) mov;
        } else {
            gulliver$bobPhase += (walkDist - gulliver$bobLastDist) / (double) mov;
        }
        gulliver$bobLastDist = walkDist;
        return (float) (gulliver$bobPhase % 2.0D);
    }
}
