package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.CarpetBlock;
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
 * BlockCarpetGulliver in 1.6.4:
 *   normal/huge → carpet has NO collision (purely visual; you don't
 *     even step up onto it)
 *   tiny       → vanilla 1/16 thick collision (tiny needs to step up)
 *   extra-tiny → 1/32 thick collision (sinks halfway in)
 *
 *   var5 = isExtraTiny ? 1 : isTiny ? 2 : 0;  // half-pixels of 1/32
 *   collision = var5 > 0 ? bbox(0..var5/32) : null;
 *
 * Modern translation: @Inject HEAD getCollisionShape — substitute empty
 * shape for HUGE only (a giant doesn't even feel carpet underfoot; the
 * 1.6.4 "no collision for normal" half is skipped because vanilla's
 * 1/16 lip is already imperceptible at normal scale and removing it
 * would change vanilla-size behavior for no visible gain), keep
 * vanilla SHAPE for tiny, substitute a thinner box for extra-tiny.
 *
 * VoxelShape construction for extra-tiny uses Shapes.box(0,0,0,1,1/32,1).
 * Targets the parent CarpetBlock so WoolCarpetBlock + MossyCarpetBlock
 * (both extend it) inherit the behavior.
 */
@Mixin(CarpetBlock.class)
public abstract class MixinCarpetBlock {

    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true, require = 0)
    private void gulliver$resizedCollision(BlockState state, BlockGetter level, BlockPos pos,
                                            CollisionContext ctx,
                                            CallbackInfoReturnable<VoxelShape> cir) {
        if (!(ctx instanceof EntityCollisionContext ecc)) return;
        Entity entity = ((EntityCollisionContextAccessor) ecc).gulliver$getEntity();
        if (entity == null) return;
        IResizeableEntity sized = (IResizeableEntity) entity;
        if (sized.getSizeMultiplier() == 1.0F) return;

        if (sized.isHuge()) {
            cir.setReturnValue(Shapes.empty());
        } else if (sized.isExtraTiny()) {
            cir.setReturnValue(Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 1.0D / 32.0D, 1.0D));
        }
        // tiny (but not extra-tiny) → fall through to vanilla 1/16
    }
}
