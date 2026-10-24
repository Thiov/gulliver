package gulliver.client;

import gulliver.access.IVisualSize;
import gulliver.api.IResizeableEntity;
import gulliver.common.GulliverConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * The size an entity is DRAWN at.
 *
 * The hitbox, the server and the gameplay formulas switch to a new size at
 * once; with client.smoothResize on, the model scale, the camera's eye
 * height, the near plane and the third-person distance glide there over
 * half a second instead, once per frame (not in 20 Hz steps). Purely
 * visual: nothing here feeds back into the entity.
 *
 * The animation is a log-space ease between the two sizes (a 1 -> 1/8
 * shrink moves at an even perceived rate) on a clock of client ticks plus
 * the frame's partial tick, so it freezes while the game is paused.
 */
public final class VisualSize {
    private VisualSize() {}

    /** Half a second. */
    private static final float DURATION_TICKS = 10.0F;
    /** An entity this young just spawned: its first size packet is not a resize. */
    private static final int SPAWN_GRACE_TICKS = 5;
    /** Not drawn for this long (2 s): any size change meanwhile is not worth replaying. */
    private static final double STALE_TICKS = 40.0D;

    private static long ticks;
    private static int previewDepth;
    private static float viewPartial = Float.NaN;

    // ---- clock -------------------------------------------------------------

    /** End of every client tick (GulliverClient). */
    public static void tick(Minecraft mc) {
        if (!mc.isPaused()) ticks++;
        // Previews are drawn between ticks; a stuck count (a preview that threw) heals here.
        previewDepth = 0;
    }

    /** The partial tick of the frame being drawn. */
    public static float framePartial() {
        Minecraft mc = Minecraft.getInstance();
        //#if MC >= 1.21
        return mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        //#else
        //$$ return mc.getFrameTime();
        //#endif
    }

    /**
     * The camera's own partial tick for this frame (set by the camera hook;
     * it differs from the frame's while ticks are frozen). Props drawn next
     * to the camera use it so they move exactly as the camera does.
     */
    public static float viewPartial() {
        return Float.isNaN(viewPartial) ? framePartial() : viewPartial;
    }

    public static void setViewPartial(float pt) {
        viewPartial = pt;
    }

    // ---- GUI previews --------------------------------------------------------

    /** Inventory-style player previews draw at the logical size so the screen can normalise it. */
    public static void beginPreview() {
        previewDepth++;
    }

    public static void endPreview() {
        if (previewDepth > 0) previewDepth--;
    }

    public static boolean inPreview() {
        return previewDepth > 0;
    }

    // ---- sizes ---------------------------------------------------------------

    /** The real size, as the server and the hitbox see it. */
    public static float logical(Entity entity) {
        return ((IResizeableEntity) entity).getSizeMultiplier();
    }

    public static float of(Entity entity) {
        return of(entity, framePartial());
    }

    /** The size to draw this entity at, this frame. */
    public static float of(Entity entity, float partialTick) {
        float logical = logical(entity);
        IVisualSize.State s = ((IVisualSize) entity).gulliver$visualState();
        if (!GulliverConfig.INSTANCE.client.smoothResize) {
            s.from = s.to = logical;
            s.seen = true;
            return logical;
        }
        double now = ticks + (double) partialTick;
        boolean stale = !s.seen || now - s.lastSeen > STALE_TICKS;
        s.lastSeen = now;
        if (stale) {
            s.from = s.to = logical;
            s.start = now;
            s.seen = true;
            return logical;
        }
        if (logical != s.to) {
            float current = entity.tickCount < SPAWN_GRACE_TICKS ? logical : at(s, now);
            s.from = current;
            s.to = logical;
            s.start = now;
        }
        return at(s, now);
    }

    public static float renderScale(Entity entity) {
        return renderScale(entity, framePartial());
    }

    /**
     * What to scale the entity's model by: the drawn size, or inside a GUI
     * preview the size the screen expects. From 1.21.2 on the preview divides
     * the model's scale back out, so it gets the real (hitbox) size to
     * divide; 1.20.1 draws the model exactly as the renderer scales it, so
     * the preview gets 1.
     */
    public static float renderScale(Entity entity, float partialTick) {
        if (previewDepth > 0) {
            //#if MC >= 1.21.2
            return logical(entity);
            //#else
            //$$ return 1.0F;
            //#endif
        }
        return of(entity, partialTick);
    }

    private static float at(IVisualSize.State s, double now) {
        float t = (float) ((now - s.start) / DURATION_TICKS);
        if (t >= 1.0F || !(s.from > 0.0F) || !(s.to > 0.0F)) return s.to;
        if (t <= 0.0F) return s.from;
        float eased = t * t * (3.0F - 2.0F * t);
        return (float) (s.from * Math.pow(s.to / (double) s.from, eased));
    }

    // ---- camera and item helpers ---------------------------------------------

    /**
     * What the near clip plane (vanilla 0.05) is multiplied by for a viewer
     * of this size: it follows the body so a tiny can stand against a wall
     * without seeing through it, but stays within what a 32-bit depth
     * buffer resolves (not below 1/16, which at 1/32 would let distant
     * terrain flicker) and short enough that a giant's nearby blocks and
     * hands are not cut away (not above 8, i.e. 0.4 blocks).
     */
    public static float nearScale(float viewerSize) {
        return Mth.clamp(viewerSize, 1.0F / 16.0F, 8.0F);
    }

    /**
     * Classic first-person held-item factor, 1/sqrt(size), kept to what the
     * default 1/8..8 range produces: no screen-filling item at 1/32, no speck
     * at 32. (Third person needs no clamp: body size x this factor is
     * sqrt(size), which never gets extreme.)
     */
    public static float heldItemScale(float size) {
        if (!(size > 0.0F)) return 1.0F;
        return Mth.clamp(1.0F / (float) Math.sqrt(size), 0.35F, 2.83F);
    }

    /**
     * How much farther than usual a nameplate or shadow of this entity
     * stays visible (vanilla: 64 and 16 blocks): giants are seen from
     * proportionally farther, up to 8 times.
     */
    public static float visibilityScale(Entity entity) {
        return Mth.clamp(of(entity), 1.0F, 8.0F);
    }
}
