package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.TripWireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * BlockTripWireGulliver in 1.6.4: tiny entities don't trigger trip wires
 * (they're too light). Source skipped the super.onEntityCollidedWithBlock
 * call when entity instanceof EntityLiving && isTiny.
 *
 * Skipping entityInside is not enough: a wire that is already tripped
 * re-counts everything standing in it on its scheduled tick, so a tiny
 * waiting in a tripped wire kept it powered forever. Every count goes
 * through checkPressed, which asks each entity isIgnoringBlockTriggers;
 * a tiny answers yes there.
 */
@Mixin(TripWireBlock.class)
public abstract class MixinTripWireBlock {

    //#if MC >= 1.21.5
    @Redirect(method = "checkPressed(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Ljava/util/List;)V",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;isIgnoringBlockTriggers()Z"))
    //#else
    //$$ @Redirect(method = "checkPressed(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
    //$$           at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;isIgnoringBlockTriggers()Z"))
    //#endif
    private boolean gulliver$tinyDoesNotTrigger(Entity entity) {
        return entity.isIgnoringBlockTriggers() || ((IResizeableEntity) entity).isTiny();
    }
}
