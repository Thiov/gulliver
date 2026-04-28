package gulliver.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import gulliver.access.IGlideRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.6.4 bfj.java:77 — held items rendered with sizerootdiv = 1/sqrt(size)
 * inside the player's already-size-scaled GL state. Net item scale =
 * size * (1/sqrt(size)) = sqrt(size).
 *
 * Critical positioning detail: the scale must apply AFTER the arm-bone
 * translation — otherwise the bone-position itself gets scaled, leaving
 * a visible gap between the held item and the hand. We hook
 * `submitArmWithItem` and apply scale at INVOKE-AFTER on
 * ArmedModel.translateToHand, riding inside the vanilla pushPose/popPose
 * pair.
 */
@Mixin(ItemInHandLayer.class)
public abstract class MixinItemInHandLayer {

    /**
     * Inject AFTER the sub-bone translate (the (±arm-x/16, hand-y/16,
     * hand-z/16) call inside submitArmWithItem that positions the item
     * at the FINGER tip, not the bone origin). After this point, all
     * remaining transforms in submitArmWithItem affect ONLY the item
     * itself, so scaling here scales the item without moving its anchor
     * away from the hand.
     */
    @Inject(method = "submitArmWithItem(Lnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At(value = "INVOKE",
                     target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V",
                     shift = At.Shift.AFTER,
                     ordinal = 0))
    private void gulliver$adjustItem(ArmedEntityRenderState state, ItemStackRenderState itemRender,
                                      ItemStack stack, HumanoidArm arm, PoseStack pose,
                                      SubmitNodeCollector buf, int light, CallbackInfo ci) {
        if (!(state instanceof IGlideRenderState g)) return;
        // Glide pose centering. After translateToHand + sub-bone
        // translate, the pose is at the FINGERTIP of the right arm with
        // arm-local frame (X+ = toward body's left, Y+ = up the arm
        // toward shoulder, Z+ = forward in arm's facing direction).
        //
        // To put the paper SPANNING the gap between both raised hands
        // (held by the fingers like a banner held overhead by both
        // hands), we translate in arm-local X+ by half the shoulder
        // width (5/16 = 0.3125), AND rotate so the paper's long axis
        // lies horizontal between the hands. The item-natural Z (length)
        // axis maps to spanning-the-hands direction after the 90° Z-rot.
        if (g.gulliver$isGliding() && arm == HumanoidArm.RIGHT) {
            pose.translate(0.3125F, 0.0F, 0.0F);
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(90.0F));
            // Lift paper higher above the hand tips and slightly back
            // toward the head (arm-local -Y after Z-rot is "toward
            // shoulder/back"; +Z is "up above the spanned banner").
            pose.translate(0.0F, -0.25F, 0.6F);
        }
        float size = g.gulliver$getSizeMultiplier();
        if (size != 1.0F) {
            float invRoot = 1.0F / (float) Math.sqrt(size);
            pose.scale(invRoot, invRoot, invRoot);
        }
    }
}
