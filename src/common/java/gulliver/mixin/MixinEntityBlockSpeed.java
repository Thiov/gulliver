package gulliver.mixin;

import gulliver.access.IGulliverFlagsInternal;
import gulliver.api.IResizeableEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SoulSandBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Size-dependent ground drag, applied through the block speed factor so it
 * counts once per move however many blocks the body overlaps:
 *
 *  - soul sand: giants stride over it, tinies sink in twice as slow;
 *  - snow (2+ layers) and carpet: extra-tinies wade through slowly
 *    (1.6.4 "struggling").
 */
@Mixin(Entity.class)
public abstract class MixinEntityBlockSpeed {

    @Inject(method = "getBlockSpeedFactor", at = @At("RETURN"), cancellable = true)
    private void gulliver$sizedSpeedFactor(CallbackInfoReturnable<Float> cir) {
        Entity self = (Entity) (Object) this;
        IResizeableEntity sized = (IResizeableEntity) self;
        if (sized.getSizeMultiplier() == 1.0F) return;
        float factor = cir.getReturnValueF();
        BlockState feet = self.level().getBlockState(self.blockPosition());

        if (feet.getBlock() instanceof SoulSandBlock
                || self.level().getBlockState(BlockPos.containing(self.getX(), self.getY() - 0.5000001D, self.getZ()))
                        .getBlock() instanceof SoulSandBlock) {
            if (sized.isHuge()) cir.setReturnValue(Math.max(factor, 1.0F));
            else if (sized.isTiny()) cir.setReturnValue(factor * 0.5F);
            return;
        }

        if (!sized.isExtraTiny()) return;
        float wade;
        if (feet.getBlock() instanceof SnowLayerBlock) {
            if (feet.getValue(SnowLayerBlock.LAYERS) <= 1) return;
            wade = 0.4F;
        } else if (feet.getBlock() instanceof CarpetBlock) {
            wade = 0.7F;
        } else {
            return;
        }
        cir.setReturnValue(factor * wade * sized.getSizeMultiplierRoot());
        if (self instanceof LivingEntity) ((IGulliverFlagsInternal) self).gulliver$setStruggling(true);
    }
}
