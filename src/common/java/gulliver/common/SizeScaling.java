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

    /** A body this many times bigger than what pushes it doesn't move at all (Entity.push, #14). */
    public static final float WALL_RATIO = 4.0F;
    /** Most a much bigger pusher multiplies vanilla's push by. */
    private static final double MAX_PUSH_BOOST = 3.0D;

    /**
     * How much of vanilla's push a body of size {@code pushed} takes from one
     * of size {@code pusher}:
     *
     *   pusher as big or bigger:      sqrt(pusher / pushed), at most 3
     *   pusher smaller:               ((g - 1/4) / (3/4))^2, g = pusher / pushed
     *   pusher under a quarter size:  0 (a wall)
     *
     * Equal sizes give exactly 1, a half-size pusher 1/9, and the share
     * falls smoothly to 0 at the wall.
     */
    public static double pushShare(float pusher, float pushed) {
        if (!(pusher > 0.0F) || !(pushed > 0.0F)) return 1.0D;
        double g = pusher / (double) pushed;
        if (g >= 1.0D) return Math.min(Math.sqrt(g), MAX_PUSH_BOOST);
        double wall = 1.0D / WALL_RATIO;
        if (g <= wall) return 0.0D;
        double t = (g - wall) / (1.0D - wall);
        return t * t;
    }

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

    /**
     * How often a mob's blow or grab at something smaller than itself misses:
     * 1 - target/attacker, at most 90%. Half its size, every other blow
     * misses; an eighth, seven in eight.
     */
    public static float mobMissChance(float attackerSize, float targetSize) {
        if (!(attackerSize > 0.0F) || !(targetSize < attackerSize)) return 0.0F;
        return Math.min(0.9F, 1.0F - targetSize / attackerSize);
    }

    /** Food value multiplier for an eater of this size: inverse of size, kept inside 1/32..32. */
    public static float foodFactor(float size) {
        if (size == 1.0F || !(size > 0.0F)) return 1.0F;
        return Math.max(1.0F / 32.0F, Math.min(32.0F, 1.0F / size));
    }
}
