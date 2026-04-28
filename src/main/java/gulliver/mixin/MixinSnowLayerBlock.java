package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Per BlockSnowGulliver in 1.6.4:
 *   addCollisionBoxesToList(...): for the queried entity, shifts the
 *   effective layer count by +1 if extra-tiny (stands on a finer layer
 *   instead of falling through), or by -(size * 1.25) if huge (sinks in).
 *
 * Modern SnowLayerBlock has 8 discrete layers (LAYERS property 1..8). The
 * SHAPES[] array contains a VoxelShape per layer. We grab the entity from
 * the CollisionContext, compute the adjusted layer index, and substitute
 * the matching shape. The 1.6.4 mod's per-half-layer granularity collapses
 * to per-layer here — the closest faithful mapping that the 26.x snow
 * system supports.
 */
@Mixin(SnowLayerBlock.class)
public abstract class MixinSnowLayerBlock {

    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void gulliver$resize(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext ctx, CallbackInfoReturnable<VoxelShape> cir) {
        if (!(ctx instanceof EntityCollisionContext ecc)) return;
        Entity entity = ((EntityCollisionContextAccessor) ecc).gulliver$getEntity();
        if (entity == null) return;
        IResizeableEntity sized = (IResizeableEntity) entity;
        float mult = sized.getSizeMultiplier();
        if (mult == 1.0F) return;

        int layers = state.getValue(SnowLayerBlock.LAYERS); // 1..8
        int adj = layers;
        if (sized.isExtraTiny()) {
            adj = layers + 1;
        } else if (sized.isHuge()) {
            adj = layers - (int) (mult * 1.25F);
        }

        if (adj <= 0) {
            cir.setReturnValue(Shapes.empty());
        } else if (adj >= 8) {
            cir.setReturnValue(SnowLayerBlock.SHAPES[7]);
        } else {
            cir.setReturnValue(SnowLayerBlock.SHAPES[adj - 1]);
        }
    }
}
