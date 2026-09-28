package gulliver.forge;

import gulliver.client.GulliverClient;
import gulliver.client.KeyInputHandler;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;

final class GulliverForgeClient {
    private GulliverForgeClient() {}

    static void init(BusGroup modBus) {
        GulliverClient.init();
        RegisterKeyMappingsEvent.BUS.addListener(e -> {
            for (KeyMapping key : KeyInputHandler.ALL) e.register(key);
        });
    }
}
