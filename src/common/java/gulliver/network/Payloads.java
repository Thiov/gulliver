package gulliver.network;

import gulliver.Gulliver;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
//#if MC >= 1.20.5
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//#endif

/**
 * Gulliver's custom packets. The two originals from the 1.6.4 mod
 * (Packet171EntitySize, Packet172AttachEntitySpecial) keep their wire
 * layout; the rest are new.
 */
public final class Payloads {
    private Payloads() {}

    /**
     * S2C: an entity's size changed. sizeMult is the full composed
     * multiplier (base x potion x items) the entity is heading to; the
     * server is the source of truth and the client stores it as its base.
     */
    public record EntitySize(int entityId, float sizeMult) implements GulliverPayload {
        public static final Identifier ID = Gulliver.id("entity_size");

        public static EntitySize read(FriendlyByteBuf b) {
            return new EntitySize(b.readVarInt(), b.readFloat());
        }

        @Override
        public void write(FriendlyByteBuf b) {
            b.writeVarInt(entityId);
            b.writeFloat(sizeMult);
        }

        //#if MC >= 1.20.5
        public static final Type<EntitySize> TYPE = new Type<>(ID);
        @Override public Type<EntitySize> type() { return TYPE; }
        //#endif
    }

    /**
     * S2C: attach (or detach) a carried entity to a carrier's hand or
     * shoulder. attachmentType is a ShoulderHelper.SLOT_* constant. On a
     * detach whose passenger could not be resolved entityId is -1.
     */
    public record AttachEntitySpecial(int entityId, int vehicleEntityId, byte attachmentType)
            implements GulliverPayload {
        public static final Identifier ID = Gulliver.id("attach_entity_special");

        public static AttachEntitySpecial read(FriendlyByteBuf b) {
            return new AttachEntitySpecial(b.readVarInt(), b.readVarInt(), b.readByte());
        }

        @Override
        public void write(FriendlyByteBuf b) {
            b.writeVarInt(entityId);
            b.writeVarInt(vehicleEntityId);
            b.writeByte(attachmentType);
        }

        //#if MC >= 1.20.5
        public static final Type<AttachEntitySpecial> TYPE = new Type<>(ID);
        @Override public Type<AttachEntitySpecial> type() { return TYPE; }
        //#endif
    }

    /**
     * S2C: a huge entity landed hard at (x, y, z). Much smaller clients turn
     * it into a screen quake with distance falloff (TremorHandler).
     */
    public record GroundShock(double x, double y, double z, float sourceSize, float strength)
            implements GulliverPayload {
        public static final Identifier ID = Gulliver.id("ground_shock");

        public static GroundShock read(FriendlyByteBuf b) {
            return new GroundShock(b.readDouble(), b.readDouble(), b.readDouble(),
                    b.readFloat(), b.readFloat());
        }

        @Override
        public void write(FriendlyByteBuf b) {
            b.writeDouble(x);
            b.writeDouble(y);
            b.writeDouble(z);
            b.writeFloat(sourceSize);
            b.writeFloat(strength);
        }

        //#if MC >= 1.20.5
        public static final Type<GroundShock> TYPE = new Type<>(ID);
        @Override public Type<GroundShock> type() { return TYPE; }
        //#endif
    }

    /**
     * S2C: a grappling hook bit into a block at (x, y, z). Clients pin
     * their copy there too; they'd otherwise keep simulating its fall.
     */
    public record HookAnchor(int hookId, double x, double y, double z) implements GulliverPayload {
        public static final Identifier ID = Gulliver.id("hook_anchor");

        public static HookAnchor read(FriendlyByteBuf b) {
            return new HookAnchor(b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble());
        }

        @Override
        public void write(FriendlyByteBuf b) {
            b.writeVarInt(hookId);
            b.writeDouble(x);
            b.writeDouble(y);
            b.writeDouble(z);
        }

        //#if MC >= 1.20.5
        public static final Type<HookAnchor> TYPE = new Type<>(ID);
        @Override public Type<HookAnchor> type() { return TYPE; }
        //#endif
    }

    /**
     * C2S: the player right-clicked the air with a resizing item (cyan or
     * purple dye, red or brown mushroom). Vanilla never sends a use packet
     * for these, so this one carries the intent to the server.
     */
    public record ConsumeResizingItem(boolean mainHand) implements GulliverPayload {
        public static final Identifier ID = Gulliver.id("consume_resizing_item");

        public static ConsumeResizingItem read(FriendlyByteBuf b) {
            return new ConsumeResizingItem(b.readBoolean());
        }

        @Override
        public void write(FriendlyByteBuf b) {
            b.writeBoolean(mainHand);
        }

        //#if MC >= 1.20.5
        public static final Type<ConsumeResizingItem> TYPE = new Type<>(ID);
        @Override public Type<ConsumeResizingItem> type() { return TYPE; }
        //#endif
    }

    /**
     * C2S: carry-system actions from keybinds and clicks. Replaces the old
     * "/shoulderentity" chat-command round trip, which failed on servers
     * that restrict commands and spammed the command log.
     */
    public record CarryAction(byte action) implements GulliverPayload {
        public static final Identifier ID = Gulliver.id("carry_action");
        public static final byte CYCLE = 0;
        public static final byte THROW = 1;
        public static final byte DROP = 2;

        public static CarryAction read(FriendlyByteBuf b) {
            return new CarryAction(b.readByte());
        }

        @Override
        public void write(FriendlyByteBuf b) {
            b.writeByte(action);
        }

        //#if MC >= 1.20.5
        public static final Type<CarryAction> TYPE = new Type<>(ID);
        @Override public Type<CarryAction> type() { return TYPE; }
        //#endif
    }
}
