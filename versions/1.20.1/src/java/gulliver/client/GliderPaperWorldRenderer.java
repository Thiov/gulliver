package gulliver.client;

import com.mojang.blaze3d.vertex.PoseStack;
import gulliver.api.IResizeableLiving;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** 1.20.1 first-person glider paper above the head (third person is bone-attached). */
public final class GliderPaperWorldRenderer {
    private GliderPaperWorldRenderer() {}

    public static void submit(PoseStack pose, MultiBufferSource buffers, Camera cam, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.getCameraType() != CameraType.FIRST_PERSON) return;
        LocalPlayer player = mc.player;
        if (player == null || !((IResizeableLiving) player).isGliding()) return;
        Vec3 camPos = cam.getPosition();
        double yawRad = Math.toRadians(player.yBodyRot);
        double rightX = -Math.cos(yawRad);
        double rightZ = -Math.sin(yawRad);
        pose.pushPose();
        pose.translate(Mth.lerp(pt, player.xOld, player.getX()) - camPos.x + rightX * 0.5D,
                Mth.lerp(pt, player.yOld, player.getY()) + player.getBbHeight() + 0.7D - camPos.y,
                Mth.lerp(pt, player.zOld, player.getZ()) - camPos.z + rightZ * 0.5D);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - player.yBodyRot));
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90.0F));
        pose.translate(-0.5F, 0.0F, 0.5F);
        mc.getItemRenderer().renderStatic(new ItemStack(Items.PAPER), ItemDisplayContext.NONE, 15728880,
                OverlayTexture.NO_OVERLAY, pose, buffers, player.level(), 0);
        pose.popPose();
    }
}
