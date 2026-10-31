package gulliver.common;

import gulliver.access.IGulliverShoulderInternal;
import gulliver.api.IResizeableEntity;
import gulliver.network.Payloads;
import gulliver.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
//#if MC >= 1.21.11
import net.minecraft.server.permissions.Permissions;
//#endif
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
//#if MC >= 1.21.5
import net.minecraft.world.entity.EntityReference;
//#endif
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.UUID;

/**
 * Carry slots: HAND + RIGHT shoulder + LEFT shoulder, up to three
 * passengers. Pickup goes to the hand; V cycles hand <-> shoulders.
 *
 * Who can be carried follows the 1.6.4 original: the target must be
 * "quite smaller" (under 0.4x the carrier's size) and narrow enough for
 * the hand, the carrier's main hand must be empty, bosses can't be lifted,
 * other players only where PvP is on, and a carried player can always
 * break free by sneaking. Someone else's tamed animal is off limits
 * (unless you are an operator in creative), and claim mods get a veto.
 *
 * A carried player is placed by the server alone, like a passenger: the
 * positions its client sends are ignored (MixinServerGamePacketCarried),
 * and it is let go at the server's own position, on the carrier's side of
 * every wall.
 */
public final class ShoulderHelper {
    private ShoulderHelper() {}

    /** Slot identifiers, also the packet attachment byte. */
    public static final byte SLOT_DETACH = 0;
    public static final byte SLOT_HAND = 1;
    public static final byte SLOT_RIGHT = 2;
    public static final byte SLOT_LEFT = 3;
    /** Attachment byte RELEASED + slot: that slot was emptied (entity id may be -1). */
    public static final byte RELEASED = 4;

    /** 1.6.4 isQuiteSmallerThan: under 0.4x the carrier's size. */
    private static final float MAX_SIZE_RATIO = 0.4F;

    /**
     * Fastest a throw can send a creature (blocks per tick): 12 up to size 8,
     * and growing with the square root of the carrier's size above that, so
     * a 32x giant still outthrows an 8x one without sending anything
     * absurdly fast.
     */
    private static float maxThrowPower(float carrierSize) {
        return 12.0F * Math.max(1.0F, (float) Math.sqrt(carrierSize / 8.0F));
    }

    /** Set while a claim-mod probe runs on this thread, so the probe event doesn't re-enter the carry handlers. */
    private static final ThreadLocal<Boolean> PROBING = ThreadLocal.withInitial(() -> Boolean.FALSE);

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
        if (carrier.isSpectator() || carrier.isDeadOrDying()) return false;
        if (!(target instanceof LivingEntity)) return false;
        if (isUnholdable(target)) return false;
        if (target.isPassenger() || target.isVehicle()) return false;
        if (holderOf(target) != null) return false;
        float carrierSize = ((IResizeableEntity) carrier).getSizeMultiplier();
        float targetSize = ((IResizeableEntity) target).getSizeMultiplier();
        if (targetSize >= carrierSize * MAX_SIZE_RATIO) return false;
        if (target.getBbWidth() > maxHeldWidth(carrier)) return false;
        if (target instanceof Player p) {
            // Sneaking is how a carried player breaks free; it also keeps a
            // player who is hiding from being grabbed at all.
            if (p.isCreative() || p.isSpectator() || p.isShiftKeyDown()) return false;
            if (carrier instanceof Player cp && !cp.canHarmPlayer(p)) return false;
        }
        return true;
    }

    /** Carry actions need a free main hand, like the original. */
    public static boolean handFree(Player carrier) {
        return carrier.getMainHandItem().isEmpty();
    }

    /**
     * Whether the HAND slot is in use. On a client a slot whose creature is
     * gone (a lost release packet, a V-key race) is cleared on the spot,
     * so it can't swallow every later attack and use click.
     */
    public static boolean handOccupied(Player carrier) {
        IGulliverShoulderInternal cs = (IGulliverShoulderInternal) carrier;
        UUID id = cs.gulliver$getHandEntity();
        if (id == null) return false;
        if (carrier.level().isClientSide()) {
            Entity held = lookup(carrier, id);
            if (held == null || !carrier.getUUID().equals(holderOf(held))) {
                cs.gulliver$setHandEntity(null);
                return false;
            }
        }
        return true;
    }

    private static UUID holderOf(Entity e) {
        return ((IGulliverShoulderInternal) e).gulliver$getHoldingEntity();
    }

    /** Whether somebody is carrying this entity right now. */
    public static boolean isCarried(Entity e) {
        return holderOf(e) != null;
    }

    // ---- who may be grabbed: ownership and claims ----

    /** The owner's UUID of a tamed animal, or null when it has none. */
    public static UUID ownerOf(Entity e) {
        if (!(e instanceof OwnableEntity o)) return null;
        //#if MC >= 1.21.5
        EntityReference<LivingEntity> ref = o.getOwnerReference();
        return ref == null ? null : ref.getUUID();
        //#else
        //$$ return o.getOwnerUUID();
        //#endif
    }

    private static boolean isOperator(ServerPlayer p) {
        //#if MC >= 1.21.11
        return p.permissions().hasPermission(Permissions.COMMANDS_MODERATOR);
        //#else
        //$$ return p.createCommandSourceStack().hasPermission(2);
        //#endif
    }

    /** True while {@link #mayInteract} is asking the loader's claim hooks; the carry handlers stay out of it. */
    public static boolean isProbing() {
        return PROBING.get();
    }

    /**
     * Gulliver's own inputs (V key, carry, tiny mounts) act on an entity the
     * way a right-click would: claim and protection mods get a veto through
     * the loader's interaction event. The probe fires that event, which
     * Gulliver listens to as well, hence the re-entry guard.
     */
    public static boolean mayInteract(ServerPlayer player, Entity target) {
        if (PROBING.get()) return true;
        PROBING.set(Boolean.TRUE);
        try {
            return Services.platform().mayInteractWithEntity(player, target, InteractionHand.MAIN_HAND);
        } finally {
            PROBING.set(Boolean.FALSE);
        }
    }

    /** Ownership and claims for picking something up. */
    private static boolean mayGrab(ServerPlayer carrier, Entity target) {
        UUID owner = ownerOf(target);
        // Someone else's pet is theirs to carry; an operator in creative may move it anyway.
        if (owner != null && !owner.equals(carrier.getUUID())
                && !(carrier.isCreative() && isOperator(carrier))) {
            return false;
        }
        return mayInteract(carrier, target);
    }

    // ---- picking up and letting go ----

    /**
     * Pick the target up into the HAND slot. A previous hand-held entity is
     * set down in place (swap); shoulder slots are never touched.
     */
    public static boolean pickUp(ServerPlayer carrier, Entity target) {
        if (!canCarry(carrier, target) || !mayGrab(carrier, target)) return false;
        IGulliverShoulderInternal cs = (IGulliverShoulderInternal) carrier;
        detachHand(carrier);
        cs.gulliver$setHandEntity(target.getUUID());
        ((IGulliverShoulderInternal) target).gulliver$setHoldingEntity(carrier.getUUID());
        broadcastAttach(carrier, target, SLOT_HAND);
        GulliverAdvancements.award(carrier, GulliverAdvancements.POCKET_FRIEND);
        return true;
    }

    /**
     * Clear the HAND slot and release the held entity. Returns it, or null
     * when the hand was empty or the entity is gone (the carrier's client
     * still gets a detach so its slot clears). Single source of truth for
     * every hand-drop: swap, set-down, place-on-block, throw.
     */
    public static Entity detachHand(LivingEntity carrier) {
        IGulliverShoulderInternal cs = (IGulliverShoulderInternal) carrier;
        UUID handId = cs.gulliver$getHandEntity();
        if (handId == null) return null;
        cs.gulliver$setHandEntity(null);
        return release(carrier, handId, SLOT_HAND, true);
    }

    /**
     * Like detachHand, but the creature is not set down anywhere: it is
     * about to be eaten (Appetite) where it is, at the carrier's mouth.
     */
    public static Entity takeFromHand(LivingEntity carrier) {
        IGulliverShoulderInternal cs = (IGulliverShoulderInternal) carrier;
        UUID handId = cs.gulliver$getHandEntity();
        if (handId == null) return null;
        cs.gulliver$setHandEntity(null);
        return release(carrier, handId, SLOT_HAND, false);
    }

    /**
     * A giant mob snatches its prey into its hand (Appetite): the same
     * carry the players use, without the player-only pick-up rules.
     */
    public static void grab(LivingEntity carrier, Entity prey) {
        ((IGulliverShoulderInternal) carrier).gulliver$setHandEntity(prey.getUUID());
        ((IGulliverShoulderInternal) prey).gulliver$setHoldingEntity(carrier.getUUID());
        prey.noPhysics = true;
        prey.setDeltaMovement(0.0D, 0.0D, 0.0D);
        // Everyone tracking the carrier, the prey's own client included.
        Services.platform().sendToTrackingAndSelf(carrier, new Payloads.AttachEntitySpecial(
                prey.getId(), carrier.getId(), SLOT_HAND));
    }

    /**
     * Release one carried entity (by id) from an already-cleared slot and
     * tell everyone. The packet names the slot, so clients clear it even
     * when the entity is gone (disconnected, converted, changed dimension).
     * Only an entity this carrier really holds is touched.
     */
    private static Entity release(LivingEntity carrier, UUID id, byte slot) {
        return release(carrier, id, slot, true);
    }

    private static Entity release(LivingEntity carrier, UUID id, byte slot, boolean setDown) {
        Entity held = resolve((ServerLevel) carrier.level(), id);
        if (held != null && carrier.getUUID().equals(holderOf(held))) {
            ((IGulliverShoulderInternal) held).gulliver$setHoldingEntity(null);
            if (setDown) {
                settle(held, carrier);
            } else {
                held.noPhysics = false;
            }
        } else {
            held = null;
        }
        Services.platform().sendToTrackingAndSelf(carrier, new Payloads.AttachEntitySpecial(
                held != null ? held.getId() : -1, carrier.getId(), (byte) (RELEASED + slot)));
        return held;
    }

    /**
     * Put a creature that was just let go somewhere it fits, on the
     * carrier's side of every wall: where the slot was if that is clear,
     * else pulled back toward the carrier's feet, which always fits
     * something this much smaller.
     *
     * A carried player was placed by the server alone (client positions are
     * ignored while carried), so the position settled here is the one the
     * player continues from; it is sent down as a teleport, never read back
     * from a client claim.
     */
    private static void settle(Entity held, Entity carrier) {
        held.noPhysics = false;
        if (!held.isAlive()) return;
        Level level = held.level();
        Vec3 slot = held.position();
        Vec3 feet = carrier.position();
        Vec3 body = new Vec3(feet.x, feet.y + carrier.getBbHeight() * 0.5D, feet.z);
        double height = held.getBbHeight();
        Vec3 at = feet;
        for (int i = 0; i < 4; i++) {
            Vec3 c = slot.lerp(feet, i / 4.0D);
            if (!fits(level, held, c.x, c.y, c.z)) continue;
            Vec3 middle = new Vec3(c.x, c.y + height * 0.5D, c.z);
            if (level.clip(new ClipContext(body, middle, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, carrier)).getType() != HitResult.Type.MISS) continue;
            at = c;
            break;
        }
        if (held instanceof ServerPlayer || at.distanceToSqr(slot) > 1.0E-6D) {
            held.teleportTo(at.x, at.y, at.z);
        }
    }

    private static boolean fits(Level level, Entity e, double x, double y, double z) {
        double half = e.getBbWidth() * 0.5D;
        AABB box = new AABB(x - half, y, z - half, x + half, y + e.getBbHeight(), z + half);
        return level.noCollision(e, box.deflate(1.0E-4D));
    }

    /**
     * Set a hand-held creature down on the face the carrier clicked: on top
     * of the block (the collision top for fences and walls), beside it, or
     * under it, with its body outside the block either way. Does nothing
     * when the clicked face is not one the carrier can see, or nothing fits
     * there; the creature then stays where the hand let go.
     */
    public static void setDownOn(ServerPlayer carrier, Entity held, BlockHitResult hit) {
        Level level = carrier.level();
        BlockPos pos = hit.getBlockPos();
        Vec3 loc = hit.getLocation();
        BlockHitResult seen = level.clip(new ClipContext(carrier.getEyePosition(), loc,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, carrier));
        if (seen.getType() == HitResult.Type.BLOCK && !seen.getBlockPos().equals(pos)) return;

        Direction face = hit.getDirection();
        double x = loc.x;
        double y = loc.y;
        double z = loc.z;
        double push = held.getBbWidth() * 0.5D + 1.0E-3D;
        switch (face) {
            case UP -> {
                VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
                if (!shape.isEmpty()) y = Math.max(y, pos.getY() + shape.max(Direction.Axis.Y));
            }
            case DOWN -> y = loc.y - held.getBbHeight() - 1.0E-3D;
            default -> {
                x += face.getStepX() * push;
                z += face.getStepZ() * push;
            }
        }
        // A little slack upward for slabs, carpets and snow layers.
        for (int i = 0; i < 5; i++) {
            double at = y + i * 0.25D;
            if (fits(level, held, x, at, z)) {
                held.teleportTo(x, at, z);
                held.setDeltaMovement(Vec3.ZERO);
                held.fallDistance = 0.0F;
                return;
            }
            if (face == Direction.DOWN) return;
        }
    }

    /**
     * Server-side orphan check for a carried entity, run from its own tick
     * (so mobs without AI and players are covered too): if the carrier
     * vanishes without dropping it (disconnect, dimension change, kill
     * command) it would stay frozen forever.
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

    /**
     * The carried entity is about to leave its level (it changes dimension
     * or logs out): let go of it quietly, clearing the carrier's slot.
     */
    public static void freeCarried(Entity carried) {
        IGulliverShoulderInternal me = (IGulliverShoulderInternal) carried;
        UUID carrierId = me.gulliver$getHoldingEntity();
        if (carrierId == null) return;
        me.gulliver$setHoldingEntity(null);
        carried.noPhysics = false;
        if (!(carried.level() instanceof ServerLevel sl)) return;
        Entity carrier = sl.getEntity(carrierId);
        if (!(carrier instanceof LivingEntity cp)) return;
        byte slot = slotOf(cp, carried.getUUID());
        if (slot == SLOT_DETACH) return;
        clearSlot(cp, slot);
        if (slot == SLOT_HAND) Appetite.stopChewing(cp);
        Services.platform().sendToTrackingAndSelf(cp, new Payloads.AttachEntitySpecial(
                carried.getId(), cp.getId(), (byte) (RELEASED + slot)));
    }

    /**
     * A player is about to be removed from its level (dimension change,
     * End exit, logout): everything it carries is released first, at the
     * carrier's last position, and a player it is carried by is freed.
     */
    public static void onLeavingLevel(ServerPlayer player) {
        if (((IGulliverShoulderInternal) player).gulliver$hasAnyCarry()) drop(player);
        freeCarried(player);
    }

    private static byte slotOf(Entity carrier, UUID id) {
        IGulliverShoulderInternal cs = (IGulliverShoulderInternal) carrier;
        if (id.equals(cs.gulliver$getHandEntity())) return SLOT_HAND;
        if (id.equals(cs.gulliver$getRightShoulder())) return SLOT_RIGHT;
        if (id.equals(cs.gulliver$getLeftShoulder())) return SLOT_LEFT;
        return SLOT_DETACH;
    }

    /** Clear every slot whose creature is gone or no longer held by this carrier, telling the clients. */
    private static void pruneStale(ServerPlayer carrier) {
        IGulliverShoulderInternal cs = (IGulliverShoulderInternal) carrier;
        pruneSlot(carrier, cs.gulliver$getHandEntity(), SLOT_HAND);
        pruneSlot(carrier, cs.gulliver$getRightShoulder(), SLOT_RIGHT);
        pruneSlot(carrier, cs.gulliver$getLeftShoulder(), SLOT_LEFT);
    }

    private static void pruneSlot(ServerPlayer carrier, UUID id, byte slot) {
        if (id == null) return;
        Entity e = resolve((ServerLevel) carrier.level(), id);
        if (e != null && e.isAlive() && carrier.getUUID().equals(holderOf(e))) return;
        clearSlot(carrier, slot);
        release(carrier, id, slot);
    }

    /**
     * V keybind: move the hand-held to the first free shoulder (right, then
     * left), swapping with the right shoulder when both are full; with an
     * empty hand, pull a shoulder passenger back into it.
     */
    public static boolean toggleHandShoulder(ServerPlayer carrier) {
        pruneStale(carrier);
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
        if (player.isSpectator() || player.isDeadOrDying()) return false;
        if (((IGulliverShoulderInternal) player).gulliver$hasAnyCarry()) {
            return toggleHandShoulder(player);
        }
        if (!handFree(player)) return false;
        double reach = GulliverEnvoy.entityReach(player);
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
        byte[] slots = { SLOT_HAND, SLOT_RIGHT, SLOT_LEFT };
        cs.gulliver$setHandEntity(null);
        cs.gulliver$setRightShoulder(null);
        cs.gulliver$setLeftShoulder(null);
        boolean any = false;
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] == null) continue;
            release(carrier, ids[i], slots[i]);
            any = true;
        }
        return any;
    }

    /**
     * Throw the HAND-carried entity along the carrier's look. Power grows
     * with the size ratio and the carrier's absolute size (a giant's throw
     * is mighty in world units): 1.5 x carrierSize / sqrt(targetSize),
     * capped by {@link #maxThrowPower}.
     */
    public static boolean throwHeld(ServerPlayer carrier) {
        IGulliverShoulderInternal cs = (IGulliverShoulderInternal) carrier;
        if (cs.gulliver$getHandEntity() == null || carrier.isSpectator() || carrier.isDeadOrDying()) return false;
        Entity held = detachHand(carrier);
        if (held == null) return true;
        //#if MC >= 26.3
        carrier.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        //#else
        //$$ carrier.swing(InteractionHand.MAIN_HAND, true);
        //#endif
        // A player who ducked in the same tick as the pickup stays put.
        if (held instanceof Player p && p.isShiftKeyDown()) return true;
        Vec3 look = carrier.getLookAngle();
        float carrierSize = ((IResizeableEntity) carrier).getSizeMultiplier();
        float targetRoot = ((IResizeableEntity) held).getSizeMultiplierRoot();
        if (targetRoot <= 0.0F) targetRoot = 1.0F;
        float power = Math.min(maxThrowPower(carrierSize), 1.5F * carrierSize / targetRoot);
        held.setDeltaMovement(look.x * power, look.y * power + 0.3F, look.z * power);
        held.hurtMarked = true;
        return true;
    }

    // ---- per-tick placement (both sides, every carrier) ----

    /**
     * Snap each carried entity to its slot. Runs on the server and on every
     * client — for the local player via Player.aiStep, for other players
     * via their client-side tick, for mobs via LivingEntity.aiStep — so
     * everybody sees the same thing. While the carrier eats (Appetite) the
     * hand brings its creature up to the mouth and chews.
     */
    public static void positionPassengers(LivingEntity carrier) {
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
            double hx = carrier.getX() + rightX * sideUnit + fwdX * armLength;
            double hz = carrier.getZ() + rightZ * sideUnit + fwdZ * armLength;
            double hy = shoulderY;
            float lift = Appetite.lift(carrier, 0.0F);
            Vec3 grab = ((IGulliverShoulderInternal) carrier).gulliver$getGrabOffset();
            if (grab != null && Appetite.isChewing(carrier)) {
                // A monster's snatch starts where the prey was, not in its hand.
                hx = carrier.getX() + grab.x;
                hy = carrier.getY() + grab.y;
                hz = carrier.getZ() + grab.z;
            }
            if (lift > 0.0F) {
                // To the mouth: just in front of the face, the creature's
                // middle a little under the eyes (in plain view in first
                // person), dipping into the mouth with every bite.
                Entity held = lookup(carrier, hand);
                double half = held == null ? 0.0D : held.getBbHeight() * 0.5D;
                // The mouth turns with the head (for a player: the view),
                // which can be well off the body's heading.
                double head = Math.toRadians(headYaw(carrier));
                Vec3 eye = carrier.getEyePosition();
                double reach = carrier.getBbWidth() * 0.55D;
                double mx = eye.x - Math.sin(head) * reach;
                double mz = eye.z + Math.cos(head) * reach;
                double my = eye.y - carrier.getBbHeight() * 0.05D - half + Appetite.chomp(carrier, 0.0F);
                hx += (mx - hx) * lift;
                hy += (my - hy) * lift;
                hz += (mz - hz) * lift;
            }
            place(carrier, hand, SLOT_HAND, hx, hy, hz, lift);
        }
        if (right != null) {
            place(carrier, right, SLOT_RIGHT,
                    carrier.getX() + rightX * sideUnit, shoulderY, carrier.getZ() + rightZ * sideUnit, 0.0F);
        }
        if (left != null) {
            place(carrier, left, SLOT_LEFT,
                    carrier.getX() - rightX * sideUnit, shoulderY, carrier.getZ() - rightZ * sideUnit, 0.0F);
        }
    }

    private static void place(LivingEntity carrier, UUID id, byte slot, double x, double y, double z, float turn) {
        Entity p = lookup(carrier, id);
        boolean server = !carrier.level().isClientSide();
        if (p == null || !p.isAlive() || !carrier.getUUID().equals(holderOf(p))) {
            // Gone, dead, or a respawned twin with the same UUID: free the slot.
            if (server) {
                clearSlot(carrier, slot);
                release(carrier, id, slot);
            }
            return;
        }
        if (server) {
            // A carried player breaks free by sneaking (not from a monster's
            // fist: that takes hurting the monster, see Appetite); anything
            // that grew too big for the hand slips out; a carrier turned
            // spectator lets go; a carried creature that boarded something
            // is no longer in hand.
            boolean breakFree = p instanceof Player pl && (pl.isCreative() || pl.isSpectator()
                            || (pl.isShiftKeyDown() && carrier instanceof Player))
                    || carrier.isSpectator() || p.isPassenger();
            boolean tooBig = ((IResizeableEntity) p).getSizeMultiplier()
                    >= ((IResizeableEntity) carrier).getSizeMultiplier() * MAX_SIZE_RATIO * 1.25F;
            if (breakFree || tooBig) {
                clearSlot(carrier, slot);
                release(carrier, id, slot);
                if (slot == SLOT_HAND) Appetite.stopChewing(carrier);
                return;
            }
        }
        // Keep the slot on the carrier's side of any wall it is facing.
        Vec3 from = new Vec3(carrier.getX(), y, carrier.getZ());
        Vec3 to = new Vec3(x, y, z);
        HitResult wall = carrier.level().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, carrier));
        if (wall.getType() != HitResult.Type.MISS) {
            double len = from.distanceTo(to);
            double keep = Math.max(0.0D, from.distanceTo(wall.getLocation()) - p.getBbWidth() * 0.5D - 0.05D);
            if (len > 1.0E-6D) {
                Vec3 at = from.add(to.subtract(from).scale(Math.min(keep, len) / len));
                x = at.x;
                z = at.z;
            }
        }
        // The renderer (xOld) and the camera (xo) lerp from last tick's
        // position to this one, and the carrier itself is drawn lerped too,
        // so the passenger's old position must be exactly last tick's slot -
        // remembered here, never read back from the entity, which a server
        // update or its own tick may have nudged in between (that was the
        // flicker in the hand, and the shaking first-person view of a
        // carried player). MixinEntity freezes its move(), setOldPosAndRot()
        // and client interpolation while carried.
        IGulliverShoulderInternal held = (IGulliverShoulderInternal) p;
        Vec3 last = held.gulliver$getLastSlot();
        if (last == null || last.distanceToSqr(x, y, z) > 64.0D * carrier.getBbWidth() * carrier.getBbWidth() + 4.0D) {
            last = new Vec3(x, y, z);
        }
        p.setPos(x, y, z);
        p.xOld = p.xo = last.x;
        p.yOld = p.yo = last.y;
        p.zOld = p.zo = last.z;
        held.gulliver$setLastSlot(new Vec3(x, y, z));
        p.setDeltaMovement(0.0D, 0.0D, 0.0D);
        p.fallDistance = 0.0F;
        p.noPhysics = true;
        // Held still, facing where the carrier faces - turned towards its
        // mouth as it is lifted to be eaten. Players keep their own view.
        if (p instanceof LivingEntity living && !(p instanceof Player)) {
            float yaw = carrier.yBodyRot
                    + (net.minecraft.util.Mth.wrapDegrees(headYaw(carrier) - carrier.yBodyRot) + 180.0F) * turn;
            living.yRotO = living.getYRot();
            living.setYRot(yaw);
            living.yBodyRotO = living.yBodyRot;
            living.yBodyRot = yaw;
            living.yHeadRotO = living.yHeadRot;
            living.yHeadRot = yaw;
            living.xRotO = living.getXRot();
            living.setXRot(0.0F);
        }
    }

    /** Where the carrier's face points: a player's view, a mob's head. */
    private static float headYaw(LivingEntity carrier) {
        return carrier instanceof Player ? carrier.getYRot() : carrier.getYHeadRot();
    }

    private static void clearSlot(Entity carrier, byte slot) {
        IGulliverShoulderInternal cs = (IGulliverShoulderInternal) carrier;
        switch (slot) {
            case SLOT_HAND -> cs.gulliver$setHandEntity(null);
            case SLOT_RIGHT -> cs.gulliver$setRightShoulder(null);
            case SLOT_LEFT -> cs.gulliver$setLeftShoulder(null);
            default -> { }
        }
    }

    private static Entity lookup(Entity carrier, UUID id) {
        if (carrier.level() instanceof ServerLevel sl) return sl.getEntity(id);
        // The slots are within an arm's length of the body; capped so a 32x giant doesn't scan a 150-block box every tick.
        double r = Math.min(Math.max(8.0D, carrier.getBbWidth() * 4.0D), 24.0D);
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
        if (e != null) {
            broadcastAttach(carrier, e, slot);
        } else {
            // Nothing to attach: tell the clients the slot is empty so none keeps a phantom.
            Services.platform().sendToTrackingAndSelf(carrier,
                    new Payloads.AttachEntitySpecial(-1, carrier.getId(), (byte) (RELEASED + slot)));
        }
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
