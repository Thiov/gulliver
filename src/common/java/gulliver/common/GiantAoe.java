package gulliver.common;

import gulliver.api.IResizeableEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Giant-scale area effects: a huge body doesn't interact with the
 * world one block or one target at a time — its fist alone spans
 * several blocks.
 *
 * BLOCK BREAKING — breaking a block as a huge entity shatters a
 * fist-shaped disc of neighbors on the plane perpendicular to the
 * punch (the dominant look axis), one layer deep:
 *
 *   size 2.4 (huge threshold) → 3×3 (rounded, 9 blocks)
 *   size 5                    → 5×5 disc (21 blocks)
 *   size 7.6+                 → 7×7 disc (37 blocks)
 *
 * Rules that keep it immersive instead of chaotic:
 *   - The crater is round (circular mask) — a fist print, not a
 *     cursor selection.
 *   - Neighbors significantly harder than the block actually broken
 *     survive: punching a dirt patch out of a stone wall leaves the
 *     stone; punching the stone itself takes stone-grade neighbors
 *     with it. (Threshold: 1.5× the center block's hardness.)
 *   - Block entities (chests, furnaces, spawners) never shatter as
 *     collateral — deliberate breaks only.
 *   - Unbreakables and fluids are untouched.
 *   - SNEAK = precision mode: exactly one block, no spread.
 *   - Respects the gulliver:size_griefing gamerule.
 *
 * MELEE SPLASH — a huge attacker's punch is an area of effect: other
 * creatures within 0.35 × size blocks of the struck target take 40%
 * of the attack damage with distance falloff, scaled by the attack-
 * cooldown charge (spam-clicking splashes nothing). SNEAK again means
 * a precise single-target hit. The splash routes through the normal
 * player-attack damage source, so per-target size scaling and armor
 * apply as usual; the attacker's own carried pets, creative players,
 * and spectators are never splashed.
 */
public final class GiantAoe {
    private GiantAoe() {}

    /**
     * Hit face of the block about to break, captured in the BEFORE
     * event (while the block still exists to be ray-traced) and
     * consumed by AFTER in the same call stack. Deriving the plane
     * from the look axis alone broke down at range: aiming at a floor
     * far away makes the look vector near-horizontal, so the disc
     * stood vertically and carved a 1-wide trench ("breaks blocks in
     * a line") instead of a flat crater.
     */
    private static final ThreadLocal<Direction> PUNCH_FACE = new ThreadLocal<>();
    /** State of the block about to break, for the AFTER half. */
    private static final ThreadLocal<BlockState> BROKEN_STATE = new ThreadLocal<>();

    // ---- fist-crater mining (driven by MixinServerPlayerGameModeBreak) ----

    public static void beforeBreak(Level level, Player player, BlockPos pos) {
        PUNCH_FACE.remove();
        BROKEN_STATE.set(level.getBlockState(pos));
        if (((IResizeableEntity) player).isHuge()) {
            double reach = player.blockInteractionRange() + 1.0D;
            if (player.pick(reach, 1.0F, false)
                    instanceof net.minecraft.world.phys.BlockHitResult bhr
                    && bhr.getBlockPos().equals(pos)) {
                PUNCH_FACE.set(bhr.getDirection());
            }
        }
    }

    /** The break was cancelled or failed: forget the captured state. */
    public static void clearBreak() {
        PUNCH_FACE.remove();
        BROKEN_STATE.remove();
    }

    /** Called only when the break actually happened. */
    public static void afterBreak(Level level, Player player, BlockPos pos) {
        Direction face = PUNCH_FACE.get();
        BlockState state = BROKEN_STATE.get();
        PUNCH_FACE.remove();
        BROKEN_STATE.remove();
        if (state == null || level.isClientSide()) return;
        IResizeableEntity sized = (IResizeableEntity) player;
        if (!sized.isHuge()) return;
        if (player.isShiftKeyDown()) return;          // precision mode
        if (!GulliverEnvoy.canSizeGrief(player)) return;

        float size = sized.getSizeMultiplier();
        int r = Math.min(3, 1 + (int) ((size - 2.4F) / 2.6F));
        if (r <= 0) return;

        // Spread on the plane of the struck FACE (from the BEFORE
        // raytrace): breaking a floor spreads flat, breaking a wall
        // spreads vertically — regardless of distance or view angle.
        // Look-axis fallback only if the raytrace missed (e.g. the
        // block broke through a gap the ray can't thread).
        if (face == null) face = Direction.getApproximateNearest(player.getLookAngle());
        Direction.Axis axis = face.getAxis();

        float centerHardness = Math.max(0.0F, state.getDestroySpeed(level, pos));
        float hardnessCap = Math.max(centerHardness * 1.5F, centerHardness + 0.5F);
        double mask = (r + 0.5D) * (r + 0.5D);
        boolean drops = !player.isCreative();

        crater:
        for (int a = -r; a <= r; a++) {
            for (int b = -r; b <= r; b++) {
                if (a == 0 && b == 0) continue;
                if (a * a + b * b > mask) continue;   // round the crater
                BlockPos p = switch (axis) {
                    case Y -> pos.offset(a, 0, b);
                    case X -> pos.offset(0, a, b);
                    case Z -> pos.offset(a, b, 0);
                };
                BlockState st = level.getBlockState(p);
                if (st.isAir()) continue;
                if (!st.getFluidState().isEmpty()) continue;
                float h = st.getDestroySpeed(level, p);
                if (h < 0.0F || h > hardnessCap) continue;
                if (level.getBlockEntity(p) != null) continue;
                // Spawn protection / world border, like a normal break.
                if (!level.mayInteract(player, p)) continue;
                // Collateral only drops what the held tool could harvest, and
                // wears the tool like mining it would (no free ore).
                boolean harvest = drops && player.hasCorrectToolForDrops(st);
                if (level.destroyBlock(p, harvest, player) && drops) {
                    net.minecraft.world.item.ItemStack tool = player.getMainHandItem();
                    if (!tool.isEmpty()) {
                        tool.mineBlock(level, st, p, player);
                        if (tool.isEmpty()) break crater; // the tool broke mid-swing
                    }
                }
            }
        }
    }

    // ---- punch splash ----

    public static InteractionResult onAttack(Player player, Level level, Entity target) {
        if (level.isClientSide()) return InteractionResult.PASS;
        IResizeableEntity sized = (IResizeableEntity) player;
        if (!sized.isHuge()) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!(target instanceof LivingEntity)) return InteractionResult.PASS;

        float size = sized.getSizeMultiplier();
        double radius = 0.35D * size;
        // Attack-cooldown charge: a spam-click splashes nothing.
        float charge = player.getAttackStrengthScale(0.5F);
        float base = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE)
                * 0.4F * charge * charge;
        if (base < 0.5F) return InteractionResult.PASS;

        AABB zone = target.getBoundingBox().inflate(radius);
        for (Entity e : level.getEntities(player, zone)) {
            if (e == target) continue;
            if (!(e instanceof LivingEntity victim)) continue;
            if (e.getVehicle() == player) continue;
            if (player.getUUID().equals(((gulliver.access.IGulliverShoulderInternal) e)
                    .gulliver$getHoldingEntity())) continue;
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;

            double dist = e.distanceTo(target);
            double falloff = 1.0D - Math.min(1.0D, dist / (radius + 1.0E-3D));
            float dmg = (float) (base * falloff);
            if (dmg < 0.5F) continue;
            victim.hurt(level.damageSources().playerAttack(player), dmg);
        }
        return InteractionResult.PASS;
    }
}
