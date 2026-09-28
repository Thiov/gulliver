package gulliver.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import gulliver.api.IResizeableLiving;
import gulliver.client.PropRender;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.20.1 held items: hide the lily-pad while it is a raft or umbrella,
 * hold the glider paper across both raised hands, and scale items by
 * the chosen held-item style (see the 26.x twin for the reasoning).
 */
@Mixin(ItemInHandLayer.class)
public abstract class MixinItemInHandLayer {

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void gulliver$hideLilyPad(LivingEntity entity, ItemStack stack, ItemDisplayContext ctx, HumanoidArm arm,
                                       PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        IResizeableLiving g = (IResizeableLiving) entity;
        if ((g.isRafting() || g.doesUmbrella()) && arm == PropRender.propArm(entity)) ci.cancel();
    }

    @Inject(method = "renderArmWithItem",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V",
                     shift = At.Shift.AFTER, ordinal = 0))
    private void gulliver$adjustItem(LivingEntity entity, ItemStack stack, ItemDisplayContext ctx, HumanoidArm arm,
                                      PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        IResizeableLiving g = (IResizeableLiving) entity;
        if (g.isGliding() && arm == PropRender.propArm(entity)) {
            float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
            pose.translate(0.3125F * side, 0.0F, 0.0F);
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(90.0F * side));
            pose.translate(0.0F, 0.3F, -0.3F);
        }
        float size = g.getSizeMultiplier();
        if (size != 1.0F && !gulliver.common.GulliverConfig.INSTANCE.client.proportionalHeldItems()) {
            float invRoot = 1.0F / (float) Math.sqrt(size);
            pose.scale(invRoot, invRoot, invRoot);
        }
    }
}
