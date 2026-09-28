package gulliver.common;

import gulliver.api.IResizeableEntity;
import gulliver.access.IGulliverShoulderInternal;
import gulliver.network.Payloads;
import gulliver.platform.Services;
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
        // Drop any previous hand-held in place (do not touch shoulder slots).
        detachHand(carrier);
        cs.gulliver$setHandEntity(target.getUUID());
        ((IGulliverShoulderInternal) target).gulliver$setHoldingEntity(carrier.getUUID());
        broadcastAttach(carrier, target, SLOT_HAND);
        return true;
    }

    /**
     * Clear the HAND slot: restore the held entity's physics, clear its
     * back-reference, broadcast the detach. Returns the formerly-held
     * entity, or null if the hand was empty or the entity couldn't be
     * resolved (in which case the carrier still gets a detach packet so
     * its client-side slot state clears). Single source of truth for
     * hand-drop — used by pickUp's swap, right-click set-down, place-on-
     * block, and throw.
     */
    public static Entity detachHand(ServerPlayer carrier) {
        IGulliverShoulderInternal cs = (IGulliverShoulderInternal) carrier;
        UUID handId = cs.gulliver$getHandEntity();
        if (handId == null) return null;
        cs.gulliver$setHandEntity(null);
        Entity held = resolve((ServerLevel) carrier.level(), handId);
        if (held == null) {
            Services.platform().sendToPlayer(carrier,
                    new Payloads.AttachEntitySpecial(-1, carrier.getId(), SLOT_DETACH));
            return null;
        }
        ((IGulliverShoulderInternal) held).gulliver$setHoldingEntity(null);
        held.noPhysics = false;
        broadcastAttach(carrier, held, SLOT_DETACH);
        return held;
    }

    /**
     * Server-side orphan check for a carried entity. While
     * gulliver$holdingEntity is set, MixinEntity cancels move() and the
     * carrier's placePassenger holds noPhysics=true — so if the carrier
     * vanishes without dropping (disconnect, dimension change, kill
     * command mid-carry), the carried entity would be frozen in place
     * forever. Called (throttled) from the carried entity's own move
     * inject: if the recorded carrier is gone, dead, or no longer lists
     * this entity in any slot, release the carry and tell tracking
     * clients to unfreeze their copy too.
     */
    public static void validateCarried(Entity carried) {
        if (!(carried.level() instanceof ServerLevel sl)) return;
        IGulliverShoulderInternal me = (IGulliverShoulderInternal) carried;
        UUID carrierId = me.gulliver$getHoldingEntity();
        if (carrierId == null) return;
        Entity carrier = sl.getEntity(carrierId);
        boolean valid = carrier != null && carrier.isAlive();
        if (valid) {
            IGulliverShoulderInternal cs = (IGulliverShoulderInternal) carrier;
            UUID myId = carried.getUUID();
            valid = myId.equals(cs.gulliver$getHandEntity())
                 || myId.equals(cs.gulliver$getRightShoulder())
                 || myId.equals(cs.gulliver$getLeftShoulder());
        }
        if (valid) return;

        me.gulliver$setHoldingEntity(null);
        carried.noPhysics = false;
        Services.platform().sendToTrackingAndSelf(carried, new Payloads.AttachEntitySpecial(
                carried.getId(), carrier != null ? carrier.getId() : -1, SLOT_DETACH));
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
     * V keybind / "/shoulderentity": when carrying anything, cycle the
     * slots (hand <-> shoulders); when empty, pick up the carryable
     * living entity under the crosshair, if any.
     */
    public static boolean cycleOrPickUp(ServerPlayer player) {
        if (((IGulliverShoulderInternal) player).gulliver$hasAnyCarry()) {
            return toggleHandShoulder(player);
        }
        double reach = player.blockInteractionRange();
        Entity targeted = null;
        double bestDistSq = reach * reach;
        net.minecraft.world.phys.Vec3 eye = player.getEyePosition();
        net.minecraft.world.phys.Vec3 look = player.getLookAngle();
        net.minecraft.world.phys.Vec3 end = eye.add(look.x * reach, look.y * reach, look.z * reach);
        // No extra inflate beyond the clip test: tinies have little reach,
        // so they need to be close to (or overlapping) what they lift.
        net.minecraft.world.phys.AABB scan = player.getBoundingBox().expandTowards(look.scale(reach));
        for (Entity candidate : player.level().getEntities(player, scan)) {
            if (!(candidate instanceof LivingEntity)) continue;
            if (!canCarry(player, candidate)) continue;
            net.minecraft.world.phys.AABB cb = candidate.getBoundingBox().inflate(0.3D);
            java.util.Optional<net.minecraft.world.phys.Vec3> hit = cb.clip(eye, end);
            if (hit.isEmpty()) continue;
            double dsq = eye.distanceToSqr(hit.get());
            if (dsq < bestDistSq) {
                bestDistSq = dsq;
                targeted = candidate;
            }
        }
        return targeted != null && pickUp(player, targeted);
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
            Services.platform().sendToPlayer(carrier, new Payloads.AttachEntitySpecial(-1, carrier.getId(), SLOT_DETACH));
        }
        return true;
    }

    /**
     * Throw the HAND-carried entity in the carrier's look direction.
     *
     * Power = 1.5 × carrierSize / targetRoot, capped at 12. Two effects
     * compose: the size RATIO (bigger disparity → further), and the
     * carrier's ABSOLUTE size (a giant's throw is mighty in world
     * units, not just relative to its own body). The old
     * carrierRoot/targetRoot ratio gave a size-8 player throwing a
     * size-1 mob the exact same power as a size-1 player throwing a
     * size-0.125 mob (4.2) — mathematically consistent, but the
     * giant's throw looked pathetic at its scale.
     *
     *   1     → 1      : 1.5   (vanilla shove)
     *   1     → 0.125  : 4.2   (unchanged from before)
     *   2     → 1      : 3
     *   4     → 1      : 6
     *   8     → 1      : 12    (capped — sails across the landscape)
     *   8     → 0.125  : 12    (cap keeps physics sane)
     *   0.25  → 0.125  : 1.06  (tiny arms, gentle toss)
     */
    public static boolean throwHeld(ServerPlayer carrier) {
        IGulliverShoulderInternal cs = (IGulliverShoulderInternal) carrier;
        if (cs.gulliver$getHandEntity() == null) return false;
        Entity held = detachHand(carrier);
        if (held == null) return true; // slot was stale; cleared anyway
        net.minecraft.world.phys.Vec3 look = carrier.getLookAngle();
        float carrierSize = ((IResizeableEntity) carrier).getSizeMultiplier();
        float targetRoot  = ((IResizeableEntity) held).getSizeMultiplierRoot();
        if (targetRoot <= 0.0F) targetRoot = 1.0F;
        float power = Math.min(12.0F, 1.5F * carrierSize / targetRoot);
        held.setDeltaMovement(look.x * power, look.y * power + 0.3F, look.z * power);
        held.hurtMarked = true;
        // Visible throw: broadcast an arm swing (shows in 1st AND 3rd
        // person — swing(hand, true) includes the carrier itself).
        carrier.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
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
        Services.platform().sendToTrackingAndSelf(carrier,
                new Payloads.AttachEntitySpecial(target.getId(), carrier.getId(), slot));
    }

    private static void broadcastAttachByUuid(ServerPlayer carrier, UUID targetId, byte slot) {
        Entity e = resolve((ServerLevel) carrier.level(), targetId);
        if (e != null) broadcastAttach(carrier, e, slot);
    }
}
