package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.common.EatContext;
import gulliver.common.SizeScaling;
import net.minecraft.world.entity.LivingEntity;
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
 *   factor = 1 / size   (inverse of size, within 1/32..32)
 *
 *   size 0.125 → 8× food fill (one steak refills hunger fully and then some)
 *   size 1     → vanilla
 *   size 8     → 0.125× (tiny effect — giants need to eat a lot)
 *
 * Nutrition is whole points, so it is rounded stochastically: a cookie
 * (2 points) at size 8 is worth 0.25 points, i.e. a one-in-four chance of
 * one point, instead of the old floor of 1 that made a cookie as good as a
 * steak for giants. The expected value stays exactly proportional.
 *
 * 1.6.4 GulliverEnvoy mirrors this by overriding food values.
 *
 * Implementation: mixin the private `add(int, float)` method on FoodData.
 * Read the eating player via the thread-local set by MixinConsumableEat,
 * and only for that player's own hunger bar.
 *
 * Before 1.21.2 the method is eat(int, float) and its second argument is
 * a saturation MODIFIER, from which FoodData itself computes the
 * saturation as nutrition * modifier * 2. Scaling the modifier as well
 * would apply the factor twice (size² instead of size), so there only the
 * nutrition is scaled and the saturation follows from it.
 */
@Mixin(FoodData.class)
public abstract class MixinFoodDataEat {

    //#if MC >= 1.21.2
    @ModifyVariable(method = "add", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    //#else
    //$$ @ModifyVariable(method = "eat(IF)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    //#endif
    private int gulliver$scaleNutrition(int nutrition) {
        LivingEntity eater = EatContext.eaterOf((FoodData) (Object) this);
        if (eater == null) return nutrition;
        float size = ((IResizeableEntity) eater).getSizeMultiplier();
        if (size == 1.0F) return nutrition;
        return SizeScaling.stochasticRound(nutrition * (double) SizeScaling.foodFactor(size), eater.getRandom());
    }

    //#if MC >= 1.21.2
    @ModifyVariable(method = "add", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float gulliver$scaleSaturation(float saturation) {
        LivingEntity eater = EatContext.eaterOf((FoodData) (Object) this);
        if (eater == null) return saturation;
        float size = ((IResizeableEntity) eater).getSizeMultiplier();
        if (size == 1.0F) return saturation;
        return saturation * SizeScaling.foodFactor(size);
    }
    //#endif
}
