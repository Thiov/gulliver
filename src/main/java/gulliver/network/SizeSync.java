package gulliver.network;

import gulliver.access.IGulliverEntityInternal;
import gulliver.api.IResizeableEntity;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Server-side size broadcaster. Sends EntitySize payloads whenever an
 * entity's composed sizeMultiplier changes, and on entity-tracking-start
 * so newly-tracking clients see the correct size immediately.
 *
 * Mirrors the 1.6.4 mod's behavior: when sizeBaseMultiplier was written
 * server-side (via setBaseSize, the resize potion's tick handler, or
 * item-modifier change), Packet171EntitySize was sent to all tracking
 * clients carrying the FULL composed multiplier. This file is the
 * one-shot equivalent.
 */
public final class SizeSync {
    private SizeSync() {}

    public static void registerCommon() {
        EntityTrackingEvents.START_TRACKING.register((entity, tracker) -> {
            if (!(entity instanceof IGulliverEntityInternal sized)) return;
            float dest = composedDest(sized);
            if (dest == 1.0F) return;
            ServerPlayNetworking.send(tracker, new Payloads.EntitySize(entity.getId(), dest));
        });
    }

    /**
     * Broadcast the entity's destination composed size to every tracking
     * player. The packet carries the FINAL value the entity is tweening
     * toward, not the live (mid-lerp) value — clients copy it into their
     * own gulliver$sizeBaseDestMultiplier and run their own local lerp
     * with the same formula starting from their stored base, so server
     * and client end up at the same value at the same time.
     *
     * Players seeing themselves resize (entity == ServerPlayer) are
     * included via PlayerLookup.tracking + an explicit self-send (vanilla
     * tracking does not include the entity itself).
     */
    public static void broadcast(Entity entity) {
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof IGulliverEntityInternal sized)) return;
        float dest = composedDest(sized);
        Payloads.EntitySize p = new Payloads.EntitySize(entity.getId(), dest);
        for (ServerPlayer viewer : PlayerLookup.tracking(entity)) {
            ServerPlayNetworking.send(viewer, p);
        }
        if (entity instanceof ServerPlayer self) {
            ServerPlayNetworking.send(self, p);
        }
    }

    private static float composedDest(IGulliverEntityInternal sized) {
        return sized.gulliver$getSizeBaseDestMultiplier()
                * sized.gulliver$getSizePotionMultiplier()
                * sized.gulliver$getSizeItemMultiplier();
    }
}
