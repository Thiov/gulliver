package gulliver.neoforge;

import gulliver.client.GulliverClient;
import gulliver.client.KeyInputHandler;
import net.minecraft.client.KeyMapping;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

final class GulliverNeoForgeClient {
    private GulliverNeoForgeClient() {}

    static void init(IEventBus modBus) {
        GulliverClient.init();
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            for (KeyMapping key : KeyInputHandler.ALL) e.register(key);
        });
    }
}
