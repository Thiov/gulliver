package gulliver.fabric;

import gulliver.client.GulliverClient;
import gulliver.client.KeyInputHandler;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.KeyMapping;
//#if MC >= 26.1
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
//#else
//$$ import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
//#endif

public final class GulliverFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        GulliverClient.init();
        FabricNetworking.registerClient();
        for (KeyMapping key : KeyInputHandler.ALL) {
            //#if MC >= 26.1
            KeyMappingHelper.registerKeyMapping(key);
            //#else
            //$$ KeyBindingHelper.registerKeyBinding(key);
            //#endif
        }
    }
}
