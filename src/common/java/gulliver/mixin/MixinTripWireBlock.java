package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
//#if MC >= 1.21.5
import net.minecraft.world.entity.InsideBlockEffectApplier;
//#endif
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BlockTripWireGulliver in 1.6.4: tiny entities don't trigger trip wires
 * (they're too light). Source skipped the super.onEntityCollidedWithBlock
 * call when entity instanceof EntityLiving && isTiny.
 */
@Mixin(TripWireBlock.class)
public abstract class MixinTripWireBlock {

    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void gulliver$tinyDoesNotTrigger(BlockState state, Level level, BlockPos pos,
                                              Entity entity,
                                             //#if MC >= 1.21.5
                                             InsideBlockEffectApplier applier, boolean inside,
                                             //#endif
                                             CallbackInfo ci) {
        if (((IResizeableEntity) entity).isTiny()) {
            ci.cancel();
        }
    }
}
