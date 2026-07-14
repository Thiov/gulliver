package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Size-scaled trident wind-up. Vanilla releaseUsing requires
 * charge = getUseDuration() - remaining >= 10 ticks before it throws.
 * Same redirect shape as MixinBowItem: the effective charge becomes
 * `heldTicks * size`, so
 *
 *   size 0.125 → ready after 80 ticks (4 s — hoisting a harpoon
 *                many times your body length takes a moment)
 *   size 0.5   → 20 ticks (1 s)
 *   vanilla    → 10 ticks
 *   size 4     → ~3 ticks (a giant flicks it)
 *
 * This also carries the fix for the "tinies can't throw tridents at
 * all" bug: the old blanket ItemStack.getUseDuration scaling seeded
 * the use countdown with a scaled value while releaseUsing measured
 * charge against the unscaled Item-level constant, making the charge
 * negative for every size below 1. Use-duration scaling is now
 * consumable-only (MixinItemStackUseDuration), and weapon draw speed
 * lives here where both sides of the subtraction are consistent.
 */
@Mixin(TridentItem.class)
public abstract class MixinTridentItem {

    @Redirect(method = "releaseUsing",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/world/item/TridentItem;getUseDuration(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)I"))
    private int gulliver$scaleThrowCharge(TridentItem item, ItemStack stack, LivingEntity entity,
                                           ItemStack stack2, Level level, LivingEntity user,
                                           int remaining) {
        int duration = item.getUseDuration(stack, entity);
        float size = ((IResizeableEntity) entity).getSizeMultiplier();
        if (size == 1.0F) return duration;
        int held = duration - remaining;
        return remaining + Math.round(held * size);
    }
}
