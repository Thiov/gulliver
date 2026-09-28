package gulliver.forge;

import gulliver.client.GulliverClient;
import gulliver.client.KeyInputHandler;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.IEventBus;

final class GulliverForgeClient {
    private GulliverForgeClient() {}

    static void init(IEventBus modBus) {
        GulliverClient.init();
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            for (KeyMapping key : KeyInputHandler.ALL) e.register(key);
        });
    }
}
