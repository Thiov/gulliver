package gulliver.network;

import gulliver.GulliverFabric;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Fabric custom payloads ported from the 1.6.4 mod's two custom packets:
 *   Packet171EntitySize          (channel id 171) → EntitySize
 *   Packet172AttachEntitySpecial (channel id 172) → AttachEntitySpecial
 *
 * Both are server→client (clientbound). The 1.6.4 wire formats were
 * fixed-width int/float/byte; preserved exactly here so semantics match.
 */
public final class Payloads {
    private Payloads() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> mkType(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(GulliverFabric.MOD_ID, path));
    }

    /**
     * Packet171EntitySize equivalent: tells client an entity's size has changed.
     * sizeMult is the FULL composed multiplier (base × potion × items) — the
     * server is the source of truth. The client stores it as the base
     * multiplier so getSizeMultiplier() returns the same value (since clients
     * don't independently apply potion / item modifiers).
     */
    public record EntitySize(int entityId, float sizeMult) implements CustomPacketPayload {
        public static final Type<EntitySize> TYPE = mkType("entity_size");
        public static final StreamCodec<FriendlyByteBuf, EntitySize> CODEC = StreamCodec.ofMember(
                (p, b) -> { b.writeVarInt(p.entityId); b.writeFloat(p.sizeMult); },
                b -> new EntitySize(b.readVarInt(), b.readFloat()));
        public Type<EntitySize> type() { return TYPE; }
    }

    /**
     * Packet172AttachEntitySpecial equivalent: shoulder-attach (or detach) an
     * entity onto another. attachmentType is a ShoulderHelper.SLOT_*
     * constant: 0 = detach, 1 = hand, 2 = right shoulder, 3 = left
     * shoulder. entityId may be -1 on a detach whose passenger could
     * not be resolved (stale slot) — receivers must handle both ids
     * failing to resolve.
     * Used by the shoulder-entity feature.
     */
    public record AttachEntitySpecial(int entityId, int vehicleEntityId, byte attachmentType) implements CustomPacketPayload {
        public static final Type<AttachEntitySpecial> TYPE = mkType("attach_entity_special");
        public static final StreamCodec<FriendlyByteBuf, AttachEntitySpecial> CODEC = StreamCodec.ofMember(
                (p, b) -> { b.writeVarInt(p.entityId); b.writeVarInt(p.vehicleEntityId); b.writeByte(p.attachmentType); },
                b -> new AttachEntitySpecial(b.readVarInt(), b.readVarInt(), b.readByte()));
        public Type<AttachEntitySpecial> type() { return TYPE; }
    }

    /**
     * S2C: a huge entity landed hard at (x, y, z). Clients that are
     * much smaller than sourceSize convert this into a screen quake
     * with distance falloff (TremorHandler.groundShock); strength is
     * the server's shockwave punch (0.3..1.6, from fall distance).
     */
    public record GroundShock(double x, double y, double z, float sourceSize, float strength)
            implements CustomPacketPayload {
        public static final Type<GroundShock> TYPE = mkType("ground_shock");
        public static final StreamCodec<FriendlyByteBuf, GroundShock> CODEC = StreamCodec.ofMember(
                (p, b) -> {
                    b.writeDouble(p.x); b.writeDouble(p.y); b.writeDouble(p.z);
                    b.writeFloat(p.sourceSize); b.writeFloat(p.strength);
                },
                b -> new GroundShock(b.readDouble(), b.readDouble(), b.readDouble(),
                        b.readFloat(), b.readFloat()));
        public Type<GroundShock> type() { return TYPE; }
    }

    /**
     * C2S packet: client tells server "I right-clicked with a resizing
     * item (cyan/purple dye, red/brown mushroom) in air". Vanilla item.use
     * returns PASS for these so the client never sends the standard
     * use-item packet — our packet bypasses that and triggers the
     * effect application server-side.
     *
     * mainHand: true if the active hand is MAIN, false for OFF.
     */
    public record ConsumeResizingItem(boolean mainHand) implements CustomPacketPayload {
        public static final Type<ConsumeResizingItem> TYPE = mkType("consume_resizing_item");
        public static final StreamCodec<FriendlyByteBuf, ConsumeResizingItem> CODEC = StreamCodec.ofMember(
                (p, b) -> b.writeBoolean(p.mainHand),
                b -> new ConsumeResizingItem(b.readBoolean()));
        public Type<ConsumeResizingItem> type() { return TYPE; }
    }
}
