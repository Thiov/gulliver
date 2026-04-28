package gulliver.client;

import com.mojang.blaze3d.vertex.PoseStack;
import gulliver.api.IResizeableLiving;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Renders the glide paper above the player's head in world space, using
 * the LEVEL render pipeline. This works the SAME way in 1st and 3rd
 * person — the paper is just a world-space object positioned at the
 * player's head, rotated by body yaw, laid flat.
 *
 * Hooked via Fabric's LevelRenderEvents.COLLECT_SUBMITS — at this
 * stage the SubmitNodeCollector is active and the PoseStack is in
 * world-space-relative-to-camera convention (translates by
 * world - camera.position).
 *
 * In 3rd person, the existing MixinItemInHandLayer also bone-attaches
 * a paper to the right arm — we render this WORLD paper only in 1st
 * person to avoid double-rendering.
 */
public final class GliderPaperWorldRenderer {
    private GliderPaperWorldRenderer() {}

    public static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(GliderPaperWorldRenderer::collectSubmits);
    }

    private static void collectSubmits(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        // Only in 1st person — 3rd person uses bone-attached rendering
        // already.
        if (mc.options.getCameraType() != CameraType.FIRST_PERSON) return;
        LocalPlayer player = mc.player;
        if (player == null) return;
        IResizeableLiving sized = (IResizeableLiving) player;
        if (!sized.isGliding()) return;

        PoseStack pose = ctx.poseStack();
        SubmitNodeCollector buf = ctx.submitNodeCollector();
        net.minecraft.client.renderer.state.level.CameraRenderState camState = ctx.levelState().cameraRenderState;
        // CameraRenderState has the camera's world position via its underlying camera. Use the actual Camera object.
        net.minecraft.client.Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 camPos = cam.position();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);

        double px = Mth.lerp(pt, player.xOld, player.getX());
        double py = Mth.lerp(pt, player.yOld, player.getY());
        double pz = Mth.lerp(pt, player.zOld, player.getZ());

        // World-space offsets for "above and to player's right".
        // Sign was reversed last build (paper went left); flip to -cos/-sin.
        double yawRad = Math.toRadians(player.yBodyRot);
        double rightX = -Math.cos(yawRad);
        double rightZ = -Math.sin(yawRad);
        double rightShift = 0.5D;  // wider shift to actually appear right
        double upShift = 0.7D;     // higher above head

        pose.pushPose();
        // World-relative-to-camera convention: translate by (world - camera).
        pose.translate(
                (float) (px - camPos.x + rightX * rightShift),
                (float) (py + player.getBbHeight() + upShift - camPos.y),
                (float) (pz - camPos.z + rightZ * rightShift));
        // Body yaw — paper rotates with body when player turns.
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - player.yBodyRot));
        // Lay flat (90° around X tips item face down).
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90.0F));
        // Recenter (NONE-context model has corner at origin).
        pose.translate(-0.5F, 0.0F, 0.5F);

        ItemStackRenderState rs = new ItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(rs,
                new ItemStack(Items.PAPER), ItemDisplayContext.NONE,
                player.level(), (LivingEntity) player, 0);
        rs.submit(pose, buf, 15728880, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }
}
