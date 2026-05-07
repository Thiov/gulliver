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
 * 1st-person world-space lily-pad raft. Mirrors GliderPaperWorldRenderer:
 * 3rd person is handled by MixinLivingEntityRenderer.gulliver$drawRaftLilypad
 * via the entity-render submit hook; 1st person can't use that path because
 * the local player isn't passed through the entity-render pipeline, so we
 * draw a flat lily-pad below the camera-anchor in world coords.
 *
 * Visual: tiny rafting on water sees a lily-pad disc just below their
 * camera, oriented to body yaw, sized 1.5x to read as a raft.
 */
public final class LilyRaftWorldRenderer {
    private LilyRaftWorldRenderer() {}

    public static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(LilyRaftWorldRenderer::collectSubmits);
    }

    private static void collectSubmits(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.getCameraType() != CameraType.FIRST_PERSON) return;
        LocalPlayer player = mc.player;
        if (player == null) return;
        IResizeableLiving sized = (IResizeableLiving) player;
        if (!sized.isRafting()) return;

        renderRaft(ctx, player, sized);
    }

    /**
     * Public so the 3rd-person path (MixinLivingEntityRenderer) can also
     * call into it — same world-anchor render, used in BOTH camera modes.
     * In 3rd person we run from the entity submit hook, but the entity
     * pose-stack is at entity-origin, not world-origin; we want a unified
     * world-space render so geometry is identical regardless of view.
     */
    static void renderRaft(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext ctx,
                            LocalPlayer player, IResizeableLiving sized) {
        Minecraft mc = Minecraft.getInstance();
        PoseStack pose = ctx.poseStack();
        SubmitNodeCollector buf = ctx.submitNodeCollector();
        net.minecraft.client.Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 camPos = cam.position();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);

        double px = Mth.lerp(pt, player.xOld, player.getX());
        double py = Mth.lerp(pt, player.yOld, player.getY());
        double pz = Mth.lerp(pt, player.zOld, player.getZ());

        // Anchor at player feet — bbox bottom is player.getY().
        // Disc sits very slightly above the feet so it reads as
        // "platform under the player" not "embedded in feet".
        float scale = sized.getSizeMultiplier();

        pose.pushPose();
        pose.translate(
                (float) (px - camPos.x),
                (float) (py + 0.02F * scale - camPos.y),
                (float) (pz - camPos.z));
        // Body yaw — raft rotates with body.
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - player.yBodyRot));
        // Lay flat: lily-pad item is a vertical 2D quad in NONE display
        // context. Rotate +90° around X so the quad lies horizontal.
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90.0F));
        // Uniform 1.5× scale relative to the player's size — wide enough
        // to read as a raft/vehicle, scales with player.
        float disc = 1.5F * scale;
        pose.scale(disc, disc, disc);
        // Recenter (NONE-context model has corner at origin).
        pose.translate(-0.5F, 0.0F, 0.5F);

        ItemStackRenderState rs = new ItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(rs,
                new ItemStack(Items.LILY_PAD), ItemDisplayContext.NONE,
                player.level(), (LivingEntity) player, 0);
        rs.submit(pose, buf, 15728880, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }
}
