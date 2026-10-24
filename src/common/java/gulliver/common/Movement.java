package gulliver.common;

import gulliver.api.IResizeableEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The size curves behind walking, stepping and jumping. They live here, not
 * in the mixins, because several mixins (and older Minecraft versions with a
 * different jump hook) share them.
 */
public final class Movement {
    private Movement() {}

    /** Heavy (gold-armoured) bodies walk at their full size up to here ... */
    private static final float WEIGHTED_LINEAR_LIMIT = 8.0F;
    /** ... and gain one more step of speed per this much size beyond it. */
    private static final float WEIGHTED_SLOPE = 6.0F;

    /** A size that can be fed to the curves below (positive and finite), else 1. */
    public static float safeSize(float size) {
        return size > 0.0F && !Float.isInfinite(size) ? size : 1.0F;
    }

    /**
     * 1.6.4 getSizeMovementMultiplier: sqrt(size), or the size itself for a
     * weighted body. The linear case is what keeps a gold-armoured giant
     * quick, but at the top of the size range it would outrun the server's
     * moved-too-quickly check (10 blocks a tick), so beyond size 8 it grows
     * by a sixth of the size instead: 12 at size 32, about 3.4 blocks a tick
     * flat out.
     */
    public static float speedMultiplier(float size, boolean weighted) {
        size = safeSize(size);
        if (!weighted) return (float) Math.sqrt(size);
        if (size <= WEIGHTED_LINEAR_LIMIT) return size;
        return WEIGHTED_LINEAR_LIMIT + (size - WEIGHTED_LINEAR_LIMIT) / WEIGHTED_SLOPE;
    }

    /**
     * 1.6.4 of.java:486-512 step height: a power-of-2 normalisation of the
     * vanilla value rather than a straight multiplier.
     *
     *   norm = 1
     *   while (norm * size < 0.5) { norm *= 2;  f /= 2; }
     *   while (norm * size > 1.0) { norm /= 2;  f *= 2; }
     *   if (norm * size < 0.65) f *= norm * size;
     *
     * Sizes that aren't positive and finite count as 1; the loops stop
     * after a bounded number of halvings whatever comes in.
     */
    public static float stepHeight(float vanilla, float size) {
        float m = safeSize(size);
        if (m == 1.0F) return vanilla;
        m = Mth.clamp(m, 1.0F / 1024.0F, 1024.0F);
        float f = vanilla;
        float norm = 1.0F;
        for (int i = 0; i < 24 && norm * m < 0.5F; i++) { norm *= 2.0F; f /= 2.0F; }
        for (int i = 0; i < 24 && norm * m > 1.0F; i++) { norm /= 2.0F; f *= 2.0F; }
        if (norm * m < 0.65F) f *= norm * m;
        return f;
    }

    /**
     * 1.6.4 jump scaling, applied to vanilla's own jump value (so honey
     * blocks, the jump_strength attribute and other mods' changes still
     * count). The 1.6.4 formula for a plain jump:
     *
     *   y = 0.375 + (size <= 1 ? size : sqrt(size)) * 0.045      (0.42 at size 1)
     *   weighted, or tiny with a sticky item:  y *= sqrt(size)
     *   else bigger than 1:                    y *= size^0.25
     *   else a tiny player not sprinting:      y *= size^(1/6)
     *
     * Jump Boost is scaled separately by size^0.25 (1.6.4 of.java 2487-2489),
     * and a sneaking body on the ground jumps higher still (see sneakBoost).
     *
     * @param boost the Jump Boost part already contained in {@code vanilla}
     */
    public static float scaledJump(LivingEntity self, float vanilla, float boost) {
        IResizeableEntity sized = (IResizeableEntity) self;
        float size = sized.getSizeMultiplier();
        if (!(size > 0.0F) || Float.isInfinite(size) || size == 1.0F) return vanilla;

        float root = sized.getSizeMultiplierRoot();
        float y = 0.375F + (size <= 1.0F ? size : root) * 0.045F;
        boolean tiny = sized.isTiny();
        if (sized.isWeighted() || (tiny && sized.isSticky())) {
            y *= root;
        } else if (size > 1.0F) {
            y *= (float) Math.sqrt(root);
        } else if (tiny && !self.isSprinting() && self instanceof Player) {
            y *= (float) Math.cbrt(root);
        }

        float plain = vanilla - boost;
        y = plain * (y / 0.42F) + boost * (float) Math.sqrt(root);

        // Reconstructed sneak-jump (the 1.6.4 ASM patch is invisible in
        // JDCore): shift+space lifts higher than a plain jump. Only from the
        // ground, so it doesn't compound during the same airborne arc.
        if (self.isShiftKeyDown() && self.onGround()) {
            y *= sneakBoost(size);
        }
        return y;
    }

    /**
     * How much higher a sneaking body jumps: 1.5x at half size or smaller and
     * at double size or larger, easing to nothing as the size comes back to
     * 1 (so a vanilla-size body is unchanged and 0.99 isn't suddenly a
     * super-jumper).
     */
    public static float sneakBoost(float size) {
        double octaves = Math.abs(Math.log(safeSize(size)) / Math.log(2.0D));
        double t = Math.min(1.0D, octaves);
        return (float) (1.0D + 0.5D * t * t * (3.0D - 2.0D * t));
    }
}
