package gulliver.common;

import gulliver.access.IGulliverEntityInternal;
import gulliver.api.IResizeableEntity;
import gulliver.api.IResizeableLiving;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.Locale;

/**
 * Finding room for a body bigger than the place it appeared in: a giant
 * respawning in a bedroom, arriving through a three-high nether portal, or
 * spawning under a low ceiling. Vanilla only ever checked for a normal-size
 * body.
 *
 * Players are first moved to the nearest spot where the whole body fits
 * (sideways up to 16 blocks, upwards up to 24, then dropped onto the
 * ground; never into void, lava, fire or a cactus patch, and never into
 * chunks that aren't loaded). When the area is still loading the search
 * repeats for two seconds. If there really is no room, the body is shrunk
 * step by step until it fits where it stands, which is what a freshly
 * spawned mob does too. Creative players (invulnerable and able to fly out)
 * and spectators (no collision at all) are left where vanilla put them.
 */
public final class BodyFit {
    private BodyFit() {}

    private static final int RADIUS = 16;
    private static final int UP = 24;
    private static final int MAX_DROP = 24;
    /** Block cells the search may examine (box volume x boxes tried), so huge bodies stay cheap. */
    private static final long CELL_BUDGET = 3_000_000L;
    private static final int SETTLE_TICKS = 40;

    /** Whether the entity's body overlaps no block or solid entity where it stands. */
    public static boolean fits(Entity entity) {
        return entity.level().noCollision(entity, entity.getBoundingBox().deflate(1.0E-4D));
    }

    /**
     * Shrinks a body bigger than normal in steps (never below 1) until it
     * fits where it stands. Returns the base size it settled on.
     */
    public static float shrinkToFit(LivingEntity entity, float size) {
        IResizeableLiving sized = (IResizeableLiving) entity;
        float fit = size;
        while (fit > 1.0F && !fits(entity)) {
            fit = Math.max(1.0F, fit * 0.75F);
            sized.setBaseSize(fit);
        }
        return fit;
    }

    // ---- players arriving (respawn, dimension change) ----

    private enum Result { FITS, MOVED, WAIT, NONE }

    /** Call once the player stands where the game put them. */
    public static void settle(ServerPlayer player) {
        if (!needsRoom(player)) return;
        switch (tryPlace(player)) {
            case WAIT -> ((IGulliverEntityInternal) player).gulliver$setSettleTicks(SETTLE_TICKS);
            case NONE -> shrink(player);
            default -> { }
        }
    }

    /** Every tick of a player still looking for room (see MixinLivingEntitySizeTween). */
    public static void tickSettle(ServerPlayer player) {
        IGulliverEntityInternal access = (IGulliverEntityInternal) player;
        int left = access.gulliver$getSettleTicks() - 1;
        access.gulliver$setSettleTicks(Math.max(0, left));
        if (!needsRoom(player)) {
            access.gulliver$setSettleTicks(0);
            return;
        }
        if (left > 0 && left % 4 != 0) return;
        Result result = tryPlace(player);
        if (result == Result.WAIT && left > 0) return;
        access.gulliver$setSettleTicks(0);
        if (result == Result.WAIT || result == Result.NONE) shrink(player);
    }

    private static boolean needsRoom(ServerPlayer player) {
        if (player.isSpectator() || player.isCreative() || player.isPassenger()) return false;
        if (((IResizeableEntity) player).getSizeMultiplier() <= 1.05F) return false;
        return !fits(player);
    }

    private static Result tryPlace(ServerPlayer player) {
        if (fits(player)) return Result.FITS;
        boolean[] unloaded = { false };
        Vec3 spot = findSpot(player, unloaded);
        if (spot == null) return unloaded[0] ? Result.WAIT : Result.NONE;
        player.connection.teleport(spot.x, spot.y, spot.z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
        return Result.MOVED;
    }

    private static void shrink(ServerPlayer player) {
        IGulliverEntityInternal access = (IGulliverEntityInternal) player;
        float start = access.gulliver$getSizeBaseDestMultiplier();
        GulliverEnvoy.withoutGrowthBurst(() -> shrinkToFit(player, start));
        float now = ((IResizeableEntity) player).getSizeMultiplier();
        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "There was no room for your size here, so you were shrunk to %.2fx.", now)), true);
    }

    /**
     * The nearest position, upwards and outwards from where the body stands,
     * where it fits and has solid ground below. Null when there is none (the
     * flag says whether unloaded chunks cut the search short).
     */
    private static Vec3 findSpot(ServerPlayer player, boolean[] unloaded) {
        Level level = player.level();
        AABB box = player.getBoundingBox();
        int minY = level.getMinY();
        int maxY = minY + level.getHeight();
        long cells = Math.max(1L, (long) (Math.ceil(box.getXsize() + 1) * Math.ceil(box.getYsize() + 1) * Math.ceil(box.getZsize() + 1)));
        long boxes = CELL_BUDGET / cells;

        for (long key : Offsets.SORTED) {
            int dz = (int) (key & 63) - 32;
            int dy = (int) ((key >> 6) & 63);
            int dx = (int) ((key >> 12) & 63) - 32;
            if (dx == 0 && dy == 0 && dz == 0) continue;
            AABB at = box.move(dx, dy, dz);
            if (at.minY < minY + 1 || at.maxY > maxY) continue;
            if (!level.hasChunksAt(Mth.floor(at.minX), Mth.floor(at.minY), Mth.floor(at.minZ),
                    Mth.floor(at.maxX), Mth.floor(at.maxY), Mth.floor(at.maxZ))) {
                unloaded[0] = true;
                continue;
            }
            if (boxes-- <= 0) return null;
            if (!free(level, player, at)) continue;

            // Settle onto whatever is below; a long fall (void, chasm) is no place to stand.
            int dropped = 0;
            while (dropped < MAX_DROP && at.minY - 1 >= minY && boxes-- > 0
                    && free(level, player, at.move(0.0D, -1.0D, 0.0D))) {
                at = at.move(0.0D, -1.0D, 0.0D);
                dropped++;
            }
            if (dropped >= MAX_DROP || at.minY - 1 < minY) continue;
            for (double step = 0.5D; step > 0.01D; step *= 0.5D) {
                AABB lower = at.move(0.0D, -step, 0.0D);
                if (free(level, player, lower)) at = lower;
            }
            if (hazard(level, at)) continue;
            return player.position().add(at.minX - box.minX, at.minY - box.minY, at.minZ - box.minZ);
        }
        return null;
    }

    private static boolean free(Level level, Entity entity, AABB box) {
        return level.noCollision(entity, box.deflate(1.0E-4D));
    }

    /** Lava, fire, magma or a cactus inside the body or in the floor under it. */
    private static boolean hazard(Level level, AABB box) {
        AABB b = box.expandTowards(0.0D, -1.0D, 0.0D);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Mth.floor(b.minX); x <= Mth.floor(b.maxX - 1.0E-7D); x++) {
            for (int y = Mth.floor(b.minY); y <= Mth.floor(b.maxY - 1.0E-7D); y++) {
                for (int z = Mth.floor(b.minZ); z <= Mth.floor(b.maxZ - 1.0E-7D); z++) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (state.getFluidState().is(FluidTags.LAVA) || state.is(BlockTags.FIRE)
                            || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Search offsets (dx, dy >= 0, dz) nearest first, packed into longs; built on first use. */
    private static final class Offsets {
        static final long[] SORTED = build();

        private static long[] build() {
            long[] keys = new long[(2 * RADIUS + 1) * (2 * RADIUS + 1) * (UP + 1)];
            int i = 0;
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    for (int dy = 0; dy <= UP; dy++) {
                        long distance = (long) dx * dx + (long) dy * dy + (long) dz * dz;
                        keys[i++] = (distance << 18) | ((long) (dx + 32) << 12) | ((long) dy << 6) | (dz + 32);
                    }
                }
            }
            Arrays.sort(keys);
            return keys;
        }
    }
}
