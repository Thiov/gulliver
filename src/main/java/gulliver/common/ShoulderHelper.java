package gulliver.common;

import gulliver.api.IResizeableEntity;
import gulliver.mixin.iface.IGulliverShoulderInternal;
import gulliver.network.Payloads;
import gulliver.network.SizeSync;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * Shoulder-entity carry/drop helpers, ported from the 1.6.4 ASM-injected
 * methods on Entity / Player:
 *   maxHeldWidth(other) — biggest other.width that the carrier can lift.
 *                          1.6.4 used carrier.width / 2 - other.width as
 *                          a 'fits-on-shoulder' room check.
 *   pickUpEntity(target) — set heldEntity / holdingEntity, send packet.
 *   dropHeldEntity()     — clear fields, send drop packet.
 */
public final class ShoulderHelper {
    private ShoulderHelper() {}

    /**
     * Returns max bounding-box width of a target the carrier can put on
     * their shoulder. Negative means 'no room'.
     *
     * 1.6.4 maxHeldWidth: carrier.width × 0.5 - margin, scaled by
     * carrier.sizeMultiplier. Larger carrier ⇒ wider acceptable target.
     */
    public static float maxHeldWidth(LivingEntity carrier) {
        return carrier.getBbWidth() * 0.5F;
    }

    public static boolean canCarry(LivingEntity carrier, Entity target) {
        if (target == null || target == carrier) return false;
        if (target.getVehicle() != null) return false;
        if (target instanceof Player) return false;
        if (target.isPassenger() || target.getPassengers().size() > 0) return false;
        if (((IGulliverShoulderInternal) carrier).gulliver$getHeldEntity() != null) return false;
        if (((IGulliverShoulderInternal) target).gulliver$getHoldingEntity() != null) return false;
        if (target.getBbWidth() > maxHeldWidth(carrier)) return false;
        return true;
    }

    public static boolean pickUp(ServerPlayer carrier, Entity target) {
        if (!canCarry(carrier, target)) return false;
        ((IGulliverShoulderInternal) carrier).gulliver$setHeldEntity(target.getUUID());
        ((IGulliverShoulderInternal) target).gulliver$setHoldingEntity(carrier.getUUID());
        broadcastAttach(carrier, target, (byte) 1);
        return true;
    }

    public static boolean drop(ServerPlayer carrier) {
        UUID heldId = ((IGulliverShoulderInternal) carrier).gulliver$getHeldEntity();
        if (heldId == null) return false;
        Entity held = resolve((ServerLevel) carrier.level(), heldId);
        ((IGulliverShoulderInternal) carrier).gulliver$setHeldEntity(null);
        if (held != null) {
            ((IGulliverShoulderInternal) held).gulliver$setHoldingEntity(null);
            broadcastAttach(carrier, held, (byte) 0);
        } else {
            // resolve failed — still tell clients to detach by id 0
            ServerPlayNetworking.send(carrier, new Payloads.AttachEntitySpecial(-1, carrier.getId(), (byte) 0));
        }
        return true;
    }

    public static Entity resolve(ServerLevel level, UUID id) {
        if (id == null) return null;
        return level.getEntity(id);
    }

    private static void broadcastAttach(ServerPlayer carrier, Entity target, byte type) {
        Payloads.AttachEntitySpecial p =
                new Payloads.AttachEntitySpecial(target.getId(), carrier.getId(), type);
        for (ServerPlayer viewer : PlayerLookup.tracking(carrier)) {
            ServerPlayNetworking.send(viewer, p);
        }
        ServerPlayNetworking.send(carrier, p);
    }
}
