package gulliver.common;

import gulliver.api.IResizeableEntity;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Port of the 1.6.4 InteractEventHandler.handleInteractEvent right-click
 * gating, plus the broader "tiny players need a pointy item to interact
 * with anything functional" rule.
 *
 * 1.6.4 used two hooks together:
 *   - PlayerInteractEvent (RIGHT_CLICK_BLOCK) cancelled door/lever/
 *     button/gate/hatch/cabinet/safe when canOpenSingleBlock failed.
 *     The substring class-name match is reproduced verbatim — the
 *     check is intentionally fuzzy and matches modded blocks that
 *     follow the same naming convention.
 *   - PlayerOpenContainerEvent denied chest / furnace / generic
 *     container UIs when the player couldn't reach (canInteractWith*),
 *     which for tinies meant "couldn't open without a strength bump".
 *
 * In Fabric 26.1.2 there's no PlayerOpenContainerEvent, but UseBlockCallback
 * fires before the container UI opens, so we handle both cases there:
 *
 *   - Non-tiny players: existing canOpenSingleBlock gate for door/lever/
 *     button/etc., matching the original.
 *   - Tiny players (size < 0.3): block ANY block with a menu provider
 *     (chest, furnace, crafting table, dispenser, hopper, brewing stand,
 *     anvil, beacon, lectern, etc.) plus the class-name interactive set
 *     (button, lever, door, gate, trapdoor, jukebox, bed, repeater,
 *     comparator, note block, ...). The only exit is to hold a pointy
 *     item — sword or stick or any tool — which mirrors the original
 *     mod's "tinies use a stick/sword as a pry tool" rule.
 *
 * Returning InteractionResult.FAIL also blocks the item-on-block path,
 * which is fine: a tiny clicking a chest with dirt in hand wasn't going
 * to place dirt over the chest anyway.
 *
 * The 1.6.4 'BlockLittleChunk' (LittleBlocks compat) branch is dropped,
 * per the binding scope (no Optifine/TMI/LittleBlocks integrations).
 */
public final class InteractEventHandler {
    private InteractEventHandler() {}

    public static void registerCommon() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            IResizeableEntity sized = (IResizeableEntity) player;
            float mult = sized.getSizeMultiplier();
            if (mult == 1.0F) return InteractionResult.PASS;

            BlockPos pos = hit.getBlockPos();
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) return InteractionResult.PASS;

            // Pointy item (sword/stick/pickaxe/axe/shovel/hoe/shears) —
            // tinies wielding one get full interaction privileges,
            // matching the 1.6.4 strength bonus path.
            boolean pointy = GulliverEnvoy.holdingPointyItem(player);

            if (sized.isTiny() && !pointy) {
                // Block ANY functional block. Two checks combined:
                //  1) menu provider — covers chests, furnaces, crafting
                //     table, anvil, beacon, brewing stand, hopper,
                //     dispenser, dropper, lectern, smoker, blast furnace,
                //     loom, stonecutter, cartography table, smithing
                //     table, grindstone.
                //  2) class-name substring set — covers buttons, levers,
                //     doors, fence gates, trapdoors, pressure plates,
                //     repeaters, comparators, jukeboxes, beds, beehives,
                //     campfires, composters, note blocks, redstone wire,
                //     daylight sensors, cake, lecterns. The 1.6.4 source
                //     used the same substring trick for forward-compat
                //     with modded interactive blocks.
                if (isInteractive(state, level, pos)) {
                    return InteractionResult.FAIL;
                }
                return InteractionResult.PASS;
            }

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

    private static boolean isInteractive(BlockState state, Level level, BlockPos pos) {
        MenuProvider mp = state.getMenuProvider(level, pos);
        if (mp != null) return true;
        String name = state.getBlock().getClass().getSimpleName();
        return name.contains("Door")
                || name.contains("Lever")
                || name.contains("Button")
                || name.contains("Gate")
                || name.contains("Trap")          // TrapDoorBlock
                || name.contains("Plate")         // PressurePlateBlock (won't trigger anyway, but block right-click too)
                || name.contains("Repeater")
                || name.contains("Comparator")
                || name.contains("Jukebox")
                || name.contains("Bed")
                || name.contains("Note")
                || name.contains("Cake")
                || name.contains("Beehive")
                || name.contains("Bee")
                || name.contains("Campfire")
                || name.contains("Composter")
                || name.contains("Cauldron")
                || name.contains("Daylight")
                || name.contains("Lectern")
                || name.contains("RespawnAnchor")
                || name.contains("Hatch")
                || name.contains("Cabinet")
                || name.contains("Safe");
    }
}
