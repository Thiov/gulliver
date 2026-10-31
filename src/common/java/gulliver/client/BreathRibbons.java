package gulliver.client;

import gulliver.api.IResizeableEntity;
import gulliver.common.BreathGust;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/**
 * The wind of a breath gust, as geometry: streaks of air that leave the
 * mouth, spiral slowly outward along the cone and fade at its end, drawn
 * with a wispy streak texture (textures/effect/wind.png) that scrolls
 * along them. Every length is in the blower's own size - the cone is four
 * body lengths (BreathGust.range), a streak starts as wide as the mouth
 * and widens as the air spreads - so a tiny breathes a faint whisper and a
 * giant a gale, and they look the same to scale.
 *
 * More lungs, more air: a tiny blows three streaks, a normal body six, a
 * giant ten, and a colossus (size 8 and up) fourteen plus rings of wind
 * rolling out of its mouth like shockwaves.
 *
 * Quads are produced into a {@link Sink} in camera-relative coordinates;
 * BreathRibbonRenderer feeds them to whichever renderer the version has.
 */
public final class BreathRibbons {
    private BreathRibbons() {}

    /** Receives the corners of quads, four at a time, relative to the camera. */
    public interface Sink {
        void vertex(float x, float y, float z, float u, float v, float alpha);
    }

    private static final int SEGMENTS = 12;

    public static boolean any() {
        return !BreathFx.gusts().isEmpty();
    }

    /** Where the wind leaves this body: a little below the eyes, further out in its own first-person view. */
    public static Vec3 mouth(Entity e, float partialTick, boolean ownView) {
        Vec3 dir = e.getViewVector(partialTick);
        double out = (ownView ? 0.55D : 0.25D) * e.getBbWidth() / 0.6D;
        return e.getEyePosition(partialTick).add(dir.scale(out)).subtract(0.0D, e.getBbHeight() * 0.05D, 0.0D);
    }

    public static void build(Vec3 camera, float partialTick, Sink sink) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        for (Map.Entry<Integer, BreathFx.Gust> entry : BreathFx.gusts().entrySet()) {
            Entity e = level.getEntity(entry.getKey());
            BreathFx.Gust gust = entry.getValue();
            float strength = gust.strength(partialTick);
            if (e == null || strength <= 0.0F) continue;
            boolean ownView = e == mc.player && mc.options.getCameraType().isFirstPerson();
            draw(e, gust.age + partialTick, strength, ownView, camera, partialTick, sink);
        }
    }

    private static void draw(Entity e, float time, float strength, boolean ownView, Vec3 camera, float pt, Sink sink) {
        float size = Math.max(1.0F / 64.0F, ((IResizeableEntity) e).getSizeMultiplier());
        double range = BreathGust.range(size);
        Vec3 dir = e.getViewVector(pt);
        Vec3 mouth = mouth(e, pt, ownView);
        Vec3 side = dir.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0E-6D) side = new Vec3(1.0D, 0.0D, 0.0D);
        side = side.normalize();
        Vec3 up = side.cross(dir).normalize();
        int tier = size < 0.5F ? 0 : size < 2.0F ? 1 : size < 8.0F ? 2 : 3;
        int streaks = new int[] { 4, 8, 12, 16 }[tier];
        // Width of the mouth's breath; a streak grows fourfold as the air spreads out.
        double mouthWidth = 0.32D * e.getBbWidth() / 0.6D;
        double seconds = time / 20.0D;
        double flow = (0.8D + 0.25D * tier) * (1.0D + 0.4D * Math.min(1.0D, BreathGust.windSpeed(size)));

        for (int i = 0; i < streaks; i++) {
            double h1 = hash(i, 1), h2 = hash(i, 2), h3 = hash(i, 3), h4 = hash(i, 4);
            double angle0 = Math.PI * 2.0D * (i + 0.5D * h1) / streaks;
            double radius = range * (0.08D + 0.12D * h2);
            double twist = (h3 * 2.0D - 1.0D) * 2.2D;
            double length = 0.42D + 0.25D * h4;
            // The streak's middle runs from the mouth to the end of the cone, over and over.
            double centre = frac(seconds * flow * (0.85D + 0.3D * h1) + h3);
            double t0 = centre - length * 0.5D;
            double t1 = centre + length * 0.5D;
            double from = Math.max(0.02D, t0);
            double to = Math.min(0.98D, t1);
            if (to - from < 0.02D) continue;
            double spin = angle0 + seconds * 0.9D * (h2 > 0.5D ? 1.0D : -1.0D);
            double scroll = seconds * 1.6D + h1;

            Vec3 prevP = null, prevB = null;
            float prevU = 0.0F, prevA = 0.0F;
            for (int s = 0; s <= SEGMENTS; s++) {
                double t = Mth.lerp(s / (double) SEGMENTS, from, to);
                Vec3 p = point(mouth, dir, side, up, range, radius, spin, twist, t);
                Vec3 ahead = point(mouth, dir, side, up, range, radius, spin, twist, Math.min(1.0D, t + 0.01D));
                Vec3 tangent = ahead.subtract(p);
                if (tangent.lengthSqr() < 1.0E-12D) tangent = dir;
                Vec3 view = p.subtract(camera);
                Vec3 across = tangent.cross(view);
                if (across.lengthSqr() < 1.0E-12D) across = side;
                double width = mouthWidth * (0.6D + 3.4D * t);
                Vec3 b = across.normalize().scale(width * 0.5D);
                // Soft at both ends of the streak, thinner towards the end of the cone.
                double inStreak = Math.sin(Math.PI * Mth.clamp((t - t0) / (t1 - t0), 0.0D, 1.0D));
                float alpha = (float) (strength * 0.9D * inStreak * Math.pow(1.0D - t, 0.45D)
                        * Mth.clamp(t / 0.06D, 0.0D, 1.0D));
                float u = (float) (t * 3.0D - scroll);
                if (prevP != null) quad(sink, camera, prevP, prevB, p, b, prevU, u, prevA, alpha);
                prevP = p;
                prevB = b;
                prevU = u;
                prevA = alpha;
            }
        }

        if (tier >= 3) {
            for (int k = 0; k < 2; k++) {
                double t = frac(seconds * 0.7D + k * 0.5D);
                double ringRadius = range * (0.05D + 0.14D * t);
                double band = mouthWidth * (1.0D + 2.0D * t);
                float alpha = (float) (strength * 0.7D * Math.sin(Math.PI * t) * (1.0D - t));
                ring(sink, camera, mouth.add(dir.scale(range * t * 0.8D)), side, up, ringRadius, band, alpha,
                        (float) (seconds * 0.8D));
            }
        }
    }

    private static Vec3 point(Vec3 mouth, Vec3 dir, Vec3 side, Vec3 up, double range, double radius,
                              double spin, double twist, double t) {
        double r = radius * Math.pow(t, 0.75D);
        double a = spin + twist * t;
        return mouth.add(dir.scale(range * t)).add(side.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r));
    }

    /** Both faces, so no camera angle and no render type's culling hides a streak. */
    private static void quad(Sink sink, Vec3 cam, Vec3 p0, Vec3 b0, Vec3 p1, Vec3 b1,
                             float u0, float u1, float a0, float a1) {
        if (a0 <= 0.002F && a1 <= 0.002F) return;
        vertex(sink, cam, p0.subtract(b0), u0, 0.0F, a0);
        vertex(sink, cam, p0.add(b0), u0, 1.0F, a0);
        vertex(sink, cam, p1.add(b1), u1, 1.0F, a1);
        vertex(sink, cam, p1.subtract(b1), u1, 0.0F, a1);
        vertex(sink, cam, p1.subtract(b1), u1, 0.0F, a1);
        vertex(sink, cam, p1.add(b1), u1, 1.0F, a1);
        vertex(sink, cam, p0.add(b0), u0, 1.0F, a0);
        vertex(sink, cam, p0.subtract(b0), u0, 0.0F, a0);
    }

    /** A band of wind round the cone's axis, rolling outward. */
    private static void ring(Sink sink, Vec3 cam, Vec3 centre, Vec3 side, Vec3 up, double radius, double band,
                             float alpha, float scroll) {
        if (alpha <= 0.002F) return;
        int n = 24;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2.0D * i / n;
            double a1 = Math.PI * 2.0D * (i + 1) / n;
            Vec3 r0 = side.scale(Math.cos(a0)).add(up.scale(Math.sin(a0)));
            Vec3 r1 = side.scale(Math.cos(a1)).add(up.scale(Math.sin(a1)));
            float u0 = (float) i / n * 4.0F - scroll;
            float u1 = (float) (i + 1) / n * 4.0F - scroll;
            Vec3 in0 = centre.add(r0.scale(radius - band * 0.5D));
            Vec3 out0 = centre.add(r0.scale(radius + band * 0.5D));
            Vec3 out1 = centre.add(r1.scale(radius + band * 0.5D));
            Vec3 in1 = centre.add(r1.scale(radius - band * 0.5D));
            vertex(sink, cam, in0, u0, 0.0F, alpha);
            vertex(sink, cam, out0, u0, 1.0F, alpha);
            vertex(sink, cam, out1, u1, 1.0F, alpha);
            vertex(sink, cam, in1, u1, 0.0F, alpha);
            vertex(sink, cam, in1, u1, 0.0F, alpha);
            vertex(sink, cam, out1, u1, 1.0F, alpha);
            vertex(sink, cam, out0, u0, 1.0F, alpha);
            vertex(sink, cam, in0, u0, 0.0F, alpha);
        }
    }

    private static void vertex(Sink sink, Vec3 cam, Vec3 p, float u, float v, float alpha) {
        sink.vertex((float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z), u, v, alpha);
    }

    private static double frac(double x) {
        return x - Math.floor(x);
    }

    /** A fixed pseudo-random number in [0, 1) per streak and purpose. */
    private static double hash(int i, int salt) {
        long h = (i * 0x9E3779B97F4A7C15L) ^ (salt * 0xC2B2AE3D27D4EB4FL);
        h ^= (h >>> 31);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 29);
        return (h >>> 11) * 0x1.0p-53;
    }
}
