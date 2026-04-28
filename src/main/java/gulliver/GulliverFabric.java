package gulliver;

import gulliver.command.GulliverCommands;
import gulliver.common.DyeResizing;
import gulliver.common.GulliverConfig;
import gulliver.common.InteractEventHandler;
import gulliver.init.GulliverEffects;
import gulliver.init.GulliverPotions;
import gulliver.network.PacketHandlers;
import gulliver.network.SizeSync;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GulliverFabric implements ModInitializer {
    public static final String MOD_ID = "gulliver";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Gulliver (Fabric) initializing");
        GulliverConfig.load();
        GulliverEffects.init();
        GulliverPotions.init();
        PacketHandlers.registerCommon();
        SizeSync.registerCommon();
        InteractEventHandler.registerCommon();
        DyeResizing.registerCommon();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                GulliverCommands.register(dispatcher));
    }
}
