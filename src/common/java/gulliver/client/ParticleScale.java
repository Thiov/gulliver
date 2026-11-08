package gulliver.client;

import gulliver.access.IGulliverParticle;
import gulliver.common.GulliverConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.AABB;

/**
 * Particles drawn at the size of the creature that gives them off.
 *
 * Vanilla particles have one size for everyone, so a tiny's footsteps
 * kicked up dust bigger than its whole body and a giant's eating crumbs
 * were specks. Every particle is now scaled by the drawn size
 * (VisualSize) of the creature it belongs to, whole: the sprite, its
 * hitbox, its speed and its gravity, so it flies the same path at the
 * creature's scale in the same time (a giant's splash leaves at eight
 * times the speed, eight times as big, and falls eight times as fast).
 *
 * Particles carry no owner, so the owner is whoever the game was busy
 * with when the particle was made:
 *  - a creature's own client tick (and its passengers'): walking and
 *    sprinting dust, potion swirls, eating crumbs, bubbles, splashes,
 *    an enderman's portal sparks, block particles under its feet;
 *  - an entity event from the server (death poof, hearts, angry or happy
 *    villagers, taming smoke, a wolf shaking off water);
 *  - a tracking emitter (critical and enchanted hits on that creature);
 *  - particles the server sends to a position (landing dust, damage
 *    hearts, sweeps, Gulliver's own ground shock): the smallest creature
 *    whose body holds that point;
 *  - an explosion where a creeper stands: the creeper's size.
 * Anything else (rain, torches, block breaking, campfires) has no owner
 * and stays vanilla. client.particleScaling turns it all off.
 */
public final class ParticleScale {
    private ParticleScale() {}

    /** Nested owners (a passenger ticks inside its vehicle's tick); 1 means "unscaled". */
    private static final float[] STACK = new float[32];
    private static int depth;

    // ---- owners ------------------------------------------------------------

    /** Particles made from here until {@link #pop()} belong to {@code owner}. */
    public static void push(Entity owner) {
        pushScale(owner == null ? 1.0F : VisualSize.of(owner));
    }

    /** Particles made from here until {@link #pop()} are drawn at this size. */
    public static void pushScale(float size) {
        if (!onRenderThread()) return;
        if (depth < STACK.length) STACK[depth] = size > 0.0F ? size : 1.0F;
        depth++;
    }

    public static void pop() {
        if (!onRenderThread()) return;
        if (depth > 0) depth--;
    }

    /**
     * Particles the server sent to this point belong to the smallest
     * creature whose body holds it (a tiny's landing dust at a giant's feet
     * is the tiny's).
     */
    public static void pushAt(double x, double y, double z) {
        if (!onRenderThread()) return;
        pushScale(ownerSize(x, y, z));
    }

    /** An explosion at this point, as big as the creeper (if any) that stood there. */
    public static void pushExplosion(double x, double y, double z) {
        if (!onRenderThread()) return;
        float size = 1.0F;
        var level = Minecraft.getInstance().level;
        if (level != null) {
            double best = Double.MAX_VALUE;
            AABB probe = new AABB(x - 1.0D, y - 1.0D, z - 1.0D, x + 1.0D, y + 1.0D, z + 1.0D);
            for (Entity e : level.getEntities((Entity) null, probe)) {
                if (!(e instanceof Creeper)) continue;
                double d = e.distanceToSqr(x, y, z);
                double reach = Math.max(0.5D, e.getBbWidth());
                if (d <= reach * reach && d < best) {
                    best = d;
                    size = VisualSize.of(e);
                }
            }
        }
        pushScale(size);
    }

    /** Start of every client tick: an owner left behind by a handler that threw is dropped. */
    public static void reset() {
        depth = 0;
    }

    private static float ownerSize(double x, double y, double z) {
        var level = Minecraft.getInstance().level;
        if (level == null) return 1.0F;
        AABB probe = new AABB(x - 0.25D, y - 0.25D, z - 0.25D, x + 0.25D, y + 0.25D, z + 0.25D);
        LivingEntity owner = null;
        double smallest = Double.MAX_VALUE;
        for (Entity e : level.getEntities((Entity) null, probe)) {
            if (!(e instanceof LivingEntity living)) continue;
            AABB body = living.getBoundingBox();
            double margin = Math.max(0.02D, 0.15D * living.getBbWidth());
            if (!body.inflate(margin).contains(x, y, z)) continue;
            double volume = body.getXsize() * body.getYsize() * body.getZsize();
            if (volume < smallest) {
                smallest = volume;
                owner = living;
            }
        }
        return owner == null ? 1.0F : VisualSize.of(owner);
    }

    // ---- scaling -------------------------------------------------------------

    /** The size particles made right now are drawn at (1 when nobody owns them). */
    public static float current() {
        if (depth == 0 || depth > STACK.length) return 1.0F;
        return STACK[depth - 1];
    }

    /** Whether scaling is on and the hand-made size effects should stand back (MixinEntitySplash, sprint dust). */
    public static boolean enabled() {
        return GulliverConfig.INSTANCE.client.particleScaling;
    }

    /** ParticleEngine.add: a new particle takes on its owner's size. */
    public static void apply(Particle particle) {
        if (particle == null || !enabled() || !onRenderThread()) return;
        float s = current();
        if (Math.abs(s - 1.0F) < 1.0E-3F) return;
        s = Math.max(GulliverConfig.MIN_SIZE, Math.min(GulliverConfig.MAX_SIZE, s));
        ((IGulliverParticle) particle).gulliver$scaleWhole(s);
    }

    private static boolean onRenderThread() {
        return Minecraft.getInstance().isSameThread();
    }
}
