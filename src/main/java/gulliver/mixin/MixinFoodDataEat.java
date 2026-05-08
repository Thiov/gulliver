package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Scale food fill by entity size: tinies gain MORE nutrition + saturation
 * per food (a small body needs less food but the bite fills proportionally
 * more); giants gain LESS (their body is bigger so the same bite fills
 * less of their hunger bar).
 *
 *   factor = 1 / size   (inverse of size)
 *
 *   size 0.125 → 8× food fill (one steak refills hunger fully and then some)
 *   size 1     → vanilla
 *   size 8     → 0.125× (tiny effect — giants need to eat a lot)
 *
 * 1.6.4 GulliverEnvoy mirrors this by overriding food values.
 *
 * Implementation: mixin the private `add(int, float)` method on FoodData.
 * Read the eating player via the thread-local set by MixinConsumableEat.
 */
@Mixin(FoodData.class)
public abstract class MixinFoodDataEat {

    @ModifyVariable(method = "add", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int gulliver$scaleNutrition(int nutrition) {
        LivingEntity eater = gulliver.common.EatContext.get();
        if (eater == null) return nutrition;
        float size = ((IResizeableEntity) eater).getSizeMultiplier();
        if (size == 1.0F) return nutrition;
        return Math.max(1, Math.round(nutrition / size));
    }

    @ModifyVariable(method = "add", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float gulliver$scaleSaturation(float saturation) {
        LivingEntity eater = gulliver.common.EatContext.get();
        if (eater == null) return saturation;
        float size = ((IResizeableEntity) eater).getSizeMultiplier();
        if (size == 1.0F) return saturation;
        return saturation / size;
    }
}
