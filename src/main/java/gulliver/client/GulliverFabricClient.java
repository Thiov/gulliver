package gulliver.client;

import net.fabricmc.api.ClientModInitializer;

public class GulliverFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPacketHandlers.register();
    }
}
