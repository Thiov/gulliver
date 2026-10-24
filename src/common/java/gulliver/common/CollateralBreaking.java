package gulliver.common;

import gulliver.api.IResizeableEntity;
import gulliver.platform.Services;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Every block a size power breaks as a side effect goes through here: the
 * fist crater, the growth burst and the cracking of brittle floors. One
 * place so they all behave like a player breaking the block:
 *
 *  - the loader's break event (claim and protection mods, spawn
 *    protection, adventure mode) can veto it, when a player is behind it;
 *  - drops are computed with the real tool and the breaker as the loot
 *    entity, so Silk Touch and Fortune count, and only blocks the tool may
 *    harvest drop anything (a bare-handed breaker gets no ore);
 *  - a player pays the usual costs: mined-block stat, exhaustion, and (for
 *    the fist crater only) wear on the held tool;
 *  - creative players break blocks without drops, as in vanilla;
 *  - unbreakable blocks, fluids and block entities are never touched.
 *
 * The growth burst of a big body can sweep thousands of blocks in one go,
 * so it works through a per-tick budget instead of all at once.
 */
public final class CollateralBreaking {
    private CollateralBreaking() {}

    public enum Mode {
        /** A fist punch: the held tool wears like it does when mining. */
        CRATER,
        /** Body mass (growth, standing on a brittle floor): no tool wear. */
        BODY
    }

    public enum Result {
        SKIPPED, BROKEN,
        /** Broken, and it was the last of the held tool: stop swinging. */
        BROKEN_TOOL_LOST
    }

    /** Blocks one growth burst may break per level tick. */
    private static final int BURST_BREAKS_PER_TICK = 256;
    /** Positions one growth burst may look at per level tick (skipped ones cost time too). */
    private static final int BURST_LOOKS_PER_TICK = 4096;

    public static Result breakBlock(ServerLevel level, BlockPos pos, Entity breaker, Mode mode) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) return Result.SKIPPED;
        if (state.hasBlockEntity()) return Result.SKIPPED;
        if (state.getDestroySpeed(level, pos) < 0.0F) return Result.SKIPPED;

        ServerPlayer player = breaker instanceof ServerPlayer sp ? sp : null;
        if (player != null && !Services.platform().mayBreakBlock(level, pos, state, player)) return Result.SKIPPED;

        boolean creative = player != null && player.isCreative();
        ItemStack tool = player != null ? player.getMainHandItem() : ItemStack.EMPTY;
        // The loot context needs the tool as it was, even if breaking the block uses it up.
        ItemStack usedTool = tool.copy();
        boolean harvest = !creative
                && (player != null ? player.hasCorrectToolForDrops(state) : !state.requiresCorrectToolForDrops());

        if (!level.destroyBlock(pos, false, breaker)) return Result.SKIPPED;

        boolean toolLost = false;
        if (mode == Mode.CRATER && player != null && !creative && !tool.isEmpty()) {
            tool.mineBlock(level, state, pos, player);
            toolLost = tool.isEmpty();
        }
        if (harvest) {
            if (player != null) {
                state.getBlock().playerDestroy(level, player, pos, state, null, usedTool);
            } else {
                Block.dropResources(state, level, pos, null, breaker, ItemStack.EMPTY);
            }
        }
        return toolLost ? Result.BROKEN_TOOL_LOST : Result.BROKEN;
    }

    // ---- growth burst ----

    /** Blocks still to break for one growth, spread over the next ticks. */
    private static final class Burst {
        final UUID entity;
        final LongArrayList positions;
        int next;

        Burst(UUID entity, LongArrayList positions) {
            this.entity = entity;
            this.positions = positions;
        }

        boolean done() {
            return next >= positions.size();
        }
    }

    /** Pending bursts per level. Keys are weak and values hold no level reference, so unloading a world frees it. */
    private static final Map<ServerLevel, ArrayDeque<Burst>> PENDING = new WeakHashMap<>();

    /**
     * Whether a growing body of this size bursts through the block: its
     * hardness is below the size, plus cobwebs for huge bodies (1.6.4).
     */
    private static boolean burstable(ServerLevel level, BlockPos pos, BlockState st, float size, boolean huge) {
        if (st.isAir() || !st.getFluidState().isEmpty() || st.hasBlockEntity()) return false;
        float h = st.getDestroySpeed(level, pos);
        if (h < 0.0F) return false;
        return h < size || (huge && st.getBlock() instanceof WebBlock);
    }

    /**
     * Starts a growth burst: finds the blocks newly swept by the grown
     * body (those the old box did not already overlap), breaks as many as
     * this tick's budget allows and queues the rest.
     */
    public static void growthBurst(LivingEntity entity, AABB oldBox) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        AABB newBox = entity.getBoundingBox();
        float size = ((IResizeableEntity) entity).getSizeMultiplier();
        boolean huge = ((IResizeableEntity) entity).isHuge();

        int x1 = net.minecraft.util.Mth.floor(newBox.minX);
        int y1 = net.minecraft.util.Mth.floor(newBox.minY);
        int z1 = net.minecraft.util.Mth.floor(newBox.minZ);
        int x2 = net.minecraft.util.Mth.floor(newBox.maxX - 1.0E-7D);
        int y2 = net.minecraft.util.Mth.floor(newBox.maxY - 1.0E-7D);
        int z2 = net.minecraft.util.Mth.floor(newBox.maxZ - 1.0E-7D);
        LongArrayList found = new LongArrayList();
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2 && y > level.getMinY(); y++) {
                for (int z = z1; z <= z2; z++) {
                    mp.set(x, y, z);
                    // Only the newly-swept shell: skip cubes the old body already overlapped.
                    if (oldBox.intersects(new AABB(mp))) continue;
                    if (burstable(level, mp, level.getBlockState(mp), size, huge)) found.add(mp.asLong());
                }
            }
        }
        if (found.isEmpty()) return;

        Burst burst = new Burst(entity.getUUID(), found);
        work(level, burst, BURST_BREAKS_PER_TICK);
        if (!burst.done()) PENDING.computeIfAbsent(level, l -> new ArrayDeque<>()).add(burst);
    }

    /** Continues queued growth bursts of this level; called once per level tick. */
    public static void tick(ServerLevel level) {
        if (PENDING.isEmpty()) return;
        ArrayDeque<Burst> queue = PENDING.get(level);
        if (queue == null) return;
        int budget = BURST_BREAKS_PER_TICK;
        for (Iterator<Burst> it = queue.iterator(); it.hasNext() && budget > 0; ) {
            Burst b = it.next();
            budget = work(level, b, budget);
            if (b.done()) it.remove();
        }
        if (queue.isEmpty()) PENDING.remove(level);
    }

    /** Breaks up to {@code budget} of the burst's blocks; returns the budget left. */
    private static int work(ServerLevel level, Burst b, int budget) {
        if (!(level.getEntity(b.entity) instanceof LivingEntity entity) || !entity.isAlive()
                || !GulliverEnvoy.canSizeGrief(entity)) {
            b.next = b.positions.size(); // the grower is gone: nothing more to burst
            return budget;
        }
        float size = ((IResizeableEntity) entity).getSizeMultiplier();
        boolean huge = ((IResizeableEntity) entity).isHuge();
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        int looks = BURST_LOOKS_PER_TICK;
        while (!b.done() && budget > 0 && looks-- > 0) {
            mp.set(b.positions.getLong(b.next++));
            // It may have changed (or the body shrunk) since the scan.
            if (!burstable(level, mp, level.getBlockState(mp), size, huge)) continue;
            if (breakBlock(level, mp, entity, Mode.BODY) != Result.SKIPPED) budget--;
        }
        return budget;
    }
}
