package gulliver;

import gulliver.common.GulliverConfig;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GulliverFabric implements ModInitializer {
    public static final String MOD_ID = "gulliver";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Gulliver (Fabric) initializing");
        GulliverConfig.load();
    }
}
