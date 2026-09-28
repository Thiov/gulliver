package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Size-scaled bow draw: the smaller you are, the longer a full draw
 * takes (a bow is a two-hand workout for a tiny); the bigger you are,
 * the faster (a giant flexes it like a twig).
 *
 * Vanilla releaseUsing computes charge = getUseDuration() - remaining
 * and feeds it to getPowerForTime (full power at charge >= 20). We
 * redirect the getUseDuration call INSIDE releaseUsing so the computed
 * charge becomes `heldTicks * size`:
 *
 *   size 0.125 → full draw at 160 ticks (8 s)
 *   size 0.5   → full draw at 40 ticks (2 s)
 *   vanilla    → 20 ticks (1 s)
 *   size 4     → 5 ticks (instant snap-shot)
 *
 * Redirect-only: the countdown seed (ItemStack.getUseDuration) stays
 * at the unscaled 72000, so client and server agree on remaining ticks
 * and the release math is self-consistent on both sides.
 */
@Mixin(BowItem.class)
public abstract class MixinBowItem {

    @Redirect(method = "releaseUsing",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/world/item/BowItem;getUseDuration(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)I"))
    private int gulliver$scaleDrawCharge(BowItem item, ItemStack stack, LivingEntity entity,
                                          ItemStack stack2, Level level, LivingEntity user,
                                          int remaining) {
        int duration = item.getUseDuration(stack, entity);
        float size = ((IResizeableEntity) entity).getSizeMultiplier();
        if (size == 1.0F) return duration;
        // charge = returned - remaining; we want charge' = heldTicks * size
        // where heldTicks = duration - remaining. So return
        // remaining + round(heldTicks * size).
        int held = duration - remaining;
        return remaining + Math.round(held * size);
    }
}
