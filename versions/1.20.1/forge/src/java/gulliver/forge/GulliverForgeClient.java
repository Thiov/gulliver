package gulliver.forge;

import gulliver.client.GulliverClient;
import gulliver.client.GulliverConfigScreen;
import gulliver.client.KeyInputHandler;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.eventbus.api.IEventBus;

final class GulliverForgeClient {
    private GulliverForgeClient() {}

    static void init(IEventBus modBus) {
        GulliverClient.init();
        // The mod list's "Config" button.
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new GulliverConfigScreen(parent)));
        if (gulliver.debug.SelfTest.ENABLED) {
            var container = ModLoadingContext.get().getActiveContainer();
            gulliver.client.ClientSelfTest.loaderConfigScreen = parent -> container
                    .getCustomExtension(ConfigScreenHandler.ConfigScreenFactory.class)
                    .map(f -> f.screenFunction().apply(net.minecraft.client.Minecraft.getInstance(), parent))
                    .orElse(null);
        }
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            for (KeyMapping key : KeyInputHandler.ALL) e.register(key);
        });
    }
}
