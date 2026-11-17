package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
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
 * Carpet collision by size (1.6.4): a giant's foot squashes it flat (no
 * collision), an extra-tiny feels only half its thickness. Carpets don't
 * override getCollisionShape, so the hook sits on BlockBehaviour.
 *
 * Also gives tinies something to stand on where normal bodies walk
 * through: buttons, levers, pressure plates and signs (TinyBlocks, #19).
 */
@Mixin(BlockBehaviour.class)
public abstract class MixinBlockBehaviourCollision {

    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void gulliver$carpetCollision(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx,
                                           CallbackInfoReturnable<VoxelShape> cir) {
        if (!(ctx instanceof EntityCollisionContext ecc)) return;
        Entity entity = ((EntityCollisionContextAccessor) ecc).gulliver$getEntity();
        if (entity == null) return;
        // Buttons, levers, pressure plates and signs: solid for tinies, shaped as they are drawn (#19).
        if ((Object) this instanceof net.minecraft.world.level.block.Block block
                && gulliver.common.TinyBlocks.solidFor(block, entity)) {
            cir.setReturnValue(state.getShape(level, pos, ctx));
            return;
        }
        if (!((Object) this instanceof CarpetBlock)) return;
        IResizeableEntity sized = (IResizeableEntity) entity;
        if (sized.isHuge()) {
            cir.setReturnValue(Shapes.empty());
        } else if (sized.isExtraTiny()) {
            cir.setReturnValue(Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 1.0D / 32.0D, 1.0D));
        }
    }
}
