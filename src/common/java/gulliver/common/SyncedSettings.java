package gulliver.common;

/**
 * What a client knows about the server's settings: the size range every
 * creature lives in and whether dye/mushroom resizing is on. The server
 * sends them on join and after every reload (Payloads.ServerSettings).
 *
 * Sizes on the wire are already clamped by the server, so the client only
 * uses the range as a sanity net; it never falls back to its own config,
 * which is what used to squash a 32x giant on a client with default
 * settings. Until the server has spoken, the full 1/32..32 range applies.
 * Servers without Gulliver never send entity sizes at all, and the dye
 * flag only matters where the server understands the resize packet.
 */
public final class SyncedSettings {
    private SyncedSettings() {}

    private static volatile boolean dyeResizing = true;
    private static volatile float minSize = GulliverConfig.MIN_SIZE;
    private static volatile float maxSize = GulliverConfig.MAX_SIZE;
    /** Negative until a server has spoken: then the local config's value applies. */
    private static volatile float soundVolumeScaling = -1.0F;
    /** Whether the server lets tinies grow and use wings (off on servers that don't know them). */
    private static volatile boolean tinyWings = false;
    private static volatile float wingsMaxSize = 0.3F;
    private static volatile float wingStamina = 1.0F;

    /** Whether small blocks are solid for tinies (off where the server doesn't say so). */
    private static volatile boolean smallBlockCollision = false;

    public static boolean smallBlockCollision() {
        return smallBlockCollision;
    }

    public static boolean tinyWings() {
        return tinyWings;
    }

    public static float wingsMaxSize() {
        return wingsMaxSize;
    }

    public static float wingStamina() {
        return wingStamina;
    }

    public static boolean dyeResizing() {
        return dyeResizing;
    }

    public static float minSize() {
        return minSize;
    }

    public static float maxSize() {
        return maxSize;
    }

    /**
     * How strongly size changes loudness (general.soundVolumeScaling). The
     * server scales what it sends out; the client uses the server's value
     * for the sounds it plays by itself (its own steps, the resize chime),
     * so a player sounds the same to themselves as to everyone else.
     */
    public static float soundVolumeScaling() {
        float synced = soundVolumeScaling;
        return synced >= 0.0F ? synced : (float) GulliverConfig.INSTANCE.general.soundVolumeScaling;
    }

    /** Client thread: the server's settings arrived. */
    public static void apply(boolean dye, float min, float max, float volumeScaling,
                             boolean wings, float wingsMax, float stamina, boolean smallBlocks) {
        smallBlockCollision = smallBlocks;
        tinyWings = wings;
        wingsMaxSize = wingsMax > 0.0F ? wingsMax : 0.3F;
        wingStamina = stamina > 0.0F && !Float.isInfinite(stamina) ? stamina : 1.0F;
        if (!(volumeScaling >= 0.0F)) volumeScaling = 1.0F;
        soundVolumeScaling = Math.min(volumeScaling, (float) GulliverConfig.MAX_SOUND_SCALING);
        if (!(min >= GulliverConfig.MIN_SIZE)) min = GulliverConfig.MIN_SIZE;
        if (!(max <= GulliverConfig.MAX_SIZE)) max = GulliverConfig.MAX_SIZE;
        if (max < min) max = min;
        dyeResizing = dye;
        minSize = min;
        maxSize = max;
        // Entities cache their limits against this counter.
        GulliverConfig.bump();
    }
}
