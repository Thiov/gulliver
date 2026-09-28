package gulliver.forge;

import gulliver.Gulliver;
import gulliver.network.GulliverNetwork;
import gulliver.network.GulliverPayload;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** Gulliver's packet table as one Forge 1.20.1 SimpleChannel. */
final class ForgeNetworking {
    private ForgeNetworking() {}

    private static final String VERSION = "1";

    static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(Gulliver.id("main"),
            () -> VERSION, NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));

    private static int index;

    static void init() {
        for (GulliverNetwork.Spec<?> spec : GulliverNetwork.CLIENTBOUND) register(spec, NetworkDirection.PLAY_TO_CLIENT);
        for (GulliverNetwork.Spec<?> spec : GulliverNetwork.SERVERBOUND) register(spec, NetworkDirection.PLAY_TO_SERVER);
    }

    private static <T extends GulliverPayload> void register(GulliverNetwork.Spec<T> spec, NetworkDirection dir) {
        CHANNEL.messageBuilder(spec.type(), index++, dir)
                .encoder((msg, buf) -> msg.write(buf))
                .decoder(buf -> spec.reader().apply(buf))
                .consumerMainThread((msg, ctx) -> {
                    if (dir == NetworkDirection.PLAY_TO_SERVER) {
                        GulliverNetwork.handleServerbound(msg, ctx.get().getSender());
                    } else {
                        GulliverNetwork.handleClientbound(msg);
                    }
                })
                .add();
    }
}
