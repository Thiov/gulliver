package gulliver.init;

import gulliver.GulliverFabric;
import gulliver.effect.HugeEffect;
import gulliver.effect.TinyEffect;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;

public final class GulliverEffects {
    private GulliverEffects() {}

    public static final Holder<MobEffect> TINY =
            Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, GulliverFabric.id("tiny"), new TinyEffect());
    public static final Holder<MobEffect> HUGE =
            Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, GulliverFabric.id("huge"), new HugeEffect());

    public static void init() {}
}
