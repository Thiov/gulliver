package gulliver.common;

import net.minecraft.world.entity.LivingEntity;

/**
 * Thread-local capture of the entity currently consuming an item.
 * Set in MixinConsumableEat at HEAD of Consumable.onConsume, cleared
 * at RETURN. Read by MixinFoodDataEat.add to scale food fill values
 * by the eater's size.
 *
 * FoodData itself doesn't carry an entity reference, so we need this
 * indirection.
 */
public final class EatContext {
    private EatContext() {}
    private static final ThreadLocal<LivingEntity> EATER = new ThreadLocal<>();
    public static void set(LivingEntity e) { EATER.set(e); }
    public static LivingEntity get() { return EATER.get(); }
    public static void clear() { EATER.remove(); }
}
