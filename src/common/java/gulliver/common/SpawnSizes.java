package gulliver.common;

import gulliver.access.IGulliverEntityInternal;
import gulliver.api.IResizeableLiving;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Applies the configured spawn sizes (config "spawnSize": per class
 * animal / monster / npc / player, per-entity overrides, ranges like
 * "0.5-2", sets like "0.5,1,2", heights like "5'9\"") the first time an
 * entity ticks on the server. Entities loaded from a save with Gulliver
 * data, and respawned players, are already initialised and keep their
 * size.
 */
public final class SpawnSizes {
    private SpawnSizes() {}

    public static void initIfNeeded(LivingEntity entity) {
        IGulliverEntityInternal internal = (IGulliverEntityInternal) entity;
        if (internal.gulliver$isSizeInitialized()) return;
        internal.gulliver$setSizeInitialized(true);
        if (GulliverEnvoy.isDragonEntity(entity)) return;
        float size = entity instanceof Player
                ? GulliverEnvoy.getNewBasePlayerSize()
                : GulliverEnvoy.getNewBaseEntitySize(entity);
        if (GulliverEnvoy.isInvalidSize(size) || size == 1.0F) return;
        // Spawning at a size is not growing: don't burst the surroundings.
        GulliverEnvoy.withoutGrowthBurst(() -> ((IResizeableLiving) entity).setBaseSize(size));
    }
}
