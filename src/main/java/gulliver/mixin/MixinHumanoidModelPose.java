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
 * Post-vanilla-setupAnim pose adjustments. KEY DESIGN RULE: vanilla
 * walking/punching animation MUST be untouched across sizes (user
 * observation: anything else feels jittery when /doublesize-ing during
 * a walk). The model itself is scaled by `state.scale` already, so
 * vanilla anim cycles naturally adjust to body size in render-space.
 *
 * The only entry-points for our pose changes:
 *  - isGliding   -> arms straight up, legs dampened (parachute pose)
 *  - doesUmbrella -> right arm straight up
 *  - isRafting   -> seated paddle pose
 *
 * We only ever write .xRot/.yRot/.zRot. We do NOT touch .x/.y/.z bone
 * positions — those are owned by vanilla setupAnim (e.g. crouching
 * shifts arm.y to 3.0, body.y to 3.2, leg.y to 12.2) and by subclass
 * setupAnim overrides (EndermanModel re-aligns its own leg origins
 * since Enderman is taller-and-thinner than Player). Any reset of
 * those fields would either undo vanilla's crouch shift on the player
 * OR stomp non-Player humanoid defaults on every mob that extends
 * HumanoidModel (Zombie, Skeleton, Husk, Drowned, Stray, WitherSkeleton,
 * Piglin, ZombifiedPiglin, ArmorStand, Giant, Enderman-with-block...).
 */
@Mixin(HumanoidModel.class)
public abstract class MixinHumanoidModelPose {

    @Shadow public ModelPart rightArm;
    @Shadow public ModelPart leftArm;
    @Shadow public ModelPart rightLeg;
    @Shadow public ModelPart leftLeg;
    @Shadow public ModelPart body;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",
            at = @At("RETURN"))
    private void gulliver$applyPose(HumanoidRenderState state, CallbackInfo ci) {
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
