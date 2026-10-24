package gulliver.common;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * How far the biggest resized body currently loaded reaches, so entity
 * lookups can find it.
 *
 * Vanilla files an entity under the chunk section of its feet and, when
 * looking for entities in a box, only searches 4 blocks below and 0 above
 * (and 2 to each side): enough for anything up to 4 blocks tall and wide.
 * A giant's chest and head live in sections the search never visits, so
 * arrows, tridents and picks flew straight through them. This tracks the
 * largest height and half-width of any body above normal size, on both
 * logical sides, and {@link #widen} stretches the search by exactly that.
 * With no giant around nothing is widened and nothing is allocated.
 *
 * The figures are kept for two windows of ten seconds: every giant reports
 * on its own tick, and when the last one is gone the margins shrink back.
 */
public final class GiantExtents {
    private GiantExtents() {}

    /** Reach vanilla's section search already covers (feet-to-top, feet-to-side). */
    private static final float VANILLA_HEIGHT = 4.0F;
    private static final float VANILLA_HALF_WIDTH = 2.0F;
    private static final long WINDOW_NANOS = 10_000_000_000L;

    private static volatile float height;
    private static volatile float halfWidth;
    private static volatile float previousHeight;
    private static volatile float previousHalfWidth;
    private static volatile long windowStart = System.nanoTime();

    /** A resized body ticked: remember how big it is. */
    public static void report(Entity entity) {
        float h = entity.getBbHeight();
        float w = entity.getBbWidth() * 0.5F;
        if (h <= VANILLA_HEIGHT && w <= VANILLA_HALF_WIDTH) return;
        rotate();
        if (h > height) height = h;
        if (w > halfWidth) halfWidth = w;
    }

    /** The box to pick entity sections with, wide enough for the biggest body around. */
    public static AABB widen(AABB box) {
        float h = Math.max(height, previousHeight);
        float w = Math.max(halfWidth, previousHalfWidth);
        if (h <= VANILLA_HEIGHT && w <= VANILLA_HALF_WIDTH) return box;
        rotate();
        h = Math.max(height, previousHeight);
        w = Math.max(halfWidth, previousHalfWidth);
        double below = Math.max(0.0D, h - VANILLA_HEIGHT);
        double side = Math.max(0.0D, w - VANILLA_HALF_WIDTH);
        if (below == 0.0D && side == 0.0D) return box;
        return new AABB(box.minX - side, box.minY - below, box.minZ - side,
                box.maxX + side, box.maxY, box.maxZ + side);
    }

    private static void rotate() {
        long now = System.nanoTime();
        if (now - windowStart < WINDOW_NANOS) return;
        windowStart = now;
        previousHeight = height;
        previousHalfWidth = halfWidth;
        height = 0.0F;
        halfWidth = 0.0F;
    }
}
