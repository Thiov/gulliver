package gulliver.neoforge;

import gulliver.client.GulliverClient;
import gulliver.client.GulliverConfigScreen;
import gulliver.client.KeyInputHandler;
import net.minecraft.client.KeyMapping;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

final class GulliverNeoForgeClient {
    private GulliverNeoForgeClient() {}

    static void init(IEventBus modBus, ModContainer container) {
        GulliverClient.init();
        // The mod list's "Config" button.
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (mod, parent) -> new GulliverConfigScreen(parent));
        if (gulliver.debug.SelfTest.ENABLED) {
            gulliver.client.ClientSelfTest.loaderConfigScreen = parent -> container
                    .getCustomExtension(IConfigScreenFactory.class)
                    .map(f -> f.createScreen(container, parent)).orElse(null);
        }
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            for (KeyMapping key : KeyInputHandler.ALL) e.register(key);
        });
    }
}
