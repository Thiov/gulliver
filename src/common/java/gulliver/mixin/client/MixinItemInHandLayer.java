package gulliver.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import gulliver.access.IGlideRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
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

    private static final String GULLIVER$ARM_WITH_ITEM =
            //#if MC >= 1.21.11
            "submitArmWithItem(Lnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V";
            //#else
            //$$ "submitArmWithItem(Lnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V";
            //#endif

    /**
     * The lily-pad is drawn as its own disc while rafting (under the feet)
     * or held up as an umbrella (over the head), so the arm holding it
     * doesn't also draw it.
     */
    @Inject(method = GULLIVER$ARM_WITH_ITEM, at = @At("HEAD"), cancellable = true)
    private void gulliver$hideLilyPadWhileRafting(ArmedEntityRenderState state, ItemStackRenderState itemRender,
                                                    //#if MC >= 1.21.11
                                                    net.minecraft.world.item.ItemStack stack,
                                                    //#endif
                                                    HumanoidArm arm, PoseStack pose,
                                                    SubmitNodeCollector buf, int light, CallbackInfo ci) {
        if (!(state instanceof IGlideRenderState g)) return;
        if ((g.gulliver$isRafting() || g.gulliver$doesUmbrella()) && arm == g.gulliver$getPropArm()) {
            ci.cancel();
        }
    }

    /**
     * Runs after the sub-bone translate that puts the item at the finger
     * tip, so what follows moves only the item, never its anchor.
     */
    @Inject(method = GULLIVER$ARM_WITH_ITEM,
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V",
                     shift = At.Shift.AFTER, ordinal = 0))
    private void gulliver$adjustItem(ArmedEntityRenderState state, ItemStackRenderState itemRender,
                                      //#if MC >= 1.21.11
                                      net.minecraft.world.item.ItemStack stack,
                                      //#endif
                                      HumanoidArm arm, PoseStack pose,
                                      SubmitNodeCollector buf, int light, CallbackInfo ci) {
        if (!(state instanceof IGlideRenderState g)) return;
        // Glider: the paper spans both raised hands like a banner held
        // overhead — shift half a shoulder width toward the body's middle
        // and turn the paper's long axis across (mirrored for the left arm).
        if (g.gulliver$isGliding() && arm == g.gulliver$getPropArm()) {
            float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
            pose.translate(0.3125F * side, 0.0F, 0.0F);
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(90.0F * side));
            pose.translate(0.0F, 0.3F, -0.3F);
        }
        // Classic look: the body is already scaled by `size`, so 1/sqrt
        // leaves items at sqrt(size) — a tiny's sword looks big. The
        // "proportional" option keeps items exactly in body proportion
        // (issue #5), matching tools rendered on the back by other mods.
        float size = g.gulliver$getSizeMultiplier();
        if (size != 1.0F && !gulliver.common.GulliverConfig.INSTANCE.client.proportionalHeldItems()) {
            float invRoot = 1.0F / (float) Math.sqrt(size);
            pose.scale(invRoot, invRoot, invRoot);
        }
    }
}
