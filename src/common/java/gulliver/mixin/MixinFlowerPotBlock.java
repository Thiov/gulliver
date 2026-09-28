package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BlockFlowerPotGulliver: tiny entities ARE small enough to fall inside
 * a flower pot — but if the pot has a thorny content (cactus or rose),
 * the tiny gets pricked.
 *
 *   if (pot is rose or cactus && entity is tiny living) → 1.0F damage
 *
 * The 1.6.4 source allowed any tiny entity to pass through the pot rim
 * via custom multi-AABB collision; modern FlowerPotBlock already has a
 * narrow 0.375 × 0.375 stub which any extra-tiny entity can fit beside
 * naturally. So this mixin only adds the prick-damage branch.
 */
@Mixin(FlowerPotBlock.class)
public abstract class MixinFlowerPotBlock {

    @Inject(method = "entityInside", at = @At("HEAD"), require = 0)
    private void gulliver$tinyPrickedByThornyContent(BlockState state, Level level, BlockPos pos,
                                                      Entity entity, InsideBlockEffectApplier applier,
                                                      boolean inside, CallbackInfo ci) {
        if (level.isClientSide()) return;
        if (!(entity instanceof LivingEntity living)) return;
        if (!((IResizeableEntity) living).isTiny()) return;

        FlowerPotBlock self = (FlowerPotBlock) (Object) this;
        var content = self.getPotted();
        if (content == Blocks.CACTUS
                || content == Blocks.ROSE_BUSH
                || content == Blocks.WITHER_ROSE
                || content == Blocks.SWEET_BERRY_BUSH) {
            living.hurt(level.damageSources().cactus(), 1.0F);
        }
    }
}
