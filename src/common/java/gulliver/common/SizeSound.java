package gulliver.common;

import gulliver.api.IResizeableEntity;

/** How loud a resized entity's own sounds are; one rule for everyone, the local player included. */
public final class SizeSound {
    private SizeSound() {}

    /**
     * Scaling is asymmetric on purpose:
     *   size < 1 -> x sqrt(size)    (1.6.4 verbatim: tinies are quieter)
     *   size > 1 -> x size^1.5, capped at 5.0
     * The steeper up-slope exists because audible range only grows once the
     * final volume exceeds 1.0 (range = 16 x volume), and most entity sound
     * bases are small (a cow's moo is 0.4, footsteps 0.15).
     */
    public static float scaleVolume(IResizeableEntity sized, float volume) {
        float size = sized.getSizeMultiplier();
        if (size == 1.0F) return volume;
        if (size > 1.0F) {
            return Math.min(volume * size * sized.getSizeMultiplierRoot(), 5.0F);
        }
        return volume * sized.getSizeMultiplierRoot();
    }
}
