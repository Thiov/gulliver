package gulliver.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import gulliver.api.IResizeableLiving;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.6.4 bfj.java:418-451 — first-person held-item renderer applies a
 * special parachute pose when the player isGliding (paper held overhead)
 * or doesUmbrella (lily-pad held overhead). The original code:
 *
 *   GL11.glRotatef(90, 0,1,0);
 *   if (gliding || umbrella) GL11.glRotatef(160, 0,0,1);
 *   GL11.glRotatef(B + 80, 0,0,1);
 *   GL11.glRotatef(-90, 1,0,0);
 *   if (gliding) GL11.glRotatef(-60, 1,0,0);
 *   GL11.glTranslatef(0, 0.1F * sizerootdiv, 0);
 *   GL11.glScalef(sizerootdiv, sizerootdiv, sizerootdiv);
 *
 * Where sizerootdiv = 1/sqrt(size). For modern MC we hook
 * renderArmWithItem and rotate/scale the pose-stack on HEAD so the item
 * shows the parachute pose. Pop on RETURN keeps the stack balanced.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class MixinItemInHandRenderer {

    @Inject(method = "renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At("HEAD"))
    private void gulliver$pushGlidePose(AbstractClientPlayer player, float partialTick,
                                         float pitch, InteractionHand hand,
                                         float swingProgress, ItemStack stack,
                                         float equippedProgress, PoseStack pose,
                                         SubmitNodeCollector buf, int light, CallbackInfo ci) {
        IResizeableLiving sized = (IResizeableLiving) player;
        boolean gliding = sized.isGliding();
        boolean umbrella = sized.doesUmbrella();
        float size = sized.getSizeMultiplier();
        if (!gliding && !umbrella && size == 1.0F) return;
        pose.pushPose();
        if (gliding || umbrella) {
            // 1.6.4 parachute rotations
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90.0F));
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(160.0F));
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(80.0F));
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90.0F));
            if (gliding) {
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-60.0F));
            }
        }
        if (size != 1.0F) {
            float invRoot = 1.0F / (float) Math.sqrt(size);
            pose.translate(0.0F, 0.1F * invRoot, 0.0F);
            pose.scale(invRoot, invRoot, invRoot);
        }
    }

    @Inject(method = "renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At("RETURN"))
    private void gulliver$popGlidePose(AbstractClientPlayer player, float partialTick,
                                        float pitch, InteractionHand hand,
                                        float swingProgress, ItemStack stack,
                                        float equippedProgress, PoseStack pose,
                                        SubmitNodeCollector buf, int light, CallbackInfo ci) {
        IResizeableLiving sized = (IResizeableLiving) player;
        boolean gliding = sized.isGliding();
        boolean umbrella = sized.doesUmbrella();
        float size = sized.getSizeMultiplier();
        if (!gliding && !umbrella && size == 1.0F) return;
        pose.popPose();
    }
}
