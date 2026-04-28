package gulliver.common;

/**
 * 1.6.4 dye-resizing: drinking cyan dye applies the Tiny effect, drinking
 * purple dye applies Huge. Same shape for red/brown mushroom.
 *
 * The client-side trigger lives in MixinMultiPlayerGameModeUseItem
 * (intercepts the RMB on these items and sends a ConsumeResizingItem
 * packet to the server). The server-side effect application lives in
 * PacketHandlers (handler for ConsumeResizingItem). This file is kept
 * as a placeholder so the existing GulliverFabric.onInitialize call
 * to DyeResizing.registerCommon() doesn't break — registerCommon is
 * now a no-op since all logic moved to the packet path.
 */
public final class DyeResizing {
    private DyeResizing() {}

    public static void registerCommon() {
        // No-op — see class javadoc.
    }
}
