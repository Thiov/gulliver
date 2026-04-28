package gulliver.network;

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
            if (!(entity instanceof IResizeableEntity sized)) return;
            float m = sized.getSizeMultiplier();
            if (m == 1.0F) return;
            ServerPlayNetworking.send(tracker, new Payloads.EntitySize(entity.getId(), m));
        });
    }

    /**
     * Broadcast the entity's current composed size to every tracking player.
     * Players seeing themselves resize (the entity itself, when it's a
     * ServerPlayer) are included via PlayerLookup.tracking.
     */
    public static void broadcast(Entity entity) {
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof IResizeableEntity sized)) return;
        float m = sized.getSizeMultiplier();
        Payloads.EntitySize p = new Payloads.EntitySize(entity.getId(), m);
        for (ServerPlayer viewer : PlayerLookup.tracking(entity)) {
            ServerPlayNetworking.send(viewer, p);
        }
        // PlayerLookup.tracking does NOT include the entity itself when it's a
        // player, so explicitly send to a player's own client too.
        if (entity instanceof ServerPlayer self) {
            ServerPlayNetworking.send(self, p);
        }
    }
}
