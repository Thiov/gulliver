package gulliver.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import gulliver.Gulliver;
import gulliver.access.IGulliverWings;
import gulliver.common.WingType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Draws a player's wings (TinyWings) on its back, from what WingAnim
 * worked out: folded along the back on the ground (a butterfly's held
 * upright together, opening now and then), spread and beating in flight.
 * Fly and bee wings beat too fast to see and are drawn as a shimmering
 * blurred fan; butterfly and fairy wings flap visibly, twisting through
 * each stroke. Fairy wings glow. A metamorphosis grows them out of the
 * back, crumpled at first, and unfolds them.
 *
 * Space: the entity's feet, already turned to face its body yaw
 * (+X right, +Y up, +Z behind), in size-1 units. The renderer mixins put
 * the pose there and scale it; the body lean in flight is shared with the
 * player model (applyLean) so the wings stay on the back.
 */
public final class WingRender {
    private WingRender() {}

    /** One frame's worth of a player's wings. */
    public record Frame(WingType type, int color, float spread, float stroke, float phase, float growth,
                        float bask, float lean, boolean crouching, boolean glow) {}

    public static Identifier texture(WingType type) {
        return Gulliver.id("textures/entity/wings/" + type.key + ".png");
    }

    /** The wings as they are this frame, or null when there is nothing to draw. */
    public static Frame capture(Player p, float partialTick) {
        if (!WingAnim.visible(p) || p.isSleeping() || p.isFallFlying()) return null;
        WingAnim.State s = WingAnim.of(p);
        if (s == null) return null;
        IGulliverWings w = (IGulliverWings) p;
        WingType type = w.gulliver$wingType();
        float growth = Mth.lerp(partialTick, s.prevGrowth, s.growth);
        if (growth <= 0.01F) return null;
        return new Frame(type, w.gulliver$wingColor(),
                Mth.lerp(partialTick, s.prevSpread, s.spread),
                Mth.lerp(partialTick, s.prevStroke, s.stroke),
                Mth.lerp(partialTick, s.prevPhase, s.phase),
                growth,
                Mth.lerp(partialTick, s.prevBask, s.bask),
                Mth.lerp(partialTick, s.prevTilt, s.tilt),
                p.isCrouching(),
                type == WingType.FAIRY);
    }

    /**
     * The body in flight, blended in as the wings open: legs trailing a
     * little behind and swaying, arms eased out from the sides.
     */
    public static void flightPose(net.minecraft.client.model.geom.ModelPart rightArm,
                                  net.minecraft.client.model.geom.ModelPart leftArm,
                                  net.minecraft.client.model.geom.ModelPart rightLeg,
                                  net.minecraft.client.model.geom.ModelPart leftLeg, float open, float age) {
        float sway = Mth.sin(age * 0.11F);
        rightLeg.xRot = Mth.lerp(open, rightLeg.xRot, 0.32F + 0.09F * sway);
        leftLeg.xRot = Mth.lerp(open, leftLeg.xRot, 0.22F - 0.09F * sway);
        rightLeg.yRot = Mth.lerp(open, rightLeg.yRot, 0.0F);
        leftLeg.yRot = Mth.lerp(open, leftLeg.yRot, 0.0F);
        rightLeg.zRot = Mth.lerp(open, rightLeg.zRot, 0.04F);
        leftLeg.zRot = Mth.lerp(open, leftLeg.zRot, -0.04F);
        rightArm.xRot = Mth.lerp(open, rightArm.xRot, 0.18F + 0.05F * sway);
        leftArm.xRot = Mth.lerp(open, leftArm.xRot, 0.18F - 0.05F * sway);
        rightArm.zRot = Mth.lerp(open, rightArm.zRot, 0.32F);
        leftArm.zRot = Mth.lerp(open, leftArm.zRot, -0.32F);
    }

    /** The body's lean in flight, about its middle (size-1 units, after the body yaw). */
    public static void applyLean(PoseStack pose, float leanDegrees) {
        if (Math.abs(leanDegrees) < 0.01F) return;
        pose.translate(0.0F, 0.9F, 0.0F);
        pose.mulPose(com.mojang.math.Axis.XN.rotationDegrees(leanDegrees));
        pose.translate(0.0F, -0.9F, 0.0F);
    }

    /** Receives each wing's quad, already posed (the version-specific part: WingDraw). */
    public interface Sink {
        void quad(PoseStack pose, Identifier texture, float v0, float v1, int argb, int light);
    }

    /**
     * Builds the wings in the pose's space (feet, body yaw applied, scaled
     * to the body), handing each quad to the sink.
     */
    public static void build(PoseStack pose, Frame f, int light, Sink sink) {
        Identifier tex = texture(f.type());
        int lit = f.glow() ? 15728880 : light;
        float growth = ease(f.growth());
        float span = f.type().span * growth;
        // Crumpled while it grows: narrow and wrinkled, then smoothing out.
        float crumple = 1.0F - Mth.clamp((f.growth() - 0.3F) / 0.5F, 0.0F, 1.0F);
        float chordScale = 1.0F - 0.55F * crumple;
        int rgb = f.color() & 0xFFFFFF;

        pose.pushPose();
        if (f.crouching()) {
            pose.translate(0.0F, -0.2F, 0.05F);
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(28.0F));
        }
        boolean blur = f.type().blurred && f.stroke() > 0.25F;
        for (int side = -1; side <= 1; side += 2) {
            for (int pair = 0; pair < (f.type().hindwings ? 2 : 1); pair++) {
                boolean hind = pair == 1;
                float len = span * (hind ? hindRatio(f.type()) : 1.0F);
                float[] angles = angles(f, hind);
                float sweep = angles[0], elev = angles[1], twist = angles[2], amp = angles[3];
                float wobble = crumple > 0 ? Mth.sin((f.growth() * 40.0F) + pair + side) * 25.0F * crumple : 0.0F;
                if (blur) {
                    // A fan of faint copies across the stroke, and one firmer at the wing's place now.
                    int copies = 6;
                    for (int k = 0; k < copies; k++) {
                        float e = elev - amp + 2.0F * amp * (k + 0.5F) / copies;
                        wing(pose, sink, tex, side, hind, sweep, e, twist * 0.3F, len, chordScale,
                                argb(0.33F, rgb), lit);
                    }
                    float now = elev + amp * Mth.sin(f.phase() * Mth.TWO_PI);
                    wing(pose, sink, tex, side, hind, sweep, now, twist, len, chordScale, argb(0.5F, rgb), lit);
                } else {
                    float stroke = Mth.sin(f.phase() * Mth.TWO_PI);
                    float e = elev + amp * stroke + wobble;
                    // Leading edge dips on the downstroke and lifts on the upstroke.
                    float t = twist * Mth.cos(f.phase() * Mth.TWO_PI) + wobble * 0.5F;
                    wing(pose, sink, tex, side, hind, sweep, e, t, len, chordScale,
                            argb(0.6F + 0.4F * growth, rgb), lit);
                }
            }
        }
        pose.popPose();
    }

    private static float hindRatio(WingType t) {
        return switch (t) {
            case BUTTERFLY -> 0.78F;
            case BEE -> 0.62F;
            default -> 0.72F;
        };
    }

    /** {sweep back, elevation, twist amplitude, stroke amplitude} in degrees, for the wing's current pose. */
    private static float[] angles(Frame f, boolean hind) {
        float open = f.spread();
        float stroke = f.stroke();
        WingType t = f.type();
        float sweepOpen = hind ? 34.0F : 10.0F;
        float elevOpen = hind ? -6.0F : 6.0F;
        float sweepFold, elevFold;
        if (t == WingType.BUTTERFLY) {
            // Closed upright over the back, opening flat when basking.
            sweepFold = hind ? 30.0F : 18.0F;
            elevFold = Mth.lerp(f.bask(), 86.0F, hind ? -2.0F : 8.0F);
        } else {
            // Laid back along the body, the fore pair over the hind one.
            sweepFold = hind ? 82.0F : 76.0F;
            elevFold = hind ? -14.0F : -8.0F;
        }
        float sweep = Mth.lerp(open, sweepFold, sweepOpen);
        float elev = Mth.lerp(open, elevFold, elevOpen);
        float amp;
        float twist;
        switch (t) {
            case BUTTERFLY -> {
                // Butterflies stroke from nearly touching above to below level.
                elev = Mth.lerp(open, elev, elev + 26.0F * stroke);
                amp = 58.0F;
                twist = 16.0F;
            }
            case FAIRY -> {
                amp = 34.0F;
                twist = 14.0F;
            }
            default -> {
                amp = 48.0F;
                twist = 20.0F;
            }
        }
        return new float[]{sweep, elev, twist * stroke * open, amp * stroke * open};
    }

    private static void wing(PoseStack pose, Sink sink, Identifier tex, int side, boolean hind, float sweep,
                             float elev, float twist, float len, float chordScale, int argb, int light) {
        pose.pushPose();
        // Hinges on the upper back, the hind pair a little lower and further back.
        pose.translate(side * 0.07F, hind ? 1.2F : 1.31F, hind ? 0.17F : 0.15F);
        if (side < 0) pose.scale(-1.0F, 1.0F, 1.0F);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-sweep));
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(elev));
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(twist));
        pose.scale(len, 1.0F, len * 0.5F * chordScale);
        sink.quad(pose, tex, hind ? 0.5F : 0.0F, hind ? 1.0F : 0.5F, argb, light);
        pose.popPose();
    }

    /**
     * The unit quad: hinge at the origin, span along +X to 1, chord along Z
     * from -0.5 (leading edge, the top of the texture) to +0.5.
     */
    public static void emit(PoseStack.Pose p, VertexConsumer vc, float v0, float v1, int argb, int light) {
        int a = argb >>> 24, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        //#if MC >= 1.21
        vc.addVertex(p, 0.0F, 0.0F, -0.5F).setColor(r, g, b, a).setUv(0.0F, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0.0F, 1.0F, 0.0F);
        vc.addVertex(p, 1.0F, 0.0F, -0.5F).setColor(r, g, b, a).setUv(1.0F, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0.0F, 1.0F, 0.0F);
        vc.addVertex(p, 1.0F, 0.0F, 0.5F).setColor(r, g, b, a).setUv(1.0F, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0.0F, 1.0F, 0.0F);
        vc.addVertex(p, 0.0F, 0.0F, 0.5F).setColor(r, g, b, a).setUv(0.0F, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0.0F, 1.0F, 0.0F);
        //#else
        //$$ org.joml.Matrix4f m = p.pose();
        //$$ org.joml.Matrix3f n = p.normal();
        //$$ vc.vertex(m, 0.0F, 0.0F, -0.5F).color(r, g, b, a).uv(0.0F, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0.0F, 1.0F, 0.0F).endVertex();
        //$$ vc.vertex(m, 1.0F, 0.0F, -0.5F).color(r, g, b, a).uv(1.0F, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0.0F, 1.0F, 0.0F).endVertex();
        //$$ vc.vertex(m, 1.0F, 0.0F, 0.5F).color(r, g, b, a).uv(1.0F, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0.0F, 1.0F, 0.0F).endVertex();
        //$$ vc.vertex(m, 0.0F, 0.0F, 0.5F).color(r, g, b, a).uv(0.0F, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0.0F, 1.0F, 0.0F).endVertex();
        //#endif
    }

    private static int argb(float alpha, int rgb) {
        return ((int) (Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F) << 24) | rgb;
    }

    private static float ease(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
