package gulliver.neoforge;

import gulliver.network.GulliverPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Client-only half, kept apart so the dedicated server never loads it. */
final class NeoForgeClientNetworking {
    private NeoForgeClientNetworking() {}

    /** False on servers without Gulliver: NeoForge would throw on the send. */
    static boolean sendToServer(GulliverPayload payload) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null || !connection.hasChannel(payload)) return false;
        ClientPacketDistributor.sendToServer(payload);
        return true;
    }
}
