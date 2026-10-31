package gulliver.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import gulliver.Gulliver;
import net.minecraft.resources.Identifier;
//#if MC >= 1.21.9
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;
//#if MC >= 1.21.11
import net.minecraft.client.renderer.rendertype.RenderTypes;
//#else
//$$ import net.minecraft.client.renderer.RenderType;
//#endif
//#else
//$$ import net.minecraft.client.Camera;
//$$ import net.minecraft.client.renderer.MultiBufferSource;
//$$ import net.minecraft.client.renderer.RenderType;
//$$ import org.joml.Matrix3f;
//$$ import org.joml.Matrix4f;
//#endif

/**
 * Draws BreathRibbons in the world, right after the level's entities, in
 * camera-relative world space (MixinLevelRendererSubmit), with the
 * translucent beacon-beam render type: no world light and no shading, so
 * the air stays white whichever way the camera looks (the entity render
 * types shade by normal and turned it into grey smoke).
 */
public final class BreathRibbonRenderer {
    private BreathRibbonRenderer() {}

    private static final Identifier TEXTURE = Gulliver.id("textures/effect/wind.png");
    private static final int FULL_BRIGHT = 15728880;
    /** A faint blue-white, like cold breath. */
    private static final float R = 0.92F, G = 0.97F, B = 1.0F;

    //#if MC >= 1.21.9
    public static void submit(PoseStack pose, SubmitNodeCollector buf) {
        if (!BreathRibbons.any()) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 camera = mc.gameRenderer.mainCamera().position();
        float pt = VisualSize.viewPartial();
        //#if MC >= 1.21.11
        var type = RenderTypes.beaconBeam(TEXTURE, true);
        //#else
        //$$ var type = RenderType.beaconBeam(TEXTURE, true);
        //#endif
        buf.submitCustomGeometry(pose, type, (p, consumer) -> BreathRibbons.build(camera, pt,
                (x, y, z, u, v, a) -> emit(consumer.addVertex(p, x, y, z), u, v, a).setNormal(p, 0.0F, 1.0F, 0.0F)));
    }

    private static VertexConsumer emit(VertexConsumer c, float u, float v, float a) {
        return c.setColor(R, G, B, a).setUv(u, v).setLight(FULL_BRIGHT);
    }
    //#else
    //$$ public static void submit(PoseStack pose, MultiBufferSource buffers, Camera camera, float pt) {
    //$$     if (!BreathRibbons.any()) return;
    //$$     VertexConsumer consumer = buffers.getBuffer(RenderType.beaconBeam(TEXTURE, true));
    //$$     Matrix4f m = pose.last().pose();
    //$$     Matrix3f n = pose.last().normal();
    //$$     BreathRibbons.build(camera.getPosition(), pt, (x, y, z, u, v, a) -> consumer.vertex(m, x, y, z)
    //$$             .color(R, G, B, a).uv(u, v).uv2(FULL_BRIGHT)
    //$$             .normal(n, 0.0F, 1.0F, 0.0F).endVertex());
    //$$ }
    //#endif
}
