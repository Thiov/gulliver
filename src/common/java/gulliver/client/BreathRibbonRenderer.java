package gulliver.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import gulliver.Gulliver;
import net.minecraft.resources.Identifier;
//#if MC >= 1.21.9
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
//#if MC >= 1.21.11
import net.minecraft.client.renderer.rendertype.RenderTypes;
//#else
//$$ import net.minecraft.client.renderer.RenderType;
//#endif
//#else
//$$ import com.mojang.blaze3d.systems.RenderSystem;
//$$ import net.minecraft.client.Camera;
//$$ import net.minecraft.client.renderer.MultiBufferSource;
//$$ import net.minecraft.client.renderer.RenderType;
//#endif

/**
 * Draws BreathRibbons in the world in camera-relative world space, last
 * among the world's see-through things: after the translucent terrain
 * (water, stained glass, ice), the clouds and the weather. The ribbons
 * write no depth, so anything see-through drawn after them was painted
 * over them wherever it lay behind - wind under the water, clouds in front
 * of the wind. They are drawn right after the world border, which every
 * version draws at the end of its weather pass (MixinWorldBorderRendererBreath;
 * on 1.20.1 MixinLevelRendererSubmit, just before the border): through
 * the buffer source up to 26.1, by uploading the ribbons and drawing them
 * with the render type's own pipeline from 26.2 on, and on 26.3 in each
 * stage of its order-independent transparency when that is on.
 *
 * It uses the translucent beacon-beam render type: no world light and no
 * shading, so the air stays white whichever way the camera looks (the
 * entity render types shade by normal and turned it into grey smoke).
 */
public final class BreathRibbonRenderer {
    private BreathRibbonRenderer() {}

    private static final Identifier TEXTURE = Gulliver.id("textures/effect/wind.png");
    private static final int FULL_BRIGHT = 15728880;
    /** A faint blue-white, like cold breath. */
    private static final float R = 0.92F, G = 0.97F, B = 1.0F;

    //#if MC >= 1.21.9
    //#if MC >= 1.21.11
    private static final net.minecraft.client.renderer.rendertype.RenderType TYPE = RenderTypes.beaconBeam(TEXTURE, true);
    //#else
    //$$ private static final RenderType TYPE = RenderType.beaconBeam(TEXTURE, true);
    //#endif

    private static void build(VertexConsumer consumer) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
        BreathRibbons.build(camera, VisualSize.viewPartial(),
                (x, y, z, u, v, a) -> emit(consumer.addVertex(x, y, z), u, v, a).setNormal(0.0F, 1.0F, 0.0F));
    }

    private static VertexConsumer emit(VertexConsumer c, float u, float v, float a) {
        return c.setColor(R, G, B, a).setUv(u, v).setLight(FULL_BRIGHT);
    }

    //#if MC < 26.2
    /** After the weather: straight into the buffer source and drawn. */
    public static void drawLate() {
        if (!BreathRibbons.any()) return;
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        build(buffers.getBuffer(TYPE));
        buffers.endBatch(TYPE);
    }

    /** Once a frame, before any pass runs. */
    public static void beginFrame() {
    }
    //#else
    //#if MC >= 26.3
    //$$ private static final java.util.List<com.mojang.renderpearl.api.buffers.GpuBuffer> RETIRED = new java.util.ArrayList<>();
    //$$ private static com.mojang.renderpearl.api.buffers.GpuBuffer uploaded;
    //#else
    private static final java.util.List<com.mojang.blaze3d.buffers.GpuBuffer> RETIRED = new java.util.ArrayList<>();
    private static com.mojang.blaze3d.buffers.GpuBuffer uploaded;
    //#endif
    private static com.mojang.blaze3d.vertex.ByteBufferBuilder bytes;
    private static int indexCount;

    /**
     * Once a frame, before any pass runs: the frame's ribbons are uploaded
     * here, and the texture loaded the first time, because 26.3 allows
     * neither while one of its render passes is open, and the late draw
     * happens inside one. A buffer is closed two frames after its last use,
     * since the passes that draw it may only run at the end of their frame.
     */
    public static void beginFrame() {
        if (RETIRED.size() > 2) RETIRED.remove(0).close();
        if (uploaded != null) RETIRED.add(uploaded);
        uploaded = null;
        if (!textureLoaded) {
            textureLoaded = true;
            Minecraft.getInstance().getTextureManager().getTexture(TEXTURE);
        }
        upload();
    }

    private static boolean textureLoaded;

    /** This frame's ribbons in a vertex buffer; false when there are none. */
    private static boolean upload() {
        if (!BreathRibbons.any()) return false;
        if (bytes == null) bytes = new com.mojang.blaze3d.vertex.ByteBufferBuilder(1 << 16);
        var builder = new com.mojang.blaze3d.vertex.BufferBuilder(bytes, TYPE.primitiveTopology(), TYPE.format());
        build(builder);
        try (com.mojang.blaze3d.vertex.MeshData mesh = builder.build()) {
            if (mesh == null) return false;
            indexCount = mesh.drawState().indexCount();
            uploaded = com.mojang.blaze3d.systems.RenderSystem.getDevice().createBuffer(() -> "Gulliver breath gust",
                    //#if MC >= 26.3
                    //$$ com.mojang.renderpearl.api.buffers.GpuBuffer.USAGE_VERTEX,
                    //#else
                    com.mojang.blaze3d.buffers.GpuBuffer.USAGE_VERTEX,
                    //#endif
                    mesh.vertexBuffer());
        }
        return true;
    }

    //#if MC >= 26.3
    //$$ private static net.minecraft.client.renderer.StagedVertexBuffer.ExecuteInfo info() {
    //$$     var sequence = com.mojang.blaze3d.systems.RenderSystem.getSequentialBuffer(TYPE.primitiveTopology());
    //$$     return new net.minecraft.client.renderer.StagedVertexBuffer.ExecuteInfo(uploaded, sequence.getBuffer(indexCount),
    //$$             sequence.type(), 0, 0, indexCount, TYPE.primitiveTopology());
    //$$ }
    //$$
    //$$ /** After the weather, in the classic transparency pass. */
    //$$ public static void drawLate(com.mojang.renderpearl.api.commands.RenderPass pass) {
    //$$     if (uploaded != null) TYPE.prepare().drawFromBuffer(info(), pass);
    //$$ }
    //$$
    //$$ /** After the weather, in each stage of the order-independent transparency. */
    //$$ public static void drawLateOit(net.minecraft.client.renderer.oit.OitStage stage,
    //$$                                com.mojang.renderpearl.api.commands.RenderPass pass) {
    //$$     if (uploaded != null) TYPE.prepare().drawFromBufferOit(info(), stage, pass);
    //$$ }
    //#else
    /** After the weather, drawn with the render type's own pipeline. */
    public static void drawLate() {
        if (uploaded == null) return;
        var sequence = com.mojang.blaze3d.systems.RenderSystem.getSequentialBuffer(TYPE.primitiveTopology());
        TYPE.prepare().drawFromBuffer(uploaded, sequence.getBuffer(indexCount), sequence.type(), 0, 0, indexCount);
    }
    //#endif
    //#endif
    //#else
    //$$ /**
    //$$  * Right before the world border, after the clouds and the weather.
    //$$  * The model-view matrix is set to the camera's turn here, whatever the
    //$$  * caller left it at; the ribbons are camera-relative.
    //$$  */
    //$$ public static void drawLate(PoseStack pose, MultiBufferSource.BufferSource buffers, Camera camera, float pt) {
    //$$     if (!BreathRibbons.any()) return;
    //$$     PoseStack view = RenderSystem.getModelViewStack();
    //$$     view.pushPose();
    //$$     view.setIdentity();
    //$$     view.mulPoseMatrix(pose.last().pose());
    //$$     RenderSystem.applyModelViewMatrix();
    //$$     RenderType type = RenderType.beaconBeam(TEXTURE, true);
    //$$     VertexConsumer consumer = buffers.getBuffer(type);
    //$$     BreathRibbons.build(camera.getPosition(), pt, (x, y, z, u, v, a) -> consumer.vertex(x, y, z)
    //$$             .color(R, G, B, a).uv(u, v).uv2(FULL_BRIGHT).normal(0.0F, 1.0F, 0.0F).endVertex());
    //$$     buffers.endBatch(type);
    //$$     view.popPose();
    //$$     RenderSystem.applyModelViewMatrix();
    //$$ }
    //#endif
}
