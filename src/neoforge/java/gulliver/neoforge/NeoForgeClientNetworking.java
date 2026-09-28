package gulliver.neoforge;

import gulliver.network.GulliverPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Client-only half, kept apart so the dedicated server never loads it. */
final class NeoForgeClientNetworking {
    private NeoForgeClientNetworking() {}

    static void sendToServer(GulliverPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }
}
