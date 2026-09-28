package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SoulSandBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//#if MC >= 1.21.5
import net.minecraft.world.entity.InsideBlockEffectApplier;
//#endif

/**
 * Size effects of standing inside a few blocks. Hooked on the base
 * BlockBehaviour.entityInside because none of these blocks override it
 * (so a mixin on the block classes themselves never ran):
 *
 *  - snow (2+ layers) and carpet: extra-tinies wade through them slowly
 *    (1.6.4 "struggling");
 *  - soul sand: tinies sink in twice as slow, giants stride over it;
 *  - flower pots with cactus, roses, wither roses or berry bushes prick
 *    tinies that climb in.
 */
@Mixin(BlockBehaviour.class)
public abstract class MixinBlockBehaviourEntityInside {

    @Inject(method = "entityInside", at = @At("HEAD"))
    private void gulliver$sizedInside(BlockState state, Level level, BlockPos pos, Entity entity,
                                       //#if MC >= 1.21.5
                                       InsideBlockEffectApplier applier, boolean inWorld,
                                       //#endif
                                       CallbackInfo ci) {
        IResizeableEntity sized = (IResizeableEntity) entity;
        float size = sized.getSizeMultiplier();
        if (size == 1.0F) return;
        Object block = state.getBlock();

        if (block instanceof SoulSandBlock) {
            Vec3 m = entity.getDeltaMovement();
            if (sized.isTiny()) {
                entity.setDeltaMovement(m.x * 0.5D, m.y, m.z * 0.5D);
            } else if (sized.isHuge()) {
                // Undo most of the vanilla soul-sand drag for a giant's stride.
                entity.setDeltaMovement(m.x * 2.5D, m.y, m.z * 2.5D);
            }
            return;
        }

        if (block instanceof FlowerPotBlock pot) {
            if (level.isClientSide() || !(entity instanceof LivingEntity living) || !sized.isTiny()) return;
            var content = pot.getPotted();
            if (content == Blocks.CACTUS || content == Blocks.ROSE_BUSH
                    || content == Blocks.WITHER_ROSE || content == Blocks.SWEET_BERRY_BUSH) {
                living.hurt(level.damageSources().cactus(), 1.0F);
            }
            return;
        }

        if (!sized.isExtraTiny()) return;
        double factor;
        if (block instanceof SnowLayerBlock) {
            if (state.getValue(SnowLayerBlock.LAYERS) <= 1) return;
            factor = 0.4D;
        } else if (block instanceof CarpetBlock) {
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
