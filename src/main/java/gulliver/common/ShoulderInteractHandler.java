package gulliver.common;

import gulliver.access.IGulliverShoulderInternal;
import gulliver.api.IResizeableEntity;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Right-click on a target entity (with optional sneak / string in hand)
 * dispatches one of:
 *  - shift+RMB on carryable target  -> pick up into HAND slot
 *  - RMB while holding STRING on a sufficiently-larger target -> rider
 *    starts riding the target (vanilla startRiding)
 *  - shift+RMB while already carrying -> drop everything
 */
public final class ShoulderInteractHandler {
    private ShoulderInteractHandler() {}

    public static void registerCommon() {
        // Right-click in air while carrying anything in HAND -> drop the
        // hand-held in front of the player. Only fires when the click hits
        // nothing else (no entity / no block).
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (world.isClientSide()) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer carrier)) return InteractionResult.PASS;
            IGulliverShoulderInternal cs = (IGulliverShoulderInternal) carrier;
            if (cs.gulliver$getHandEntity() == null) return InteractionResult.PASS;
            // Drop only the hand-held in place (don't touch shoulder slots).
            java.util.UUID handId = cs.gulliver$getHandEntity();
            net.minecraft.world.entity.Entity held = ((net.minecraft.server.level.ServerLevel) carrier.level()).getEntity(handId);
            cs.gulliver$setHandEntity(null);
            if (held != null) {
                ((IGulliverShoulderInternal) held).gulliver$setHoldingEntity(null);
                held.noPhysics = false;
                ShoulderHelper.broadcastDetach(carrier, held);
            }
            return InteractionResult.SUCCESS;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (world.isClientSide()) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer carrier)) return InteractionResult.PASS;
            ItemStack stack = carrier.getMainHandItem();

            // String-ride: rider holds STRING, RMB on a sufficiently-larger
            // target -> rider starts riding the target.
            if (!player.isShiftKeyDown() && stack != null && stack.is(Items.STRING)) {
                if (entity instanceof LivingEntity target) {
                    float riderSize  = ((IResizeableEntity) carrier).getSizeMultiplier();
                    float targetSize = ((IResizeableEntity) target).getSizeMultiplier();
                    // Rider must be at most half the target's size — same
                    // size-difference gate as carry / squish.
                    if (riderSize <= targetSize * 0.5F) {
                        if (carrier.getVehicle() == null) {
                            carrier.startRiding(target, true, false);
                            return InteractionResult.SUCCESS;
                        }
                    }
                }
            }

            // Sneak + RMB carry-pickup
            if (!player.isShiftKeyDown()) return InteractionResult.PASS;
            IGulliverShoulderInternal cs = (IGulliverShoulderInternal) carrier;
            // Already carrying anything -> drop ALL.
            if (cs.gulliver$hasAnyCarry()) {
                ShoulderHelper.drop(carrier);
                return InteractionResult.SUCCESS;
            }
            if (!(entity instanceof LivingEntity)) return InteractionResult.PASS;
            if (!ShoulderHelper.canCarry(carrier, entity)) return InteractionResult.PASS;
            if (ShoulderHelper.pickUp(carrier, entity)) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
    }
}
