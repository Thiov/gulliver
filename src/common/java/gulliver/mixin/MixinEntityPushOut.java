package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.6.4 EntityResizeableClientPlayerMP.i (line 41-156) overrode the
 * vanilla pushOutOfBlocks so a tiny entity fitting inside a 1-block-wide
 * tunnel wouldn't be ejected. The check: only push if the *actual* block
 * bbox intersects the tiny's bbox at the queried position. Vanilla
 * pushed on any solid-but-non-cube block (slabs, fences, etc.) which
 * would cause tinies in stair-corner alcoves to twitch.
 *
 * Modern equivalent: Entity.moveTowardsClosestSpace(x,y,z) is the call
 * vanilla makes inside checkInsideBlocks when an entity is judged
 * "stuck" inside an opaque non-passable block. Cancel that call for
 * tinies whose bbox doesn't actually overlap any solid voxel at the
 * queried position.
 */
@Mixin(Entity.class)
public abstract class MixinEntityPushOut {

    @Inject(method = "moveTowardsClosestSpace", at = @At("HEAD"), cancellable = true)
    private void gulliver$skipPushIfTinyFits(double x, double y, double z, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        IResizeableEntity sized = (IResizeableEntity) this;
        if (!sized.isTiny()) return;

        Level level = self.level();
        BlockPos pos = BlockPos.containing(x, y, z);
        BlockState state = level.getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            // No solid voxel at all -> tiny is in air pocket -> don't push.
            ci.cancel();
            return;
        }
        AABB entityBb = self.getBoundingBox();
        // If the entity's bbox doesn't overlap any sub-shape voxel,
        // it's threading through a partial block — leave it alone.
        boolean overlaps = false;
        for (AABB sub : shape.toAabbs()) {
            AABB world = sub.move(pos.getX(), pos.getY(), pos.getZ());
            if (world.intersects(entityBb)) {
                overlaps = true;
                break;
            }
        }
        if (!overlaps) ci.cancel();
    }
}
