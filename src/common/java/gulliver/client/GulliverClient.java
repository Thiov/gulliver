package gulliver.client;

import gulliver.network.GulliverNetwork;
import net.minecraft.client.Minecraft;

/** Loader-independent client setup; called by each loader's client entrypoint. */
public final class GulliverClient {
    private GulliverClient() {}

    public static void init() {
        GulliverNetwork.setClientHandler(ClientPacketHandlers::handle);
        ClientSelfTest.startWatchdog();
    }

    /** End of every client tick (MixinMinecraftTick). */
    public static void onClientTick(Minecraft mc) {
        PickBoost.end(); // the crosshair pick completes within a frame; this only heals a stuck window
        KeyInputHandler.tick(mc);
        VisualSize.tick(mc);
        BreathFx.tick(mc);
        TremorHandler.tick(mc);
        ClientSelfTest.tick(mc);
    }
}
