package gulliver.init;

import gulliver.GulliverFabric;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * The 1.6.4 mod's EntityDamageSourcePassive — registered as a data-driven
 * DamageType in 26.x. Used by stepOnSmallerEntities (Phase 10) to deal
 * "step" damage from a huge entity stomping a tiny one.
 *
 * The original overrode getDeathMessageType-style stuff to return false
 * for difficulty scaling. Modern equivalent: scaling=NEVER in the JSON.
 *
 * RAIN is the environmental channel for extra-tinies dissolving in the
 * rain (1.6.4 tinyCaughtInRain). It has no attacker entity — with the
 * PASSIVE type the victim doubled as its own attacker, which produced
 * "X was crushed by X" death messages.
 */
public final class GulliverDamageTypes {
    private GulliverDamageTypes() {}

    public static final ResourceKey<DamageType> PASSIVE =
            ResourceKey.create(Registries.DAMAGE_TYPE, GulliverFabric.id("passive"));
    public static final ResourceKey<DamageType> RAIN =
            ResourceKey.create(Registries.DAMAGE_TYPE, GulliverFabric.id("rain"));

    public static DamageSource passive(Level level, Entity direct) {
        return new DamageSource(
                level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(PASSIVE),
                direct);
    }

    public static DamageSource passive(RegistryAccess access, Entity direct) {
        return new DamageSource(
                access.lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(PASSIVE),
                direct);
    }

    public static DamageSource rain(Level level) {
        return new DamageSource(
                level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(RAIN));
    }
}
