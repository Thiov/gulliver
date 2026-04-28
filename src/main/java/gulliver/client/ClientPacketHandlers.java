package gulliver.client;

import gulliver.access.IGulliverEntityInternal;
import gulliver.network.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

public final class ClientPacketHandlers {
    private ClientPacketHandlers() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(Payloads.EntitySize.TYPE, (payload, ctx) -> {
            ctx.client().execute(() -> {
                Entity e = entityById(payload.entityId());
                if (e == null) return;
                // Server sends the DESTINATION composed multiplier (base_dest
                // × potion × item). Store it as the client's destination; the
                // local MixinLivingEntitySizeTween will lerp the live base
                // toward it using the same formula as the server. Potion and
                // item kept at 1.0 locally so the tween's product matches the
                // server-authoritative value exactly.
                IGulliverEntityInternal sized = (IGulliverEntityInternal) e;
                sized.gulliver$setSizeBaseDestMultiplier(payload.sizeMult());
                sized.gulliver$setSizePotionMultiplier(1.0F);
                sized.gulliver$setSizeItemMultiplier(1.0F);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(Payloads.AttachEntitySpecial.TYPE, (payload, ctx) -> {
            ctx.client().execute(() -> {
                Entity carrier = entityById(payload.vehicleEntityId());
                Entity passenger = entityById(payload.entityId());
                if (carrier == null) return;
                gulliver.access.IGulliverShoulderInternal cs =
                        (gulliver.access.IGulliverShoulderInternal) carrier;
                byte slot = payload.attachmentType();
                java.util.UUID pid = passenger == null ? null : passenger.getUUID();
                // Detach this passenger from any slot it currently occupies
                // before assigning the new slot, so /shoulderentity-style
                // hand→shoulder cycling stays consistent.
                if (pid != null) {
                    if (pid.equals(cs.gulliver$getHandEntity()))     cs.gulliver$setHandEntity(null);
                    if (pid.equals(cs.gulliver$getRightShoulder()))  cs.gulliver$setRightShoulder(null);
                    if (pid.equals(cs.gulliver$getLeftShoulder()))   cs.gulliver$setLeftShoulder(null);
                }
                switch (slot) {
                    case gulliver.common.ShoulderHelper.SLOT_DETACH:
                        // slot-clear is enough; if passenger is known, also
                        // clear its holding-back-ref.
                        if (passenger != null) {
                            ((gulliver.access.IGulliverShoulderInternal) passenger)
                                    .gulliver$setHoldingEntity(null);
                        }
                        break;
                    case gulliver.common.ShoulderHelper.SLOT_HAND:
                        if (pid != null) cs.gulliver$setHandEntity(pid);
                        if (passenger != null) ((gulliver.access.IGulliverShoulderInternal) passenger)
                                .gulliver$setHoldingEntity(carrier.getUUID());
                        break;
                    case gulliver.common.ShoulderHelper.SLOT_RIGHT:
                        if (pid != null) cs.gulliver$setRightShoulder(pid);
                        if (passenger != null) ((gulliver.access.IGulliverShoulderInternal) passenger)
                                .gulliver$setHoldingEntity(carrier.getUUID());
                        break;
                    case gulliver.common.ShoulderHelper.SLOT_LEFT:
                        if (pid != null) cs.gulliver$setLeftShoulder(pid);
                        if (passenger != null) ((gulliver.access.IGulliverShoulderInternal) passenger)
                                .gulliver$setHoldingEntity(carrier.getUUID());
                        break;
                }
            });
        });
    }

    private static Entity entityById(int id) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? null : level.getEntity(id);
    }
}
