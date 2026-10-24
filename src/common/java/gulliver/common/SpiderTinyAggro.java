package gulliver.common;

import gulliver.api.IResizeableEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
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
 * Implementation: each predator looks for prey once a second (staggered
 * by entity id, so the scans spread over the ticks instead of landing
 * together): with no current target, find the nearest tiny within 16
 * blocks and call setTarget. Skipping mobs with an existing target
 * avoids interrupting whatever they're already doing. Prey must be
 * visible (not invisible, in line of sight) and not the predator's own
 * kind. Neutral mobs (bees) only attack while angry, so they are also
 * made angry; vanilla's anger bookkeeping then follows the target.
 *
 * The 16-block range matches the modern NearestAttackableTargetGoal
 * default detection range. This is run only when the level isn't
 * peaceful — peaceful disables hostile mob targeting at the world
 * level, and we don't want to override that — and only while a tiny
 * creature exists at all (no entity walk on servers without any).
 */
public final class SpiderTinyAggro {
    private SpiderTinyAggro() {}

    private static final int SCAN_INTERVAL_TICKS = 20;
    private static final double SCAN_RADIUS = 16.0D;
    private static final double SCAN_RADIUS_SQ = SCAN_RADIUS * SCAN_RADIUS;

    /** Called at the end of every ServerLevel tick (MixinServerLevelTick). */
    public static void onLevelTick(ServerLevel level) {
        if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) return;
        if (!GulliverEnvoy.tinySeenRecently(level)) return;
        int phase = level.getServer().getTickCount() % SCAN_INTERVAL_TICKS;

        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof Mob mob)) continue;
            if (mob.getId() % SCAN_INTERVAL_TICKS != phase) continue;
            if (!isSmallCreaturePredator(mob.getType())) continue;
            if (mob.getTarget() != null && mob.getTarget().isAlive()) continue;

            LivingEntity nearest = findNearestTiny(level, mob);
            if (nearest != null) {
                mob.setTarget(nearest);
                // A bee attacks only while angry (BeeAttackGoal).
                if (mob instanceof NeutralMob angry && !angry.isAngry()) angry.startPersistentAngerTimer();
            }
        }
    }

    private static LivingEntity findNearestTiny(ServerLevel level, Mob mob) {
        AABB scan = mob.getBoundingBox().inflate(SCAN_RADIUS);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, scan,
                e -> e != mob && e.isAlive() && ((IResizeableEntity) e).isTiny()
                        && e.getType() != mob.getType()   // spiders don't hunt spiders
                        && !e.isInvisible()
                        && !isUntargetablePlayer(e));
        LivingEntity best = null;
        double bestSq = SCAN_RADIUS_SQ;
        for (LivingEntity cand : nearby) {
            double dsq = mob.distanceToSqr(cand);
            // Line of sight last: the ray cast is the costly test.
            if (dsq < bestSq && mob.getSensing().hasLineOfSight(cand)) {
                bestSq = dsq;
                best = cand;
            }
        }
        return best;
    }

    /**
     * Creative and spectator players are outside survival rules — vanilla
     * targeting skips them, and force-feeding them to setTarget would have
     * mobs path after players they can never hurt.
     */
    private static boolean isUntargetablePlayer(LivingEntity e) {
        return e instanceof net.minecraft.world.entity.player.Player p
                && (p.isCreative() || p.isSpectator());
    }

    private static boolean isSmallCreaturePredator(EntityType<?> type) {
        return type == EntityTypes.SPIDER
            || type == EntityTypes.CAVE_SPIDER
            || type == EntityTypes.SILVERFISH
            || type == EntityTypes.ENDERMITE
            || type == EntityTypes.BEE;
    }
}
