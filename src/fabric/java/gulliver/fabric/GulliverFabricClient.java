package gulliver.fabric;

import gulliver.client.GulliverClient;
import gulliver.client.KeyInputHandler;
import gulliver.client.SizeTooltips;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
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
        if (gulliver.debug.SelfTest.ENABLED && net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("modmenu")) {
            ModMenuCheck.install();
        }
        //#if MC >= 1.21
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> SizeTooltips.add(stack, lines));
        //#else
        //$$ ItemTooltipCallback.EVENT.register((stack, flag, lines) -> SizeTooltips.add(stack, lines));
        //#endif
        for (KeyMapping key : KeyInputHandler.ALL) {
            //#if MC >= 26.1
            KeyMappingHelper.registerKeyMapping(key);
            //#else
            //$$ KeyBindingHelper.registerKeyBinding(key);
            //#endif
        }
    }
}
