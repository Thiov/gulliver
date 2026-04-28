package gulliver.init;

import gulliver.GulliverFabric;
import net.fabricmc.fabric.api.registry.FabricPotionBrewingBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;

/**
 * Six Potion presets covering the 1.6.4 brewing scope:
 *   gulliver:tiny       — base 3:00 amp 0
 *   gulliver:long_tiny  — 8:00 amp 0   (extended via redstone)
 *   gulliver:strong_tiny — 1:30 amp 1  (amplified via glowstone)
 * and the same three for huge.
 *
 * Brewing recipes registered via Fabric BUILD callback:
 *   AWKWARD + RED_MUSHROOM   → tiny
 *   AWKWARD + BROWN_MUSHROOM → huge
 *   tiny    + REDSTONE       → long_tiny
 *   tiny    + GLOWSTONE_DUST → strong_tiny
 *   huge    + REDSTONE       → long_huge
 *   huge    + GLOWSTONE_DUST → strong_huge
 *
 * Mirrors the 1.6.4 PotionResizing.initResizingPotions which seeded
 * PotionHelper bitmask entries for "red mushroom modifies awkward into
 * tiny" and the brown variant. Splash / lingering variants are produced
 * automatically by vanilla potion-brewing infrastructure once the base
 * potion is registered.
 */
public final class GulliverPotions {
    private GulliverPotions() {}

    private static final int BASE_DURATION = 3600;     // 3:00, vanilla potion default
    private static final int LONG_DURATION = 9600;     // 8:00, extended (redstone)
    private static final int STRONG_DURATION = 1800;   // 1:30, amplified (glowstone)

    public static final Holder<Potion> TINY = registerPotion("tiny",
            new MobEffectInstance(GulliverEffects.TINY, BASE_DURATION, 0));
    public static final Holder<Potion> LONG_TINY = registerPotion("long_tiny",
            new MobEffectInstance(GulliverEffects.TINY, LONG_DURATION, 0));
    public static final Holder<Potion> STRONG_TINY = registerPotion("strong_tiny",
            new MobEffectInstance(GulliverEffects.TINY, STRONG_DURATION, 1));
    public static final Holder<Potion> HUGE = registerPotion("huge",
            new MobEffectInstance(GulliverEffects.HUGE, BASE_DURATION, 0));
    public static final Holder<Potion> LONG_HUGE = registerPotion("long_huge",
            new MobEffectInstance(GulliverEffects.HUGE, LONG_DURATION, 0));
    public static final Holder<Potion> STRONG_HUGE = registerPotion("strong_huge",
            new MobEffectInstance(GulliverEffects.HUGE, STRONG_DURATION, 1));

    private static Holder<Potion> registerPotion(String name, MobEffectInstance... effects) {
        return Registry.registerForHolder(BuiltInRegistries.POTION, GulliverFabric.id(name),
                new Potion(name, effects));
    }

    public static void init() {
        FabricPotionBrewingBuilder.BUILD.register(builder -> {
            builder.addMix(Potions.AWKWARD, Items.RED_MUSHROOM, TINY);
            builder.addMix(Potions.AWKWARD, Items.BROWN_MUSHROOM, HUGE);
            builder.addMix(TINY, Items.REDSTONE, LONG_TINY);
            builder.addMix(TINY, Items.GLOWSTONE_DUST, STRONG_TINY);
            builder.addMix(HUGE, Items.REDSTONE, LONG_HUGE);
            builder.addMix(HUGE, Items.GLOWSTONE_DUST, STRONG_HUGE);
        });
    }
}
