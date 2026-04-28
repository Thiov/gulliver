package gulliver.common;

import gulliver.api.IResizeableLiving;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.level.ServerPlayer;

/**
 * 1.6.4 karma mode: when GulliverConfig.general.enableKarmaMode is true,
 * a player's base size is reset to GulliverEnvoy.getNewBasePlayerSize()
 * on every respawn — losing all the size adjustments they accumulated
 * before death. The /instantkarma command does the same thing on
 * demand, but karma mode = automatic.
 *
 * Mirrors GulliverConfigHelper enable-karma-mode key, which the original
 * mod consulted from a death-respawn callback.
 */
public final class KarmaMode {
    private KarmaMode() {}

    public static void registerCommon() {
        // Persist size fields across death respawn / dimension change.
        // Without this, the new ServerPlayer entity is created with
        // default fields (1.0F) — base size effectively reset on every
        // death even when karma mode is off.
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            gulliver.access.IGulliverEntityInternal oldI =
                    (gulliver.access.IGulliverEntityInternal) oldPlayer;
            gulliver.access.IGulliverEntityInternal newI =
                    (gulliver.access.IGulliverEntityInternal) newPlayer;
            newI.gulliver$setSizeBaseMultiplier(oldI.gulliver$getSizeBaseMultiplier());
            newI.gulliver$setSizeBaseDestMultiplier(oldI.gulliver$getSizeBaseDestMultiplier());
            newI.gulliver$setSizePotionMultiplier(oldI.gulliver$getSizePotionMultiplier());
            newI.gulliver$setSizeItemMultiplier(oldI.gulliver$getSizeItemMultiplier());
            // Refresh dimensions + sync to clients now that the size is restored.
            newPlayer.refreshDimensions();
            gulliver.network.SizeSync.broadcast(newPlayer);
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            // alive=true means dimension change / end-portal exit, not a real
            // death. Only reset on actual death respawns.
            if (alive) return;
            if (!GulliverConfig.INSTANCE.general.enableKarmaMode) return;
            float spawnBase = GulliverEnvoy.getNewBasePlayerSize();
            ((IResizeableLiving) (ServerPlayer) newPlayer).setBaseSize(spawnBase);
        });
    }
}
