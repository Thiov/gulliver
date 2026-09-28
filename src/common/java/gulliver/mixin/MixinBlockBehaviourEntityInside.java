package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//#if MC >= 1.21.5
import net.minecraft.world.entity.InsideBlockEffectApplier;
//#endif

/**
 * Flower pots with cactus, roses, wither roses or berry bushes prick tinies
 * that climb in. Hooked on the base BlockBehaviour.entityInside because
 * FlowerPotBlock doesn't override it. (Soul sand, snow and carpet drag live
 * in MixinEntityBlockSpeed: entityInside runs once per overlapped block, so
 * velocity changes here would compound.)
 */
@Mixin(BlockBehaviour.class)
public abstract class MixinBlockBehaviourEntityInside {

    @Inject(method = "entityInside", at = @At("HEAD"))
    private void gulliver$sizedInside(BlockState state, Level level, BlockPos pos, Entity entity,
                                       //#if MC >= 1.21.5
                                       InsideBlockEffectApplier applier, boolean inWorld,
                                       //#endif
                                       CallbackInfo ci) {
        if (level.isClientSide() || !(state.getBlock() instanceof FlowerPotBlock pot)) return;
        if (!(entity instanceof LivingEntity living) || !((IResizeableEntity) entity).isTiny()) return;
        var content = pot.getPotted();
        if (content == Blocks.CACTUS || content == Blocks.ROSE_BUSH
                || content == Blocks.WITHER_ROSE || content == Blocks.SWEET_BERRY_BUSH) {
            living.hurt(level.damageSources().cactus(), 1.0F);
        }
    }
}
