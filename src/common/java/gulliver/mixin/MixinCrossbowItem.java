package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Size-scaled crossbow loading. Everything crossbow-related (the
 * release check, the charge-percent used by onUseTick's loading
 * sounds, quick-charge math) funnels through the static
 * getChargeDuration(stack, entity), so one RETURN scale covers the
 * whole mechanic consistently on client and server:
 *
 *   duration' = duration / size
 *
 *   size 0.125 → 200 ticks (10 s of cranking — it's a siege engine
 *                at that scale)
 *   size 0.5   → 50 ticks (2.5 s)
 *   vanilla    → 25 ticks
 *   size 4+    → ~6 ticks (a giant spans it with two fingers)
 */
@Mixin(CrossbowItem.class)
public abstract class MixinCrossbowItem {

    @Inject(method = "getChargeDuration", at = @At("RETURN"), cancellable = true)
    private static void gulliver$scaleChargeDuration(ItemStack stack, LivingEntity entity,
                                                      CallbackInfoReturnable<Integer> cir) {
        float size = ((IResizeableEntity) entity).getSizeMultiplier();
        if (size == 1.0F) return;
        cir.setReturnValue(Math.max(1, Math.round(cir.getReturnValueI() / size)));
    }
}
