package gulliver.common;

import gulliver.access.IGulliverShoulderInternal;
import gulliver.api.IResizeableEntity;
import gulliver.init.GulliverDamageTypes;
import gulliver.network.Payloads;
import gulliver.platform.Services;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
 * Bigger things eat smaller things (a CurseForge suggestion): anything can
 * eat a creature at most a quarter of its own size - by size difference,
 * like trampling and carrying, so a normal player can snack on a tiny
 * chicken and a size-8 giant on a normal player.
 *
 *  - A player holding a creature in its hand eats it by holding sneak +
 *    right-click on nothing, like eating food: the hand brings it up to
 *    the mouth in half a second (the carry slot moves, the arm follows in
 *    first and third person) and the player takes four bites, one every
 *    four ticks, as long as the button stays down. A creature that can't
 *    be eaten is set down instead, as a sneak-click always did.
 *  - A hostile mob that strikes prey this small which is down to a third
 *    of its health snatches it instead: it lifts the prey to its mouth
 *    over a second and bites four times, half a second apart - three
 *    seconds in all. Sneaking doesn't get you out of a monster's fist;
 *    hurting the monster (or a friend hurting it) makes it drop you.
 *
 * Every bite is real damage of the gulliver:eaten type - a player's
 * attack (at least 2), half a monster's (at least 1.5) - scaled by the
 * size gap like any blow, and reduced
 * by armor, Protection and Resistance (only shields can't stop it). The
 * bite that finishes the meal swallows it ("was eaten by"); a meal tough
 * enough to come through every bite is spat out, hurt but alive - so
 * armor is what keeps a small player from being eaten. A totem of
 * undying also gets you spat out.
 *
 * Swallowed creatures leave no loot table drops or experience and no
 * body; what they carried or wore still drops, and players drop their
 * inventory as on any death. The eater is fed by the meal's health
 * (players get food, monsters heal); undead meals upset the stomach like
 * rotten flesh, spiders and other arthropods poison it like a spider eye.
 * Pets, team mates and bosses are never on the menu, players only where
 * PvP is allowed (or for monsters). general.giantAppetite turns all of it
 * off.
 */
public final class Appetite {
    private Appetite() {}

    /** Biggest meal, as a share of the eater's size. */
    public static final float MAX_MEAL_RATIO = 0.25F;
    /** A hostile mob snatches prey at or below this share of its health. */
    public static final float SWALLOW_HEALTH = 1.0F / 3.0F;
    /** Bites a meal gets; one that survives them all is spat out. */
    public static final int BITES = 4;
    /** Ticks without a sneak + right-click request after which a player stops eating. */
    private static final int REQUEST_GAP = 8;

    /** Ticks the hand takes to reach the mouth: half a second for a player, a second for a monster. */
    public static int liftTicks(Entity eater) {
        return eater instanceof Player ? 10 : 20;
    }

    /** Ticks between bites: four for a player, ten for a monster. */
    public static int biteEvery(Entity eater) {
        return eater instanceof Player ? 4 : 10;
    }

    /** Chew tick of the first bite: just after the meal reaches the mouth. */
    public static int firstBite(Entity eater) {
        return liftTicks(eater) + (eater instanceof Player ? 2 : 6);
    }

    /** Chew tick of the last bite. */
    public static int lastBite(Entity eater) {
        return firstBite(eater) + (BITES - 1) * biteEvery(eater);
    }

    private static final class Meal {
        UUID food;
        long last;
    }

    private static final Map<UUID, Meal> MEALS = new ConcurrentHashMap<>();
    /** Creatures being eaten: no loot table drops, no experience. */
    private static final Set<Entity> EATEN = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private static float size(Entity e) {
        return ((IResizeableEntity) e).getSizeMultiplier();
    }

    private static IGulliverShoulderInternal carry(Entity e) {
        return (IGulliverShoulderInternal) e;
    }

    public static boolean canEat(LivingEntity eater, Entity meal) {
        if (!GulliverConfig.INSTANCE.general.giantAppetite) return false;
        if (!(meal instanceof LivingEntity food) || meal == eater || !food.isAlive()) return false;
        if (eater.isSpectator() || eater.isDeadOrDying()) return false;
        if (meal instanceof ArmorStand || ShoulderHelper.isUnholdable(meal)) return false;
        if (size(meal) > size(eater) * MAX_MEAL_RATIO) return false;
        UUID holder = carry(meal).gulliver$getHoldingEntity();
        if (holder != null && !holder.equals(eater.getUUID())) return false; // in someone else's hand
        if (meal instanceof Player p) {
            if (p.isCreative() || p.isSpectator()) return false;
            if (eater instanceof Player ep && !ep.canHarmPlayer(p)) return false;
        }
        return !GiantAoe.spares(eater, meal);
    }

    /** Whether a melee strike by {@code attacker} snatches {@code target} instead of hitting it. */
    public static boolean swallowsInFight(Mob attacker, Entity target) {
        return attacker instanceof Enemy && !isChewing(attacker)
                && carry(attacker).gulliver$getHandEntity() == null
                && carry(target).gulliver$getHoldingEntity() == null
                && canEat(attacker, target)
                && ((LivingEntity) target).getHealth() <= ((LivingEntity) target).getMaxHealth() * SWALLOW_HEALTH;
    }

    /** A hostile mob snatches its prey: into the hand, up to the mouth, then the bites (see tick). */
    public static void snatch(Mob eater, LivingEntity prey) {
        if (prey.isPassenger()) prey.stopRiding();
        ShoulderHelper.grab(eater, prey);
        startChewing(eater);
        Vec3 at = prey.position();
        eater.level().playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_WEAK,
                SoundSource.HOSTILE, SizeSound.scaleVolume((IResizeableEntity) eater, 0.8F), 0.5F);
    }

    public static boolean isChewing(Entity e) {
        return carry(e).gulliver$getChewTicks() >= 0;
    }

    public static boolean isEaten(Entity e) {
        return EATEN.contains(e);
    }

    /** How far the hand has come up to the mouth, 0 to 1, eased. */
    public static float lift(Entity e, float partialTick) {
        int ticks = carry(e).gulliver$getChewTicks();
        if (ticks < 0) return 0.0F;
        float t = Mth.clamp((ticks + partialTick) / liftTicks(e), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /** Vertical bob of a meal being chewed, in blocks: one dip per bite. */
    public static double chomp(Entity e, float partialTick) {
        int ticks = carry(e).gulliver$getChewTicks();
        int lift = liftTicks(e);
        if (ticks < lift) return 0.0D;
        double phase = Math.abs(Math.sin(Math.PI * (ticks + partialTick - lift) / biteEvery(e)));
        return -phase * e.getBbHeight() * 0.02D;
    }

    static void startChewing(LivingEntity eater) {
        carry(eater).gulliver$setChewTicks(0);
        Services.platform().sendToTrackingAndSelf(eater,
                new Payloads.ActionState(eater.getId(), Payloads.ActionState.CHEW, true));
    }

    /** The meal is over, eaten or not. */
    public static void stopChewing(LivingEntity eater) {
        MEALS.remove(eater.getUUID());
        if (!isChewing(eater)) return;
        carry(eater).gulliver$setChewTicks(-1);
        if (!eater.level().isClientSide()) {
            Services.platform().sendToTrackingAndSelf(eater,
                    new Payloads.ActionState(eater.getId(), Payloads.ActionState.CHEW, false));
        }
    }

    /**
     * Every tick of a living body, on both sides (LivingEntity.aiStep; other
     * players on a client from their own tick): count the chewing on, and
     * on the server end it - a player who stopped biting puts the meal
     * back in the hand, a mob swallows once its time is up.
     */
    public static void tick(LivingEntity eater) {
        IGulliverShoulderInternal c = carry(eater);
        int ticks = c.gulliver$getChewTicks();
        if (ticks < 0) return;
        c.gulliver$setChewTicks(ticks + 1);
        if (eater.level().isClientSide()) return;
        if (c.gulliver$getHandEntity() == null || !eater.isAlive()) {
            stopChewing(eater);
            return;
        }
        if (eater instanceof Player) {
            // A player eats only while the button stays down.
            Meal meal = MEALS.get(eater.getUUID());
            if (meal == null || eater.level().getGameTime() - meal.last > REQUEST_GAP) {
                stopChewing(eater);
                return;
            }
        }
        int now = ticks + 1;
        int first = firstBite(eater);
        if (now < first || (now - first) % biteEvery(eater) != 0) return;
        Entity food = ShoulderHelper.resolve((ServerLevel) eater.level(), c.gulliver$getHandEntity());
        if (!(food instanceof LivingEntity meal) || !canEat(eater, meal)) {
            ShoulderHelper.detachHand(eater);
            stopChewing(eater);
        } else if (!chew(eater, meal) && now >= lastBite(eater)) {
            spitOut(eater, meal);
        }
    }

    /** A mob that gets hurt while it holds its prey up drops it. */
    public static void onHurt(LivingEntity eater) {
        if (eater instanceof Player || !isChewing(eater) || eater.level().isClientSide()) return;
        ShoulderHelper.detachHand(eater);
        stopChewing(eater);
    }

    /**
     * One sneak + right-click on nothing from a player with a creature in
     * its hand (repeated every four ticks while the button is held): starts
     * the meal or keeps it going; the bites themselves follow the tick.
     * Returns false when that creature can't be eaten (the click then sets
     * it down, as before).
     */
    public static boolean bite(ServerPlayer player) {
        UUID handId = carry(player).gulliver$getHandEntity();
        Entity held = handId == null ? null : ShoulderHelper.resolve((ServerLevel) player.level(), handId);
        Meal meal = MEALS.get(player.getUUID());
        long now = player.level().getGameTime();
        if (held == null || !canEat(player, held)) {
            // A meal already under way stays put while the button is held.
            return meal != null && now - meal.last <= REQUEST_GAP;
        }
        if (meal == null || !held.getUUID().equals(meal.food) || !isChewing(player)) {
            meal = new Meal();
            meal.food = held.getUUID();
            MEALS.put(player.getUUID(), meal);
            startChewing(player);
        }
        meal.last = now;
        return true;
    }

    private static void chewingSound(LivingEntity eater) {
        Vec3 mouth = mouth(eater);
        SoundSource source = eater instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE;
        eater.level().playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.GENERIC_EAT, source,
                SizeSound.scaleVolume((IResizeableEntity) eater, 0.5F), 0.6F + eater.getRandom().nextFloat() * 0.3F);
        ((ServerLevel) eater.level()).sendParticles(ParticleTypes.POOF, mouth.x, mouth.y, mouth.z,
                2, 0.08D * eater.getBbWidth(), 0.05D * eater.getBbHeight(), 0.08D * eater.getBbWidth(), 0.01D);
    }

    /**
     * The damage of one bite before armor (the size gap scales it like any
     * blow): a player's attack, at least 2; half a monster's, at least 1.5,
     * so a snatched player lasts a bite or two longer than one punch.
     */
    static float biteDamage(LivingEntity eater) {
        var attack = eater.getAttribute(Attributes.ATTACK_DAMAGE);
        double value = attack == null ? 0.0D : attack.getValue();
        return eater instanceof Player ? (float) Math.max(2.0D, value) : (float) Math.max(1.5D, value * 0.5D);
    }

    /**
     * One bite of the meal in the eater's hand. Returns true when that bite
     * finished it: then it is swallowed.
     */
    static boolean chew(LivingEntity eater, LivingEntity meal) {
        ServerLevel level = (ServerLevel) eater.level();
        float mealHealth = meal.getMaxHealth();
        // Bites come faster than the usual half-second of invulnerability.
        //#if MC >= 26.3
        meal.damageCooldownTime = 0;
        //#else
        //$$ meal.invulnerableTime = 0;
        //#endif
        EATEN.add(meal);
        //#if MC >= 1.21.2
        meal.hurtServer(level, GulliverDamageTypes.eaten(level, eater), biteDamage(eater));
        //#else
        //$$ meal.hurt(GulliverDamageTypes.eaten(level, eater), biteDamage(eater));
        //#endif
        if (meal.isAlive()) {
            EATEN.remove(meal);
            chewingSound(eater);
            return false;
        }
        ShoulderHelper.takeFromHand(eater);
        stopChewing(eater);
        swallowed(eater, meal, mealHealth);
        return true;
    }

    /** Too tough to swallow: out of the hand, tossed away from the mouth. */
    private static void spitOut(LivingEntity eater, LivingEntity meal) {
        ShoulderHelper.detachHand(eater);
        stopChewing(eater);
        Vec3 out = eater.getLookAngle().scale(0.3D + 0.1D * Math.sqrt(size(eater)));
        meal.setDeltaMovement(out.x, 0.25D, out.z);
        meal.hurtMarked = true;
        Vec3 mouth = mouth(eater);
        eater.level().playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.LLAMA_SPIT,
                eater instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE,
                SizeSound.scaleVolume((IResizeableEntity) eater, 0.8F), 0.7F);
    }

    /** The meal went down. */
    private static void swallowed(LivingEntity eater, LivingEntity meal, float mealHealth) {
        ServerLevel level = (ServerLevel) eater.level();
        Vec3 mouth = mouth(eater);
        if (!(meal instanceof Player)) meal.discard();
        feed(eater, meal, mealHealth);
        double spread = 0.12D * eater.getBbWidth();
        level.sendParticles(ParticleTypes.POOF, mouth.x, mouth.y, mouth.z, 8, spread, spread, spread, 0.02D);
        float volume = SizeSound.scaleVolume((IResizeableEntity) eater, 0.8F);
        SoundSource source = eater instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE;
        level.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.GENERIC_EAT, source, volume, 0.5F);
        level.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.PLAYER_BURP, source, volume, 0.6F);
        if (eater instanceof ServerPlayer player) {
            GulliverAdvancements.award(player, GulliverAdvancements.FEE_FI_FO_FUM);
        }
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
                .subtract(0.0D, eater.getBbHeight() * 0.06D, 0.0D);
    }

    /** A player left the server: forget a half-eaten meal. */
    public static void forget(ServerPlayer player) {
        MEALS.remove(player.getUUID());
    }
}
