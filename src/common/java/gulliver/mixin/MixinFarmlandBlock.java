package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
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

    /**
     * Tinies are too light to trample; giants always trample. Either way
     * the fall itself still hurts — cancelling fallOn would also skip the
     * Block.fallOn -> causeFallDamage call, which let a giant jump off a
     * cliff onto a field unharmed (and skipped its landing shockwave).
     */
    @Inject(method = "fallOn", at = @At("HEAD"), cancellable = true)
    private void gulliver$resizedFallOn(Level level, BlockState state, BlockPos pos, Entity entity,
                                         //#if MC >= 1.21.5
                                         double fallDistance,
                                         //#else
                                         //$$ float fallDistance,
                                         //#endif
                                         CallbackInfo ci) {
        if (level.isClientSide()) return;

        IResizeableEntity sized = (IResizeableEntity) entity;
        if (sized.isTiny()) {
            gulliver$fallDamage(entity, fallDistance);
            ci.cancel();
            return;
        }
        if (sized.isHuge()) {
            // Honour mobGriefing for non-players (vanilla path decides then).
            if (!(entity instanceof Player)
                    && level instanceof ServerLevel sl
                    && !gulliver.init.GulliverGameRules.mobGriefing(sl)) {
                return;
            }
            FarmlandBlock.turnToDirt(entity, state, level, pos);
            gulliver$fallDamage(entity, fallDistance);
            ci.cancel();
        }
    }

    private static void gulliver$fallDamage(Entity entity,
                                             //#if MC >= 1.21.5
                                             double fallDistance
                                             //#else
                                             //$$ float fallDistance
                                             //#endif
    ) {
        entity.causeFallDamage(fallDistance, 1.0F, entity.damageSources().fall());
    }
}
