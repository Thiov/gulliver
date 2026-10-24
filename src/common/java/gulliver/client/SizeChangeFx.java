package gulliver.client;

import gulliver.common.GulliverConfig;
import gulliver.common.SizeSound;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Client-side reaction to a size change the server announced for an
 * entity that was already visible (not its first size packet): a puff of
 * particles around the body, a sound that rises when shrinking and falls
 * when growing, and, for the local player, the new size on the action bar.
 */
public final class SizeChangeFx {
    private SizeChangeFx() {}

    /** Effects are skipped beyond this distance from the local player. */
    private static final double MAX_DISTANCE_SQ = 48.0D * 48.0D;

    public static void onSizeChanged(Entity entity, float oldSize, float newSize) {
        if (!(oldSize > 0.0F) || !(newSize > 0.0F) || Math.abs(newSize - oldSize) < 1.0E-4F) return;
        Minecraft mc = Minecraft.getInstance();
        Level level = entity.level();
        if (mc.level == null || level != mc.level) return;
        if (entity == mc.player) readout(mc, newSize);
        if (entity.isSpectator()) return;
        if (mc.player != null && entity.distanceToSqr(mc.player) > MAX_DISTANCE_SQ) return;
        puff(level, entity, oldSize, newSize);
        chime(level, entity, oldSize, newSize);
    }

    private static void readout(Minecraft mc, float newSize) {
        if (!GulliverConfig.INSTANCE.client.sizeReadout || mc.player == null) return;
        Component text = Component.translatable("gulliver.size_readout", SizeFormat.of(newSize));
        //#if MC >= 26.1
        mc.player.sendOverlayMessage(text);
        //#else
        //$$ mc.player.displayClientMessage(text, true);
        //#endif
    }

    /**
     * A ring of cloud around the body (bigger and busier for bigger bodies,
     * capped), drifting outward when growing and inward when shrinking.
     */
    private static void puff(Level level, Entity entity, float oldSize, float newSize) {
        float larger = Math.max(oldSize, newSize);
        // The hitbox still has the old size here; its base measure is size-independent.
        float baseWidth = entity.getBbWidth() / oldSize;
        float baseHeight = entity.getBbHeight() / oldSize;
        double halfWidth = Math.max(0.05D, baseWidth * larger * 0.5D);
        double height = Math.max(0.1D, baseHeight * larger);
        int count = Mth.clamp((int) (6.0D + 10.0D * Math.sqrt(larger)), 6, 56);
        boolean growing = newSize > oldSize;
        double drift = Mth.clamp(0.03D * Math.sqrt(larger), 0.02D, 0.35D) * (growing ? 1.0D : -1.0D);
        var random = level.getRandom();
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            double y = entity.getY() + random.nextDouble() * height;
            level.addParticle(i % 4 == 0 ? ParticleTypes.POOF : ParticleTypes.CLOUD,
                    entity.getX() + dx * halfWidth, y, entity.getZ() + dz * halfWidth,
                    dx * drift, 0.01D + random.nextDouble() * 0.02D, dz * drift);
        }
    }

    private static void chime(Level level, Entity entity, float oldSize, float newSize) {
        // Rises as the body shrinks, falls as it grows; a 2x change is about half an octave.
        float pitch = Mth.clamp((float) Math.pow(oldSize / (double) newSize, 0.5D), 0.5F, 2.0F);
        float volume = SizeSound.scaleVolume((gulliver.api.IResizeableEntity) entity, 0.7F);
        level.playLocalSound(entity.getX(), entity.getY(), entity.getZ(), SoundEvents.CHORUS_FRUIT_TELEPORT,
                entity.getSoundSource(), volume, pitch, false);
    }
}
