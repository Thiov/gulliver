package gulliver.forge;

import gulliver.network.GulliverPayload;
import net.minecraft.client.Minecraft;
//#if MC >= 1.20.5
import net.minecraftforge.network.PacketDistributor;
//#endif

/** Client-only half, kept apart so the dedicated server never loads it. */
final class ForgeClientNetworking {
    private ForgeClientNetworking() {}

    /** False on servers without Gulliver. */
    static boolean sendToServer(GulliverPayload payload) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null || !ForgeNetworking.CHANNEL.isRemotePresent(connection.getConnection())) return false;
        //#if MC >= 1.20.5
        ForgeNetworking.CHANNEL.send(payload, PacketDistributor.SERVER.noArg());
        //#else
        //$$ ForgeNetworking.CHANNEL.sendToServer(payload);
        //#endif
        return true;
    }
}
