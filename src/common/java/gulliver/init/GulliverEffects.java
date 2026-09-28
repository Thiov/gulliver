package gulliver.init;

import gulliver.Gulliver;
import gulliver.effect.HugeEffect;
import gulliver.effect.TinyEffect;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
//#if MC >= 1.20.5
import net.minecraft.core.Holder;
//#endif

import java.util.function.BiConsumer;

/**
 * The two resizing effects. The instances are created here and handed to
 * the loader's registry through {@link #register}; common code reaches
 * them through {@link #tiny()} / {@link #huge()} once registered.
 */
public final class GulliverEffects {
    private GulliverEffects() {}

    public static final MobEffect TINY = new TinyEffect();
    public static final MobEffect HUGE = new HugeEffect();

    public static void register(BiConsumer<net.minecraft.resources.Identifier, MobEffect> sink) {
        sink.accept(Gulliver.id("tiny"), TINY);
        sink.accept(Gulliver.id("huge"), HUGE);
    }

    //#if MC >= 1.20.5
    public static Holder<MobEffect> tiny() {
        return BuiltInRegistries.MOB_EFFECT.wrapAsHolder(TINY);
    }

    public static Holder<MobEffect> huge() {
        return BuiltInRegistries.MOB_EFFECT.wrapAsHolder(HUGE);
    }
    //#else
    //$$ public static MobEffect tiny() { return TINY; }
    //$$ public static MobEffect huge() { return HUGE; }
    //#endif
}
