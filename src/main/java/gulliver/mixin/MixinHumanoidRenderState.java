package gulliver.mixin;

import gulliver.access.IGlideRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Adds per-frame gliding / doesUmbrella flags to HumanoidRenderState so
 * HumanoidModel.setupAnim can read them. Populated by
 * MixinLivingEntityRenderer.extractRenderState; consumed by
 * MixinHumanoidModelPose.
 */
@Mixin(HumanoidRenderState.class)
public abstract class MixinHumanoidRenderState implements IGlideRenderState {

    @Unique private boolean gulliver$isGliding;
    @Unique private boolean gulliver$doesUmbrella;
    @Unique private boolean gulliver$isRafting;
    @Unique private boolean gulliver$hasHandPassenger;
    @Unique private float gulliver$sizeMultiplier = 1.0F;

    @Override @Unique public boolean gulliver$isGliding() { return gulliver$isGliding; }
    @Override @Unique public boolean gulliver$doesUmbrella() { return gulliver$doesUmbrella; }
    @Override @Unique public boolean gulliver$isRafting() { return gulliver$isRafting; }
    @Override @Unique public boolean gulliver$hasHandPassenger() { return gulliver$hasHandPassenger; }
    @Override @Unique public float gulliver$getSizeMultiplier() { return gulliver$sizeMultiplier; }
    @Override @Unique public void gulliver$setGliding(boolean v) { gulliver$isGliding = v; }
    @Override @Unique public void gulliver$setDoesUmbrella(boolean v) { gulliver$doesUmbrella = v; }
    @Override @Unique public void gulliver$setRafting(boolean v) { gulliver$isRafting = v; }
    @Override @Unique public void gulliver$setHandPassenger(boolean v) { gulliver$hasHandPassenger = v; }
    @Override @Unique public void gulliver$setSizeMultiplier(float v) { gulliver$sizeMultiplier = v; }
}
