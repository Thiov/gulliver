package gulliver.client;

import com.mojang.blaze3d.platform.InputConstants;
import gulliver.network.Payloads;
import gulliver.platform.Services;
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
 * G (held) blows a breath gust, see BreathGust.
 *
 * Default keys U (embiggen), I (ensmallen), V (shoulder) and G come from
 * InputConstants, never as raw numbers: the codes behind them changed when
 * 26.3 moved from GLFW to SDL (U was 85, is 24).
 */
public final class KeyInputHandler {
    private KeyInputHandler() {}

    //#if MC >= 1.21.9
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.MISC;
    //#else
    //$$ private static final String CATEGORY = "key.categories.gulliver";
    //#endif
    // Defaults moved off R/F to U/I in 4(338); R/F collide with vanilla
    // swap-hands (F) and reload-resourcepack (F3+R).
    public static final KeyMapping UPSIZE = new KeyMapping(
            "key.gulliver.upsize", InputConstants.Type.KEYSYM, InputConstants.KEY_U, CATEGORY);
    public static final KeyMapping DOWNSIZE = new KeyMapping(
            "key.gulliver.downsize", InputConstants.Type.KEYSYM, InputConstants.KEY_I, CATEGORY);
    public static final KeyMapping SHOULDER = new KeyMapping(
            "key.gulliver.shoulder", InputConstants.Type.KEYSYM, InputConstants.KEY_V, CATEGORY);

    /** Hold to blow a breath gust (BreathGust); G is free in vanilla. */
    public static final KeyMapping BREATH = new KeyMapping(
            "key.gulliver.breath", InputConstants.Type.KEYSYM, InputConstants.KEY_G, CATEGORY);

    /** Every key mapping the loader must register. */
    public static final KeyMapping[] ALL = { UPSIZE, DOWNSIZE, SHOULDER, BREATH };

    /** Whether the server was last told the breath key is down. */
    private static boolean blowing;
    private static int breathRepeat;

    /** End of every client tick. */
    public static void tick(Minecraft client) {
        if (client.player == null || client.level == null) {
            blowing = false;
            return;
        }
        breath(client);
        if (client.gui.screen() != null) return; // don't fire while a GUI is open
        while (UPSIZE.consumeClick()) dispatchSize(client, true);
        while (DOWNSIZE.consumeClick()) dispatchSize(client, false);
        while (SHOULDER.consumeClick()) {
            Services.platform().sendToServer(new Payloads.CarryAction(Payloads.CarryAction.CYCLE));
        }
    }

    /**
     * The breath key is held, not clicked: tell the server when it goes
     * down, every ten ticks while it stays down (the server lets a gust
     * lapse without them) and when it comes up or a screen opens.
     */
    private static void breath(Minecraft client) {
        boolean held = BREATH.isDown() && client.gui.screen() == null && !client.player.isSpectator();
        if (held) {
            if (!blowing || ++breathRepeat >= 10) {
                Services.platform().sendToServer(new Payloads.CarryAction(Payloads.CarryAction.BREATH));
                breathRepeat = 0;
            }
            blowing = true;
        } else if (blowing) {
            Services.platform().sendToServer(new Payloads.CarryAction(Payloads.CarryAction.BREATH_STOP));
            blowing = false;
        }
    }

    private static void dispatchSize(Minecraft client, boolean upsize) {
        LocalPlayer player = client.player;
        if (player == null) return;
        // Both self-resize and entity-target resize are creative-only.
        if (!player.getAbilities().instabuild) return;
        ItemStack mainHand = player.getMainHandItem();
        // Stick = entity-resize wand. While holding it, the keybinds
        // ONLY target the entity under the crosshair — a missed aim
        // does nothing instead of falling through to self-resize
        // (accidentally shrinking yourself mid-aim was infuriating).
        if (mainHand.is(Items.STICK)) {
            if (client.hitResult instanceof EntityHitResult ehr) {
                Entity target = ehr.getEntity();
                sendCommand(player, (upsize ? "entitydoublesize " : "entityhalfsize ") + target.getId());
            }
            return;
        }
        sendCommand(player, upsize ? "doublesize" : "halfsize");
    }

    private static void sendCommand(LocalPlayer player, String cmd) {
        player.connection.sendCommand(cmd);
    }
}
