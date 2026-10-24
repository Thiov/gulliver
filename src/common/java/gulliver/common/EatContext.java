package gulliver.common;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;

/**
 * Thread-local capture of the entity currently consuming an item.
 * Set in MixinConsumableEat at HEAD of Consumable.onConsume, cleared
 * at RETURN. Read by MixinFoodDataEat.add to scale food fill values
 * by the eater's size.
 *
 * FoodData itself doesn't carry an entity reference, so we need this
 * indirection. If another mod cancels the consuming call after our
 * HEAD hook, the RETURN hook never runs; two guards make that harmless:
 * {@link #eaterOf} only answers for the FoodData that really belongs to
 * the captured player, and the context is cleared between level ticks.
 */
public final class EatContext {
    private EatContext() {}
    private static final ThreadLocal<LivingEntity> EATER = new ThreadLocal<>();
    public static void set(LivingEntity e) { EATER.set(e); }
    public static LivingEntity get() { return EATER.get(); }
    public static void clear() { EATER.remove(); }

    /** The captured eater, but only if {@code food} is its own hunger bar (never a stale capture of someone else). */
    public static LivingEntity eaterOf(FoodData food) {
        LivingEntity e = EATER.get();
        return e instanceof Player p && p.getFoodData() == food ? e : null;
    }
}
