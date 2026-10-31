package gulliver.client;

import gulliver.api.IResizeableEntity;
import gulliver.common.BreathGust;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * What a breath gust looks and sounds like (BreathGust only tells clients
 * who is blowing). The wind itself is drawn by BreathRibbons: streaks of
 * air spiralling out of the mouth along the cone, four body lengths long
 * and as wide as the mouth, so the same gust looks right at 1/32 and at
 * 32. This class keeps who is blowing (with a short fade in and out), the
 * wind sound that deepens and carries further with size, and what the wind
 * does where it strikes from size 2 up: it kicks up the ground itself
 * (dirt, sand, leaves) and whips up spray from water, and from size 8 it
 * also throws up swirling gusts (1.21+). The particle setting thins that
 * out.
 */
public final class BreathFx {
    private BreathFx() {}

    private static final RandomSource RAND = RandomSource.create();
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
            return;
        }
        if (mc.isPaused() || ACTIVE.isEmpty()) return;
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
            if (gust.sound == null || gust.sound.isStopped()) {
                gust.sound = new WindSound(e);
                mc.getSoundManager().play(gust.sound);
            }
            strikes(mc, level, e, gust.age);
        }
    }

    /** Where the wind of a blower of size 2 and up strikes the ground or water. */
    private static void strikes(Minecraft mc, ClientLevel level, Entity e, int age) {
        float size = Math.max(1.0F / 64.0F, ((IResizeableEntity) e).getSizeMultiplier());
        if (size < 2.0F || age % 2 != 0) return;
        int tier = size < 8.0F ? 2 : 3;
        int setting = mc.options.particles().get().ordinal(); // all, decreased, minimal
        float density = setting == 0 ? 1.0F : setting == 1 ? 0.6F : 0.25F;
        double range = BreathGust.range(size);
        Vec3 dir = e.getViewVector(1.0F);
        Vec3 mouth = BreathRibbons.mouth(e, 1.0F, false);
        Vec3 side = dir.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0E-6D) side = new Vec3(1.0D, 0.0D, 0.0D);
        side = side.normalize();
        Vec3 up = side.cross(dir).normalize();
        for (int r = 0; r < 3; r++) {
            double spread = 0.18D * RAND.nextDouble();
            double angle = RAND.nextDouble() * Math.PI * 2.0D;
            Vec3 ray = dir.add(side.scale(Math.cos(angle) * spread)).add(up.scale(Math.sin(angle) * spread)).normalize();
            strike(mc, level, e, mouth, ray, range, size, tier, density);
        }
    }

    private static void strike(Minecraft mc, ClientLevel level, Entity e, Vec3 mouth, Vec3 ray, double range,
                               float size, int tier, float density) {
        BlockHitResult hit = level.clip(new ClipContext(mouth, mouth.add(ray.scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, e));
        if (hit.getType() != HitResult.Type.BLOCK) return;
        double reach = mouth.distanceTo(hit.getLocation()) / range;
        if (reach > 0.95D) return;
        float strength = (float) (1.0D - reach);
        Vec3 at = hit.getLocation();
        BlockPos pos = hit.getBlockPos();
        // Blown along the surface and up off it.
        net.minecraft.core.Direction face = hit.getDirection();
        Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
        Vec3 along = ray.subtract(normal.scale(ray.dot(normal)));
        double speed = range / 8.0D * strength;
        Vec3 v = along.scale(speed * 0.8D).add(normal.scale(speed * 0.45D));
        float grain = (float) Math.sqrt(size);
        int n = count(tier >= 3 ? 6 : 3, density);
        if (level.getFluidState(pos).is(FluidTags.WATER)) {
            for (int i = 0; i < n; i++) {
                spawn(mc, ParticleTypes.SPLASH, jitter(at, 0.04D * range), v.add(0.0D, speed * 0.3D, 0.0D), grain, 20);
            }
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return;
        for (int i = 0; i < n; i++) {
            spawn(mc, new BlockParticleOption(ParticleTypes.BLOCK, state), jitter(at, 0.04D * range), jitterSpeed(v),
                    grain * 0.9F, 20 + RAND.nextInt(10));
        }
        //#if MC >= 1.21
        if (tier >= 3 && RAND.nextInt(4) == 0) {
            spawn(mc, ParticleTypes.GUST, at.add(normal.scale(0.05D * range)), Vec3.ZERO, size / 8.0F, 12);
        }
        //#endif
    }

    private static Vec3 jitter(Vec3 at, double amount) {
        return at.add((RAND.nextDouble() - 0.5D) * amount, (RAND.nextDouble() - 0.5D) * amount * 0.5D,
                (RAND.nextDouble() - 0.5D) * amount);
    }

    private static Vec3 jitterSpeed(Vec3 v) {
        double f = 0.7D + 0.6D * RAND.nextDouble();
        return new Vec3(v.x * f, v.y * (0.8D + 0.5D * RAND.nextDouble()), v.z * f);
    }

    private static int count(int full, float density) {
        float n = full * density;
        int whole = (int) n;
        return whole + (RAND.nextFloat() < n - whole ? 1 : 0);
    }

    private static void spawn(Minecraft mc, ParticleOptions type, Vec3 at, Vec3 v, float scale, int life) {
        Particle p = mc.particleEngine.createParticle(type, at.x, at.y, at.z, v.x, v.y, v.z);
        if (p == null) return;
        p.setParticleSpeed(v.x, v.y, v.z);
        p.scale(scale);
        p.setLifetime(life);
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
