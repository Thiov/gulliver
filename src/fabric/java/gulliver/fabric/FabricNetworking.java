package gulliver.fabric;

import gulliver.network.GulliverNetwork;
import gulliver.network.GulliverPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
//#if MC >= 1.20.5
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
//#else
//$$ import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
//$$ import net.minecraft.network.FriendlyByteBuf;
//$$ import net.minecraft.server.level.ServerPlayer;
//#endif

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

    //#if MC >= 1.20.5
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
    //#else
    //$$ // Before 1.20.5: raw channels keyed by id, payloads written to a buffer.
    //$$ private static <T extends GulliverPayload> void registerClientbound(GulliverNetwork.Spec<T> spec) {
    //$$ }
    //$$
    //$$ private static <T extends GulliverPayload> void registerServerbound(GulliverNetwork.Spec<T> spec) {
    //$$     ServerPlayNetworking.registerGlobalReceiver(spec.id(), (server, player, handler, buf, sender) -> {
    //$$         T payload = spec.reader().apply(buf);
    //$$         server.execute(() -> GulliverNetwork.handleServerbound(payload, player));
    //$$     });
    //$$ }
    //$$
    //$$ private static <T extends GulliverPayload> void registerClientReceiver(GulliverNetwork.Spec<T> spec) {
    //$$     ClientPlayNetworking.registerGlobalReceiver(spec.id(), (client, handler, buf, sender) -> {
    //$$         T payload = spec.reader().apply(buf);
    //$$         client.execute(() -> GulliverNetwork.handleClientbound(payload));
    //$$     });
    //$$ }
    //$$
    //$$ static void send(ServerPlayer player, GulliverPayload payload) {
    //$$     var id = GulliverNetwork.specFor(payload).id();
    //$$     if (!ServerPlayNetworking.canSend(player, id)) return;
    //$$     FriendlyByteBuf buf = PacketByteBufs.create();
    //$$     payload.write(buf);
    //$$     ServerPlayNetworking.send(player, id, buf);
    //$$ }
    //$$
    //$$ static boolean sendToServer(GulliverPayload payload) {
    //$$     var id = GulliverNetwork.specFor(payload).id();
    //$$     if (!ClientPlayNetworking.canSend(id)) return false;
    //$$     FriendlyByteBuf buf = PacketByteBufs.create();
    //$$     payload.write(buf);
    //$$     ClientPlayNetworking.send(id, buf);
    //$$     return true;
    //$$ }
    //#endif
}
