package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Tinies are too light to depress pressure plates. 1.6.4 apw.java
 * (BlockPressurePlate.e) called {@code GulliverEnvoy.pruneSmallerEntities(0.3F, list)}
 * before counting entities, which dropped any entity smaller than 0.3
 * from the list — both the wooden/stone "any-mob" plate (apx.b) and the
 * player-specific plate (apx.c).
 *
 * In modern MC the count is centralised in
 * {@link BasePressurePlateBlock#getEntityCount(Level, AABB, Class)},
 * called from PressurePlateBlock.getSignalStrength (any/=15) and
 * WeightedPressurePlateBlock.getSignalStrength (count → power level),
 * so a single mixin point covers both cases.
 *
 * Re-implements the count rather than decrementing because vanilla's
 * filter ({@code !Entity.isIgnoringBlockTriggers}) needs to stay — we
 * just AND in {@code !isTiny()}.
 *
 * Also springs tinies off a plate that comes back up (TinyBlocks, #19).
 *
 * Companion to MixinTripWireBlock (same intent: tinies don't trigger
 * traps). Note: pressure-plate-as-button (huge stomping) goes through
 * BlockSetType / canPressPlateLikeButton elsewhere and isn't affected.
 */
@Mixin(BasePressurePlateBlock.class)
public abstract class MixinPressurePlateTinyIgnore {

    @org.spongepowered.asm.mixin.Shadow
    protected abstract int getSignalForState(net.minecraft.world.level.block.state.BlockState state);

    /** A plate that was pressed and now is not springs tinies standing on it into the air (TinyBlocks). */
    @Inject(method = "checkPressed", at = @At("TAIL"))
    private void gulliver$springTinies(Entity entity, Level level, net.minecraft.core.BlockPos pos,
                                       net.minecraft.world.level.block.state.BlockState state, int oldSignal,
                                       org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (oldSignal > 0 && getSignalForState(level.getBlockState(pos)) == 0) {
            gulliver.common.TinyBlocks.plateReleased(level, pos, (BasePressurePlateBlock) (Object) this);
        }
    }

    @Inject(method = "getEntityCount", at = @At("HEAD"), cancellable = true)
    private static void gulliver$skipTinies(Level level, AABB aabb,
                                              Class<? extends Entity> clazz,
                                              CallbackInfoReturnable<Integer> cir) {
        List<? extends Entity> list = level.getEntitiesOfClass(clazz, aabb,
                e -> !e.isSpectator()
                  && !e.isIgnoringBlockTriggers()
                  && !((IResizeableEntity) e).isTiny());
        cir.setReturnValue(list.size());
    }
}
