package gulliver.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
//#if MC >= 1.20.5
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//#endif

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Loader-neutral packet table. Each loader walks {@link #CLIENTBOUND} and
 * {@link #SERVERBOUND} to register its channels, then routes received
 * packets to {@link #handleServerbound} (server thread) or to the client
 * handler installed by the client entrypoint (client thread).
 */
public final class GulliverNetwork {
    private GulliverNetwork() {}

    public record Spec<T extends GulliverPayload>(Identifier id, Class<T> type,
                                                   Function<FriendlyByteBuf, T> reader) {
        public void write(GulliverPayload payload, FriendlyByteBuf buf) {
            payload.write(buf);
        }

        //#if MC >= 1.20.5
        public CustomPacketPayload.Type<T> payloadType() {
            return new CustomPacketPayload.Type<>(id);
        }

        public StreamCodec<FriendlyByteBuf, T> codec() {
            return StreamCodec.ofMember((T p, FriendlyByteBuf b) -> p.write(b), reader::apply);
        }
        //#endif
    }

    public static final List<Spec<?>> CLIENTBOUND = List.of(
            new Spec<>(Payloads.EntitySize.ID, Payloads.EntitySize.class, Payloads.EntitySize::read),
            new Spec<>(Payloads.AttachEntitySpecial.ID, Payloads.AttachEntitySpecial.class, Payloads.AttachEntitySpecial::read),
            new Spec<>(Payloads.GroundShock.ID, Payloads.GroundShock.class, Payloads.GroundShock::read),
            new Spec<>(Payloads.HookAnchor.ID, Payloads.HookAnchor.class, Payloads.HookAnchor::read));

    public static final List<Spec<?>> SERVERBOUND = List.of(
            new Spec<>(Payloads.ConsumeResizingItem.ID, Payloads.ConsumeResizingItem.class, Payloads.ConsumeResizingItem::read),
            new Spec<>(Payloads.CarryAction.ID, Payloads.CarryAction.class, Payloads.CarryAction::read));

    /** The table entry for a payload (by its record class). */
    public static Spec<?> specFor(GulliverPayload payload) {
        for (Spec<?> s : CLIENTBOUND) if (s.type() == payload.getClass()) return s;
        for (Spec<?> s : SERVERBOUND) if (s.type() == payload.getClass()) return s;
        throw new IllegalArgumentException("Unregistered Gulliver payload " + payload.getClass());
    }

    /** Installed by the client entrypoint; runs on the client thread. */
    private static Consumer<GulliverPayload> clientHandler = p -> {};

    public static void setClientHandler(Consumer<GulliverPayload> handler) {
        clientHandler = handler;
    }

    public static void handleClientbound(GulliverPayload payload) {
        clientHandler.accept(payload);
    }

    /** Runs on the server thread. */
    public static void handleServerbound(GulliverPayload payload, ServerPlayer player) {
        if (player == null) return;
        if (payload instanceof Payloads.ConsumeResizingItem p) {
            PacketHandlers.onConsumeResizingItem(player, p);
        } else if (payload instanceof Payloads.CarryAction p) {
            PacketHandlers.onCarryAction(player, p);
        }
    }
}
