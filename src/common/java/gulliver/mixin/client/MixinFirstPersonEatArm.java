package gulliver.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import gulliver.client.EatPose;
import gulliver.client.VisualSize;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//#if MC >= 26.3
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
//#elif MC >= 1.21.9
//$$ import net.minecraft.client.renderer.ItemInHandRenderer;
//$$ import net.minecraft.client.renderer.SubmitNodeCollector;
//#else
//$$ import net.minecraft.client.renderer.ItemInHandRenderer;
//$$ import net.minecraft.client.renderer.MultiBufferSource;
//#endif

/**
 * First person, eating a hand-held creature (Appetite): the empty main
 * arm rises from the lower corner of the screen towards the middle, under
 * the camera, as the creature is brought to the mouth, and dips with every
 * bite. The creature itself is drawn in the world at the mouth by the
 * carry slot (ShoulderHelper.positionPassengers).
 */
//#if MC >= 26.3
@Mixin(FirstPersonHandsAndItemsRenderer.class)
//#else
//$$ @Mixin(ItemInHandRenderer.class)
//#endif
public abstract class MixinFirstPersonEatArm {

    //#if MC >= 26.3
    @Inject(method = "renderPlayerArm", at = @At("HEAD"))
    private void gulliver$armToMouth(PoseStack pose, SubmitNodeCollector buffer, int light, float equip,
                                     float swing, HumanoidArm arm, PlayerRenderState state, CallbackInfo ci) {
    //#elif MC >= 1.21.9
    //$$ @Inject(method = "renderPlayerArm", at = @At("HEAD"))
    //$$ private void gulliver$armToMouth(PoseStack pose, SubmitNodeCollector buffer, int light, float equip,
    //$$                                  float swing, HumanoidArm arm, CallbackInfo ci) {
    //#else
    //$$ @Inject(method = "renderPlayerArm", at = @At("HEAD"))
    //$$ private void gulliver$armToMouth(PoseStack pose, MultiBufferSource buffer, int light, float equip,
    //$$                                  float swing, HumanoidArm arm, CallbackInfo ci) {
    //#endif
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || arm != player.getMainArm()) return;
        float partial = VisualSize.framePartial();
        float lift = EatPose.lift(player, partial);
        if (lift <= 0.0F) return;
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        float bite = EatPose.bite(player, partial);
        pose.translate(-0.36F * side * lift, 0.24F * lift - 0.05F * bite, 0.1F * lift);
    }
}
