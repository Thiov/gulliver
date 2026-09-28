package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.common.GulliverEnvoy;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.6.4 string/leash climb-rope mechanic — ASM-patched in the original,
 * not visible in JDCore source. Reconstructed from intent of the
 * isHoldingStringOrLeash predicate (GulliverEnvoy.java line 634):
 *
 *   tiny + string-in-hand                 → can climb walls (rope)
 *   normal-or-medium + lead-in-hand       → can climb walls (rope)
 *
 * Conservatively scoped: when the predicate matches and the entity is
 * pressing into a wall (any solid block adjacent at body level), make
 * onClimbable return true. The vanilla climbable handler then takes
 * over — same lift-rate as a ladder, same gravity damping, same
 * graceful-fall on release.
 *
 * Limited to LivingEntity since the predicate requires getMainHandItem.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityClimbRope {

    @Inject(method = "onClimbable", at = @At("RETURN"), cancellable = true)
    private void gulliver$leashClimb(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return; // already climbable, no need to add
        LivingEntity self = (LivingEntity) (Object) this;
        if (!GulliverEnvoy.isHoldingStringOrLeash(self)) return;

        // Need to be next to a solid wall to climb. The 1.6.4 ASM patch
        // gated rope-climb on horizontal motion-into-wall plus held
        // input. Approximation: any solid block within ±1 of the entity's
        // bbox at body height makes the rope catch.
        net.minecraft.world.level.Level level = self.level();
        net.minecraft.world.phys.AABB bb = self.getBoundingBox().inflate(0.05D, 0.0D, 0.05D);
        int x1 = net.minecraft.util.Mth.floor(bb.minX);
        int x2 = net.minecraft.util.Mth.floor(bb.maxX);
        int y1 = net.minecraft.util.Mth.floor(bb.minY);
        int y2 = net.minecraft.util.Mth.floor(bb.maxY);
        int z1 = net.minecraft.util.Mth.floor(bb.minZ);
        int z2 = net.minecraft.util.Mth.floor(bb.maxZ);
        net.minecraft.core.BlockPos.MutableBlockPos mp = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    mp.set(x, y, z);
                    net.minecraft.world.level.block.state.BlockState st = level.getBlockState(mp);
                    if (st.isCollisionShapeFullBlock(level, mp)) {
                        cir.setReturnValue(true);
                        return;
                    }
                }
            }
        }
    }
}
