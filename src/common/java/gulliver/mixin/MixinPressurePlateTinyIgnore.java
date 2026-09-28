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
 * Companion to MixinTripWireBlock (same intent: tinies don't trigger
 * traps). Note: pressure-plate-as-button (huge stomping) goes through
 * BlockSetType / canPressPlateLikeButton elsewhere and isn't affected.
 */
@Mixin(BasePressurePlateBlock.class)
public abstract class MixinPressurePlateTinyIgnore {

    @Inject(method = "getEntityCount", at = @At("HEAD"), cancellable = true)
    private static void gulliver$skipTinies(Level level, AABB aabb,
                                              Class<? extends Entity> clazz,
                                              CallbackInfoReturnable<Integer> cir) {
        List<? extends Entity> list = level.getEntitiesOfClass(clazz, aabb,
                e -> !e.isIgnoringBlockTriggers()
                  && !((IResizeableEntity) e).isTiny());
        cir.setReturnValue(list.size());
    }
}
