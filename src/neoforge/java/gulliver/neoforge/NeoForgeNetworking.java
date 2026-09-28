package gulliver.neoforge;

import gulliver.network.GulliverNetwork;
import gulliver.network.GulliverPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers Gulliver's packet table with NeoForge's payload system. */
final class NeoForgeNetworking {
    private NeoForgeNetworking() {}

    static void register(RegisterPayloadHandlersEvent event) {
        // optional(): vanilla clients may still join a Gulliver server.
        PayloadRegistrar registrar = event.registrar("1").optional();
        for (GulliverNetwork.Spec<?> spec : GulliverNetwork.CLIENTBOUND) clientbound(registrar, spec);
        for (GulliverNetwork.Spec<?> spec : GulliverNetwork.SERVERBOUND) serverbound(registrar, spec);
    }

    private static <T extends GulliverPayload> void clientbound(PayloadRegistrar registrar, GulliverNetwork.Spec<T> spec) {
        registrar.playToClient(spec.payloadType(), spec.codec(),
                (payload, ctx) -> GulliverNetwork.handleClientbound(payload));
    }

    private static <T extends GulliverPayload> void serverbound(PayloadRegistrar registrar, GulliverNetwork.Spec<T> spec) {
        registrar.playToServer(spec.payloadType(), spec.codec(),
                (payload, ctx) -> GulliverNetwork.handleServerbound(payload, (ServerPlayer) ctx.player()));
    }
}
