package gulliver.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;

/**
 * 1.6.4 KeyInputHandler — three keybinds:
 *   key.UPSIZE   = LWJGL2 19 (R) → /doublesize, OR /entitydoublesize <id>
 *                  if holding feather + targeting an entity
 *   key.DOWNSIZE = LWJGL2 33 (F) → /halfsize, OR /entityhalfsize <id>
 *                  if holding feather + targeting an entity
 *   key.SHOULDER = LWJGL2 47 (V) → /shoulderentity
 *
 * GLFW key codes (used in 26.x):
 *   R → GLFW_KEY_R = 82
 *   F → GLFW_KEY_F = 70
 *   V → GLFW_KEY_V = 86
 *
 * Feather + entity target dispatches the entity-targeted variant; bare
 * (or other item) fires the self-targeted variant. Mirrors 1.6.4
 * KeyInputHandler.keyDown logic exactly.
 */
public final class KeyInputHandler {
    private KeyInputHandler() {}

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register("key.categories.gulliver");
    // GLFW key codes inlined to avoid pulling in the LWJGL3 GLFW class:
    //   R = 82, F = 70, V = 86  (matches 1.6.4 LWJGL2 codes 19/33/47 by character).
    public static final KeyMapping UPSIZE = new KeyMapping(
            "key.gulliver.upsize", InputConstants.Type.KEYSYM, 82, CATEGORY);
    public static final KeyMapping DOWNSIZE = new KeyMapping(
            "key.gulliver.downsize", InputConstants.Type.KEYSYM, 70, CATEGORY);
    public static final KeyMapping SHOULDER = new KeyMapping(
            "key.gulliver.shoulder", InputConstants.Type.KEYSYM, 86, CATEGORY);

    public static void register() {
        KeyMappingHelper.registerKeyMapping(UPSIZE);
        KeyMappingHelper.registerKeyMapping(DOWNSIZE);
        KeyMappingHelper.registerKeyMapping(SHOULDER);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) return;
            if (client.screen != null) return; // don't fire while a GUI is open
            while (UPSIZE.consumeClick()) dispatchSize(client, true);
            while (DOWNSIZE.consumeClick()) dispatchSize(client, false);
            while (SHOULDER.consumeClick()) sendCommand(client.player, "shoulderentity");
        });
    }

    private static void dispatchSize(Minecraft client, boolean upsize) {
        LocalPlayer player = client.player;
        if (player == null) return;
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.is(Items.FEATHER) && client.hitResult instanceof EntityHitResult ehr) {
            Entity target = ehr.getEntity();
            sendCommand(player, (upsize ? "entitydoublesize " : "entityhalfsize ") + target.getId());
        } else {
            sendCommand(player, upsize ? "doublesize" : "halfsize");
        }
    }

    private static void sendCommand(LocalPlayer player, String cmd) {
        player.connection.sendCommand(cmd);
    }
}
