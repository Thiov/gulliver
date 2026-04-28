package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.common.GulliverEnvoy;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
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

    @Inject(method = "entityInside", at = @At("HEAD"))
    private void gulliver$hugeBreaks(BlockState state, Level level, BlockPos pos,
                                      Entity entity, InsideBlockEffectApplier applier,
                                      boolean inside, CallbackInfo ci) {
        if (level.isClientSide()) return;
        if (!((IResizeableEntity) entity).isHuge()) return;
        if (entity.isShiftKeyDown()) return;
        if (!GulliverEnvoy.canSizeGrief(entity)) return;
        if (level.random.nextInt(50) != 0) return;

        // Drop loot then remove the cobweb (vanilla destroyBlock with drop).
        level.destroyBlock(pos, true);
    }
}
