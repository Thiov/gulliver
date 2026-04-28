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

            // Sneak + RMB pickup: ALWAYS into hand. If hand full, the
            // helper does an in-place swap (drops prev hand-held), but
            // shoulder slots are NEVER touched here.
            if (!player.isShiftKeyDown()) return InteractionResult.PASS;
            if (!(entity instanceof LivingEntity)) return InteractionResult.PASS;
            if (!ShoulderHelper.canCarry(carrier, entity)) return InteractionResult.PASS;
            if (ShoulderHelper.pickUp(carrier, entity)) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });

        // RMB on a block while carrying anything in HAND: place the
        // hand-held ON TOP of the clicked block (no fall damage).
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (world.isClientSide()) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer carrier)) return InteractionResult.PASS;
            IGulliverShoulderInternal cs = (IGulliverShoulderInternal) carrier;
            java.util.UUID handId = cs.gulliver$getHandEntity();
            if (handId == null) return InteractionResult.PASS;
            net.minecraft.world.entity.Entity held =
                    ((net.minecraft.server.level.ServerLevel) carrier.level()).getEntity(handId);
            cs.gulliver$setHandEntity(null);
            if (held != null) {
                ((IGulliverShoulderInternal) held).gulliver$setHoldingEntity(null);
                held.noPhysics = false;
                // Place precisely on top of the hit block. hitResult
                // .getLocation() gives the exact 3D point; add the entity's
                // half-height so it stands on top of that surface.
                net.minecraft.world.phys.Vec3 hit = hitResult.getLocation();
                net.minecraft.core.BlockPos pos  = hitResult.getBlockPos();
                double placeY = (hitResult.getDirection() == net.minecraft.core.Direction.UP)
                        ? pos.getY() + 1.0D
                        : hit.y;
                held.setPos(hit.x, placeY, hit.z);
                held.setDeltaMovement(0.0D, 0.0D, 0.0D);
                held.fallDistance = 0.0F;
                ShoulderHelper.broadcastDetach(carrier, held);
            }
            return InteractionResult.SUCCESS;
        });
    }
}
