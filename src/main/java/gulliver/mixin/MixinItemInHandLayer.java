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

    @Inject(method = "submitArmWithItem(Lnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/model/ArmedModel;translateToHand(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
                     shift = At.Shift.AFTER))
    private void gulliver$scaleAtHand(ArmedEntityRenderState state, ItemStackRenderState itemRender,
                                       ItemStack stack, HumanoidArm arm, PoseStack pose,
                                       SubmitNodeCollector buf, int light, CallbackInfo ci) {
        if (!(state instanceof IGlideRenderState g)) return;
        float size = g.gulliver$getSizeMultiplier();
        if (size == 1.0F) return;
        float invRoot = 1.0F / (float) Math.sqrt(size);
        pose.scale(invRoot, invRoot, invRoot);
    }
}
