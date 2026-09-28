package gulliver.fabric;

import gulliver.network.GulliverNetwork;
import gulliver.network.GulliverPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Registers Gulliver's packet table with Fabric's networking API. */
public final class FabricNetworking {
    private FabricNetworking() {}

    public static void registerCommon() {
        for (GulliverNetwork.Spec<?> spec : GulliverNetwork.CLIENTBOUND) registerClientbound(spec);
        for (GulliverNetwork.Spec<?> spec : GulliverNetwork.SERVERBOUND) registerServerbound(spec);
    }

    public static void registerClient() {
        for (GulliverNetwork.Spec<?> spec : GulliverNetwork.CLIENTBOUND) registerClientReceiver(spec);
    }

    private static <T extends GulliverPayload> void registerClientbound(GulliverNetwork.Spec<T> spec) {
        //#if MC >= 26.1
        PayloadTypeRegistry.clientboundPlay().register(spec.payloadType(), spec.codec());
        //#else
        //$$ PayloadTypeRegistry.playS2C().register(spec.payloadType(), spec.codec());
        //#endif
    }

    private static <T extends GulliverPayload> void registerServerbound(GulliverNetwork.Spec<T> spec) {
        //#if MC >= 26.1
        PayloadTypeRegistry.serverboundPlay().register(spec.payloadType(), spec.codec());
        //#else
        //$$ PayloadTypeRegistry.playC2S().register(spec.payloadType(), spec.codec());
        //#endif
        ServerPlayNetworking.registerGlobalReceiver(spec.payloadType(),
                (payload, ctx) -> GulliverNetwork.handleServerbound(payload, ctx.player()));
    }

    private static <T extends GulliverPayload> void registerClientReceiver(GulliverNetwork.Spec<T> spec) {
        ClientPlayNetworking.registerGlobalReceiver(spec.payloadType(),
                (payload, ctx) -> GulliverNetwork.handleClientbound(payload));
    }
}
