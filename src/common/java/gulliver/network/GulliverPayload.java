package gulliver.network;

//#if MC >= 1.20.5
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//#endif
import net.minecraft.network.FriendlyByteBuf;

/** Marker for Gulliver's custom packets; one record per packet in {@link Payloads}. */
public interface GulliverPayload
        //#if MC >= 1.20.5
        extends CustomPacketPayload
        //#endif
{
    void write(FriendlyByteBuf buf);
}
