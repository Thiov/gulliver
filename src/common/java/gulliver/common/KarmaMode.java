package gulliver.common;

import gulliver.api.IResizeableLiving;
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

    /**
     * The loader's "player cloned" hook (respawn or End exit). Persists the
     * size fields onto the new ServerPlayer — without this it is created
     * with defaults (1.0) and every death would reset size even with karma
     * mode off.
     */
    public static void onClone(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        {
            gulliver.access.IGulliverEntityInternal oldI =
                    (gulliver.access.IGulliverEntityInternal) oldPlayer;
            gulliver.access.IGulliverEntityInternal newI =
                    (gulliver.access.IGulliverEntityInternal) newPlayer;
            float oldBase = oldI.gulliver$getSizeBaseMultiplier();
            float oldDest = oldI.gulliver$getSizeBaseDestMultiplier();
            newI.gulliver$setSizeBaseMultiplier(oldBase);
            newI.gulliver$setSizeBaseDestMultiplier(oldDest);
            newI.gulliver$setSizePotionMultiplier(oldI.gulliver$getSizePotionMultiplier());
            newI.gulliver$setSizeItemMultiplier(oldI.gulliver$getSizeItemMultiplier());
            float liveSize = oldBase * oldI.gulliver$getSizePotionMultiplier()
                    * oldI.gulliver$getSizeItemMultiplier();
            gulliver.common.SizeAttributes.applyForSize(newPlayer, liveSize);
            newPlayer.refreshDimensions();
            gulliver.network.SizeSync.broadcast(newPlayer);
            gulliver.Gulliver.LOGGER.debug(
                    "clone: size {} preserved (alive={})", oldBase, alive);
        }
    }

    /** The loader's "after respawn" hook. */
    public static void onRespawn(ServerPlayer newPlayer, boolean alive) {
        {
            // Belt-and-suspenders: re-apply size data + attributes here too,
            // in case vanilla respawn flow ran something between COPY_FROM
            // and now that reset attributes. Force-set HP and air to max
            // so player respawns full at the right size.
            gulliver.access.IGulliverEntityInternal newI =
                    (gulliver.access.IGulliverEntityInternal) newPlayer;
            float liveSize = newI.gulliver$getSizeBaseMultiplier()
                    * newI.gulliver$getSizePotionMultiplier()
                    * newI.gulliver$getSizeItemMultiplier();
            gulliver.common.SizeAttributes.applyForSize(newPlayer, liveSize);
            newPlayer.refreshDimensions();
            // Vanilla respawn sets HP to default 20 BEFORE attributes are
            // applied — overwrite to scaled max so giants don't respawn
            // with 20 of 80 hp showing 1/4 hearts filled. Death respawns
            // only: AFTER_RESPAWN also fires on dimension change
            // (alive=true, e.g. returning from the End), where forcing
            // full HP/air would hand out a free heal.
            if (!alive) {
                newPlayer.setHealth(newPlayer.getMaxHealth());
                newPlayer.setAirSupply(newPlayer.getMaxAirSupply());
            }
            gulliver.network.SizeSync.broadcast(newPlayer);

            // Karma mode: if enabled AND this is a real death (not
            // dimension change), reset size to spawn base after the
            // restoration above.
            if (!alive && GulliverConfig.INSTANCE.general.enableKarmaMode) {
                float spawnBase = GulliverEnvoy.getNewBasePlayerSize();
                ((IResizeableLiving) newPlayer).setBaseSize(spawnBase);
            }
        }
    }
}
