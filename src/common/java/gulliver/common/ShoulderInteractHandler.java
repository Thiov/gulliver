package gulliver.common;

import gulliver.access.IGulliverShoulderInternal;
import gulliver.api.IResizeableEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Right-click handling for the carry system:
 *  - shift+RMB on a carryable target  -> pick it up into the HAND slot
 *  - RMB while holding STRING on a sufficiently-larger target -> ride it
 *  - RMB in air / on a block while carrying in HAND -> set the held
 *    entity down (on the clicked block's top face when there is one)
 *
 * Also owns the carry-lifecycle safety hooks: everything carried is
 * dropped when the carrier disconnects or changes dimension, so no
 * entity is left frozen behind (ShoulderHelper.validateCarried is the
 * belt-and-braces self-heal on the carried side).
 *
 * The loader glue calls these; a non-PASS result cancels the click.
 */
public final class ShoulderInteractHandler {
    private ShoulderInteractHandler() {}

    /** RMB in air while carrying in HAND: set the hand-held down in place. */
    public static InteractionResult onUseItem(Player player, Level level, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer carrier)) return InteractionResult.PASS;
        if (((IGulliverShoulderInternal) carrier).gulliver$getHandEntity() == null) {
            return InteractionResult.PASS;
        }
        ShoulderHelper.detachHand(carrier);
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer carrier)) return InteractionResult.PASS;

        // String-ride: rider holds STRING (either hand), RMB on a
        // sufficiently larger target -> rider starts riding the target.
        if (!player.isShiftKeyDown() && GulliverEnvoy.isHolding(carrier, Items.STRING)) {
            if (entity instanceof LivingEntity target) {
                float riderSize = ((IResizeableEntity) carrier).getSizeMultiplier();
                float targetSize = ((IResizeableEntity) target).getSizeMultiplier();
                // Rider must be at most half the target's size — the same
                // size-difference gate as carry / squish.
                if (riderSize <= targetSize * 0.5F && carrier.getVehicle() == null) {
                    //#if MC >= 1.21.5
                    carrier.startRiding(target, true, false);
                    //#else
                    //$$ carrier.startRiding(target, true);
                    //#endif
                    return InteractionResult.SUCCESS;
                }
            }
        }

        // Sneak + RMB pickup: always into the hand. If the hand is full the
        // helper swaps in place (drops the previous hand-held); shoulder
        // slots are never touched here.
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!(entity instanceof LivingEntity)) return InteractionResult.PASS;
        if (!ShoulderHelper.canCarry(carrier, entity)) return InteractionResult.PASS;
        return ShoulderHelper.pickUp(carrier, entity) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    /** RMB on a block while carrying in HAND: place the hand-held on top of it. */
    public static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hitResult) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer carrier)) return InteractionResult.PASS;
        if (((IGulliverShoulderInternal) carrier).gulliver$getHandEntity() == null) {
            return InteractionResult.PASS;
        }
        Entity held = ShoulderHelper.detachHand(carrier);
        if (held != null) {
            net.minecraft.world.phys.Vec3 hit = hitResult.getLocation();
            net.minecraft.core.BlockPos pos = hitResult.getBlockPos();
            double placeY = (hitResult.getDirection() == net.minecraft.core.Direction.UP)
                    ? pos.getY() + 1.0D
                    : hit.y;
            held.setPos(hit.x, placeY, hit.z);
            held.setDeltaMovement(0.0D, 0.0D, 0.0D);
            held.fallDistance = 0.0F;
        }
        return InteractionResult.SUCCESS;
    }

    /** Carrier disconnects: drop everything so nothing stays frozen. */
    public static void onDisconnect(ServerPlayer player) {
        if (((IGulliverShoulderInternal) player).gulliver$hasAnyCarry()) {
            ShoulderHelper.drop(player);
        }
    }

    /**
     * Carrier changed dimension: carried entities stay behind (they are
     * not vanilla passengers), so clear the slots; validateCarried then
     * releases the entities themselves within a second.
     */
    public static void onChangeDimension(ServerPlayer player) {
        if (((IGulliverShoulderInternal) player).gulliver$hasAnyCarry()) {
            ShoulderHelper.drop(player);
        }
    }
}
