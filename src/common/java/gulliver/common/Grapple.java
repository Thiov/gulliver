package gulliver.common;

import gulliver.api.IResizeableEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The fishing rod as a grappling hook (1.6.4 EntityFishHook): a tiny
 * angler's bobber bites into whatever block it hits — wall, ceiling or
 * floor — and reeling in pulls the angler up to it. Hooking a creature
 * much bigger than you pulls you onto it instead; a bigger angler reels a
 * smaller creature in harder.
 */
public final class Grapple {
    private Grapple() {}

    /** Whether this angler's bobber grabs onto blocks. */
    public static boolean canGrapple(Player angler) {
        GulliverConfig.General g = GulliverConfig.INSTANCE.general;
        return g.fishingRodGrapple
                && ((IResizeableEntity) angler).getSizeMultiplier() <= g.grappleMaxSize
                && !angler.isSpectator();
    }

    /**
     * Launch the angler toward {@code target}. The pull is aimed so the
     * angler arrives at the anchor: enough upward speed to climb the
     * height difference against gravity, and a horizontal push that
     * covers the gap under air drag.
     */
    public static void pullToward(Player angler, Vec3 target) {
        Vec3 from = angler.position().add(0.0D, angler.getBbHeight() * 0.5D, 0.0D);
        double dx = target.x - from.x;
        double dy = target.y - from.y;
        double dz = target.z - from.z;
        // Airborne players keep ~91% of horizontal speed per tick, so a
        // start speed of 9% of the gap carries them roughly there.
        double vx = dx * 0.09D;
        double vz = dz * 0.09D;
        double vy;
        if (dy > 0.0D) {
            // v^2 = 2 g h (gravity 0.08/tick), plus a little overshoot so
            // the angler can grab the ledge; bounded so it never becomes a cannon.
            vy = Math.sqrt(2.0D * 0.08D * (dy + 0.6D));
        } else {
            vy = Math.max(0.15D, 0.05D + Math.sqrt(dx * dx + dz * dz) * 0.02D);
        }
        double max = 2.2D;
        vx = clamp(vx, max);
        vy = Math.min(vy, max);
        vz = clamp(vz, max);
        angler.setDeltaMovement(vx, vy, vz);
        angler.hurtMarked = true; // server-side motion reaches the player's client
        angler.fallDistance = 0.0F;
    }

    private static double clamp(double v, double max) {
        return Math.max(-max, Math.min(max, v));
    }

    /**
     * A bigger angler reels smaller creatures in harder (1.6.4: the pull is
     * scaled by the angler/target size-root ratio).
     */
    public static float pullScale(Entity angler, Entity target) {
        float a = ((IResizeableEntity) angler).getSizeMultiplierRoot();
        float b = ((IResizeableEntity) target).getSizeMultiplierRoot();
        if (b <= 0.0F) return 1.0F;
        return Math.max(0.25F, Math.min(4.0F, a / b));
    }
}
