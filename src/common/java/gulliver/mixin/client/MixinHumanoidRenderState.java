//#if MC >= 1.21.2
package gulliver.mixin.client;

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
    @Unique private float gulliver$extractScale = 1.0F;
    @Unique private net.minecraft.world.entity.HumanoidArm gulliver$propArm;
    @Unique private float gulliver$eatLift;
    @Unique private float gulliver$eatBite;

    @Override @Unique public boolean gulliver$isGliding() { return gulliver$isGliding; }
    @Override @Unique public boolean gulliver$doesUmbrella() { return gulliver$doesUmbrella; }
    @Override @Unique public boolean gulliver$isRafting() { return gulliver$isRafting; }
    @Override @Unique public boolean gulliver$hasHandPassenger() { return gulliver$hasHandPassenger; }
    @Override @Unique public float gulliver$getSizeMultiplier() { return gulliver$sizeMultiplier; }
    @Override @Unique public float gulliver$getExtractScale() { return gulliver$extractScale; }
    @Override @Unique public void gulliver$setGliding(boolean v) { gulliver$isGliding = v; }
    @Override @Unique public void gulliver$setDoesUmbrella(boolean v) { gulliver$doesUmbrella = v; }
    @Override @Unique public void gulliver$setRafting(boolean v) { gulliver$isRafting = v; }
    @Override @Unique public void gulliver$setHandPassenger(boolean v) { gulliver$hasHandPassenger = v; }
    @Override @Unique public void gulliver$setSizeMultiplier(float v) { gulliver$sizeMultiplier = v; }
    @Override @Unique public void gulliver$setExtractScale(float v) { gulliver$extractScale = v; }
    @Override @Unique public net.minecraft.world.entity.HumanoidArm gulliver$getPropArm() { return gulliver$propArm; }
    @Override @Unique public void gulliver$setPropArm(net.minecraft.world.entity.HumanoidArm arm) { gulliver$propArm = arm; }
    @Override @Unique public float gulliver$getEatLift() { return gulliver$eatLift; }
    @Override @Unique public float gulliver$getEatBite() { return gulliver$eatBite; }
    @Override @Unique public void gulliver$setEat(float lift, float bite) { gulliver$eatLift = lift; gulliver$eatBite = bite; }
    @Unique private float gulliver$mobGrip;
    @Override @Unique public float gulliver$getMobGrip() { return gulliver$mobGrip; }
    @Override @Unique public void gulliver$setMobGrip(float grip) { gulliver$mobGrip = grip; }
}
//#endif
