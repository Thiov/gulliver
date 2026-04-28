package gulliver.mixin;

import gulliver.access.IGlideRenderState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.6.4 bbj.java:249-257 — when the player model is gliding (paper held
 * overhead, parachute pose) both arms point straight up; when umbrella'ing
 * (lily-pad held overhead in rain) the right arm points up. Plus while
 * gliding the legs animate at 0.25x walk-cycle amplitude (lines 160-163)
 * so they dangle instead of running.
 *
 * Modern HumanoidModel.setupAnim takes a state object (no entity ref) —
 * we read the flags from IGlideRenderState which MixinHumanoidRenderState
 * adds and MixinLivingEntityRenderer.extractRenderState populates.
 *
 * Translation:
 *   bbj.f.f / bbj.g.f -> rightArm.xRot / leftArm.xRot (xRot is the pitch
 *     in modern; -PI = arm rotated 180 deg backward = pointing up).
 *   bbj.h.f / bbj.i.f -> rightLeg.xRot / leftLeg.xRot.
 */
@Mixin(HumanoidModel.class)
public abstract class MixinHumanoidModelPose {

    @Shadow public ModelPart rightArm;
    @Shadow public ModelPart leftArm;
    @Shadow public ModelPart rightLeg;
    @Shadow public ModelPart leftLeg;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",
            at = @At("RETURN"))
    private void gulliver$applyGlidePose(HumanoidRenderState state, CallbackInfo ci) {
        IGlideRenderState g = (IGlideRenderState) state;
        if (g.gulliver$isGliding()) {
            float upRot = -(float) Math.PI;
            rightArm.xRot = upRot;
            leftArm.xRot = upRot;
            rightArm.yRot = 0.0F;
            leftArm.yRot = 0.0F;
            rightArm.zRot = 0.0F;
            leftArm.zRot = 0.0F;
            float pos = state.walkAnimationPos;
            float speed = Math.min(state.walkAnimationSpeed, 1.0F);
            rightLeg.xRot = Mth.cos(pos * 0.6662F * 0.25F + (float) Math.PI) * 1.4F * speed * 0.25F;
            leftLeg.xRot  = Mth.cos(pos * 0.6662F * 0.25F)                  * 1.4F * speed * 0.25F;
            rightLeg.yRot = 0.0F;
            leftLeg.yRot = 0.0F;
            return;
        }
        if (g.gulliver$doesUmbrella()) {
            rightArm.xRot = -(float) Math.PI;
            rightArm.yRot = 0.0F;
            rightArm.zRot = 0.0F;
            return;
        }
        // Walk-cycle FREQUENCY compensation is done in MixinLivingEntityRenderer
        // (scale walkAnimationPos by 1/sqrt(size) before setupAnim runs).
        // Don't dampen amplitude here — tinies should swing arms/legs at
        // body-proportional amplitude (which they do automatically since
        // the whole model is scaled by size at render time).
    }
}
