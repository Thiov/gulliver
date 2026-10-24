package gulliver.common;

import gulliver.access.IGulliverShoulderInternal;
import gulliver.api.IResizeableEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Right-click handling for the carry system:
 *  - sneak + RMB (empty hand) on a carryable target -> pick it up
 *  - RMB (empty hand, not sneaking) on a small animal at least three times
 *    your size -> ride it (TinyMounts)
 *  - RMB holding STRING on a target at least twice your size -> ride it
 *  - RMB in air / on a block while carrying in HAND -> set it down (on
 *    the clicked block's top face when there is one)
 *
 * The same decision is made on both sides from synced state, so the
 * client doesn't also predict a block placement or an item use (ghost
 * blocks); only the server acts. The loader glue calls these and a
 * non-PASS result cancels the vanilla click.
 */
public final class ShoulderInteractHandler {
    private ShoulderInteractHandler() {}

    /** RMB in air while carrying in HAND: set the hand-held down in place. */
    public static InteractionResult onUseItem(Player player, Level level, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !ShoulderHelper.handOccupied(player)) return InteractionResult.PASS;
        if (player instanceof ServerPlayer carrier) ShoulderHelper.detachHand(carrier);
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity) {
        // Fabric's UseEntityCallback fires for spectators too.
        if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) return InteractionResult.PASS;
        // The claim probe of ShoulderHelper.mayInteract fires this same event; it is none of our business.
        if (ShoulderHelper.isProbing()) return InteractionResult.PASS;

        // String-ride: holding STRING (either hand), RMB a living target at
        // least twice your size -> ride it. Bosses excepted.
        if (!player.isShiftKeyDown() && GulliverEnvoy.isHolding(player, Items.STRING)
                && entity instanceof LivingEntity target && !ShoulderHelper.isUnholdable(target)
                && player.getVehicle() == null) {
            float riderSize = ((IResizeableEntity) player).getSizeMultiplier();
            float targetSize = ((IResizeableEntity) target).getSizeMultiplier();
            if (riderSize <= targetSize * 0.5F && !TinyMounts.refusesRide(player, target)) {
                if (player instanceof ServerPlayer rider) {
                    //#if MC >= 1.21.5
                    rider.startRiding(target, true, false);
                    //#else
                    //$$ rider.startRiding(target, true);
                    //#endif
                }
                return InteractionResult.SUCCESS;
            }
        }

        // Empty hand, not sneaking, a small animal much bigger than you: climb on.
        if (!player.isShiftKeyDown() && ShoulderHelper.handFree(player) && GulliverConfig.INSTANCE.general.tinyMounts
                && TinyMounts.canBoard(player, entity)) {
            if (player instanceof ServerPlayer rider && !TinyMounts.mount(rider, entity)) return InteractionResult.PASS;
            return InteractionResult.SUCCESS;
        }

        // Sneak + RMB with an empty hand: pick up (swapping out any current
        // hand-held; shoulder slots are never touched here).
        if (!player.isShiftKeyDown() || !ShoulderHelper.handFree(player)) return InteractionResult.PASS;
        if (!ShoulderHelper.canCarry(player, entity)) return InteractionResult.PASS;
        if (player instanceof ServerPlayer carrier && !ShoulderHelper.pickUp(carrier, entity)) {
            return InteractionResult.PASS; // somebody else's pet, or a claim said no
        }
        return InteractionResult.SUCCESS;
    }

    /** RMB on a block while carrying in HAND: place the hand-held on, beside or under it. */
    public static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hitResult) {
        if (hand != InteractionHand.MAIN_HAND || !ShoulderHelper.handOccupied(player)) return InteractionResult.PASS;
        if (player instanceof ServerPlayer carrier) {
            Entity held = ShoulderHelper.detachHand(carrier);
            if (held != null) ShoulderHelper.setDownOn(carrier, held, hitResult);
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
     * Carrier changed dimension. By now the carried entities have been
     * released already (MixinServerLevelRemovePlayer lets go before the
     * player leaves); this is the safety net for anything that slipped by.
     */
    public static void onChangeDimension(ServerPlayer player) {
        if (((IGulliverShoulderInternal) player).gulliver$hasAnyCarry()) {
            ShoulderHelper.drop(player);
        }
    }
}
