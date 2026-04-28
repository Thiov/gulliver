package gulliver.common;

import gulliver.api.IResizeableEntity;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Port of the 1.6.4 InteractEventHandler.handleInteractEvent right-click
 * gating: if the player is resized (size != 1.0) AND the block is a
 * door/lever/button/gate/hatch/cabinet/safe AND the player can't open
 * a single block (per smallBlockOpeningStrength), the right-click is
 * cancelled.
 *
 * 1.6.4 used block.getClass().getName().contains("Door") etc. — string
 * substring matching against the class name. Reproduced verbatim here
 * (against modern class names: DoorBlock, ButtonBlock, LeverBlock,
 * FenceGateBlock, TrapDoorBlock, etc.) — so the check is intentionally
 * fuzzy and matches new doors/levers added by other mods that follow
 * the convention.
 *
 * The 1.6.4 'BlockLittleChunk' (LittleBlocks compat) branch is dropped,
 * per the binding scope (no Optifine/TMI/LittleBlocks integrations).
 *
 * The PlayerOpenContainerEvent reflective field-walk for chest distance
 * scaling lands in Phase 7(3) as a typed Mixin into the actual chest
 * container's stillValid — much cleaner than the 1.6.4 reflective walk,
 * but produces identical results.
 */
public final class InteractEventHandler {
    private InteractEventHandler() {}

    public static void registerCommon() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            float mult = ((IResizeableEntity) player).getSizeMultiplier();
            if (mult == 1.0F) return InteractionResult.PASS;

            BlockPos pos = hit.getBlockPos();
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) return InteractionResult.PASS;

            // 1.6.4 also gated on !block.isBlockNormalCube — if the block is
            // a full opaque cube it can't be a door/lever/button anyway, but
            // preserving the spirit, skip the test for full cubes.
            if (state.isSolidRender()) return InteractionResult.PASS;

            String name = state.getBlock().getClass().getSimpleName();
            if (!isOpenable(name)) return InteractionResult.PASS;

            if (!GulliverEnvoy.canOpenSingleBlock(player)) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
    }

    private static boolean isOpenable(String className) {
        return className.contains("Door")
                || className.contains("Lever")
                || className.contains("Button")
                || className.contains("Gate")
                || className.contains("Hatch")     // matches TrapDoorBlock too via Door, but kept for parity
                || className.contains("Cabinet")
                || className.contains("Safe");
    }
}
