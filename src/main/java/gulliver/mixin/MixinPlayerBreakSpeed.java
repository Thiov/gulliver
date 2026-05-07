package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Block-break speed scales linearly with size — giants mine fast (more
 * force), tinies mine slow (less leverage).
 *
 *   destroySpeed' = base * size
 *
 *   size 0.125 → 12.5% speed (8x slower)
 *   size 0.5   → 50% speed
 *   size 1     → vanilla
 *   size 2     → 2x speed
 *   size 4     → 4x speed
 *   size 8     → 8x speed (giants strip-mine effortlessly)
 *
 * Player.getDestroySpeed returns a multiplier on the block's hardness;
 * the actual mining-progress-per-tick = destroySpeed / hardness / 30.
 * Multiplying destroySpeed by size cleanly tracks the user's
 * "proportional to body size" intent.
 */
@Mixin(Player.class)
public abstract class MixinPlayerBreakSpeed {

    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
    private void gulliver$scaleDestroySpeed(BlockState state,
                                             CallbackInfoReturnable<Float> cir) {
        float size = ((IResizeableEntity) this).getSizeMultiplier();
        if (size == 1.0F) return;
        cir.setReturnValue(cir.getReturnValueF() * size);
    }
}
