package gulliver.network;

import gulliver.access.IGulliverEntityInternal;
import gulliver.api.IResizeableEntity;
import gulliver.common.GulliverConfig;
import gulliver.platform.Services;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Server-side size broadcaster. Sends the entity's destination size to
 * every tracking client whenever it changes, and to a client that starts
 * tracking a resized entity (see MixinServerEntityPairing), so everyone
 * always sees the right size.
 *
 * The size on the wire is the entity's EFFECTIVE size: base x potion x
 * item already clamped to the limits the server applies to that creature.
 * A client never re-derives it from its own config.
 */
public final class SizeSync {
    private SizeSync() {}

    /** Called when a player starts tracking {@code entity}. */
    public static void onStartTracking(Entity entity, ServerPlayer tracker) {
        if (!(entity instanceof IGulliverEntityInternal sized)) return;
        float dest = sized.gulliver$getEffectiveDestSize();
        if (dest != 1.0F) {
            Services.platform().sendToPlayer(tracker, new Payloads.EntitySize(entity.getId(), dest));
        }
        gulliver.common.ShoulderHelper.sendCarryState(entity, tracker);
        if (entity instanceof gulliver.access.IGulliverHookInternal hook && hook.gulliver$getAnchor() != null) {
            net.minecraft.world.phys.Vec3 a = hook.gulliver$getAnchor();
            Services.platform().sendToPlayer(tracker, new Payloads.HookAnchor(entity.getId(), a.x, a.y, a.z));
        }
    }

    /**
     * Broadcast the entity's destination effective size to every tracking
     * player and, for players, to themselves.
     */
    public static void broadcast(Entity entity) {
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof IGulliverEntityInternal sized)) return;
        sized.gulliver$setAppliedSize(((IResizeableEntity) entity).getSizeMultiplier());
        Services.platform().sendToTrackingAndSelf(entity,
                new Payloads.EntitySize(entity.getId(), sized.gulliver$getEffectiveDestSize()));
    }

    /** The server's size range and dye switch, as clients have to know them. */
    private static Payloads.ServerSettings settings() {
        GulliverConfig.General g = GulliverConfig.INSTANCE.general;
        return new Payloads.ServerSettings(g.enableDyeResizing, (float) g.minEntitySize, (float) g.maxEntitySize);
    }

    /** On join: before any entity size, so the client knows its bounds first. */
    public static void sendSettings(ServerPlayer player) {
        Services.platform().sendToPlayer(player, settings());
    }

    /** After a config reload or an edit through the settings screen. */
    public static void sendSettingsToAll(MinecraftServer server) {
        Payloads.ServerSettings payload = settings();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Services.platform().sendToPlayer(player, payload);
        }
    }
}
