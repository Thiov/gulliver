package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.common.GulliverEnvoy;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.6.4 blockClimbingRateForTiny — tinies can climb soft surfaces (dirt,
 * wool, grass, leaves, etc.) at material-typed rates. Vanilla climbable
 * is restricted to ladders/vines/scaffolding; this extends it for tinies.
 *
 * Hook onClimbable RETURN: when entity is tiny AND adjacent to a block
 * with a non-zero climb rate, return true. Vanilla climbable handler
 * then applies upward velocity. The rate-per-material isn't directly
 * exposed (vanilla treats all climbables the same speed), so this is a
 * binary "can climb / can't" — close-enough approximation of the 1.6.4
 * material-specific rates for the player-facing feel.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityTinyClimb {

    @Inject(method = "onClimbable", at = @At("RETURN"), cancellable = true)
    private void gulliver$tinyClimbSoft(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return; // already climbable
        LivingEntity self = (LivingEntity) (Object) this;
        if (!((IResizeableEntity) self).isTiny()) return;

        // Check ±1 around bbox at body height for any climbable-rate block.
        AABB bb = self.getBoundingBox().inflate(0.05D, 0.0D, 0.05D);
        int x1 = (int) Math.floor(bb.minX);
        int x2 = (int) Math.floor(bb.maxX);
        int y1 = (int) Math.floor(bb.minY);
        int y2 = (int) Math.floor(bb.maxY);
        int z1 = (int) Math.floor(bb.minZ);
        int z2 = (int) Math.floor(bb.maxZ);
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    mp.set(x, y, z);
                    if (GulliverEnvoy.blockClimbingRateForTiny(self.level(), mp) > 0.0F) {
                        cir.setReturnValue(true);
                        return;
                    }
                }
            }
        }
    }
}
