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

    public static boolean dyeResizing() {
        return dyeResizing;
    }

    public static float minSize() {
        return minSize;
    }

    public static float maxSize() {
        return maxSize;
    }

    /** Client thread: the server's settings arrived. */
    public static void apply(boolean dye, float min, float max) {
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
