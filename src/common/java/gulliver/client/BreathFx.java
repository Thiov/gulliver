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
 * who is blowing). Everything is measured in the blower's own size, so the
 * same gust looks right at 1/32 and at 32: the cone is four body lengths,
 * puffs are as big as the mouth that makes them and fly as far as the wind
 * reaches, and the wind sound deepens and carries further with size.
 *
 * On top of the stream, bigger lungs add more:
 *  - tiny (under 1/2): a faint whisper of a few small puffs;
 *  - normal (1/2 to 2): a steady stream of breath;
 *  - giant (2 to 8): a dense stream that corkscrews, and where it strikes
 *    the ground it kicks up the ground itself (dirt, sand, leaves) or
 *    whips up spray from water;
 *  - colossal (8 and up): all of that plus rings of wind rolling outward
 *    from the mouth like shockwaves, and swirling gusts where it lands.
 * The particle setting (All / Decreased / Minimal) thins it out.
 */
public final class BreathFx {
    private BreathFx() {}

    private static final RandomSource RAND = RandomSource.create();
    private static final Map<Integer, Gust> ACTIVE = new HashMap<>();

    private static final class Gust {
        int age;
        WindSound sound;
    }

    /** From the server: this entity started or stopped blowing. */
    public static void set(int entityId, boolean on) {
        if (on) {
            ACTIVE.computeIfAbsent(entityId, id -> new Gust());
        } else {
            Gust gust = ACTIVE.remove(entityId);
            if (gust != null && gust.sound != null) gust.sound.fadeOut();
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
            if (e == null || !e.isAlive()) {
                if (gust.sound != null) gust.sound.fadeOut();
                it.remove();
                continue;
            }
            if (gust.sound == null || gust.sound.isStopped()) {
                gust.sound = new WindSound(e);
                mc.getSoundManager().play(gust.sound);
            }
            draw(mc, level, e, gust.age++);
        }
    }

    private static void draw(Minecraft mc, ClientLevel level, Entity e, int age) {
        float size = Math.max(1.0F / 64.0F, ((IResizeableEntity) e).getSizeMultiplier());
        double range = BreathGust.range(size);
        Vec3 dir = e.getViewVector(1.0F);
        Vec3 eye = e.getEyePosition(1.0F);
        // The mouth: a little below the eyes; in first person further out,
        // so the stream doesn't start inside the camera.
        boolean ownView = e == mc.player && mc.options.getCameraType().isFirstPerson();
        double out = (ownView ? 0.55D : 0.2D) * e.getBbWidth() / 0.6D;
        Vec3 mouth = eye.add(dir.scale(out)).subtract(0.0D, e.getBbHeight() * 0.05D, 0.0D);
        // Perpendicular axes of the cone.
        Vec3 side = dir.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0E-6D) side = new Vec3(1.0D, 0.0D, 0.0D);
        side = side.normalize();
        Vec3 up = side.cross(dir).normalize();

        int tier = size < 0.5F ? 0 : size < 2.0F ? 1 : size < 8.0F ? 2 : 3;
        int setting = mc.options.particles().get().ordinal(); // all, decreased, minimal
        float density = setting == 0 ? 1.0F : setting == 1 ? 0.6F : 0.25F;
        // Puffs fly about as far as the wind reaches (they slow by 10% a tick).
        double speed = range / 8.0D;
        float puff = 1.6F * size;

        // The stream.
        int stream = count(new int[] { 2, 4, 7, 10 }[tier], density);
        for (int i = 0; i < stream; i++) {
            double spread = 0.22D * RAND.nextDouble();
            double angle = RAND.nextDouble() * Math.PI * 2.0D;
            Vec3 v = dir.add(side.scale(Math.cos(angle) * spread)).add(up.scale(Math.sin(angle) * spread)).normalize()
                    .scale(speed * (0.75D + 0.5D * RAND.nextDouble()));
            Vec3 at = mouth.add(v.normalize().scale(range * 0.05D * RAND.nextDouble()));
            spawn(mc, ParticleTypes.POOF, at, v, puff * (0.7F + 0.6F * RAND.nextFloat()), 14 + RAND.nextInt(7));
        }

        // Corkscrew wisps around the stream.
        if (tier >= 2 && density > 0.3F) {
            for (int k = 0; k < 2; k++) {
                double angle = age * 0.55D + k * Math.PI;
                double along = range * (0.08D + 0.04D * (age % 6));
                double radius = range * 0.05D;
                Vec3 ring = side.scale(Math.cos(angle) * radius).add(up.scale(Math.sin(angle) * radius));
                Vec3 at = mouth.add(dir.scale(along)).add(ring);
                Vec3 v = dir.scale(speed * 0.9D).add(ring.normalize().scale(speed * 0.08D));
                spawn(mc, ParticleTypes.POOF, at, v, puff * 0.8F, 16);
            }
        }

        // Rings of wind rolling out from a colossal mouth.
        if (tier >= 3 && age % 5 == 0) {
            int n = count(14, density);
            double radius = range * 0.04D;
            Vec3 center = mouth.add(dir.scale(range * 0.06D));
            for (int i = 0; i < n; i++) {
                double angle = Math.PI * 2.0D * i / n;
                Vec3 radial = side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
                spawn(mc, ParticleTypes.POOF, center.add(radial.scale(radius)),
                        dir.scale(speed * 1.1D).add(radial.scale(speed * 0.12D)), puff * 0.9F, 18);
            }
        }

        // Where the wind strikes the ground or water.
        if (tier >= 2 && age % 2 == 0) {
            for (int r = 0; r < 3; r++) {
                double spread = 0.18D * RAND.nextDouble();
                double angle = RAND.nextDouble() * Math.PI * 2.0D;
                Vec3 ray = dir.add(side.scale(Math.cos(angle) * spread)).add(up.scale(Math.sin(angle) * spread)).normalize();
                strike(mc, level, e, mouth, ray, range, size, tier, density);
            }
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
