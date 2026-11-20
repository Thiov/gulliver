package gulliver.client;

import gulliver.network.Payloads;
import gulliver.platform.Services;
import net.minecraft.client.Minecraft;

/**
 * Client side of starter sizes (#32): the server's offer is kept until the
 * world is actually on screen (no loading screen, no other screen open,
 * a moment after joining so the world is visible behind the picker), then
 * the picker opens. An offer that arrives while the picker is open (the
 * server asking again after a config reload) replaces it.
 */
public final class StarterSizeClient {
    private StarterSizeClient() {}

    /** Ticks the world has to be on screen before the picker opens. */
    private static final int SETTLE_TICKS = 20;

    private static Payloads.StarterSizeOffer pending;
    private static int settled;

    /** Client thread: the server wants this player to pick a size. */
    public static void offer(Payloads.StarterSizeOffer offer) {
        if (offer.sizes().isEmpty() && !offer.allowCustom()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof StarterSizeScreen) {
            mc.gui.setScreen(new StarterSizeScreen(offer));
            return;
        }
        pending = offer;
        settled = 0;
    }

    /** End of every client tick. */
    public static void tick(Minecraft mc) {
        if (pending == null) return;
        if (mc.player == null || mc.level == null) {
            // Left the world before the picker could open: the server asks again next time.
            pending = null;
            return;
        }
        if (mc.gui.screen() != null) {
            settled = 0;
            return;
        }
        if (++settled < SETTLE_TICKS) return;
        Payloads.StarterSizeOffer offer = pending;
        pending = null;
        mc.gui.setScreen(new StarterSizeScreen(offer));
    }

    /** Forgets an offer not shown yet (scripted tours). */
    public static void dismiss() {
        pending = null;
    }

    static void choose(float size) {
        Services.platform().sendToServer(new Payloads.StarterSizeChoice(size, false));
    }

    static void later() {
        Services.platform().sendToServer(new Payloads.StarterSizeChoice(0.0F, true));
    }
}
