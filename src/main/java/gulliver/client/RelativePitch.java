package gulliver.client;

import gulliver.api.IResizeableEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * Listener-relative pitch: entity sounds are re-pitched by the size
 * RATIO between the sound's source and the local player.
 *
 *   factor = sqrt(listenerSize / sourceSize)
 *
 * A size-1 player hears a size-8 giant's voice, footsteps, and impacts
 * pitched down to 0.35× (a rumble); the giant hears the size-1 player
 * at 2.83× (a squeak) — and the same relationship holds at every scale
 * pair (0.125 vs 1 sounds exactly like 1 vs 8). Your OWN sounds are
 * always normal — everything is relative to your body's frame, which
 * is what makes shrinking feel like the WORLD got deeper and slower,
 * not like your ears broke.
 *
 * Why client-side: pitch depends on who is listening, and the server
 * broadcasts one sound packet for everyone. SoundEngine.calculatePitch
 * is the single funnel every played sound passes through, so the hook
 * covers packet-delivered sounds AND locally-simulated ones (own
 * footsteps) alike.
 *
 * Attribution: sound packets carry a position, not an entity, so the
 * source is resolved as the nearest living entity whose (slightly
 * inflated) bounding box contains the sound origin. To avoid
 * re-pitching block/ambient noise that merely plays near a giant, only
 * the entity sound categories (PLAYERS / HOSTILE / NEUTRAL) are
 * eligible.
 */
public final class RelativePitch {
    private RelativePitch() {}

    public static float applyTo(SoundInstance sound, float basePitch) {
        SoundSource src = sound.getSource();
        if (src != SoundSource.PLAYERS && src != SoundSource.HOSTILE
                && src != SoundSource.NEUTRAL) {
            return basePitch;
        }
        if (sound.isRelative()) return basePitch; // UI / listener-attached
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer listener = mc.player;
        ClientLevel level = mc.level;
        if (listener == null || level == null) return basePitch;

        double x = sound.getX();
        double y = sound.getY();
        double z = sound.getZ();
        AABB probe = new AABB(x - 2.0D, y - 2.0D, z - 2.0D, x + 2.0D, y + 2.0D, z + 2.0D);
        LivingEntity source = null;
        double bestSq = Double.MAX_VALUE;
        for (Entity e : level.getEntities((Entity) null, probe)) {
            if (!(e instanceof LivingEntity le)) continue;
            if (!le.getBoundingBox().inflate(0.75D).contains(x, y, z)) continue;
            double dsq = le.distanceToSqr(x, y, z);
            if (dsq < bestSq) {
                bestSq = dsq;
                source = le;
            }
        }
        if (source == null) return basePitch;

        float sourceSize = ((IResizeableEntity) source).getSizeMultiplier();
        float listenerSize = ((IResizeableEntity) listener).getSizeMultiplier();
        if (sourceSize == listenerSize) return basePitch;
        float factor = (float) Math.sqrt(listenerSize / sourceSize);
        return Mth.clamp(basePitch * factor, 0.25F, 2.5F);
    }
}
