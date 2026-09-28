package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wade-damping for extra-tiny entities passing through snow / carpet.
 *
 * Targets BlockBehaviour.entityInside because neither CarpetBlock nor
 * SnowLayerBlock overrides entityInside in vanilla — the inherited
 * BlockBehaviour default is the only injectable target. We then filter
 * by the actual block type to scope the damping to snow + carpet only.
 *
 * 1.6.4 of.java line 2684-2700 (snow): horizontal motion *= 0.4 * root
 * 1.6.4 carpet wading                : horizontal motion *= 0.7 * root
 * Both also set isStruggling=true on the LivingEntity per-tick flag.
 */
@Mixin(BlockBehaviour.class)
public abstract class MixinBlockBehaviourWade {

    @Inject(method = "entityInside", at = @At("HEAD"))
    private void gulliver$wadeDamping(BlockState state, Level level, BlockPos pos,
                                       Entity entity, InsideBlockEffectApplier applier,
                                       boolean inWorld, CallbackInfo ci) {
        IResizeableEntity sized = (IResizeableEntity) entity;
        if (!sized.isExtraTiny()) return;

        double factor;
        if (state.getBlock() instanceof SnowLayerBlock) {
            int layers = state.getValue(SnowLayerBlock.LAYERS);
            if (layers <= 1) return;
            factor = 0.4D;
        } else if (state.getBlock() instanceof CarpetBlock) {
            factor = 0.7D;
        } else {
            return;
        }

        float root = sized.getSizeMultiplierRoot();
        Vec3 dm = entity.getDeltaMovement();
        entity.setDeltaMovement(dm.x * factor * root, dm.y, dm.z * factor * root);

        if (entity instanceof LivingEntity) {
            ((gulliver.access.IGulliverFlagsInternal) entity).gulliver$setStruggling(true);
        }
    }
}
