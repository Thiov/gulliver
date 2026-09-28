package gulliver.init;

import gulliver.Gulliver;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
//#if MC >= 1.20.5
import net.minecraft.core.Holder;
//#endif

import java.util.function.BiConsumer;

/**
 * Six potions covering the 1.6.4 brewing scope: Ensmallening and
 * Embiggening, each in base (3:00), long (8:00, redstone) and strong
 * (1:30, amplifier 1, glowstone) form. Awkward + red mushroom brews Tiny,
 * awkward + brown mushroom brews Huge (see MixinPotionBrewing). Splash,
 * lingering and tipped-arrow variants come for free from vanilla.
 */
public final class GulliverPotions {
    private GulliverPotions() {}

    private static final int BASE_DURATION = 3600;
    private static final int LONG_DURATION = 9600;
    private static final int STRONG_DURATION = 1800;

    public static Potion TINY, LONG_TINY, STRONG_TINY, HUGE, LONG_HUGE, STRONG_HUGE;

    /** Must run after the effects are registered (registry order guarantees it). */
    public static void register(BiConsumer<net.minecraft.resources.Identifier, Potion> sink) {
        TINY = make(sink, "tiny", "tiny", new MobEffectInstance(GulliverEffects.tiny(), BASE_DURATION, 0));
        LONG_TINY = make(sink, "long_tiny", "tiny", new MobEffectInstance(GulliverEffects.tiny(), LONG_DURATION, 0));
        STRONG_TINY = make(sink, "strong_tiny", "tiny", new MobEffectInstance(GulliverEffects.tiny(), STRONG_DURATION, 1));
        HUGE = make(sink, "huge", "huge", new MobEffectInstance(GulliverEffects.huge(), BASE_DURATION, 0));
        LONG_HUGE = make(sink, "long_huge", "huge", new MobEffectInstance(GulliverEffects.huge(), LONG_DURATION, 0));
        STRONG_HUGE = make(sink, "strong_huge", "huge", new MobEffectInstance(GulliverEffects.huge(), STRONG_DURATION, 1));
    }

    /**
     * The potion's name is the translation-key suffix
     * ("item.minecraft.potion.effect." + name), so it carries the mod
     * namespace, and long/strong variants share their base name exactly
     * like vanilla's long_swiftness/strong_swiftness do.
     */
    private static Potion make(BiConsumer<net.minecraft.resources.Identifier, Potion> sink, String id,
                               String baseName, MobEffectInstance effect) {
        Potion potion = new Potion(Gulliver.MOD_ID + "." + baseName, effect);
        sink.accept(Gulliver.id(id), potion);
        return potion;
    }

    //#if MC >= 1.20.5
    public static Holder<Potion> holder(Potion potion) {
        return BuiltInRegistries.POTION.wrapAsHolder(potion);
    }
    //#endif
}
