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
                gulliver.access.IGulliverShoulderInternal carrierAccess =
                        (gulliver.access.IGulliverShoulderInternal) carrier;
                if (payload.attachmentType() == 0) {
                    java.util.UUID prev = carrierAccess.gulliver$getHeldEntity();
                    carrierAccess.gulliver$setHeldEntity(null);
                    if (passenger != null) {
                        ((gulliver.access.IGulliverShoulderInternal) passenger).gulliver$setHoldingEntity(null);
                    } else if (prev != null) {
                        // Passenger entity is gone — best-effort detach.
                    }
                } else if (passenger != null) {
                    carrierAccess.gulliver$setHeldEntity(passenger.getUUID());
                    ((gulliver.access.IGulliverShoulderInternal) passenger)
                            .gulliver$setHoldingEntity(carrier.getUUID());
                }
            });
        });
    }

    private static Entity entityById(int id) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? null : level.getEntity(id);
    }
}
