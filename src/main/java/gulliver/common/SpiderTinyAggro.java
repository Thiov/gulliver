package gulliver.common;

import gulliver.api.IResizeableEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Forces spiders / cave spiders / silverfish / endermites / bees to
 * actively pursue tiny entities, regardless of light level or other
 * vanilla aggro gates. The 1.6.4 mod implemented this via per-mob
 * minTargetSize fields (og.java line 108-114): non-arthropods see
 * targets in [0.3, 20], arthropods see [0.1, 20], so tinies were
 * invisible to most mobs but visible to arthropods.
 *
 * Modern MC has no equivalent field — vanilla light/peaceful gating
 * keeps spiders passive in daylight. The user wants spiders ALWAYS
 * hostile to tinies (narratively: spiders eat bugs, tinies are bugs).
 *
 * Implementation: every 20 ticks (1s) walk the level's mob list, and
 * for each spider/insect with no current target, find the nearest
 * tiny within 16 blocks and call setTarget. Skipping mobs with an
 * existing target avoids interrupting whatever they're already doing.
 *
 * The 16-block range matches the modern NearestAttackableTargetGoal
 * default detection range. This is run only when the level isn't
 * peaceful — peaceful disables hostile mob targeting at the world
 * level, and we don't want to override that.
 */
public final class SpiderTinyAggro {
    private SpiderTinyAggro() {}

    private static final int SCAN_INTERVAL_TICKS = 20;
    private static final double SCAN_RADIUS = 16.0D;
    private static final double SCAN_RADIUS_SQ = SCAN_RADIUS * SCAN_RADIUS;

    public static void registerCommon() {
        ServerTickEvents.END_LEVEL_TICK.register(SpiderTinyAggro::onLevelTick);
    }

    private static void onLevelTick(ServerLevel level) {
        if (level.getServer().getTickCount() % SCAN_INTERVAL_TICKS != 0) return;
        if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) return;

        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof Mob mob)) continue;
            if (!isSmallCreaturePredator(mob.getType())) continue;
            if (mob.getTarget() != null && mob.getTarget().isAlive()) continue;

            LivingEntity nearest = findNearestTiny(level, mob);
            if (nearest != null) {
                mob.setTarget(nearest);
            }
        }
    }

    private static LivingEntity findNearestTiny(ServerLevel level, Mob mob) {
        AABB scan = mob.getBoundingBox().inflate(SCAN_RADIUS);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, scan,
                e -> e != mob && e.isAlive() && ((IResizeableEntity) e).isTiny());
        LivingEntity best = null;
        double bestSq = SCAN_RADIUS_SQ;
        for (LivingEntity cand : nearby) {
            double dsq = mob.distanceToSqr(cand);
            if (dsq < bestSq) {
                bestSq = dsq;
                best = cand;
            }
        }
        return best;
    }

    private static boolean isSmallCreaturePredator(EntityType<?> type) {
        return type == EntityType.SPIDER
            || type == EntityType.CAVE_SPIDER
            || type == EntityType.SILVERFISH
            || type == EntityType.ENDERMITE
            || type == EntityType.BEE;
    }
}
