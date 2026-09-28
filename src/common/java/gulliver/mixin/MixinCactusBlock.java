package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BlockCactusGulliver in 1.6.4: tiny LIVING entities walking into a
 * cactus block don't take damage (they're small enough to fit between
 * the spines). Non-living entities and normal/huge living entities take
 * damage normally.
 *
 *   if (!(entity instanceof EntityLiving) || !entity.isTiny())
 *     entity.attackEntityFrom(DamageSource.cactus, 1.0F);
 *
 * Modern: @Inject HEAD entityInside, cancel for tiny living entities.
 */
@Mixin(CactusBlock.class)
public abstract class MixinCactusBlock {

    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void gulliver$skipDamageForTiny(BlockState state, Level level, BlockPos pos,
                                             Entity entity, InsideBlockEffectApplier applier,
                                             boolean inside, CallbackInfo ci) {
        if (!(entity instanceof LivingEntity)) return;
        if (((IResizeableEntity) entity).isTiny()) {
            ci.cancel();
        }
    }
}
