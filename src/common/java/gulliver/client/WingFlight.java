package gulliver.client;

import gulliver.access.IGulliverShoulderInternal;
import gulliver.access.IGulliverWings;
import gulliver.api.IResizeableEntity;
import gulliver.common.SyncedSettings;
import gulliver.common.WingType;
import gulliver.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/**
 * Flying on wings (TinyWings), for the player at the keyboard. Not
 * creative flight: the body has momentum, the wings tire, and each kind of
 * wing handles differently (WingType).
 *
 *  - Jump while in the air: the wings open and you hover, bobbing gently
 *    in place on the beat of your wings.
 *  - Move (WASD): fly the way you look, speeding up and slowing down
 *    smoothly. Sprint darts.
 *  - Hold jump to climb, hold sneak to drop with the wings half shut.
 *  - Land on anything to fold them again.
 *
 * Flapping costs stamina (more to climb or dart, little to glide), which
 * comes back on the ground. Spent wings can only hold themselves open: you
 * glide down slowly and safely, and have to land before flying again. Rain
 * soaks them the same way, and water ends the flight. Butterflies glide
 * between flaps when they fly along, which costs nothing.
 *
 * Runs on the client like all player movement: the state machine before the
 * player's tick (MixinLocalPlayerWings), the motion in place of vanilla's
 * travel (MixinLivingEntityWingTravel). The server only hears what the wings
 * are doing (Payloads.WingFlight) and passes it on.
 */
public final class WingFlight {
    private WingFlight() {}

    /** 0..1. */
    private static float stamina = 1.0F;
    private static float prevStamina = 1.0F;
    /** Spent: no flapping until the wings have rested back to a third. */
    private static boolean spent;
    private static boolean jumpWasDown;
    /** Ticks the jump key has been held since the feet left the ground (-1: let go, or not jumping). */
    private static int heldAirTicks = -1;
    private static int groundHeld;
    /** Ticks since the flight started, for the hover bob and the butterfly's rhythm. */
    private static int airTicks;
    private static byte sent = IGulliverWings.FOLDED;

    public static float stamina(float partialTick) {
        return Mth.lerp(partialTick, prevStamina, stamina);
    }

    public static boolean spent() {
        return spent;
    }

    /** Whether this player's wings could carry it right now (ignoring stamina). */
    public static boolean canFly(LocalPlayer p) {
        WingType type = ((IGulliverWings) p).gulliver$wingType();
        return type != WingType.NONE && SyncedSettings.tinyWings()
                && ((IResizeableEntity) p).getSizeMultiplier() <= SyncedSettings.wingsMaxSize() + 1.0E-4F
                && !p.isPassenger() && !p.isSpectator() && !p.getAbilities().flying && !p.isFallFlying()
                && !p.isInWater() && !p.isInLava() && !p.isSleeping() && p.isAlive()
                && ((IGulliverShoulderInternal) p).gulliver$getHoldingEntity() == null;
    }

    private static boolean wet(LocalPlayer p) {
        return p.level().isRainingAt(p.blockPosition()) || p.level().isRainingAt(p.blockPosition().above());
    }

    /** Before the player's own tick: take off, land, and decide what the wings do. */
    public static void preTick(LocalPlayer p) {
        Minecraft mc = Minecraft.getInstance();
        IGulliverWings w = (IGulliverWings) p;
        prevStamina = stamina;
        boolean jump = mc.options.keyJump.isDown();
        boolean jumpPressed = jump && !jumpWasDown;
        jumpWasDown = jump;
        byte state = w.gulliver$wingFlight();
        boolean flying = IGulliverWings.flying(state);
        WingType type = w.gulliver$wingType();

        if (!canFly(p)) {
            if (flying) setState(p, IGulliverWings.FOLDED);
            rest(p);
            return;
        }
        if (!flying) {
            rest(p);
            // Keep holding jump after a jump: the wings open near the top of it.
            if (!jump) heldAirTicks = -1;
            else if (p.onGround()) heldAirTicks = 0;
            else if (heldAirTicks >= 0) heldAirTicks++;
            boolean heldThrough = heldAirTicks >= 5 && p.getDeltaMovement().y < 0.06D;
            // Or straight off the ground, when jump is held and no jump came of it.
            groundHeld = jump && p.onGround() ? groundHeld + 1 : 0;
            if (groundHeld >= 6) {
                groundHeld = 0;
                p.setOnGround(false);
                heldThrough = true;
            }
            // Take off: jump again while in the air (after a jump, or falling), or hold it.
            if ((jumpPressed || heldThrough) && !p.onGround() && !p.onClimbable() && !spent && stamina > 0.05F
                    && !wet(p)) {
                heldAirTicks = -1;
                airTicks = 0;
                setState(p, IGulliverWings.HOVER);
                Vec3 v = p.getDeltaMovement();
                p.setDeltaMovement(v.x, Math.max(v.y, 0.08D * Math.max(0.5D, type.climb / 0.2D)), v.z);
            }
            return;
        }

        airTicks++;
        if (p.onGround() && !jump) {
            setState(p, IGulliverWings.FOLDED);
            return;
        }
        boolean sneak = mc.options.keyShift.isDown();
        boolean moving = mc.options.keyUp.isDown() || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();
        boolean sprint = mc.options.keySprint.isDown() || p.isSprinting();
        byte next;
        if (spent || wet(p)) {
            next = IGulliverWings.GLIDE;
        } else if (jump) {
            next = IGulliverWings.CLIMB;
        } else if (sneak) {
            next = IGulliverWings.DIVE;
        } else if (moving) {
            next = sprint ? IGulliverWings.DASH : IGulliverWings.CRUISE;
            // A butterfly along its way: a few flaps, then a glide.
            if (type == WingType.BUTTERFLY && !sprint && (airTicks % 36) >= 16) next = IGulliverWings.GLIDE;
        } else {
            next = IGulliverWings.HOVER;
        }
        float drain = switch (next) {
            case IGulliverWings.CLIMB -> 0.0060F;
            case IGulliverWings.DASH -> 0.0070F;
            case IGulliverWings.CRUISE -> 0.0026F;
            case IGulliverWings.HOVER -> 0.0021F;
            case IGulliverWings.DIVE -> 0.0008F;
            default -> type == WingType.BUTTERFLY && !spent ? -0.0006F : 0.0F; // gliding
        };
        if (!p.getAbilities().instabuild) {
            stamina = Mth.clamp(stamina - drain / (type.endurance * SyncedSettings.wingStamina()), 0.0F, 1.0F);
        }
        if (stamina <= 0.0F) spent = true;
        setState(p, next);
        p.resetFallDistance();
    }

    /** Wings at rest (folded, or in no state to fly): get the breath back. */
    private static void rest(LocalPlayer p) {
        if (p.onGround() || p.isInWater()) {
            stamina = Math.min(1.0F, stamina + 0.012F);
            if (spent && stamina >= 0.33F) spent = false;
        }
    }

    private static void setState(LocalPlayer p, byte state) {
        ((IGulliverWings) p).gulliver$setWingFlight(state);
        if (state != sent) {
            sent = state;
            gulliver.platform.Services.platform().sendToServer(new Payloads.WingFlight(state));
        }
    }

    /**
     * In place of vanilla's travel while the wings are out: returns true
     * when it moved the player itself.
     */
    public static boolean travel(LocalPlayer p) {
        IGulliverWings w = (IGulliverWings) p;
        byte state = w.gulliver$wingFlight();
        if (!IGulliverWings.flying(state)) return false;
        Minecraft mc = Minecraft.getInstance();
        WingType type = w.gulliver$wingType();
        float size = ((IResizeableEntity) p).getSizeMultiplier();

        float fwd = (mc.options.keyUp.isDown() ? 1.0F : 0.0F) - (mc.options.keyDown.isDown() ? 1.0F : 0.0F);
        float strafe = (mc.options.keyLeft.isDown() ? 1.0F : 0.0F) - (mc.options.keyRight.isDown() ? 1.0F : 0.0F);
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        Vec3 look = Vec3.directionFromRotation(p.getXRot(), p.getYRot());
        Vec3 left = new Vec3(Mth.cos(yaw), 0.0D, Mth.sin(yaw));
        Vec3 wish = look.scale(fwd).add(left.scale(strafe));
        if (wish.lengthSqr() > 1.0E-4D) wish = wish.normalize();

        // Speeds shrink a little with the body, but stay quick for its size.
        double scale = Mth.clamp(0.7D + size * 1.2D, 0.75D, 1.1D);
        double speed = type.cruise * scale * (state == IGulliverWings.DASH ? 1.75D : 1.0D);
        double agility = type.agility;
        Vec3 v = p.getDeltaMovement();
        Vec3 target;
        switch (state) {
            case IGulliverWings.CLIMB -> target = new Vec3(wish.x * speed, Math.max(0.0D, wish.y * speed) + type.climb * scale, wish.z * speed);
            case IGulliverWings.DIVE -> target = new Vec3(wish.x * speed * 0.8D, -Math.max(0.22D, speed), wish.z * speed * 0.8D);
            case IGulliverWings.GLIDE -> {
                // Wings held open: carried on, slowly sinking; steering is gentle.
                Vec3 along = wish.lengthSqr() > 0 ? new Vec3(wish.x, 0, wish.z).normalize().scale(speed * 0.8D) : new Vec3(v.x, 0, v.z);
                target = new Vec3(along.x, -type.glideSink * (spent ? 1.4D : 1.0D), along.z);
                agility *= 0.35D;
            }
            case IGulliverWings.HOVER -> {
                // Hold still, bobbing a little on the wingbeat.
                double amp = Mth.clamp(0.03D * type.bob * size / 0.25D, 0.008D, 0.06D);
                double period = type == WingType.BUTTERFLY ? 22.0D : 34.0D;
                double w2 = 2.0D * Math.PI / period;
                target = new Vec3(0.0D, amp * w2 * Math.cos(airTicks * w2), 0.0D);
                agility = Math.max(agility, 0.18D);
            }
            default -> {
                // Level flight unless you really look up or down: a glance at the ground doesn't sink you.
                double pitch = Math.abs(p.getXRot());
                double climbShare = Mth.clamp((pitch - 10.0D) / 25.0D, 0.0D, 1.0D);
                target = new Vec3(wish.x * speed, wish.y * speed * climbShare + 0.004D, wish.z * speed);
            }
        }
        // Momentum: close a share of the gap every tick (vertical a bit quicker, to hold altitude).
        v = new Vec3(v.x + (target.x - v.x) * agility,
                v.y + (target.y - v.y) * Math.min(1.0D, agility * 1.6D),
                v.z + (target.z - v.z) * agility);
        p.move(MoverType.SELF, v);
        if (p.horizontalCollision) v = new Vec3(v.x * 0.3D, v.y, v.z * 0.3D);
        if (p.verticalCollision) v = new Vec3(v.x, 0.0D, v.z);
        p.setDeltaMovement(v);
        p.resetFallDistance();
        return true;
    }

    /** The server folded the wings (grown too big, mounted...): the next flight is announced again. */
    static void forceFolded() {
        sent = IGulliverWings.FOLDED;
    }

    /** Leaving a world: start fresh in the next one. */
    public static void reset() {
        stamina = prevStamina = 1.0F;
        spent = false;
        sent = IGulliverWings.FOLDED;
        airTicks = 0;
    }
}
