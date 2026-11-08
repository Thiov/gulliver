package gulliver.neoforge;

import gulliver.network.GulliverPayload;
import net.minecraft.client.Minecraft;
//#if MC >= 1.21.2
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
//#else
//$$ import net.neoforged.neoforge.network.PacketDistributor;
//#endif

/** Client-only half, kept apart so the dedicated server never loads it. */
final class NeoForgeClientNetworking {
    private NeoForgeClientNetworking() {}

    /** False on servers without Gulliver: NeoForge would throw on the send. */
    static boolean sendToServer(GulliverPayload payload) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null || !connection.hasChannel(payload)) return false;
        //#if MC >= 1.21.2
        ClientPacketDistributor.sendToServer(payload);
        //#else
        //$$ PacketDistributor.sendToServer(payload);
        //#endif
        return true;
    }
}
