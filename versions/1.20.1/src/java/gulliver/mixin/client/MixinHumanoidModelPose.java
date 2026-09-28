package gulliver.mixin.client;

import gulliver.access.IGulliverShoulderInternal;
import gulliver.api.IResizeableLiving;
import gulliver.client.PropRender;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.20.1 poses: arms up holding the glider paper, one arm up under the
 * lily-pad umbrella, an arm out holding a carried creature, paddling on
 * the raft. PlayerModel copies these onto its sleeves after calling up.
 */
@Mixin(HumanoidModel.class)
public abstract class MixinHumanoidModelPose {

    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;
    @Shadow @Final public ModelPart rightLeg;
    @Shadow @Final public ModelPart leftLeg;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("RETURN"))
    private void gulliver$applyPose(LivingEntity entity, float pos, float speedRaw, float age,
                                     float netHeadYaw, float headPitch, CallbackInfo ci) {
        IResizeableLiving g = (IResizeableLiving) entity;
        float speed = Math.min(speedRaw, 1.0F);
        if (g.isGliding()) {
            float upRot = -(float) Math.PI;
            rightArm.xRot = upRot;
            leftArm.xRot = upRot;
            rightArm.yRot = 0.0F;
            leftArm.yRot = 0.0F;
            rightArm.zRot = 0.0F;
            leftArm.zRot = 0.0F;
            rightLeg.xRot = Mth.cos(pos * 0.6662F * 0.25F + (float) Math.PI) * 1.4F * speed * 0.25F;
            leftLeg.xRot = Mth.cos(pos * 0.6662F * 0.25F) * 1.4F * speed * 0.25F;
            rightLeg.yRot = 0.0F;
            leftLeg.yRot = 0.0F;
            return;
        }
        if (g.doesUmbrella()) {
            ModelPart arm = PropRender.propArm(entity) == HumanoidArm.LEFT ? leftArm : rightArm;
            arm.xRot = -(float) Math.PI;
            arm.yRot = 0.0F;
            arm.zRot = 0.0F;
            return;
        }
        if (((IGulliverShoulderInternal) entity).gulliver$getHandEntity() != null) {
            rightArm.xRot = -(float) (Math.PI / 2.0);
            rightArm.yRot = 0.0F;
            rightArm.zRot = 0.0F;
        }
        if (g.isRafting()) {
            rightArm.xRot = Mth.cos(pos * 0.6662F * 0.125F + (float) Math.PI) * 2.0F * speed * 0.5F;
            leftArm.xRot = Mth.cos(pos * 0.6662F) * 2.0F * speed * 0.5F;
            rightArm.yRot = 0.0F;
            leftArm.yRot = 0.0F;
            rightLeg.xRot = -1.2566371F;
            leftLeg.xRot = -1.2566371F;
            rightLeg.yRot = 0.31415927F;
            leftLeg.yRot = -0.31415927F;
        }
    }
}
