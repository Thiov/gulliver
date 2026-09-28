package gulliver.client;

import gulliver.access.IGulliverEntityInternal;
import gulliver.access.IGulliverShoulderInternal;
import gulliver.common.ShoulderHelper;
import gulliver.network.GulliverPayload;
import gulliver.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/** Client-thread handlers for Gulliver's clientbound packets. */
public final class ClientPacketHandlers {
    private ClientPacketHandlers() {}

    public static void handle(GulliverPayload payload) {
        if (payload instanceof Payloads.EntitySize p) {
            onEntitySize(p);
        } else if (payload instanceof Payloads.GroundShock p) {
            TremorHandler.groundShock(p.x(), p.y(), p.z(), p.sourceSize(), p.strength());
        } else if (payload instanceof Payloads.AttachEntitySpecial p) {
            onAttach(p);
        } else if (payload instanceof Payloads.HookAnchor p) {
            Entity hook = entityById(p.hookId());
            if (hook instanceof gulliver.access.IGulliverHookInternal h) {
                h.gulliver$setAnchor(new net.minecraft.world.phys.Vec3(p.x(), p.y(), p.z()));
                hook.setPos(p.x(), p.y(), p.z());
            }
        }
    }

    private static void onEntitySize(Payloads.EntitySize payload) {
        Entity e = entityById(payload.entityId());
        if (e == null) return;
        IGulliverEntityInternal sized = (IGulliverEntityInternal) e;
        float newSize = payload.sizeMult();
        sized.gulliver$setSizeBaseDestMultiplier(newSize);
        sized.gulliver$setSizePotionMultiplier(1.0F);
        sized.gulliver$setSizeItemMultiplier(1.0F);
        // Entity just spawned client-side (live base still the default
        // 1.0): snap the live base too, so it never flashes at 1.0 before
        // growing to its saved size. Later resizes only move the target
        // and the tween takes it from there.
        if (sized.gulliver$getSizeBaseMultiplier() == 1.0F && newSize != 1.0F) {
            sized.gulliver$setSizeBaseMultiplier(newSize);
            e.refreshDimensions();
        }
    }

    private static void onAttach(Payloads.AttachEntitySpecial payload) {
        Entity carrier = entityById(payload.vehicleEntityId());
        Entity passenger = entityById(payload.entityId());
        if (carrier == null) {
            // Orphan-release detach (carrier already gone, id -1): still
            // unfreeze the passenger's client copy so it doesn't stay
            // pinned to its last carried position.
            if (passenger != null && (payload.attachmentType() == ShoulderHelper.SLOT_DETACH
                    || payload.attachmentType() > ShoulderHelper.RELEASED)) {
                ((IGulliverShoulderInternal) passenger).gulliver$setHoldingEntity(null);
            }
            return;
        }
        IGulliverShoulderInternal cs = (IGulliverShoulderInternal) carrier;
        byte slot = payload.attachmentType();
        if (slot > ShoulderHelper.RELEASED) {
            // A slot was emptied; the entity may already be gone here.
            switch (slot - ShoulderHelper.RELEASED) {
                case ShoulderHelper.SLOT_HAND -> cs.gulliver$setHandEntity(null);
                case ShoulderHelper.SLOT_RIGHT -> cs.gulliver$setRightShoulder(null);
                case ShoulderHelper.SLOT_LEFT -> cs.gulliver$setLeftShoulder(null);
                default -> { }
            }
            if (passenger != null) ((IGulliverShoulderInternal) passenger).gulliver$setHoldingEntity(null);
            return;
        }
        UUID pid = passenger == null ? null : passenger.getUUID();
        // Remove the passenger from whatever slot it had before assigning
        // the new one, so hand <-> shoulder cycling stays consistent.
        if (pid != null) {
            if (pid.equals(cs.gulliver$getHandEntity())) cs.gulliver$setHandEntity(null);
            if (pid.equals(cs.gulliver$getRightShoulder())) cs.gulliver$setRightShoulder(null);
            if (pid.equals(cs.gulliver$getLeftShoulder())) cs.gulliver$setLeftShoulder(null);
        }
        IGulliverShoulderInternal ps = (IGulliverShoulderInternal) passenger;
        switch (slot) {
            case ShoulderHelper.SLOT_DETACH -> {
                if (passenger != null) ps.gulliver$setHoldingEntity(null);
            }
            case ShoulderHelper.SLOT_HAND -> {
                if (pid != null) cs.gulliver$setHandEntity(pid);
                if (passenger != null) ps.gulliver$setHoldingEntity(carrier.getUUID());
            }
            case ShoulderHelper.SLOT_RIGHT -> {
                if (pid != null) cs.gulliver$setRightShoulder(pid);
                if (passenger != null) ps.gulliver$setHoldingEntity(carrier.getUUID());
            }
            case ShoulderHelper.SLOT_LEFT -> {
                if (pid != null) cs.gulliver$setLeftShoulder(pid);
                if (passenger != null) ps.gulliver$setHoldingEntity(carrier.getUUID());
            }
            default -> { }
        }
    }

    private static Entity entityById(int id) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? null : level.getEntity(id);
    }
}
