package gulliver.common;

import gulliver.api.IResizeableEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Riding rules by size.
 *
 * Giants can't sit in anything much smaller than themselves: a rider more
 * than {@link #GIANT_RATIO} times the size of its vehicle is refused, and
 * dismounted when it grows past that. Boats, minecarts and the like count
 * as size 1.
 *
 * Tinies can ride a handful of small animals (the one list is
 * {@link #specOf}). Climbing on takes an animal {@link #BOARD_RATIO} times
 * your size; the animal answers to its rider, like a saddled horse, for as
 * long as it stays {@link #CONTROL_RATIO} times your size. Whether a mount
 * is controlled depends only on the animal, the rider and their sizes, so
 * the client and the server always agree; the {@code general.tinyMounts}
 * switch only decides who may climb on.
 */
public final class TinyMounts {
    private TinyMounts() {}

    /** A rider this many times its vehicle's size is a giant in a toy. */
    public static final float GIANT_RATIO = 2.0F;
    /** How much bigger than you an animal must be for you to climb on. */
    public static final float BOARD_RATIO = 3.0F;
    /** How much bigger than you an animal must stay to obey you. */
    public static final float CONTROL_RATIO = 2.0F;

    /**
     * How a ridden animal moves. {@code speed} scales its walking speed
     * attribute, {@code jump} its jump, and a {@code hopper} only moves in
     * hops (it has no walk).
     */
    public record Spec(float speed, float jump, boolean hopper) {}

    private static final Spec CHICKEN = new Spec(0.5F, 1.0F, false);
    private static final Spec RABBIT = new Spec(1.0F, 1.0F, true);
    private static final Spec CAT = new Spec(0.75F, 1.15F, false);
    private static final Spec OCELOT = new Spec(0.85F, 1.3F, false);
    private static final Spec WOLF = new Spec(0.8F, 1.0F, false);
    private static final Spec FOX = new Spec(0.9F, 1.3F, false);

    /** A rabbit's hop and its high leap (jump key): vertical power, forward blocks per tick. */
    public static final float HOP_POWER = 0.52F;
    public static final float HOP_FORWARD = 0.26F;
    public static final float LEAP_POWER = 0.72F;
    public static final float LEAP_FORWARD = 0.32F;

    /** The small animals a tiny can ride, with how each moves; null for everything else. */
    public static Spec specOf(Entity e) {
        EntityType<?> t = e.getType();
        if (t == EntityTypes.CHICKEN) return CHICKEN;
        if (t == EntityTypes.RABBIT) return RABBIT;
        if (t == EntityTypes.CAT) return CAT;
        if (t == EntityTypes.OCELOT) return OCELOT;
        if (t == EntityTypes.WOLF) return WOLF;
        if (t == EntityTypes.FOX) return FOX;
        return null;
    }

    public static float sizeOf(Entity e) {
        return ((IResizeableEntity) e).getSizeMultiplier();
    }

    // ---- giants in toys ----

    /**
     * The size a vehicle counts for: 1 for boats, minecarts and the like; a
     * creature counts for its size, or for its height against a player's
     * when that is more (a happy ghast is a big seat even at size 1).
     */
    private static float vehicleSize(Entity vehicle) {
        if (!(vehicle instanceof LivingEntity)) return 1.0F;
        return Math.max(sizeOf(vehicle), vehicle.getBbHeight() / 1.8F);
    }

    /** True when {@code rider} is too big for {@code vehicle}; ridden or not, that is not allowed. */
    public static boolean tooBigFor(Entity rider, Entity vehicle) {
        return sizeOf(rider) > GIANT_RATIO * vehicleSize(vehicle);
    }

    /** A non-forced ride the size rules turn down. */
    public static boolean refusesRide(Entity rider, Entity vehicle) {
        if (tooBigFor(rider, vehicle)) return true;
        // With tiny mounts off, nobody small climbs on these animals either.
        return rider instanceof Player && specOf(vehicle) != null && !GulliverConfig.INSTANCE.general.tinyMounts
                && sizeOf(vehicle) >= CONTROL_RATIO * sizeOf(rider);
    }

    // ---- tinies on small animals ----

    /** The player steering this animal, or null when nobody is (or the rider is too big for it to obey). */
    public static Player controllerOf(Entity mount) {
        if (!mount.isVehicle() || specOf(mount) == null) return null;
        if (!(mount.getFirstPassenger() instanceof Player rider)) return null;
        return sizeOf(mount) >= CONTROL_RATIO * sizeOf(rider) ? rider : null;
    }

    /**
     * Can this rider climb on this animal? Both sides ask this from synced
     * state (so the click is decided alike); claims and the config switch
     * are the server's {@link #mount}.
     */
    public static boolean canBoard(Entity rider, Entity mount) {
        if (!(mount instanceof Mob m) || specOf(m) == null || rider == m) return false;
        if (!m.isAlive() || m.isBaby() || m.isVehicle() || m.isSleeping()) return false;
        if (rider.isPassenger() || rider.isSpectator() || ShoulderHelper.isCarried(rider)) return false;
        if (sizeOf(m) < BOARD_RATIO * sizeOf(rider)) return false;
        // A tamed animal carries nobody but its owner.
        UUID owner = ShoulderHelper.ownerOf(m);
        return owner == null || owner.equals(rider.getUUID());
    }

    /** Server side of the right-click: climb on, if the switch is on and the claim hooks allow it. */
    public static boolean mount(ServerPlayer rider, Entity mount) {
        if (!GulliverConfig.INSTANCE.general.tinyMounts || !canBoard(rider, mount)) return false;
        if (!ShoulderHelper.mayInteract(rider, mount)) return false;
        if (!rider.startRiding(mount)) return false;
        // Whatever the animal was busy with, it stands up and drops its quarrels.
        Mob m = (Mob) mount;
        m.setTarget(null);
        m.getNavigation().stop();
        if (m instanceof TamableAnimal pet) {
            pet.setOrderedToSit(false);
            pet.setInSittingPose(false);
        }
        return true;
    }

    // ---- steering (called from the LivingEntity ridden-movement hooks) ----

    /** Sideways and forward, like a horse: backing up is slow. */
    public static Vec3 riddenInput(Player controller) {
        float forward = controller.zza;
        if (forward < 0.0F) forward *= 0.4F;
        return new Vec3(controller.xxa * 0.8F, 0.0D, forward);
    }

    /** Ground speed of a ridden animal: a hopper only has speed in the air. */
    public static float riddenSpeed(LivingEntity mount, Spec spec) {
        if (spec.hopper() && mount.onGround()) return 0.0F;
        return (float) mount.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)
                * spec.speed();
    }
}
