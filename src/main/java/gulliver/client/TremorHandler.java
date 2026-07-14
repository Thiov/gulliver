package gulliver.client;

import gulliver.api.IResizeableEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;

/**
 * Screen tremor for the local player when something MUCH bigger moves
 * nearby. Two sources feed one shared impulse pool:
 *
 *  1. FOOTFALLS — the core of the effect. For every grounded, moving
 *     entity at least 6× the viewer's size, a stride phase is
 *     accumulated from its actual horizontal motion (one footfall per
 *     ~0.45 body-lengths — two steps per stride cycle). Each footfall
 *     lands a discrete THUMP whose strength scales with the size ratio
 *     and falls off with distance^1.5. The result is rhythmic — you feel
 *     the giant's steps approaching, not a constant jitter. A size-1
 *     player walking past a 0.125 tiny registers; a size-8 titan
 *     shakes a size-1 player's screen; a 0.25 mob near that same tiny
 *     (ratio 2) does nothing.
 *
 *  2. GROUND SHOCK — the giant-landing shockwave sends a GroundShock
 *     payload; smaller viewers get a single hard quake with distance
 *     falloff (see groundShock()).
 *
 * The pool decays ~22% per tick, so a thump reads as a sharp bump with
 * a short tail. MixinCameraShake converts the pool into a small
 * two-frequency rotational wobble each frame — rotation, not position,
 * so the camera never clips into walls and the motion reads as "the
 * ground kicks" rather than "screen offset".
 *
 * Being carried by the walker dampens (not silences) the thumps: you
 * ride the giant's gait, you don't get rattled apart by it.
 */
public final class TremorHandler {
    private TremorHandler() {}

    /** Minimum walker/viewer size ratio before footfalls register. */
    private static final float RATIO_THRESHOLD = 6.0F;
    /** Impulse pool ceiling — keeps stacked giants from nauseating. */
    private static final float MAX_SHAKE = 1.4F;

    private static final Map<Integer, Float> STRIDE_PHASE = new HashMap<>();
    private static float shake;
    private static float prevShake;
    private static int ticks;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(TremorHandler::tick);
    }

    private static void tick(Minecraft mc) {
        prevShake = shake;
        shake *= 0.78F;
        if (shake < 0.002F) shake = 0.0F;
        ticks++;

        LocalPlayer viewer = mc.player;
        ClientLevel level = mc.level;
        if (viewer == null || level == null) {
            STRIDE_PHASE.clear();
            return;
        }
        if (mc.isPaused() || viewer.isSpectator()) return;
        float viewerSize = ((IResizeableEntity) viewer).getSizeMultiplier();

        AABB scan = viewer.getBoundingBox().inflate(48.0D);
        for (Entity e : level.getEntities(viewer, scan)) {
            if (!(e instanceof LivingEntity walker)) continue;
            float walkerSize = ((IResizeableEntity) walker).getSizeMultiplier();
            float ratio = walkerSize / viewerSize;
            if (ratio < RATIO_THRESHOLD) {
                STRIDE_PHASE.remove(e.getId());
                continue;
            }
            if (!walker.onGround()) continue;
            double dx = walker.getX() - walker.xOld;
            double dz = walker.getZ() - walker.zOld;
            double moved = Math.sqrt(dx * dx + dz * dz);
            if (moved < 0.01D) continue;

            // Stride phase from real motion: a footfall every
            // ~0.45 × size blocks (half of a ~0.9 body-length stride).
            float step = 0.45F * walkerSize;
            float phase = STRIDE_PHASE.getOrDefault(e.getId(), 0.0F)
                    + (float) (moved / step);
            if (phase >= 1.0F) {
                phase %= 1.0F;
                gulliver$footfall(viewer, viewerSize, walker, walkerSize, ratio);
            }
            STRIDE_PHASE.put(e.getId(), phase);
        }
    }

    private static void gulliver$footfall(LocalPlayer viewer, float viewerSize,
                                           LivingEntity walker, float walkerSize,
                                           float ratio) {
        double dist = viewer.distanceTo(walker);
        // Tremor reach grows with the walker's mass, sublinearly —
        // size 1 → ~14 blocks, size 8 → ~29 blocks.
        double reach = Math.min(40.0D, 6.0D + 8.0D * Math.sqrt(walkerSize));
        if (dist >= reach) return;
        float falloff = (float) Math.pow(1.0D - dist / reach, 1.5D);
        // Severity ramps from the threshold: ratio 6 → faint entry,
        // 8 → unmistakable (the size-1-vs-8 case), 11+ → full weight.
        // User-tuned: the previous (ratio-4)/12 curve left ratio 8 at
        // 0.33 which — combined with quadratic falloff — was barely
        // perceptible outside point-blank range.
        float severity = Mth.clamp((ratio - 5.0F) / 6.0F, 0.0F, 1.0F);
        float impulse = severity * falloff;
        // Riding/carried by the walker: rock with the gait, gently.
        if (walker.getUUID().equals(((gulliver.access.IGulliverShoulderInternal) viewer)
                .gulliver$getHoldingEntity())) {
            impulse *= 0.4F;
        }
        addImpulse(impulse);
    }

    /**
     * GroundShock payload entry (giant landed at x/y/z). Only viewers
     * under half the source's size feel it — the same "much smaller"
     * gate the physical fling uses.
     */
    public static void groundShock(double x, double y, double z,
                                    float sourceSize, float strength) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer viewer = mc.player;
        if (viewer == null || viewer.isSpectator()) return;
        float viewerSize = ((IResizeableEntity) viewer).getSizeMultiplier();
        if (viewerSize >= sourceSize * 0.5F) return;
        double dist = Math.sqrt(viewer.distanceToSqr(x, y, z));
        double reach = Math.min(48.0D, 8.0D * sourceSize);
        if (dist >= reach) return;
        float falloff = (float) Math.pow(1.0D - dist / reach, 1.5D);
        addImpulse(Math.min(MAX_SHAKE, strength * 1.2F * falloff));
    }

    private static void addImpulse(float amount) {
        if (amount <= 0.0F) return;
        shake = Math.min(MAX_SHAKE, shake + amount);
    }

    /** Interpolated impulse energy for the current frame (degrees-ish). */
    public static float shakeAmount(float partialTick) {
        return Mth.lerp(partialTick, prevShake, shake);
    }

    /** Continuous time base for the wobble oscillators. */
    public static float time(float partialTick) {
        return ticks + partialTick;
    }
}
