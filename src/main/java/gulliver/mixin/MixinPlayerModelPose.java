package gulliver.mixin;

import gulliver.access.IGlideRenderState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PlayerModel.setupAnim(AvatarRenderState) does NOT call super.setupAnim(),
 * so MixinHumanoidModelPose's hooks never fire on players. This parallel
 * mixin applies the same pose logic on the player path.
 *
 * We only ever write .xRot/.yRot/.zRot. We do NOT touch .x/.y/.z —
 * those are owned by vanilla setupAnim (crouch shifts body/arms/legs
 * via .y) and stomping them would float arms above a crouched body.
 *
 * @Shadow on inherited fields fails because Mixin requires fields to be
 * DECLARED on the target class. We instead cast `this` to HumanoidModel
 * and access rightArm/leftArm/etc. directly through the public-widened
 * fields exposed by the merged-allpublic MC jar.
 */
@Mixin(PlayerModel.class)
public abstract class MixinPlayerModelPose {

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",
            at = @At("RETURN"))
    private void gulliver$applyPose(AvatarRenderState state, CallbackInfo ci) {
        HumanoidModel<?> hm = (HumanoidModel<?>) (Object) this;
        ModelPart rightArm = hm.rightArm;
        ModelPart leftArm  = hm.leftArm;
        ModelPart rightLeg = hm.rightLeg;
        ModelPart leftLeg  = hm.leftLeg;

        IGlideRenderState g = (IGlideRenderState) state;
        if (g.gulliver$isGliding()) {
            float upRot = -(float) Math.PI;
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
            rightArm.xRot = -(float) (Math.PI / 2.0);
            rightArm.yRot = 0.0F;
            rightArm.zRot = 0.0F;
        }
        if (g.gulliver$isRafting()) {
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
        }
    }
}
