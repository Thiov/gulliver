package gulliver.client;

import gulliver.mixin.IGulliverEntityInternal;
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
                // Server is the source of truth for sizeMultiplier. Store the
                // entire composed value as the base on the client; potion and
                // item modifiers stay 1.0F locally so getSizeMultiplier()
                // reproduces the server-authoritative number exactly.
                IGulliverEntityInternal sized = (IGulliverEntityInternal) e;
                sized.gulliver$setSizeBaseMultiplier(payload.sizeMult());
                sized.gulliver$setSizePotionMultiplier(1.0F);
                sized.gulliver$setSizeItemMultiplier(1.0F);
                e.refreshDimensions();
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(Payloads.AttachEntitySpecial.TYPE, (payload, ctx) -> {
            ctx.client().execute(() -> {
                Entity carrier = entityById(payload.vehicleEntityId());
                Entity passenger = entityById(payload.entityId());
                if (carrier == null) return;
                gulliver.mixin.IGulliverShoulderInternal carrierAccess =
                        (gulliver.mixin.IGulliverShoulderInternal) carrier;
                if (payload.attachmentType() == 0) {
                    java.util.UUID prev = carrierAccess.gulliver$getHeldEntity();
                    carrierAccess.gulliver$setHeldEntity(null);
                    if (passenger != null) {
                        ((gulliver.mixin.IGulliverShoulderInternal) passenger).gulliver$setHoldingEntity(null);
                    } else if (prev != null) {
                        // Passenger entity is gone — best-effort detach.
                    }
                } else if (passenger != null) {
                    carrierAccess.gulliver$setHeldEntity(passenger.getUUID());
                    ((gulliver.mixin.IGulliverShoulderInternal) passenger)
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
