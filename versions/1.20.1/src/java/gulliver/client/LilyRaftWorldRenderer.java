package gulliver.client;

import com.mojang.blaze3d.vertex.PoseStack;
import gulliver.api.IResizeableLiving;
import net.minecraft.client.CameraType;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** 1.20.1 first-person lily-pad raft/umbrella (third person: MixinLivingEntityRenderer). */
public final class LilyRaftWorldRenderer {
    private LilyRaftWorldRenderer() {}

    public static void submit(PoseStack pose, MultiBufferSource buffers, Camera cam, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.getCameraType() != CameraType.FIRST_PERSON) return;
        LocalPlayer player = mc.player;
        if (player == null) return;
        IResizeableLiving sized = (IResizeableLiving) player;
        boolean raft = sized.isRafting();
        if (!raft && !sized.doesUmbrella()) return;
        Vec3 camPos = cam.getPosition();
        pose.pushPose();
        pose.translate(Mth.lerp(pt, player.xOld, player.getX()) - camPos.x,
                Mth.lerp(pt, player.yOld, player.getY()) - camPos.y,
                Mth.lerp(pt, player.zOld, player.getZ()) - camPos.z);
        PropRender.lilyDisc(pose, buffers, player, PropRender.bodyYaw(player, pt), raft, 15728880);
        pose.popPose();
    }
}
