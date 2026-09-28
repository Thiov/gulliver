package gulliver.forge;

import gulliver.Gulliver;
import gulliver.network.GulliverNetwork;
import gulliver.network.GulliverPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.payload.PayloadFlow;
import net.minecraftforge.network.payload.PayloadProtocol;

/** Gulliver's packet table as one Forge payload channel. */
final class ForgeNetworking {
    private ForgeNetworking() {}

    static final Channel<CustomPacketPayload> CHANNEL;

    static {
        PayloadProtocol<RegistryFriendlyByteBuf, CustomPacketPayload> play = ChannelBuilder
                .named(Gulliver.id("main"))
                .networkProtocolVersion(2) // bump whenever the packet table changes
                .optional()
                .payloadChannel()
                .play();
        PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> flow = play.clientbound();
        for (GulliverNetwork.Spec<?> spec : GulliverNetwork.CLIENTBOUND) flow = clientbound(flow, spec);
        flow = flow.serverbound();
        for (GulliverNetwork.Spec<?> spec : GulliverNetwork.SERVERBOUND) flow = serverbound(flow, spec);
        CHANNEL = flow.build();
    }

    /** Forces the channel to be built during mod construction. */
    static void init() {}

    private static <T extends GulliverPayload> PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> clientbound(
            PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> flow, GulliverNetwork.Spec<T> spec) {
        return flow.addMain(spec.payloadType(), spec.codec().<RegistryFriendlyByteBuf>cast(),
                (payload, ctx) -> GulliverNetwork.handleClientbound(payload));
    }

    private static <T extends GulliverPayload> PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> serverbound(
            PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> flow, GulliverNetwork.Spec<T> spec) {
        return flow.addMain(spec.payloadType(), spec.codec().<RegistryFriendlyByteBuf>cast(),
                (payload, ctx) -> GulliverNetwork.handleServerbound(payload, ctx.getSender()));
    }
}
