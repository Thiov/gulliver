package gulliver.common;

import gulliver.api.IResizeableEntity;
import gulliver.access.IGulliverShoulderInternal;
import gulliver.network.Payloads;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

/**
 * Carry slots: HAND + RIGHT shoulder + LEFT shoulder. Up to 3
 * concurrent passengers. Pickup goes to HAND. V cycles HAND ↔ shoulders.
 */
public final class ShoulderHelper {
    private ShoulderHelper() {}

    /** Slot identifiers — also used as packet attachmentType byte. */
    public static final byte SLOT_DETACH = 0;
    public static final byte SLOT_HAND   = 1;
    public static final byte SLOT_RIGHT  = 2;
    public static final byte SLOT_LEFT   = 3;

    public static float maxHeldWidth(LivingEntity carrier) {
        // Carrier can lift anything narrower than itself.
        return carrier.getBbWidth();
    }

    public static boolean canCarry(LivingEntity carrier, Entity target) {
        if (target == null || target == carrier) return false;
        if (target.getVehicle() != null) return false;
        if (target.isPassenger() || target.getPassengers().size() > 0) return false;
        if (((IGulliverShoulderInternal) target).gulliver$getHoldingEntity() != null) return false;
        if (target.getBbWidth() > maxHeldWidth(carrier)) return false;
        return true;
    }

    /**
     * Pick up the target into the HAND slot. If hand is already
     * occupied, the previous hand-held is dropped IN PLACE (it stays
     * at the location where the carrier picked up the new target —
     * effectively a "place down + swap").
     */
    public static boolean pickUp(ServerPlayer carrier, Entity target) {
        if (!canCarry(carrier, target)) return false;
        IGulliverShoulderInternal cs = (IGulliverShoulderInternal) carrier;
        UUID prev = cs.gulliver$getHandEntity();
        if (prev != null) {
            // Drop the previous hand-held in place (do not touch shoulder slots).
            Entity prevEntity = resolve((ServerLevel) carrier.level(), prev);
            cs.gulliver$setHandEntity(null);
            if (prevEntity != null) {
                ((IGulliverShoulderInternal) prevEntity).gulliver$setHoldingEntity(null);
                prevEntity.noPhysics = false;
                broadcastAttach(carrier, prevEntity, SLOT_DETACH);
            }
        }
        cs.gulliver$setHandEntity(target.getUUID());
        ((IGulliverShoulderInternal) target).gulliver$setHoldingEntity(carrier.getUUID());
        broadcastAttach(carrier, target, SLOT_HAND);
        return true;
    }

    /**
     * V keybind action — toggle the carry slot rotation:
     *   - hand has someone: move them to first empty shoulder (right > left).
     *     If both shoulders full, swap with the right shoulder (right -> hand,
     *     hand -> right).
     *   - hand is empty AND a shoulder has someone: move right (preferred) or
     *     left into the hand.
     *   - all empty: no-op.
     */
    public static boolean toggleHandShoulder(ServerPlayer carrier) {
        IGulliverShoulderInternal cs = (IGulliverShoulderInternal) carrier;
        UUID hand  = cs.gulliver$getHandEntity();
        UUID right = cs.gulliver$getRightShoulder();
        UUID left  = cs.gulliver$getLeftShoulder();

        if (hand != null) {
            if (right == null) {
                cs.gulliver$setHandEntity(null);
                cs.gulliver$setRightShoulder(hand);
                broadcastAttachByUuid(carrier, hand, SLOT_RIGHT);
                return true;
            }
            if (left == null) {
                cs.gulliver$setHandEntity(null);
                cs.gulliver$setLeftShoulder(hand);
                broadcastAttachByUuid(carrier, hand, SLOT_LEFT);
                return true;
            }
            // Both shoulders full — swap hand with right shoulder.
            cs.gulliver$setHandEntity(right);
            cs.gulliver$setRightShoulder(hand);
            broadcastAttachByUuid(carrier, right, SLOT_HAND);
            broadcastAttachByUuid(carrier, hand,  SLOT_RIGHT);
            return true;
        }
        // Hand empty: pull right shoulder (preferred) into hand.
        if (right != null) {
            cs.gulliver$setRightShoulder(null);
            cs.gulliver$setHandEntity(right);
            broadcastAttachByUuid(carrier, right, SLOT_HAND);
            return true;
        }
        if (left != null) {
            cs.gulliver$setLeftShoulder(null);
            cs.gulliver$setHandEntity(left);
            broadcastAttachByUuid(carrier, left, SLOT_HAND);
            return true;
        }
        return false;
    }

    /**
     * Drop ALL carried entities (hand + both shoulders).
     */
    public static boolean drop(ServerPlayer carrier) {
        IGulliverShoulderInternal cs = (IGulliverShoulderInternal) carrier;
        boolean any = false;
        any |= dropSlotInternal(carrier, cs.gulliver$getHandEntity());
        any |= dropSlotInternal(carrier, cs.gulliver$getRightShoulder());
        any |= dropSlotInternal(carrier, cs.gulliver$getLeftShoulder());
        cs.gulliver$setHandEntity(null);
        cs.gulliver$setRightShoulder(null);
        cs.gulliver$setLeftShoulder(null);
        return any;
    }

    private static boolean dropSlotInternal(ServerPlayer carrier, UUID id) {
        if (id == null) return false;
        Entity e = resolve((ServerLevel) carrier.level(), id);
        if (e != null) {
            ((IGulliverShoulderInternal) e).gulliver$setHoldingEntity(null);
            e.noPhysics = false;
            broadcastAttach(carrier, e, SLOT_DETACH);
        } else {
            ServerPlayNetworking.send(carrier, new Payloads.AttachEntitySpecial(-1, carrier.getId(), SLOT_DETACH));
        }
        return true;
    }

    /**
     * Throw the HAND-carried entity in the carrier's look direction.
     */
    public static boolean throwHeld(ServerPlayer carrier) {
        IGulliverShoulderInternal cs = (IGulliverShoulderInternal) carrier;
        UUID handId = cs.gulliver$getHandEntity();
        if (handId == null) return false;
        Entity held = resolve((ServerLevel) carrier.level(), handId);
        cs.gulliver$setHandEntity(null);
        if (held == null) {
            ServerPlayNetworking.send(carrier, new Payloads.AttachEntitySpecial(-1, carrier.getId(), SLOT_DETACH));
            return true;
        }
        ((IGulliverShoulderInternal) held).gulliver$setHoldingEntity(null);
        held.noPhysics = false;
        broadcastAttach(carrier, held, SLOT_DETACH);
        net.minecraft.world.phys.Vec3 look = carrier.getLookAngle();
        float power = 1.5F * ((IResizeableEntity) carrier).getSizeMultiplierRoot();
        held.setDeltaMovement(look.x * power, look.y * power + 0.3F, look.z * power);
        held.hurtMarked = true;
        return true;
    }

    public static Entity resolve(ServerLevel level, UUID id) {
        if (id == null) return null;
        return level.getEntity(id);
    }

    public static void broadcastDetach(ServerPlayer carrier, Entity target) {
        broadcastAttach(carrier, target, SLOT_DETACH);
    }

    static void broadcastAttach(ServerPlayer carrier, Entity target, byte slot) {
        Payloads.AttachEntitySpecial p =
                new Payloads.AttachEntitySpecial(target.getId(), carrier.getId(), slot);
        for (ServerPlayer viewer : PlayerLookup.tracking(carrier)) {
            ServerPlayNetworking.send(viewer, p);
        }
        ServerPlayNetworking.send(carrier, p);
    }

    private static void broadcastAttachByUuid(ServerPlayer carrier, UUID targetId, byte slot) {
        Entity e = resolve((ServerLevel) carrier.level(), targetId);
        if (e != null) broadcastAttach(carrier, e, slot);
    }
}
