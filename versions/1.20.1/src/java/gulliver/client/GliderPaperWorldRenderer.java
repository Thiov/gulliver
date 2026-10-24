package gulliver.client;

import com.mojang.blaze3d.vertex.PoseStack;
import gulliver.api.IResizeableLiving;
import gulliver.common.GulliverConfig;
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

/**
 * 1.20.1 first-person glider paper above the head (third person is bone-attached).
 * It scales with the player like the bone-attached paper does, and moves with
 * the camera's own previous-position fields and partial tick.
 */
public final class GliderPaperWorldRenderer {
    private GliderPaperWorldRenderer() {}

    public static void submit(PoseStack pose, MultiBufferSource buffers, Camera cam, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.getCameraType() != CameraType.FIRST_PERSON) return;
        LocalPlayer player = mc.player;
        if (player == null || !((IResizeableLiving) player).isGliding()) return;
        Vec3 camPos = cam.getPosition();
        float size = VisualSize.of(player, pt);
        float height = player.getBbHeight() * size / VisualSize.logical(player);
        float bodyYaw = Mth.rotLerp(pt, player.yBodyRotO, player.yBodyRot);
        double yawRad = Math.toRadians(bodyYaw);
        double rightX = -Math.cos(yawRad);
        double rightZ = -Math.sin(yawRad);
        pose.pushPose();
        pose.translate(Mth.lerp(pt, player.xo, player.getX()) - camPos.x + rightX * 0.5D * size,
                Mth.lerp(pt, player.yo, player.getY()) + height + 0.7D * size - camPos.y,
                Mth.lerp(pt, player.zo, player.getZ()) - camPos.z + rightZ * 0.5D * size);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - bodyYaw));
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90.0F));
        float paper = GulliverConfig.INSTANCE.client.proportionalHeldItems() ? size : (float) Math.sqrt(size);
        pose.scale(paper, paper, paper);
        pose.translate(-0.5F, 0.0F, 0.5F);
        mc.getItemRenderer().renderStatic(new ItemStack(Items.PAPER), ItemDisplayContext.NONE, 15728880,
                OverlayTexture.NO_OVERLAY, pose, buffers, player.level(), 0);
        pose.popPose();
    }
}
