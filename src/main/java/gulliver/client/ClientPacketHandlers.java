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
                // Wired in Phase 12 (shoulder entity). Receiver registered now
                // so the channel exists end-to-end; no-op until the shoulder
                // mechanic and the @Unique heldEntity field land.
            });
        });
    }

    private static Entity entityById(int id) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? null : level.getEntity(id);
    }
}
