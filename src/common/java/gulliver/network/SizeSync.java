package gulliver.network;

import gulliver.access.IGulliverEntityInternal;
import gulliver.platform.Services;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Server-side size broadcaster. Sends the entity's destination size to
 * every tracking client whenever it changes, and to a client that starts
 * tracking a resized entity (see MixinServerEntityPairing), so everyone
 * always sees the right size.
 */
public final class SizeSync {
    private SizeSync() {}

    /** Called when a player starts tracking {@code entity}. */
    public static void onStartTracking(Entity entity, ServerPlayer tracker) {
        if (!(entity instanceof IGulliverEntityInternal sized)) return;
        float dest = composedDest(sized);
        if (dest == 1.0F) return;
        Services.platform().sendToPlayer(tracker, new Payloads.EntitySize(entity.getId(), dest));
    }

    /**
     * Broadcast the entity's destination composed size to every tracking
     * player and, for players, to themselves.
     */
    public static void broadcast(Entity entity) {
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof IGulliverEntityInternal sized)) return;
        Services.platform().sendToTrackingAndSelf(entity,
                new Payloads.EntitySize(entity.getId(), composedDest(sized)));
    }

    private static float composedDest(IGulliverEntityInternal sized) {
        return sized.gulliver$getSizeBaseDestMultiplier()
                * sized.gulliver$getSizePotionMultiplier()
                * sized.gulliver$getSizeItemMultiplier();
    }
}
