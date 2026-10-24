package gulliver.common;

import gulliver.access.IGulliverEntityInternal;
import gulliver.api.IResizeableEntity;
import gulliver.api.IResizeableLiving;
import gulliver.network.SizeSync;
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
        GulliverEnvoy.withoutGrowthBurst(() -> {
            IResizeableLiving sized = (IResizeableLiving) entity;
            sized.setBaseSize(size);
            if (size <= 1.0F) return;
            // Vanilla checked the spawn space at normal size: shrink back
            // until the body fits instead of suffocating in the ceiling.
            BodyFit.shrinkToFit(entity, size);
            // A giant is born with the hearts its size gives it.
            entity.setHealth(entity.getMaxHealth());
        });
    }

    /**
     * A mob converted into another kind (drowning, curing, lightning,
     * freezing ...) is the same creature: it keeps the size, the new body
     * starting with the hearts that size gives it, as a fresh mob does.
     */
    public static void inherit(LivingEntity from, LivingEntity to) {
        IGulliverEntityInternal a = (IGulliverEntityInternal) from;
        IGulliverEntityInternal b = (IGulliverEntityInternal) to;
        b.gulliver$setSizeBaseMultiplier(a.gulliver$getSizeBaseMultiplier());
        b.gulliver$setSizeBaseDestMultiplier(a.gulliver$getSizeBaseDestMultiplier());
        b.gulliver$setSizePotionMultiplier(a.gulliver$getSizePotionMultiplier());
        b.gulliver$setSizeItemMultiplier(a.gulliver$getSizeItemMultiplier());
        b.gulliver$setSizeInitialized(a.gulliver$isSizeInitialized());
        to.refreshDimensions();
        if (to.level().isClientSide()) return;
        float size = ((IResizeableEntity) to).getSizeMultiplier();
        SizeAttributes.applyForSize(to, size);
        if (size != 1.0F) to.setHealth(to.getMaxHealth());
        SizeSync.broadcast(to);
    }
}
