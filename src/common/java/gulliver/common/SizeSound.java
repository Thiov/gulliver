package gulliver.common;

import gulliver.api.IResizeableEntity;
import net.minecraft.world.entity.Entity;

/** How loud a resized entity's own sounds are; one rule for everyone, the local player included. */
public final class SizeSound {
    private SizeSound() {}

    /** The loudest a resized sound gets at the normal strength (16 x this = 80 blocks of range). */
    private static final float CAP = 5.0F;
    /** ... and at any strength (512 blocks). */
    private static final float MAX_CAP = 32.0F;

    /**
     * Scaling is asymmetric on purpose:
     *   size < 1 -> x sqrt(size)    (1.6.4 verbatim: tinies are quieter)
     *   size > 1 -> x size^1.5, capped at 5.0
     * The steeper up-slope exists because audible range only grows once the
     * final volume exceeds 1.0 (range = 16 x volume), and most entity sound
     * bases are small (a cow's moo is 0.4, footsteps 0.15).
     *
     * general.soundVolumeScaling (k) is the strength: the exponents are
     * multiplied by it, so 0 leaves every sound at vanilla volume, 1 is the
     * curve above and 10 makes a giant boom and a tiny all but silent. Above
     * 1 the cap rises with it (5k, at most 32).
     */
    public static float scaleVolume(IResizeableEntity sized, float volume) {
        float size = sized.getSizeMultiplier();
        if (size == 1.0F || !(size > 0.0F)) return volume;
        float k = strength(sized);
        if (k <= 0.0F) return volume;
        if (k == 1.0F) {
            if (size > 1.0F) return Math.min(volume * size * sized.getSizeMultiplierRoot(), CAP);
            return volume * sized.getSizeMultiplierRoot();
        }
        if (size > 1.0F) {
            float cap = Math.min(CAP * Math.max(1.0F, k), MAX_CAP);
            return Math.min(volume * (float) Math.pow(size, 1.5D * k), cap);
        }
        return volume * (float) Math.pow(size, 0.5D * k);
    }

    /** The server decides on its side; a client follows what the server announced. */
    private static float strength(IResizeableEntity sized) {
        if (sized instanceof Entity e && e.level().isClientSide()) return SyncedSettings.soundVolumeScaling();
        return (float) GulliverConfig.INSTANCE.general.soundVolumeScaling;
    }
}
