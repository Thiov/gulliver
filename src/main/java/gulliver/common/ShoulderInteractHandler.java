package gulliver.common;

import gulliver.access.IGulliverShoulderInternal;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Shift + right-click on a carryable entity -> pick them up onto the
 * carrier's shoulder. Drop or throw via /shoulderentity (V key) or
 * left-click. This is the natural-feeling alternative to the V-grabs-
 * nearest-target flow.
 */
public final class ShoulderInteractHandler {
    private ShoulderInteractHandler() {}

    public static void registerCommon() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            // Run only the main hand to avoid double-firing on offhand probe.
            if (hand != net.minecraft.world.InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (world.isClientSide()) return InteractionResult.PASS;
            if (!player.isShiftKeyDown()) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer carrier)) return InteractionResult.PASS;
            // Already carrying -> drop instead.
            if (((IGulliverShoulderInternal) carrier).gulliver$getHeldEntity() != null) {
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
