package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BlockFarmlandGulliver in 1.6.4:
 *   onEntityCollidedWithBlock (per-tick walk): huge → 1/2 chance to
 *     trample to dirt. Non-players gated by mobGriefing.
 *   onFallenUpon: tiny → never trample; huge → ALWAYS trample;
 *     normal → vanilla random check (random < fallDist - 0.5).
 *
 * Modern translation:
 *   - fallOn @Inject HEAD: cancel if tiny; force-trample if huge;
 *     otherwise pass through to vanilla.
 *
 * The per-tick walk-stomp branch (huge always-eventually trampling
 * just from walking) integrates more cleanly with Phase 10's
 * leaveHugeFootprints, so it's deferred — fallOn covers the dramatic
 * giant-jumps-and-cracks-the-soil moment which is the iconic feel.
 */
@Mixin(FarmlandBlock.class)
public abstract class MixinFarmlandBlock {

    @Inject(method = "fallOn", at = @At("HEAD"), cancellable = true)
    private void gulliver$resizedFallOn(Level level, BlockState state, BlockPos pos,
                                         Entity entity, double fallDistance, CallbackInfo ci) {
        if (level.isClientSide()) return;

        IResizeableEntity sized = (IResizeableEntity) entity;
        if (sized.isTiny()) {
            // Tinies never trample — invoke super logic (fall damage applies normally,
            // just no farmland-to-dirt). Cancel the FarmlandBlock-specific path.
            // Vanilla fallOn calls turnToDirt under a gameplay-specific predicate; cancelling
            // skips that and falls through to the inherited Block.fallOn (default no-op).
            ci.cancel();
            return;
        }
        if (sized.isHuge()) {
            // Always trample (skipping the random-vs-fallDistance vanilla check).
            // Honour mobGriefing for non-players.
            if (!(entity instanceof Player)
                    && level instanceof ServerLevel sl
                    && !sl.getGameRules().get(GameRules.MOB_GRIEFING)) {
                return;
            }
            FarmlandBlock.turnToDirt(entity, state, level, pos);
            ci.cancel();
        }
    }
}
