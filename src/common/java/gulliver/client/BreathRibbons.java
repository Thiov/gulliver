package gulliver.client;

import gulliver.api.IResizeableEntity;
import gulliver.common.BreathGust;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The wind of a breath gust, simulated: every tick the mouth breathes out
 * a few wisps of air, and each wisp then lives on its own - it keeps the
 * direction it was blown in, slows down, wobbles with a little turbulence,
 * drifts up like warm breath and slides along whatever it hits instead of
 * passing through it. Turning while blowing sweeps the wind round like a
 * hose; stopping lets the last breath drift away. Each wisp is drawn as a
 * streak through its recent positions with a wispy texture
 * (textures/effect/wind.png) that flows along it.
 *
 * Everything is in the blower's own size: wisps leave the mouth as wide
 * as the mouth, carry about four body lengths (BreathGust.range) and
 * spread as they go, so a tiny breathes a whisper and a giant a gale that
 * look the same to scale. More lungs, more air: one wisp a tick for a
 * tiny, up to four for a colossus, which also blows rings of wind. Where a
 * giant's wisps hit the ground they kick up the ground itself (dirt, sand,
 * leaves) or spray from water. The particle setting thins all of it.
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

    private static final int TRAIL = 7;
    private static final RandomSource RAND = RandomSource.create();
    private static final List<Wisp> WISPS = new ArrayList<>();
    private static final List<Ring> RINGS = new ArrayList<>();
    private static final int MAX_WISPS = 2000;

    private static final class Wisp {
        final double[] x = new double[TRAIL];
        final double[] y = new double[TRAIL];
        final double[] z = new double[TRAIL];
        int count;
        double vx, vy, vz;
        int age, life;
        double width;
        double seed;
        float size;
        /** The last surface it hit (normal and plane offset): near it the wisp lies flat on it. */
        boolean skims;
        double nx, ny, nz, plane;
    }

    private static final class Ring {
        double x, y, z, ox, oy, oz;
        double vx, vy, vz;
        Vec3 side, up;
        double radius, radiusO, grow, band;
        int age, life;
    }

    public static boolean any() {
        return !WISPS.isEmpty() || !RINGS.isEmpty();
    }

    public static void clear() {
        WISPS.clear();
        RINGS.clear();
    }

    /** Where the wind leaves this body: a little below the eyes, further out in its own first-person view. */
    public static Vec3 mouth(Entity e, float partialTick, boolean ownView) {
        Vec3 dir = e.getViewVector(partialTick);
        double out = (ownView ? 0.55D : 0.25D) * e.getBbWidth() / 0.6D;
        return e.getEyePosition(partialTick).add(dir.scale(out)).subtract(0.0D, e.getBbHeight() * 0.05D, 0.0D);
    }

    /** One tick of breath out of {@code e}'s mouth. */
    static void emit(Minecraft mc, Entity e, int age) {
        float size = Math.max(1.0F / 64.0F, ((IResizeableEntity) e).getSizeMultiplier());
        double range = BreathGust.range(size);
        Vec3 dir = e.getViewVector(1.0F);
        boolean ownView = e == mc.player && mc.options.getCameraType().isFirstPerson();
        Vec3 mouth = mouth(e, 1.0F, ownView);
        Vec3 side = dir.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0E-6D) side = new Vec3(1.0D, 0.0D, 0.0D);
        side = side.normalize();
        Vec3 up = side.cross(dir).normalize();
        // The blower's own motion is carried by its breath.
        Vec3 carried = new Vec3(e.getX() - e.xOld, e.getY() - e.yOld, e.getZ() - e.zOld).scale(0.6D);
        int tier = size < 0.5F ? 0 : size < 2.0F ? 1 : size < 8.0F ? 2 : 3;
        int setting = mc.options.particles().get().ordinal(); // all, decreased, minimal
        float density = setting == 0 ? 1.0F : setting == 1 ? 0.6F : 0.3F;
        // Starting speed so a wisp slowing by a tenth each tick carries about the range.
        double speed = range / 9.0D;
        double mouthWidth = 0.32D * e.getBbWidth() / 0.6D;
        int n = count(new float[] { 1.0F, 2.0F, 3.0F, 4.0F }[tier] * density);
        for (int i = 0; i < n && WISPS.size() < MAX_WISPS; i++) {
            double a = RAND.nextDouble() * Math.PI * 2.0D;
            double s = 0.14D * Math.sqrt(RAND.nextDouble());
            Vec3 v = dir.add(side.scale(Math.cos(a) * s)).add(up.scale(Math.sin(a) * s)).normalize()
                    .scale(speed * (0.85D + 0.3D * RAND.nextDouble())).add(carried);
            Wisp w = new Wisp();
            Vec3 at = mouth.add(v.normalize().scale(mouthWidth * 0.3D * RAND.nextDouble()));
            w.x[0] = at.x;
            w.y[0] = at.y;
            w.z[0] = at.z;
            w.count = 1;
            w.vx = v.x;
            w.vy = v.y;
            w.vz = v.z;
            w.life = 15 + RAND.nextInt(7);
            w.width = mouthWidth * (0.7D + 0.6D * RAND.nextDouble());
            w.seed = RAND.nextDouble();
            w.size = size;
            WISPS.add(w);
        }
        if (tier >= 3 && age % 6 == 0) {
            Ring r = new Ring();
            Vec3 c = mouth.add(dir.scale(range * 0.03D));
            r.x = r.ox = c.x;
            r.y = r.oy = c.y;
            r.z = r.oz = c.z;
            Vec3 v = dir.scale(speed * 0.85D).add(carried);
            r.vx = v.x;
            r.vy = v.y;
            r.vz = v.z;
            r.side = side;
            r.up = up;
            r.radius = r.radiusO = mouthWidth * 1.2D;
            r.grow = range * 0.014D;
            r.band = mouthWidth;
            r.life = 18;
            RINGS.add(r);
        }
    }

    /** Every client tick: the air moves on. */
    static void tick(Minecraft mc, ClientLevel level) {
        Iterator<Wisp> it = WISPS.iterator();
        while (it.hasNext()) {
            Wisp w = it.next();
            if (++w.age >= w.life) {
                it.remove();
                continue;
            }
            // Turbulence: a smooth wobble, a share of the wisp's own speed.
            double speed = Math.sqrt(w.vx * w.vx + w.vy * w.vy + w.vz * w.vz);
            double t = w.age * 0.45D + w.seed * 31.0D;
            double wobble = speed * 0.14D;
            w.vx = (w.vx + Math.sin(t * 1.3D) * wobble) * 0.9D;
            w.vy = (w.vy + Math.cos(t * 1.1D) * wobble * 0.6D) * 0.9D + 0.0008D * w.size;
            w.vz = (w.vz + Math.sin(t * 0.9D + 2.0D) * wobble) * 0.9D;
            Vec3 from = new Vec3(w.x[0], w.y[0], w.z[0]);
            Vec3 to = from.add(w.vx, w.vy, w.vz);
            //#if MC >= 1.21
            BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.ANY, net.minecraft.world.phys.shapes.CollisionContext.empty()));
            //#else
            //$$ BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
            //$$         ClipContext.Fluid.ANY, (Entity) null));
            //#endif
            if (hit.getType() == HitResult.Type.BLOCK) {
                // The air slides along what it hits.
                Direction face = hit.getDirection();
                Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
                Vec3 v = new Vec3(w.vx, w.vy, w.vz);
                Vec3 slide = v.subtract(normal.scale(v.dot(normal))).scale(0.85D);
                w.vx = slide.x;
                w.vy = slide.y;
                w.vz = slide.z;
                to = hit.getLocation().add(normal.scale(lift(w)));
                w.skims = true;
                w.nx = normal.x;
                w.ny = normal.y;
                w.nz = normal.z;
                w.plane = normal.dot(hit.getLocation());
                if (w.size >= 2.0F && RAND.nextFloat() < 0.3F) dust(mc, level, hit, slide, w.size);
            }
            for (int i = Math.min(w.count, TRAIL - 1); i > 0; i--) {
                w.x[i] = w.x[i - 1];
                w.y[i] = w.y[i - 1];
                w.z[i] = w.z[i - 1];
            }
            w.x[0] = to.x;
            w.y[0] = to.y;
            w.z[0] = to.z;
            w.count = Math.min(w.count + 1, TRAIL);
        }
        Iterator<Ring> rings = RINGS.iterator();
        while (rings.hasNext()) {
            Ring r = rings.next();
            if (++r.age >= r.life) {
                rings.remove();
                continue;
            }
            r.ox = r.x;
            r.oy = r.y;
            r.oz = r.z;
            r.x += r.vx;
            r.y += r.vy;
            r.z += r.vz;
            r.vx *= 0.92D;
            r.vy *= 0.92D;
            r.vz *= 0.92D;
            r.radiusO = r.radius;
            r.radius += r.grow * (1.0D - 0.5D * r.age / r.life);
        }
    }

    /** How far over a surface a wisp skims. */
    private static double lift(Wisp w) {
        return 0.02D * w.size + 0.01D;
    }

    /**
     * Breath under water: a stream of bubbles out of the mouth that the
     * breath pushes a little way before they wobble up, bigger and more of
     * them the bigger the body, with a burble now and then.
     */
    static void bubbles(Minecraft mc, ClientLevel level, Entity e, int age) {
        float size = Math.max(1.0F / 64.0F, ((IResizeableEntity) e).getSizeMultiplier());
        Vec3 dir = e.getViewVector(1.0F);
        boolean ownView = e == mc.player && mc.options.getCameraType().isFirstPerson();
        Vec3 mouth = mouth(e, 1.0F, ownView);
        int setting = mc.options.particles().get().ordinal();
        float density = setting == 0 ? 1.0F : setting == 1 ? 0.6F : 0.3F;
        double speed = 0.2D * Math.sqrt(size);
        double spread = 0.08D * e.getBbWidth() / 0.6D;
        int n = count((size < 0.5F ? 1.0F : size < 2.0F ? 2.0F : size < 8.0F ? 3.0F : 4.0F) * density);
        for (int i = 0; i < n; i++) {
            Vec3 v = dir.scale(speed * (0.5D + 0.5D * RAND.nextDouble()))
                    .add((RAND.nextDouble() - 0.5D) * speed * 0.4D, (RAND.nextDouble() - 0.3D) * speed * 0.4D,
                            (RAND.nextDouble() - 0.5D) * speed * 0.4D);
            Vec3 at = mouth.add((RAND.nextDouble() - 0.5D) * spread, (RAND.nextDouble() - 0.5D) * spread,
                    (RAND.nextDouble() - 0.5D) * spread);
            Particle p = mc.particleEngine.createParticle(ParticleTypes.BUBBLE, at.x, at.y, at.z, v.x, v.y, v.z);
            if (p == null) continue;
            p.setParticleSpeed(v.x, v.y, v.z);
            // Smaller right in front of the blower's own eyes, so they don't fill the view.
            p.scale(Mth.clamp((float) Math.sqrt(size), 0.4F, 6.0F) * (ownView ? 0.55F : 1.0F));
        }
        if (age % 7 == 1) {
            Vec3 eye = e.getEyePosition();
            level.playLocalSound(eye.x, eye.y, eye.z, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.PLAYERS,
                    Mth.clamp(0.4F * (float) Math.sqrt(size), 0.05F, 2.0F),
                    Mth.clamp(1.3F / (float) Math.sqrt(size), 0.5F, 2.0F), false);
        }
    }

    /** What a giant's wind throws up where it hits: the ground itself, or spray. */
    private static void dust(Minecraft mc, ClientLevel level, BlockHitResult hit, Vec3 slide, float size) {
        Vec3 at = hit.getLocation();
        Vec3 v = slide.scale(0.6D).add(0.0D, slide.length() * 0.35D, 0.0D);
        ParticleOptions type;
        if (level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER)) {
            type = ParticleTypes.SPLASH;
        } else {
            BlockState state = level.getBlockState(hit.getBlockPos());
            if (state.isAir()) return;
            type = new BlockParticleOption(ParticleTypes.BLOCK, state);
        }
        Particle p = mc.particleEngine.createParticle(type, at.x, at.y, at.z, v.x, v.y, v.z);
        if (p == null) return;
        p.setParticleSpeed(v.x, v.y, v.z);
        p.scale((float) Math.sqrt(size));
        //#if MC >= 1.21
        if (size >= 8.0F && RAND.nextInt(8) == 0) {
            Particle gust = mc.particleEngine.createParticle(ParticleTypes.GUST, at.x, at.y, at.z, 0.0D, 0.0D, 0.0D);
            if (gust != null) gust.scale(size / 8.0F);
        }
        //#endif
    }

    private static int count(float n) {
        int whole = (int) n;
        return whole + (RAND.nextFloat() < n - whole ? 1 : 0);
    }

    public static void build(Vec3 camera, float pt, Sink sink) {
        float seconds = (float) (System.nanoTime() % 1_000_000_000_000L) / 1.0E9F;
        for (Wisp w : WISPS) {
            if (w.count < 2) continue;
            float lifeIn = Mth.clamp((w.age + pt) / 3.0F, 0.0F, 1.0F);
            float lifeOut = Mth.clamp((w.life - w.age - pt) / 7.0F, 0.0F, 1.0F);
            float head = 0.85F * lifeIn * lifeOut;
            if (head <= 0.004F) continue;
            float grown = (w.age + pt) / w.life;
            int n = w.count;
            // Each trail point slides towards the newer one during the frame.
            Vec3[] p = new Vec3[n];
            boolean[] flat = new boolean[n];
            Vec3 normal = w.skims ? new Vec3(w.nx, w.ny, w.nz) : null;
            for (int i = 0; i < n; i++) {
                int newer = Math.max(0, i - 1);
                p[i] = new Vec3(Mth.lerp(pt, w.x[i], w.x[newer]), Mth.lerp(pt, w.y[i], w.y[newer]),
                        Mth.lerp(pt, w.z[i], w.z[newer]));
                if (normal == null) continue;
                // Close to the surface it hit, the streak lies flat on it, so
                // none of it dips into the ground or under the water.
                double over = normal.dot(p[i]) - w.plane;
                double half = width(w, grown, i, n) * 0.5D;
                if (over < half && over > -half) {
                    flat[i] = true;
                    if (over < lift(w)) p[i] = p[i].add(normal.scale(lift(w) - over));
                }
            }
            Vec3 prevB = null;
            float prevA = 0.0F, prevU = 0.0F;
            for (int i = 0; i < n; i++) {
                Vec3 tangent = i + 1 < n ? p[i].subtract(p[i + 1]) : p[i - 1].subtract(p[i]);
                if (tangent.lengthSqr() < 1.0E-12D) tangent = new Vec3(w.vx, w.vy, w.vz);
                Vec3 across = flat[i] ? tangent.cross(normal) : tangent.cross(p[i].subtract(camera));
                if (across.lengthSqr() < 1.0E-12D) across = tangent.cross(p[i].subtract(camera));
                if (across.lengthSqr() < 1.0E-12D) continue;
                double width = width(w, grown, i, n);
                Vec3 b = across.normalize().scale(width * 0.5D);
                float alpha = head * (float) Math.pow(1.0D - i / (n - 1.0D), 1.2D);
                float u = (float) (w.seed * 3.0D + i * 0.35D - seconds * 1.6D);
                if (prevB != null) quad(sink, camera, p[i - 1], prevB, p[i], b, prevU, u, prevA, alpha);
                prevB = b;
                prevA = alpha;
                prevU = u;
            }
        }
        for (Ring r : RINGS) {
            float t = (r.age + pt) / r.life;
            float alpha = (float) (0.7D * Math.sin(Math.PI * t) * (1.0D - t));
            Vec3 c = new Vec3(Mth.lerp(pt, r.ox, r.x), Mth.lerp(pt, r.oy, r.y), Mth.lerp(pt, r.oz, r.z));
            double radius = Mth.lerp(pt, r.radiusO, r.radius);
            ring(sink, camera, c, r.side, r.up, radius, r.band * (1.0D + 2.0D * t), alpha, seconds * 0.8F);
        }
    }

    /** How wide a wisp's streak is at trail point {@code i}: it spreads as it ages and thins towards its tail. */
    private static double width(Wisp w, float grown, int i, int n) {
        return w.width * (0.7D + 1.8D * grown) * (1.0D - 0.3D * i / (n - 1.0D));
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

    /** A band of wind round its own axis, rolling outward. */
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
}
