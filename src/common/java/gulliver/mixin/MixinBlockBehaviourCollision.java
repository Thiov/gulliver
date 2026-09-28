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
 */
@Mixin(BlockBehaviour.class)
public abstract class MixinBlockBehaviourCollision {

    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void gulliver$carpetCollision(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx,
                                           CallbackInfoReturnable<VoxelShape> cir) {
        if (!((Object) this instanceof CarpetBlock)) return;
        if (!(ctx instanceof EntityCollisionContext ecc)) return;
        Entity entity = ((EntityCollisionContextAccessor) ecc).gulliver$getEntity();
        if (entity == null) return;
        IResizeableEntity sized = (IResizeableEntity) entity;
        if (sized.isHuge()) {
            cir.setReturnValue(Shapes.empty());
        } else if (sized.isExtraTiny()) {
            cir.setReturnValue(Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 1.0D / 32.0D, 1.0D));
        }
    }
}
