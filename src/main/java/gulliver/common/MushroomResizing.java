package gulliver.common;

/**
 * 1.6.4 mushroom-eat resizing: red mushroom -> Tiny, brown mushroom ->
 * Huge. Logic is now part of the ConsumeResizingItem packet flow
 * (MixinMultiPlayerGameModeUseItem on client + PacketHandlers
 * receiver on server). This class is a placeholder so existing init
 * calls don't break.
 */
public final class MushroomResizing {
    private MushroomResizing() {}

    public static void registerCommon() {
        // No-op — see class javadoc.
    }
}
