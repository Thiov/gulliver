package gulliver.common;

import gulliver.access.IGulliverShoulderInternal;
import gulliver.api.IResizeableEntity;
import gulliver.init.GulliverDamageTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
//#if MC >= 1.20.5
import net.minecraft.tags.EntityTypeTags;
//#else
//$$ import net.minecraft.world.entity.MobType;
//#endif

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Giants eat (a CurseForge suggestion): a giant - size 2 or more - can eat
 * a creature at most a quarter of its own size.
 *
 *  - A giant player holding a creature in its hand eats it by holding
 *    sneak + right-click on nothing, like eating food (eight bites, about
 *    1.6 seconds). Nutrition comes from the meal's health; undead meals
 *    upset the stomach like rotten flesh, spiders and other arthropods
 *    poison it like a spider eye. A creature that can't be eaten is set
 *    down instead, as a sneak-click always did.
 *  - A hostile giant mob that strikes prey this small which is down to a
 *    third of its health swallows it whole instead, and heals by the
 *    prey's health. Keep your health up near giants.
 *
 * Eaten creatures die of gulliver:eaten ("was eaten by"), leave no loot
 * table drops or experience and no body; what they carried or wore still
 * drops, and players drop their inventory as on any death. Pets, team
 * mates and bosses are never on the menu, players only where PvP is
 * allowed (or for monsters). A totem of undying gets you spat out.
 * general.giantAppetite turns all of it off.
 */
public final class Appetite {
    private Appetite() {}

    /** Smallest body that counts as a giant here. */
    public static final float MIN_EATER_SIZE = 2.0F;
    /** Biggest meal, as a share of the eater's size. */
    public static final float MAX_MEAL_RATIO = 0.25F;
    /** A hostile giant swallows prey at or below this share of its health. */
    public static final float SWALLOW_HEALTH = 1.0F / 3.0F;
    /** Sneak + right-click requests (one every four ticks) until the meal is gone. */
    static final int BITES = 8;
    /** Ticks without a bite after which a player's meal starts over. */
    private static final int BITE_GAP = 8;

    private static final class Chewing {
        UUID meal;
        int bites;
        long last;
    }

    private static final Map<UUID, Chewing> CHEWING = new ConcurrentHashMap<>();
    /** Creatures being eaten: no loot table drops, no experience. */
    private static final Set<Entity> EATEN = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private static float size(Entity e) {
        return ((IResizeableEntity) e).getSizeMultiplier();
    }

    public static boolean canEat(LivingEntity eater, Entity meal) {
        if (!GulliverConfig.INSTANCE.general.giantAppetite) return false;
        if (!(meal instanceof LivingEntity food) || meal == eater || !food.isAlive()) return false;
        if (eater.isSpectator() || eater.isDeadOrDying()) return false;
        if (meal instanceof ArmorStand || ShoulderHelper.isUnholdable(meal)) return false;
        float eaterSize = size(eater);
        if (eaterSize < MIN_EATER_SIZE || size(meal) > eaterSize * MAX_MEAL_RATIO) return false;
        if (meal instanceof Player p) {
            if (p.isCreative() || p.isSpectator()) return false;
            if (eater instanceof Player ep && !ep.canHarmPlayer(p)) return false;
        }
        return !GiantAoe.spares(eater, meal);
    }

    /** Whether a melee strike by {@code attacker} swallows {@code target} instead of hitting it. */
    public static boolean swallowsInFight(Mob attacker, Entity target) {
        return attacker instanceof Enemy && canEat(attacker, target)
                && ((LivingEntity) target).getHealth() <= ((LivingEntity) target).getMaxHealth() * SWALLOW_HEALTH;
    }

    public static boolean isEaten(Entity e) {
        return EATEN.contains(e);
    }

    /**
     * One sneak + right-click on nothing from a player with a creature in
     * its hand. Returns false when that creature can't be eaten (the click
     * then sets it down, as before).
     */
    public static boolean bite(ServerPlayer player) {
        UUID handId = ((IGulliverShoulderInternal) player).gulliver$getHandEntity();
        Entity held = handId == null ? null : ShoulderHelper.resolve((ServerLevel) player.level(), handId);
        Chewing chewing = CHEWING.get(player.getUUID());
        long now = player.level().getGameTime();
        if (held == null || !canEat(player, held)) {
            // A meal already under way stays put while the button is held.
            return chewing != null && now - chewing.last <= BITE_GAP;
        }
        if (chewing == null || !held.getUUID().equals(chewing.meal) || now - chewing.last > BITE_GAP) {
            chewing = new Chewing();
            chewing.meal = held.getUUID();
            CHEWING.put(player.getUUID(), chewing);
        }
        chewing.last = now;
        Vec3 mouth = mouth(player);
        ServerLevel level = (ServerLevel) player.level();
        if (++chewing.bites < BITES) {
            level.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.GENERIC_EAT, SoundSource.PLAYERS,
                    SizeSound.scaleVolume((IResizeableEntity) player, 0.5F), 0.6F + player.getRandom().nextFloat() * 0.3F);
            return true;
        }
        CHEWING.remove(player.getUUID());
        if (ShoulderHelper.detachHand(player) == held) eat(player, (LivingEntity) held);
        return true;
    }

    /** Swallow {@code meal} now. Returns whether it is gone (a totem spits it back out). */
    public static boolean eat(LivingEntity eater, LivingEntity meal) {
        ServerLevel level = (ServerLevel) eater.level();
        float mealHealth = meal.getMaxHealth();
        Vec3 mouth = mouth(eater);
        EATEN.add(meal);
        //#if MC >= 1.21.2
        meal.hurtServer(level, GulliverDamageTypes.eaten(level, eater), 1.0E6F);
        //#else
        //$$ meal.hurt(GulliverDamageTypes.eaten(level, eater), 1.0E6F);
        //#endif
        if (meal.isAlive()) {
            EATEN.remove(meal);
            Vec3 out = eater.getLookAngle().scale(0.4D);
            meal.setDeltaMovement(out.x, 0.3D, out.z);
            meal.hurtMarked = true;
            return false;
        }
        if (!(meal instanceof Player)) meal.discard();

        feed(eater, meal, mealHealth);
        level.sendParticles(ParticleTypes.POOF, mouth.x, mouth.y, mouth.z,
                6, 0.1D * size(eater), 0.1D * size(eater), 0.1D * size(eater), 0.02D);
        float volume = SizeSound.scaleVolume((IResizeableEntity) eater, 0.8F);
        SoundSource source = eater instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE;
        level.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.GENERIC_EAT, source, volume, 0.5F);
        level.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.PLAYER_BURP, source, volume, 0.6F);
        if (eater instanceof ServerPlayer player) {
            GulliverAdvancements.award(player, GulliverAdvancements.FEE_FI_FO_FUM);
        }
        return true;
    }

    /** Players gain food, monsters health; some meals disagree with the eater. */
    private static void feed(LivingEntity eater, LivingEntity meal, float mealHealth) {
        if (eater instanceof Player player) {
            int nutrition = Math.max(1, Math.min(10, Math.round(mealHealth / 2.5F)));
            player.getFoodData().eat(nutrition, 0.3F);
        } else {
            eater.heal(mealHealth);
        }
        if (isUndead(meal) && eater.getRandom().nextFloat() < 0.8F) {
            eater.addEffect(new MobEffectInstance(MobEffects.HUNGER, 600, 0));
        } else if (isArthropod(meal)) {
            eater.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
        }
    }

    private static boolean isUndead(LivingEntity e) {
        //#if MC >= 1.20.5
        return e.getType().builtInRegistryHolder().is(EntityTypeTags.UNDEAD);
        //#else
        //$$ return e.getMobType() == MobType.UNDEAD;
        //#endif
    }

    private static boolean isArthropod(LivingEntity e) {
        //#if MC >= 1.20.5
        return e.getType().builtInRegistryHolder().is(EntityTypeTags.ARTHROPOD);
        //#else
        //$$ return e.getMobType() == MobType.ARTHROPOD;
        //#endif
    }

    private static Vec3 mouth(LivingEntity eater) {
        return eater.getEyePosition().add(eater.getLookAngle().scale(eater.getBbWidth() * 0.4D))
                .subtract(0.0D, eater.getBbHeight() * 0.04D, 0.0D);
    }

    /** A player left the server: forget a half-eaten meal. */
    public static void forget(ServerPlayer player) {
        CHEWING.remove(player.getUUID());
    }
}
