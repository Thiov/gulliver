package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.common.GulliverEnvoy;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.6.4 of.java:1919-1988 huge-fall-impact crush — when a huge entity
 * lands from any height, blocks under and around their footprint with
 * hardness <= 1.0 are destroyed in a vertical column.
 *
 * Verbatim formula:
 *   r   = ceil(width * 0.5)                  // horizontal radius
 *   lev = floor(log2(sizeMultiplier))         // depth (size 2 -> 1, 4 -> 2, 8 -> 3)
 *   if (fallDist - minimum <= 3.0) lev--      // small falls do less
 *   for y in [foot, foot - lev + 1):
 *     for x in [l-r, l+r]:
 *       for z in [n-r, n+r]:
 *         if blockHardness in [0, 1] -> destroy
 *
 * "Shift jump destroys more" comes naturally: fall distance from a
 * super-jump is higher, so the `<= 3.0` clause doesn't fire and lev
 * stays at full depth.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityHugeFall {

    @Inject(method = "causeFallDamage", at = @At("HEAD"))
    private void gulliver$crushOnLanding(double fallDistance, float multiplier,
                                          net.minecraft.world.damagesource.DamageSource source,
                                          CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        IResizeableEntity sized = (IResizeableEntity) self;
        if (!sized.isHuge()) return;
        if (self.isPassenger()) return;
        if (!GulliverEnvoy.canSizeGrief(self)) return;
        Level level = self.level();
        if (level.isClientSide()) return;

        float size = sized.getSizeMultiplier();
        AABB bb = self.getBoundingBox();
        double width = bb.maxX - bb.minX;
        int r = (int) Math.ceil(width * 0.5);
        int lev = (int) Math.floor(Math.log(size) / Math.log(2.0));
        if (fallDistance <= 3.0) lev--;
        if (lev <= 0) return;

        int l = (int) Math.floor((bb.minX + bb.maxX) * 0.5);
        int n = (int) Math.floor((bb.minZ + bb.maxZ) * 0.5);
        int footY = (int) Math.floor(bb.minY - 0.001);

        for (int s = footY; s > footY - lev + 1 && s > level.getMinY(); s--) {
            for (int p = l - r; p <= l + r; p++) {
                for (int q = n - r; q <= n + r; q++) {
                    BlockPos pos = new BlockPos(p, s, q);
                    BlockState st = level.getBlockState(pos);
                    if (st.isAir()) continue;
                    float h = st.getDestroySpeed(level, pos);
                    if (h < 0.0F || h > 1.0F) continue;  // bedrock or too-hard
                    level.destroyBlock(pos, true, self);
                }
            }
        }
    }
}
