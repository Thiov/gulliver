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

        PoseStack pose = ctx.poseStack();
        SubmitNodeCollector buf = ctx.submitNodeCollector();
        net.minecraft.client.Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 camPos = cam.position();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);

        double px = Mth.lerp(pt, player.xOld, player.getX());
        double py = Mth.lerp(pt, player.yOld, player.getY());
        double pz = Mth.lerp(pt, player.zOld, player.getZ());

        // Anchor at player feet — bbox bottom is player.getY() (no offset).
        // Lily-pad disc is rendered as a flat horizontal slab just slightly
        // above the feet, like 1.6.4's "sit ON TOP of a lily-pad block".
        float scale = sized.getSizeMultiplier();

        pose.pushPose();
        pose.translate(
                (float) (px - camPos.x),
                (float) (py + 0.05F * scale - camPos.y),
                (float) (pz - camPos.z));
        // Body yaw — raft rotates with body so the disc orientation looks
        // like the player's vehicle. 180° offset matches the 3rd-person
        // submit (NONE-display item has its face oriented opposite).
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - player.yBodyRot));
        // Disc dimensions: 1.5 wide, 0.1 thick — same as the 3rd-person
        // path in MixinLivingEntityRenderer.
        pose.scale(1.5F * scale, 0.1F * scale, 1.5F * scale);
        // Recenter (item models have corner at origin in NONE/GROUND).
        pose.translate(-0.5F, 0.0F, -0.5F);

        ItemStackRenderState rs = new ItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(rs,
                new ItemStack(Items.LILY_PAD), ItemDisplayContext.GROUND,
                player.level(), (LivingEntity) player, 0);
        rs.submit(pose, buf, 15728880, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }
}
