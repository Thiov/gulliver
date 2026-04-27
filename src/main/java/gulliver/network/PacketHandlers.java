package gulliver.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class PacketHandlers {
    private PacketHandlers() {}

    public static void registerCommon() {
        var s2c = PayloadTypeRegistry.clientboundPlay();
        s2c.register(Payloads.EntitySize.TYPE, Payloads.EntitySize.CODEC.cast());
        s2c.register(Payloads.AttachEntitySpecial.TYPE, Payloads.AttachEntitySpecial.CODEC.cast());
    }
}
