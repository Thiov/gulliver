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
 * Post-vanilla-setupAnim pose adjustments:
 *
 * 1. Glide pose (paper-as-parachute): both arms straight up + legs
 *    dampened. Pins rightArm.x = leftArm.x = 0 so both raised arms
 *    extend from body center, making the bone-attached held item
 *    appear centered above the head.
 *
 * 2. Umbrella pose (lily-pad in rain): right arm up only.
 *
 * 3. Raft pose (lily-pad in water as tiny): seated with one arm
 *    paddling. Mirrors 1.6.4 bbj.java:149-156 isRafting branch.
 *
 * 4. Tiny walk-cycle frequency compensation: a tiny moves at sqrt(size)
 *    of world-speed so vanilla walkAnimationPos increments slowly,
 *    making the gait look like slow-motion. We recompute arm/leg
 *    rotations with adjustedPos = walkPos / sqrt(size) so the cycle
 *    runs at body-length-per-stride frequency. GATED on attackTime==0
 *    to not compose with the attack animation (which writes to
 *    rightArm.xRot/zRot during punch).
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
            // Pin both shoulders to body center so the raised arms
            // converge overhead — held item (right-finger anchored)
            // ends up visually centered above the head.
            rightArm.x = 0.0F;
            leftArm.x = 0.0F;
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
        if (g.gulliver$isRafting()) {
            // 1.6.4 bbj.java:149-156: arms paddle (slow swing), legs
            // crossed forward (-72° xRot, ±18° yRot — sitting).
            float pos = state.walkAnimationPos;
            float speed = Math.min(state.walkAnimationSpeed, 1.0F);
            // Right arm = slow opposing-phase paddle; left = normal phase.
            rightArm.xRot = Mth.cos(pos * 0.6662F * 0.125F + (float) Math.PI) * 2.0F * speed * 0.5F;
            leftArm.xRot  = Mth.cos(pos * 0.6662F)                          * 2.0F * speed * 0.5F;
            rightArm.yRot = 0.0F;
            leftArm.yRot = 0.0F;
            rightLeg.xRot = -1.2566371F;   // -72° forward (sitting)
            leftLeg.xRot  = -1.2566371F;
            rightLeg.yRot =  0.31415927F;  // splayed +18°
            leftLeg.yRot  = -0.31415927F;
            return;
        }

        // Tiny walk-cycle frequency compensation. Skip during attack so
        // we don't fight setupAttackAnimation's xRot writes (otherwise
        // arms cross into the body during punch).
        if (state.attackTime <= 0.0F) {
            float size = g.gulliver$getSizeMultiplier();
            if (size < 1.0F && size > 0.0F) {
                float root = (float) Math.sqrt(size);
                float pos = state.walkAnimationPos / root;
                float speed = Math.min(state.walkAnimationSpeed, 1.0F);
                // Re-derive vanilla walking arm/leg cycles with adjusted pos
                rightArm.xRot = Mth.cos(pos * 0.6662F + (float) Math.PI) * 2.0F * speed * 0.5F;
                leftArm.xRot  = Mth.cos(pos * 0.6662F)                  * 2.0F * speed * 0.5F;
                rightLeg.xRot = Mth.cos(pos * 0.6662F)                  * 1.4F * speed;
                leftLeg.xRot  = Mth.cos(pos * 0.6662F + (float) Math.PI) * 1.4F * speed;
            }
        }
    }
}
