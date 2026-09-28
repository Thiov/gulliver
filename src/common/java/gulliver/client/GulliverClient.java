package gulliver.client;

import gulliver.network.GulliverNetwork;
import net.minecraft.client.Minecraft;

/** Loader-independent client setup; called by each loader's client entrypoint. */
public final class GulliverClient {
    private GulliverClient() {}

    public static void init() {
        GulliverNetwork.setClientHandler(ClientPacketHandlers::handle);
    }

    /** End of every client tick (MixinMinecraftTick). */
    public static void onClientTick(Minecraft mc) {
        KeyInputHandler.tick(mc);
        TremorHandler.tick(mc);
        ClientSelfTest.tick(mc);
    }
}
