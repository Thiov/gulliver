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
 * 1st-person world-space lily-pad disc, in two modes:
 *   RAFT     — under the feet while floating on water,
 *   UMBRELLA — held above the head while sheltering from rain.
 *
 * Mirrors GliderPaperWorldRenderer: 3rd person is handled by
 * MixinLivingEntityRenderer.gulliver$drawRaftLilypad via the entity-
 * render submit hook; 1st person can't use that path because the local
 * player isn't passed through the entity-render pipeline, so we draw
 * the flat lily-pad relative to the camera anchor in world coords.
 * Offsets/scales match the 3rd-person path exactly (raft: 0.4×size
 * lift, 1.5×size disc; umbrella: 2.0×size lift, 1.1×size disc) so the
 * visual is identical across camera modes.
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
        boolean raft = sized.isRafting();
        boolean umbrella = !raft && sized.doesUmbrella();
        if (!raft && !umbrella) return;

        renderDisc(ctx, player, sized, raft);
    }

    private static void renderDisc(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext ctx,
                            LocalPlayer player, IResizeableLiving sized, boolean raft) {
        Minecraft mc = Minecraft.getInstance();
        PoseStack pose = ctx.poseStack();
        SubmitNodeCollector buf = ctx.submitNodeCollector();
        net.minecraft.client.Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 camPos = cam.position();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);

        double px = Mth.lerp(pt, player.xOld, player.getX());
        double py = Mth.lerp(pt, player.yOld, player.getY());
        double pz = Mth.lerp(pt, player.zOld, player.getZ());

        // Anchor at player feet — bbox bottom is player.getY(). Raft
        // disc sits at the waterline (player is snapped 0.4×size below
        // it); umbrella disc hugs the top of the head (user-tuned:
        // 2.2 floated too high; keep in sync with the 3rd-person path).
        float scale = sized.getSizeMultiplier();
        float lift = (raft ? 0.4F : 2.0F) * scale;

        pose.pushPose();
        pose.translate(
                (float) (px - camPos.x),
                (float) (py + lift - camPos.y),
                (float) (pz - camPos.z));
        // Body yaw — disc rotates with body.
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - player.yBodyRot));
        // Lay flat: lily-pad item is a vertical 2D quad in NONE display
        // context. Rotate +90° around X so the quad lies horizontal.
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90.0F));
        // Raft reads as a vehicle (1.5×), umbrella as a hand-held
        // canopy (1.1×) — both track player size.
        float disc = (raft ? 1.5F : 1.1F) * scale;
        pose.scale(disc, disc, disc);
        // No manual recenter. ItemTransform.apply, called from
        // ItemStackRenderState$LayerRenderState.submit during
        // rs.submit, hits the NO_TRANSFORM branch for
        // ItemDisplayContext.NONE and itself applies a built-in
        // pose.translate(-0.5, -0.5, -0.5). Stacking another -0.5
        // here double-shifts the model by 0.5×disc per axis in
        // body-frame, which read as both "too far front" and "too
        // far left" in 3rd person. With no recenter, the rendered
        // model spans [-0.5, 0.5]³ in object space and ends up
        // centered on the world anchor after our rotations.

        ItemStackRenderState rs = new ItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(rs,
                new ItemStack(Items.LILY_PAD), ItemDisplayContext.NONE,
                player.level(), (LivingEntity) player, 0);
        rs.submit(pose, buf, 15728880, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }
}
