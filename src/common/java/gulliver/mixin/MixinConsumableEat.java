package gulliver.mixin;

import gulliver.common.EatContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Capture the eating LivingEntity into EatContext during the duration
 * of Consumable.onConsume. MixinFoodDataEat reads it to scale nutrition
 * and saturation by the eater's size.
 */
@Mixin(Consumable.class)
public abstract class MixinConsumableEat {

    @Inject(method = "onConsume", at = @At("HEAD"))
    private void gulliver$captureEater(Level level, LivingEntity entity, ItemStack stack,
                                         CallbackInfoReturnable<ItemStack> cir) {
        EatContext.set(entity);
    }

    @Inject(method = "onConsume", at = @At("RETURN"))
    private void gulliver$clearEater(Level level, LivingEntity entity, ItemStack stack,
                                       CallbackInfoReturnable<ItemStack> cir) {
        EatContext.clear();
    }
}
