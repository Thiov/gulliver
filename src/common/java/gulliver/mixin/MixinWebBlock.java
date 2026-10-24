package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.common.GulliverEnvoy;
import gulliver.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
//#if MC >= 1.21.5
import net.minecraft.world.entity.InsideBlockEffectApplier;
//#endif
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BlockWebGulliver: huge entities walking through cobwebs break them
 * (1/50 ticks). The 1.6.4 source guarded on size + canSizeGrief +
 * world.canSpawn(...) (player owns this region). Same shape here.
 */
@Mixin(WebBlock.class)
public abstract class MixinWebBlock {

    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void gulliver$tinySkipsAndHugeBreaks(BlockState state, Level level, BlockPos pos,
                                                  Entity entity,
                                             //#if MC >= 1.21.5
                                             InsideBlockEffectApplier applier, boolean inside,
                                             //#endif
                                             CallbackInfo ci) {
        IResizeableEntity sized = (IResizeableEntity) entity;
        // Tinies slip past silk strands — skip the slow + cancel vanilla
        // entityInside entirely.
        if (sized.isTiny()) {
            ci.cancel();
            return;
        }
        if (level.isClientSide()) return;
        if (!sized.isHuge()) return;
        if (entity.isShiftKeyDown()) return;
        if (!GulliverEnvoy.canSizeGrief(entity)) return;
        // Use getRandom() — Level.random is private since 26.x.
        if (level.getRandom().nextInt(50) != 0) return;

        // A player's giant stride is bound by the same protection as their
        // pickaxe: spawn protection, adventure mode and the loader's claim
        // checks (mobs have only the size_griefing / mobGriefing rules).
        if (entity instanceof ServerPlayer player
                && (!player.getAbilities().mayBuild
                    || !level.mayInteract(player, pos)
                    || !Services.platform().mayBreakBlock((ServerLevel) level, pos, state, player))) {
            return;
        }

        // Drop loot then remove the cobweb (vanilla destroyBlock with drop).
        level.destroyBlock(pos, true, entity);
    }
}
