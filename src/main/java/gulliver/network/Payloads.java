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
     * entity onto another. attachmentType: 0 = drop / detach, 1 = attach.
     * Used by the shoulder-entity feature.
     */
    public record AttachEntitySpecial(int entityId, int vehicleEntityId, byte attachmentType) implements CustomPacketPayload {
        public static final Type<AttachEntitySpecial> TYPE = mkType("attach_entity_special");
        public static final StreamCodec<FriendlyByteBuf, AttachEntitySpecial> CODEC = StreamCodec.ofMember(
                (p, b) -> { b.writeVarInt(p.entityId); b.writeVarInt(p.vehicleEntityId); b.writeByte(p.attachmentType); },
                b -> new AttachEntitySpecial(b.readVarInt(), b.readVarInt(), b.readByte()));
        public Type<AttachEntitySpecial> type() { return TYPE; }
    }
}
