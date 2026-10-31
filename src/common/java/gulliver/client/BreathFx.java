package gulliver.client;

import gulliver.api.IResizeableEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Who is blowing a breath gust right now (BreathGust tells clients) and
 * the rush of wind they make, deeper and further-carrying with size. The
 * wind itself - wisps of air that leave the mouth and live on their own -
 * is BreathRibbons; under water the breath goes up as bubbles instead.
 */
public final class BreathFx {
    private BreathFx() {}

    private static final Map<Integer, Gust> ACTIVE = new HashMap<>();

    /** One blower's gust as the client sees it. */
    public static final class Gust {
        public int age;
        boolean on = true;
        /** 0..1: fades in when the gust starts and out when it stops. */
        public float strength;
        public float strengthO;
        WindSound sound;

        public float strength(float partialTick) {
            return Mth.lerp(partialTick, strengthO, strength);
        }
    }

    /** Everyone blowing (or still fading out) right now, by entity id. */
    public static Map<Integer, Gust> gusts() {
        return ACTIVE;
    }

    /** From the server: this entity started or stopped blowing. */
    public static void set(int entityId, boolean on) {
        if (on) {
            ACTIVE.computeIfAbsent(entityId, id -> new Gust()).on = true;
        } else {
            Gust gust = ACTIVE.get(entityId);
            if (gust == null) return;
            gust.on = false;
            if (gust.sound != null) gust.sound.fadeOut();
        }
    }

    /** Every client tick. */
    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null) {
            ACTIVE.clear();
            BreathRibbons.clear();
            return;
        }
        if (mc.isPaused()) return;
        Iterator<Map.Entry<Integer, Gust>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Gust> entry = it.next();
            Entity e = level.getEntity(entry.getKey());
            Gust gust = entry.getValue();
            gust.strengthO = gust.strength;
            gust.strength = Mth.clamp(gust.strength + (gust.on ? 0.25F : -0.2F), 0.0F, 1.0F);
            if (e == null || !e.isAlive() || (!gust.on && gust.strength <= 0.0F)) {
                if (gust.sound != null) gust.sound.fadeOut();
                it.remove();
                continue;
            }
            gust.age++;
            if (!gust.on) continue;
            if (e.isEyeInFluid(FluidTags.WATER)) {
                if (gust.sound != null) gust.sound.fadeOut();
                BreathRibbons.bubbles(mc, level, e, gust.age);
                continue;
            }
            if (gust.sound == null || gust.sound.isStopped() || gust.sound.fading) {
                gust.sound = new WindSound(e);
                mc.getSoundManager().play(gust.sound);
            }
            BreathRibbons.emit(mc, e, gust.age);
        }
        BreathRibbons.tick(mc, level);
    }

    /** The rush of air, following the blower's mouth; deeper and further-carrying the bigger the blower. */
    private static final class WindSound extends AbstractTickableSoundInstance {
        private final Entity entity;
        private final float loud;
        private boolean fading;

        WindSound(Entity entity) {
            super(SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.entity = entity;
            float size = Math.max(1.0F / 64.0F, ((IResizeableEntity) entity).getSizeMultiplier());
            this.loud = Mth.clamp(0.35F * (float) Math.sqrt(size), 0.04F, 3.0F);
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01F;
            this.pitch = Mth.clamp(1.25F / (float) Math.sqrt(size), 0.5F, 2.0F);
            follow();
        }

        void fadeOut() {
            fading = true;
        }

        private void follow() {
            Vec3 eye = entity.getEyePosition();
            this.x = eye.x;
            this.y = eye.y;
            this.z = eye.z;
        }

        @Override
        public void tick() {
            if (entity.isRemoved()) {
                stop();
                return;
            }
            follow();
            float target = fading ? 0.0F : loud;
            volume += (target - volume) * (fading ? 0.35F : 0.25F);
            if (fading && volume < 0.01F) stop();
        }
    }
}
