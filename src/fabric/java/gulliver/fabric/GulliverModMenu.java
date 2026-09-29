package gulliver.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import gulliver.client.GulliverConfigScreen;

/** Mod Menu's settings button (only loaded when Mod Menu is installed). */
public final class GulliverModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return GulliverConfigScreen::new;
    }
}
