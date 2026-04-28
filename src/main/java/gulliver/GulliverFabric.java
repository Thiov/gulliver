package gulliver;

import gulliver.command.GulliverCommands;
import gulliver.common.GulliverConfig;
import gulliver.network.PacketHandlers;
import gulliver.network.SizeSync;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GulliverFabric implements ModInitializer {
    public static final String MOD_ID = "gulliver";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Gulliver (Fabric) initializing");
        GulliverConfig.load();
        PacketHandlers.registerCommon();
        SizeSync.registerCommon();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                GulliverCommands.register(dispatcher));
    }
}
