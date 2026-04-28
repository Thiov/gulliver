package gulliver.mixin;

import gulliver.access.IGlideRenderState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Post-vanilla-setupAnim pose adjustments. KEY DESIGN RULE: vanilla
 * walking/punching animation MUST be untouched across sizes (user
 * observation: anything else feels jittery when /doublesize-ing during
 * a walk). The model itself is scaled by `state.scale` already, so
 * vanilla anim cycles naturally adjust to body size in render-space.
 *
 * The only entry-point for our pose changes:
 *  - isGliding   -> arms straight up, legs dampened (parachute pose)
 *  - doesUmbrella -> right arm straight up
 *  - isRafting   -> seated paddle pose
 *
 * IMPORTANT: arm.x / leg.x are RESET to vanilla defaults at HEAD of
 * every setupAnim call. Setting arm.x = 0 (centering pin during glide)
 * PERSISTS across frames in modern MC's reused ModelPart instances —
 * any subsequent setupAnim including punch animation would inherit the
 * compressed shoulders. We restore -5/+5 each frame so the leak heals
 * the moment glide stops.
 */
@Mixin(HumanoidModel.class)
public abstract class MixinHumanoidModelPose {

    @Shadow public ModelPart rightArm;
    @Shadow public ModelPart leftArm;
    @Shadow public ModelPart rightLeg;
    @Shadow public ModelPart leftLeg;
    @Shadow public ModelPart body;

    /**
     * Reset arm.x / leg.x to vanilla defaults BEFORE vanilla setupAnim
     * runs (so any leaked modification from a previous frame's glide
     * pose doesn't carry over).
     */
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",
            at = @At("HEAD"))
    private void gulliver$resetBonePositionsHead(HumanoidRenderState state, CallbackInfo ci) {
        gulliver$resetBonePositions();
    }

    @Unique
    private void gulliver$resetBonePositions() {
        rightArm.x = -5.0F;
        leftArm.x  =  5.0F;
        rightArm.y =  2.0F;
        leftArm.y  =  2.0F;
        rightLeg.x = -1.9F;
        leftLeg.x  =  1.9F;
        rightLeg.y = 12.0F;
        leftLeg.y  = 12.0F;
    }

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",
            at = @At("RETURN"))
    private void gulliver$applyPose(HumanoidRenderState state, CallbackInfo ci) {
        IGlideRenderState g = (IGlideRenderState) state;
        if (g.gulliver$isGliding()) {
            float upRot = -(float) Math.PI;
            // Both arms straight up. We DON'T pin arm.x = 0 anymore (caused
            // a leak/persist bug) — instead the held item is centered via
            // a translate inside MixinItemInHandLayer.
            rightArm.xRot = upRot;
            leftArm.xRot  = upRot;
            rightArm.yRot = 0.0F;
            leftArm.yRot  = 0.0F;
            rightArm.zRot = 0.0F;
            leftArm.zRot  = 0.0F;
            float pos = state.walkAnimationPos;
            float speed = Math.min(state.walkAnimationSpeed, 1.0F);
            rightLeg.xRot = Mth.cos(pos * 0.6662F * 0.25F + (float) Math.PI) * 1.4F * speed * 0.25F;
            leftLeg.xRot  = Mth.cos(pos * 0.6662F * 0.25F)                   * 1.4F * speed * 0.25F;
            rightLeg.yRot = 0.0F;
            leftLeg.yRot  = 0.0F;
            return;
        }
        if (g.gulliver$doesUmbrella()) {
            rightArm.xRot = -(float) Math.PI;
            rightArm.yRot = 0.0F;
            rightArm.zRot = 0.0F;
            return;
        }
        if (g.gulliver$hasHandPassenger()) {
            // Right arm extended forward + up to "hold" the passenger.
            // -π/2 = 90° forward (pointing horizontally outward).
            rightArm.xRot = -(float) (Math.PI / 2.0);
            rightArm.yRot = 0.0F;
            rightArm.zRot = 0.0F;
        }
        if (g.gulliver$isRafting()) {
            // 1.6.4 bbj.java:149-156: arms paddle, legs cross forward.
            float pos = state.walkAnimationPos;
            float speed = Math.min(state.walkAnimationSpeed, 1.0F);
            rightArm.xRot = Mth.cos(pos * 0.6662F * 0.125F + (float) Math.PI) * 2.0F * speed * 0.5F;
            leftArm.xRot  = Mth.cos(pos * 0.6662F)                            * 2.0F * speed * 0.5F;
            rightArm.yRot = 0.0F;
            leftArm.yRot  = 0.0F;
            rightLeg.xRot = -1.2566371F;
            leftLeg.xRot  = -1.2566371F;
            rightLeg.yRot =  0.31415927F;
            leftLeg.yRot  = -0.31415927F;
            gulliver$resetBonePositions();
            return;
        }
        // Walk-cycle freq normalization is now done at SOURCE in
        // MixinLivingEntityWalkAnim (scale walkAnimation.update speed
        // arg by 1/sizeMovementMultiplier — matches 1.6.4 sf.java:574
        // verbatim). No render-time recompute needed; vanilla setupAnim
        // already sees a normalized walkAnimationPos.
        gulliver$resetBonePositions();
    }
}
