package gulliver.common;

import gulliver.Gulliver;
import gulliver.access.IGulliverEntityInternal;
import gulliver.api.IResizeableEntity;
import gulliver.api.IResizeableLiving;
import gulliver.network.SizeSync;
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
     * The loader's "player cloned" hook (death respawn or End exit). Moves
     * the size fields onto the new ServerPlayer, which vanilla creates with
     * defaults. The potion multiplier only survives when the resizing
     * effect itself did (alive = End exit keeps effects); after a death the
     * effect is gone, so its multiplier must go too — otherwise a Strong
     * Embiggening potion drunk before dying left a permanent giant.
     */
    public static void onClone(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        IGulliverEntityInternal oldI = (IGulliverEntityInternal) oldPlayer;
        IGulliverEntityInternal newI = (IGulliverEntityInternal) newPlayer;
        newI.gulliver$setSizeBaseMultiplier(oldI.gulliver$getSizeBaseMultiplier());
        newI.gulliver$setSizeBaseDestMultiplier(oldI.gulliver$getSizeBaseDestMultiplier());
        newI.gulliver$setSizeItemMultiplier(oldI.gulliver$getSizeItemMultiplier());
        newI.gulliver$setSizePotionMultiplier(alive ? oldI.gulliver$getSizePotionMultiplier() : 1.0F);
        newI.gulliver$setSizeInitialized(true);
        // Attributes and the client sync follow in onRespawn, once the new
        // player is actually in its level.
        newPlayer.refreshDimensions();
        Gulliver.LOGGER.debug("clone: base size {} kept (alive={})", oldI.gulliver$getSizeBaseMultiplier(), alive);
    }

    /** The loader's "after respawn" hook (death or End exit). */
    public static void onRespawn(ServerPlayer player, boolean alive) {
        if (!alive && GulliverConfig.INSTANCE.general.enableKarmaMode) {
            // Karma: a real death resets you to the configured spawn size.
            ((IResizeableLiving) player).setBaseSize(GulliverEnvoy.getNewBasePlayerSize());
        }
        refresh(player);
        // Vanilla restores 20 HP before our max-health modifier is back, so a
        // giant would respawn with a fraction of its hearts. Deaths only —
        // on an End exit this would be a free heal.
        if (!alive) {
            player.setHealth(player.getMaxHealth());
            player.setAirSupply(player.getMaxAirSupply());
        }
    }

    /**
     * Changing dimension (portal, /tp) rebuilds the client's LocalPlayer
     * with default fields, so the size has to be sent again.
     */
    public static void onChangedDimension(ServerPlayer player) {
        refresh(player);
    }

    private static void refresh(ServerPlayer player) {
        SizeAttributes.applyForSize(player, ((IResizeableEntity) player).getSizeMultiplier());
        player.refreshDimensions();
        SizeSync.broadcast(player);
    }
}
