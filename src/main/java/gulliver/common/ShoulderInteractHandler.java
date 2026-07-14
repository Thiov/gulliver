package gulliver.common;

import gulliver.access.IGulliverShoulderInternal;
import gulliver.api.IResizeableEntity;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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
 *  - RMB in air / on a block while carrying in HAND -> set the held
 *    entity down (on the clicked block's top face when there is one)
 *
 * Also owns the carry-lifecycle safety hooks: everything carried is
 * dropped when the carrier disconnects or changes dimension, so no
 * entity is left frozen behind (see ShoulderHelper.validateCarried for
 * the belt-and-suspenders self-heal on the carried side).
 */
public final class ShoulderInteractHandler {
    private ShoulderInteractHandler() {}

    public static void registerCommon() {
        // Right-click in air while carrying anything in HAND -> set the
        // hand-held down in place. Only fires when the click hits
        // nothing else (no entity / no block).
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (world.isClientSide()) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer carrier)) return InteractionResult.PASS;
            if (((IGulliverShoulderInternal) carrier).gulliver$getHandEntity() == null) {
                return InteractionResult.PASS;
            }
            // Drop only the hand-held in place (don't touch shoulder slots).
            ShoulderHelper.detachHand(carrier);
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
            if (((IGulliverShoulderInternal) carrier).gulliver$getHandEntity() == null) {
                return InteractionResult.PASS;
            }
            Entity held = ShoulderHelper.detachHand(carrier);
            if (held != null) {
                // Place precisely on top of the hit block. hitResult
                // .getLocation() gives the exact 3D point; snap to the
                // block's top surface when the top face was clicked.
                net.minecraft.world.phys.Vec3 hit = hitResult.getLocation();
                net.minecraft.core.BlockPos pos  = hitResult.getBlockPos();
                double placeY = (hitResult.getDirection() == net.minecraft.core.Direction.UP)
                        ? pos.getY() + 1.0D
                        : hit.y;
                held.setPos(hit.x, placeY, hit.z);
                held.setDeltaMovement(0.0D, 0.0D, 0.0D);
                held.fallDistance = 0.0F;
            }
            return InteractionResult.SUCCESS;
        });

        // Carrier disconnects: drop everything so carried entities don't
        // stay frozen (their move() is cancelled while the carry flag is
        // set) until the throttled self-heal notices.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            if (((IGulliverShoulderInternal) player).gulliver$hasAnyCarry()) {
                ShoulderHelper.drop(player);
            }
        });

        // Carrier changed dimension: carried entities stay in the origin
        // level (they're not vanilla passengers), so clear the carrier's
        // slots. The carried entities themselves are released by the
        // validateCarried self-heal within a second.
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register(
                (player, origin, destination) -> {
                    if (((IGulliverShoulderInternal) player).gulliver$hasAnyCarry()) {
                        ShoulderHelper.drop(player);
                    }
                });
    }
}
