package gulliver.fabric;

import com.terraformersmc.modmenu.api.ModMenuApi;
import gulliver.Gulliver;
import gulliver.client.ClientSelfTest;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

/** Self-test only, and only loaded when Mod Menu is installed. */
final class ModMenuCheck {
    private ModMenuCheck() {}

    static void install() {
        ClientSelfTest.loaderConfigScreen = parent -> {
            for (EntrypointContainer<ModMenuApi> c
                    : FabricLoader.getInstance().getEntrypointContainers("modmenu", ModMenuApi.class)) {
                if (Gulliver.MOD_ID.equals(c.getProvider().getMetadata().getId())) {
                    return c.getEntrypoint().getModConfigScreenFactory().create(parent);
                }
            }
            return null;
        };
    }
}
