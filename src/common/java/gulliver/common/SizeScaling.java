package gulliver.common;

import net.minecraft.util.RandomSource;

/**
 * The size formulas that several mixins share, with the guards that keep
 * them sane across the whole 1/32..32 range: a hit never multiplies by
 * more than 8 either way, a meal or a draw never takes fewer than 4 ticks
 * or more than 8x the vanilla time, and food stays in proportion.
 */
public final class SizeScaling {
    private SizeScaling() {}

    /** Largest ratio any knockback or damage scaling may apply, either way (1/8x .. 8x). */
    public static final float MAX_HIT_RATIO = 8.0F;
    /** Shortest an eating, drawing or loading action may get (unless vanilla is shorter already). */
    public static final int MIN_USE_TICKS = 4;
    /** Longest, as a multiple of the vanilla time. */
    public static final int MAX_USE_SLOWDOWN = 8;

    public static float clampRatio(float ratio) {
        if (!(ratio > 0.0F)) return 1.0F; // NaN / 0 / negative: no scaling rather than a blow-up
        return Math.max(1.0F / MAX_HIT_RATIO, Math.min(MAX_HIT_RATIO, ratio));
    }

    /**
     * A use time (eating, drinking, crossbow loading) scaled by 1/size:
     * tinies are slower, giants quicker, within [min(base, 4), 8 * base].
     */
    public static int scaledTicks(int base, float size) {
        if (base <= 0 || size == 1.0F || !(size > 0.0F)) return base;
        long scaled = Math.round(base / (double) size);
        long lo = Math.min(base, MIN_USE_TICKS);
        long hi = (long) base * MAX_USE_SLOWDOWN;
        return (int) Math.max(lo, Math.min(hi, scaled));
    }

    /**
     * How many "ticks of draw" one held tick is worth for a weapon that
     * needs {@code vanillaTicks} at size 1 (bow 20, trident 10): the
     * reciprocal of {@link #scaledTicks}, so the clamps match the eating ones.
     */
    public static float chargeFactor(float size, int vanillaTicks) {
        if (size == 1.0F || vanillaTicks <= 0) return 1.0F;
        return vanillaTicks / (float) scaledTicks(vanillaTicks, size);
    }

    /** value rounded up with probability equal to its fraction, so the expected result is exact. */
    public static int stochasticRound(double value, RandomSource random) {
        if (!(value > 0.0D)) return 0;
        int floor = (int) Math.min(Integer.MAX_VALUE - 1, Math.floor(value));
        return floor + (random.nextDouble() < value - floor ? 1 : 0);
    }

    /** Food value multiplier for an eater of this size: inverse of size, kept inside 1/32..32. */
    public static float foodFactor(float size) {
        if (size == 1.0F || !(size > 0.0F)) return 1.0F;
        return Math.max(1.0F / 32.0F, Math.min(32.0F, 1.0F / size));
    }
}
