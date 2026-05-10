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
 * 1.6.4 KeyInputHandler — three keybinds. Defaults updated 4(338):
 *   key.UPSIZE   = U → /doublesize, OR /entitydoublesize <id>
 *   key.DOWNSIZE = I → /halfsize,  OR /entityhalfsize <id>
 *   key.SHOULDER = V → /shoulderentity
 *
 * Both self-resize AND entity-target resize are CREATIVE-ONLY. 1.6.4
 * assumed creative-or-cheat but Forge didn't gate either; in this port
 * we gate both explicitly. Survival players can't hotkey their own size
 * (would let them duck under blocks for free) and they can't hotkey-
 * resize random mobs they're looking at (server still op-gates via the
 * /entity*size command, but creative-only on the client side keeps
 * the keybind from firing for survival players at all).
 *
 * Entity-target trigger item is STICK (4(357), changed from FEATHER):
 * stick + entity target → /entitydoublesize | /entityhalfsize.
 *
 * GLFW key codes (used in 26.x):
 *   U → GLFW_KEY_U = 85
 *   I → GLFW_KEY_I = 73
 *   V → GLFW_KEY_V = 86
 */
public final class KeyInputHandler {
    private KeyInputHandler() {}

    // KeyMapping.Category.register is private at runtime; use the built-in
    // MISC category so we don't trip an IllegalAccessError on init. Custom
    // category labelling can come back via Mixin @Invoker on register() if
    // worth the wiring later.
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.MISC;
    // GLFW key codes inlined to avoid pulling in the LWJGL3 GLFW class:
    //   U = 85, I = 73, V = 86. Defaults moved off R/F to U/I in 4(338);
    //   R/F collide with vanilla swap-hands (F) and reload-resourcepack (F3+R).
    public static final KeyMapping UPSIZE = new KeyMapping(
            "key.gulliver.upsize", InputConstants.Type.KEYSYM, 85, CATEGORY);
    public static final KeyMapping DOWNSIZE = new KeyMapping(
            "key.gulliver.downsize", InputConstants.Type.KEYSYM, 73, CATEGORY);
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
        // Both self-resize and entity-target resize are creative-only.
        if (!player.getAbilities().instabuild) return;
        ItemStack mainHand = player.getMainHandItem();
        // Stick + targeted entity → resize the entity (op-gated server-side).
        if (mainHand.is(Items.STICK) && client.hitResult instanceof EntityHitResult ehr) {
            Entity target = ehr.getEntity();
            sendCommand(player, (upsize ? "entitydoublesize " : "entityhalfsize ") + target.getId());
            return;
        }
        sendCommand(player, upsize ? "doublesize" : "halfsize");
    }

    private static void sendCommand(LocalPlayer player, String cmd) {
        player.connection.sendCommand(cmd);
    }
}
