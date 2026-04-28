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
