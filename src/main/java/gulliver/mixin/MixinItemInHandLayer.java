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
        // Glide pose centering. After translateToHand + the sub-bone
        // translate, pose is at the FINGER position with arm-local frame.
        // For arm pointing UP, the local +Y direction (arm extension) is
        // already past the fingertip — moving in -Y goes back toward
        // shoulder, +Z goes "above the held item". We want item to sit
        // ABOVE the held finger (so hands grip it by the edges).
        if (g.gulliver$isGliding() && arm == HumanoidArm.RIGHT) {
            // Move the item +0.3125 in X (toward body center to span
            // both hands), and -0.3 in Y (back toward shoulder so the
            // ITEM is held BY the fingers, not at fingertip).
            pose.translate(0.3125F, -0.3F, 0.0F);
            // Rotate around Z 90° so paper lies horizontal across the
            // top of head (long edge spans both hands like a banner).
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(90.0F));
        }
        float size = g.gulliver$getSizeMultiplier();
        if (size != 1.0F) {
            float invRoot = 1.0F / (float) Math.sqrt(size);
            pose.scale(invRoot, invRoot, invRoot);
        }
    }
}
