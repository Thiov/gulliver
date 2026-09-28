package gulliver.common;

import gulliver.access.IGulliverShoulderInternal;
import gulliver.api.IResizeableEntity;
import gulliver.network.Payloads;
import gulliver.platform.Services;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Carry slots: HAND + RIGHT shoulder + LEFT shoulder, up to three
 * passengers. Pickup goes to the hand; V cycles hand <-> shoulders.
 *
 * Who can be carried follows the 1.6.4 original: the target must be
 * "quite smaller" (under 0.4x the carrier's size) and narrow enough for
 * the hand, the carrier's main hand must be empty, bosses can't be lifted,
 * other players only where PvP is on, and a carried player can always
 * break free by sneaking.
 */
public final class ShoulderHelper {
    private ShoulderHelper() {}

    /** Slot identifiers, also the packet attachment byte. */
    public static final byte SLOT_DETACH = 0;
    public static final byte SLOT_HAND = 1;
    public static final byte SLOT_RIGHT = 2;
    public static final byte SLOT_LEFT = 3;

    /** 1.6.4 isQuiteSmallerThan: under 0.4x the carrier's size. */
    private static final float MAX_SIZE_RATIO = 0.4F;

    /** Widest body the hand can wrap around (1.6.4: 0.5-0.8x the carrier's width). */
    public static float maxHeldWidth(LivingEntity carrier) {
        return carrier.getBbWidth() * 0.8F;
    }

    /** Bosses and multipart giants can't be carried or ridden with string. */
    public static boolean isUnholdable(Entity e) {
        EntityType<?> t = e.getType();
        return t == EntityTypes.ENDER_DRAGON || t == EntityTypes.WITHER || t == EntityTypes.WARDEN
                || t == EntityTypes.ELDER_GUARDIAN || GulliverEnvoy.isDragonEntity(e);
    }

    public static boolean canCarry(LivingEntity carrier, Entity target) {
        if (target == null || target == carrier || !target.isAlive()) return false;
        if (!(target instanceof LivingEntity)) return false;
        if (isUnholdable(target)) return false;
        if (target.isPassenger() || target.isVehicle()) return false;
        if (((IGulliverShoulderInternal) target).gulliver$getHoldingEntity() != null) return false;
        float carrierSize = ((IResizeableEntity) carrier).getSizeMultiplier();
        float targetSize = ((IResizeableEntity) target).getSizeMultiplier();
        if (targetSize >= carrierSize * MAX_SIZE_RATIO) return false;
        if (target.getBbWidth() > maxHeldWidth(carrier)) return false;
        if (target instanceof Player p) {
            if (p.isCreative() || p.isSpectator()) return false;
            if (carrier instanceof Player cp && !cp.canHarmPlayer(p)) return false;
        }
        return true;
    }

    /** Carry actions need a free main hand, like the original. */
    public static boolean handFree(Player carrier) {
        return carrier.getMainHandItem().isEmpty();
    }

    /**
     * Pick the target up into the HAND slot. A previous hand-held entity is
     * set down in place (swap); shoulder slots are never touched.
     */
    public static boolean pickUp(ServerPlayer carrier, Entity target) {
        if (!canCarry(carrier, target)) return false;
        IGulliverShoulderInternal cs = (IGulliverShoulderInternal) carrier;
        detachHand(carrier);
        cs.gulliver$setHandEntity(target.getUUID());
        ((IGulliverShoulderInternal) target).gulliver$setHoldingEntity(carrier.getUUID());
        broadcastAttach(carrier, target, SLOT_HAND);
        return true;
    }

    /**
     * Clear the HAND slot and release the held entity. Returns it, or null
     * when the hand was empty or the entity is gone (the carrier's client
     * still gets a detach so its slot clears). Single source of truth for
     * every hand-drop: swap, set-down, place-on-block, throw.
     */
    public static Entity detachHand(ServerPlayer carrier) {
        IGulliverShoulderInternal cs = (IGulliverShoulderInternal) carrier;
        UUID handId = cs.gulliver$getHandEntity();
        if (handId == null) return null;
        cs.gulliver$setHandEntity(null);
        return release(carrier, handId);
    }

    /** Release one carried entity (by id) from this carrier and tell everyone. */
    private static Entity release(ServerPlayer carrier, UUID id) {
        Entity held = resolve((ServerLevel) carrier.level(), id);
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
     * Server-side orphan check for a carried entity. While carried its
     * move() is cancelled, so if the carrier vanishes without dropping it
     * (disconnect, dimension change, kill command) it would stay frozen
     * forever. Called (throttled) from the carried entity's move hook.
     */
    public static void validateCarried(Entity carried) {
        if (!(carried.level() instanceof ServerLevel sl)) return;
        IGulliverShoulderInternal me = (IGulliverShoulderInternal) carried;
        UUID carrierId = me.gulliver$getHoldingEntity();
        if (carrierId == null) return;
        Entity carrier = sl.getEntity(carrierId);
        boolean valid = carrier != null && carrier.isAlive() && slotOf(carrier, carried.getUUID()) != SLOT_DETACH;
        if (valid) return;
        me.gulliver$setHoldingEntity(null);
        carried.noPhysics = false;
        Services.platform().sendToTrackingAndSelf(carried, new Payloads.AttachEntitySpecial(
                carried.getId(), carrier != null ? carrier.getId() : -1, SLOT_DETACH));
    }

    private static byte slotOf(Entity carrier, UUID id) {
        IGulliverShoulderInternal cs = (IGulliverShoulderInternal) carrier;
        if (id.equals(cs.gulliver$getHandEntity())) return SLOT_HAND;
        if (id.equals(cs.gulliver$getRightShoulder())) return SLOT_RIGHT;
        if (id.equals(cs.gulliver$getLeftShoulder())) return SLOT_LEFT;
        return SLOT_DETACH;
    }

    /**
     * V keybind: move the hand-held to the first free shoulder (right, then
     * left), swapping with the right shoulder when both are full; with an
     * empty hand, pull a shoulder passenger back into it.
     */
    public static boolean toggleHandShoulder(ServerPlayer carrier) {
        IGulliverShoulderInternal cs = (IGulliverShoulderInternal) carrier;
        UUID hand = cs.gulliver$getHandEntity();
        UUID right = cs.gulliver$getRightShoulder();
        UUID left = cs.gulliver$getLeftShoulder();

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
            cs.gulliver$setHandEntity(right);
            cs.gulliver$setRightShoulder(hand);
            broadcastAttachByUuid(carrier, right, SLOT_HAND);
            broadcastAttachByUuid(carrier, hand, SLOT_RIGHT);
            return true;
        }
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
     * slots; otherwise pick up the carryable entity under the crosshair —
     * never through a wall.
     */
    public static boolean cycleOrPickUp(ServerPlayer player) {
        if (((IGulliverShoulderInternal) player).gulliver$hasAnyCarry()) {
            return toggleHandShoulder(player);
        }
        if (!handFree(player)) return false;
        double reach = player.entityInteractionRange();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.x * reach, look.y * reach, look.z * reach);
        HitResult block = player.level().clip(new ClipContext(eye, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        double bestDistSq = eye.distanceToSqr(end);
        Entity targeted = null;
        AABB scan = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0D);
        for (Entity candidate : player.level().getEntities(player, scan)) {
            if (!canCarry(player, candidate)) continue;
            AABB cb = candidate.getBoundingBox().inflate(Math.max(0.1D, candidate.getBbWidth() * 0.3D));
            java.util.Optional<Vec3> hit = cb.clip(eye, end);
            if (hit.isEmpty()) continue;
            double dsq = eye.distanceToSqr(hit.get());
            if (dsq < bestDistSq) {
                bestDistSq = dsq;
                targeted = candidate;
            }
        }
        return targeted != null && pickUp(player, targeted);
    }

    /** Drop everything carried (hand + both shoulders). */
    public static boolean drop(ServerPlayer carrier) {
        IGulliverShoulderInternal cs = (IGulliverShoulderInternal) carrier;
        UUID[] ids = { cs.gulliver$getHandEntity(), cs.gulliver$getRightShoulder(), cs.gulliver$getLeftShoulder() };
        cs.gulliver$setHandEntity(null);
        cs.gulliver$setRightShoulder(null);
        cs.gulliver$setLeftShoulder(null);
        boolean any = false;
        for (UUID id : ids) {
            if (id == null) continue;
            release(carrier, id);
            any = true;
        }
        return any;
    }

    /**
     * Throw the HAND-carried entity along the carrier's look. Power grows
     * with the size ratio and the carrier's absolute size (a giant's throw
     * is mighty in world units): 1.5 x carrierSize / sqrt(targetSize),
     * capped at 12.
     */
    public static boolean throwHeld(ServerPlayer carrier) {
        IGulliverShoulderInternal cs = (IGulliverShoulderInternal) carrier;
        if (cs.gulliver$getHandEntity() == null) return false;
        Entity held = detachHand(carrier);
        if (held == null) return true;
        Vec3 look = carrier.getLookAngle();
        float carrierSize = ((IResizeableEntity) carrier).getSizeMultiplier();
        float targetRoot = ((IResizeableEntity) held).getSizeMultiplierRoot();
        if (targetRoot <= 0.0F) targetRoot = 1.0F;
        float power = Math.min(12.0F, 1.5F * carrierSize / targetRoot);
        held.setDeltaMovement(look.x * power, look.y * power + 0.3F, look.z * power);
        held.hurtMarked = true;
        carrier.swing(InteractionHand.MAIN_HAND, true);
        return true;
    }

    // ---- per-tick placement (both sides, every carrier) ----

    /**
     * Snap each carried entity to its slot. Runs on the server and on every
     * client — for the local player via Player.aiStep, for other players
     * via their client-side tick — so everybody sees the same thing.
     */
    public static void positionPassengers(Player carrier) {
        IGulliverShoulderInternal cs = (IGulliverShoulderInternal) carrier;
        UUID hand = cs.gulliver$getHandEntity();
        UUID right = cs.gulliver$getRightShoulder();
        UUID left = cs.gulliver$getLeftShoulder();
        if (hand == null && right == null && left == null) return;

        double yaw = Math.toRadians(carrier.yBodyRot);
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        // yaw 0 faces +Z: forward = (-sin, cos); the right arm is on -X at
        // yaw 0, so right = (-cos, -sin).
        double fwdX = -sin, fwdZ = cos;
        double rightX = -cos, rightZ = -sin;
        // Vanilla model: shoulder at +-5/16, arm reaches 8/16 forward; both
        // scale with the (already size-scaled) body width.
        double widthScale = carrier.getBbWidth() / 0.6D;
        double sideUnit = (5.0D / 16.0D) * widthScale;
        double armLength = (8.0D / 16.0D) * widthScale;
        double shoulderY = carrier.getY() + carrier.getBbHeight() * (24.0D / 32.0D);

        if (hand != null) {
            place(carrier, hand, SLOT_HAND,
                    carrier.getX() + rightX * sideUnit + fwdX * armLength, shoulderY,
                    carrier.getZ() + rightZ * sideUnit + fwdZ * armLength);
        }
        if (right != null) {
            place(carrier, right, SLOT_RIGHT,
                    carrier.getX() + rightX * sideUnit, shoulderY, carrier.getZ() + rightZ * sideUnit);
        }
        if (left != null) {
            place(carrier, left, SLOT_LEFT,
                    carrier.getX() - rightX * sideUnit, shoulderY, carrier.getZ() - rightZ * sideUnit);
        }
    }

    private static void place(Player carrier, UUID id, byte slot, double x, double y, double z) {
        Entity p = lookup(carrier, id);
        boolean server = carrier instanceof ServerPlayer;
        if (p == null || !p.isAlive()
                || !carrier.getUUID().equals(((IGulliverShoulderInternal) p).gulliver$getHoldingEntity())) {
            // Gone, dead, or a respawned twin with the same UUID: free the slot.
            if (server) {
                clearSlot(carrier, slot);
                release((ServerPlayer) carrier, id);
            }
            return;
        }
        if (server) {
            // A carried player breaks free by sneaking; anything that grew too
            // big for the hand slips out.
            boolean breakFree = p instanceof Player && p.isShiftKeyDown();
            boolean tooBig = ((IResizeableEntity) p).getSizeMultiplier()
                    >= ((IResizeableEntity) carrier).getSizeMultiplier() * MAX_SIZE_RATIO * 1.25F;
            if (breakFree || tooBig) {
                clearSlot(carrier, slot);
                release((ServerPlayer) carrier, id);
                return;
            }
        }
        // The renderer lerps from {xOld..} to {x..}; the carrier itself is
        // drawn lerped too, so the passenger's old position must be exactly
        // last tick's slot position. MixinEntity freezes its own move() and
        // setOldPosAndRot() while carried, so nothing else disturbs these.
        double ox = p.getX(), oy = p.getY(), oz = p.getZ();
        p.setPos(x, y, z);
        p.xOld = ox;
        p.yOld = oy;
        p.zOld = oz;
        p.setDeltaMovement(0.0D, 0.0D, 0.0D);
        p.fallDistance = 0.0F;
        p.noPhysics = true;
    }

    private static void clearSlot(Player carrier, byte slot) {
        IGulliverShoulderInternal cs = (IGulliverShoulderInternal) carrier;
        switch (slot) {
            case SLOT_HAND -> cs.gulliver$setHandEntity(null);
            case SLOT_RIGHT -> cs.gulliver$setRightShoulder(null);
            case SLOT_LEFT -> cs.gulliver$setLeftShoulder(null);
            default -> { }
        }
    }

    private static Entity lookup(Player carrier, UUID id) {
        if (carrier.level() instanceof ServerLevel sl) return sl.getEntity(id);
        double r = Math.max(8.0D, carrier.getBbWidth() * 4.0D);
        for (Entity e : carrier.level().getEntities(carrier, carrier.getBoundingBox().inflate(r))) {
            if (e.getUUID().equals(id)) return e;
        }
        return null;
    }

    // ---- sync ----

    public static Entity resolve(ServerLevel level, UUID id) {
        return id == null ? null : level.getEntity(id);
    }

    static void broadcastAttach(ServerPlayer carrier, Entity target, byte slot) {
        Services.platform().sendToTrackingAndSelf(carrier,
                new Payloads.AttachEntitySpecial(target.getId(), carrier.getId(), slot));
    }

    private static void broadcastAttachByUuid(ServerPlayer carrier, UUID targetId, byte slot) {
        Entity e = resolve((ServerLevel) carrier.level(), targetId);
        if (e != null) broadcastAttach(carrier, e, slot);
    }

    /**
     * A player started tracking {@code entity}: tell it about any carry
     * the entity is part of, so late arrivals see the arm pose and the
     * passenger in place.
     */
    public static void sendCarryState(Entity entity, ServerPlayer viewer) {
        if (!(entity.level() instanceof ServerLevel sl)) return;
        IGulliverShoulderInternal es = (IGulliverShoulderInternal) entity;
        if (entity instanceof ServerPlayer carrier && es.gulliver$hasAnyCarry()) {
            sendSlot(sl, carrier, es.gulliver$getHandEntity(), SLOT_HAND, viewer);
            sendSlot(sl, carrier, es.gulliver$getRightShoulder(), SLOT_RIGHT, viewer);
            sendSlot(sl, carrier, es.gulliver$getLeftShoulder(), SLOT_LEFT, viewer);
        }
        UUID holder = es.gulliver$getHoldingEntity();
        if (holder != null && sl.getEntity(holder) instanceof ServerPlayer carrier) {
            byte slot = slotOf(carrier, entity.getUUID());
            if (slot != SLOT_DETACH) sendSlot(sl, carrier, entity.getUUID(), slot, viewer);
        }
    }

    private static void sendSlot(ServerLevel sl, ServerPlayer carrier, UUID id, byte slot, ServerPlayer viewer) {
        if (id == null) return;
        Entity e = sl.getEntity(id);
        if (e != null) {
            Services.platform().sendToPlayer(viewer, new Payloads.AttachEntitySpecial(e.getId(), carrier.getId(), slot));
        }
    }
}
