package gulliver.common;

import gulliver.access.IGulliverShoulderInternal;
import gulliver.api.IResizeableEntity;
import gulliver.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Breath gusts (issue #10): holding the breath key blows a cone of wind
 * out of the mouth. The wind's strength is the blower's size and each
 * thing's inertia its own size, so a giant knocks normal mobs back the way
 * a normal player only stirs a tiny, and a giant's gust turns arrows and
 * fireballs around (deflected projectiles count as the blower's). Lit
 * candles, campfires and fire straight ahead are blown out, walls stop the
 * wind, and the blower's pets, teammates and anything it carries are left
 * alone, like with trampling.
 *
 * Blowing costs air: lungs that are full last three seconds of gust, the
 * gust stops when they are empty and only starts again once the key has
 * been let go. It can't be done underwater; creative players never run
 * out. The key rides on the CarryAction packet, so an older server just
 * ignores it, and the client repeats it while held so a lost "stop" can't
 * leave anyone blowing forever.
 */
public final class BreathGust {
    private BreathGust() {}

    /** Air spent per tick of gust; vanilla gives 4 back each tick on land, so a full 300 lasts 60 ticks. */
    static final int AIR_PER_TICK = 9;
    /** Ticks without a repeat from the client after which the gust stops. */
    private static final int KEEP_ALIVE = 20;
    /** Half-angle of the wind cone (cos 30 degrees). */
    private static final double CONE_COS = 0.866D;
    /** Push per tick for each unit of blower size against a size-1 body, before falloff. */
    private static final double PUSH_PER_SIZE = 0.025D;
    private static final double MAX_PUSH = 0.8D;
    private static final double MAX_RANGE = 48.0D;
    private static final int MAX_BLOWN = 48;

    private static final class State {
        long lastRequest;
        boolean exhausted;
        int ticks;
    }

    private static final Map<UUID, State> BLOWERS = new ConcurrentHashMap<>();

    /** The breath key went down (or is still down: the client repeats this while held). */
    public static void start(ServerPlayer player) {
        if (!GulliverConfig.INSTANCE.general.breathGust || player.isSpectator()) return;
        State state = BLOWERS.computeIfAbsent(player.getUUID(), id -> new State());
        state.lastRequest = player.level().getGameTime();
    }

    /** The breath key came up: the gust ends and empty lungs may blow again next time. */
    public static void stop(ServerPlayer player) {
        BLOWERS.remove(player.getUUID());
    }

    /** Server tick of every player (from LivingEntity.aiStep). */
    public static void tick(ServerPlayer player) {
        State state = BLOWERS.get(player.getUUID());
        if (state == null) return;
        if (!GulliverConfig.INSTANCE.general.breathGust || !player.isAlive() || player.isSpectator()
                || player.isSleeping() || player.level().getGameTime() - state.lastRequest > KEEP_ALIVE) {
            BLOWERS.remove(player.getUUID());
            return;
        }
        if (state.exhausted || player.isEyeInFluid(FluidTags.WATER)) return;
        if (!player.getAbilities().invulnerable) {
            int air = player.getAirSupply();
            if (air <= 0) {
                state.exhausted = true;
                return;
            }
            player.setAirSupply(Math.max(0, air - AIR_PER_TICK));
        }
        blow((ServerLevel) player.level(), player, state.ticks++);
    }

    /**
     * One tick of wind from this body's mouth along its look. Returns how
     * many things it moved. Any living body can blow; only players have
     * the key.
     */
    public static int blow(ServerLevel level, LivingEntity blower, int tick) {
        float size = ((IResizeableEntity) blower).getSizeMultiplier();
        double range = range(size);
        Vec3 eye = blower.getEyePosition();
        Vec3 dir = blower.getLookAngle();

        if (tick % 10 == 0) {
            level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.HORSE_BREATHE, SoundSource.PLAYERS,
                    SizeSound.scaleVolume((IResizeableEntity) blower, 0.6F), Mth.clamp(1.3F / (float) Math.sqrt(size), 0.4F, 2.0F));
        }
        puffs(level, eye, dir, range, size, blower);
        if (tick % 5 == 0 && blower instanceof ServerPlayer player) blowOutFlames(level, player, eye, dir, range);

        AABB box = new AABB(eye, eye.add(dir.scale(range))).inflate(range * 0.5D);
        int blown = 0;
        UUID blowerId = blower.getUUID();
        for (Entity e : level.getEntities(blower, box, e -> e.isAlive() && !e.isSpectator())) {
            if (e.getVehicle() == blower || blower.getVehicle() == e) continue;
            if (blowerId.equals(((IGulliverShoulderInternal) e).gulliver$getHoldingEntity())) continue;
            if (GiantAoe.spares(blower, e)) continue;
            if (e instanceof Player p && (p.getAbilities().flying || p.isCreative()
                    || (blower instanceof Player bp && !bp.canHarmPlayer(p)))) continue;

            Vec3 center = e.getBoundingBox().getCenter();
            Vec3 to = center.subtract(eye);
            double dist = to.length();
            if (dist > range || dist < 1.0E-4D) continue;
            double cos = to.dot(dir) / dist;
            if (cos < CONE_COS) continue;
            if (level.clip(new ClipContext(eye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, blower))
                    .getType() != HitResult.Type.MISS) continue;

            double push = push(size, e, dist / range);
            if (push < 0.002D) continue;
            Vec3 before = e.getDeltaMovement();
            double lift = e.onGround() ? push * 0.2D : 0.0D;
            Vec3 after = before.add(dir.x * push, dir.y * push + lift, dir.z * push);
            e.setDeltaMovement(after);
            e.hurtMarked = true;
            if (e instanceof Projectile projectile) turnProjectile(projectile, blower, dir, before, after);
            if (++blown >= MAX_BLOWN) break;
        }
        return blown;
    }

    /** How far a body of this size blows: four of its own lengths. */
    public static double range(float size) {
        return Mth.clamp(4.0D * size, 1.0D, MAX_RANGE);
    }

    /** Speed added this tick to {@code target} at {@code reach} (0 at the mouth, 1 at the end of the cone). */
    static double push(float blowerSize, Entity target, double reach) {
        double mass = ((IResizeableEntity) target).getSizeMultiplier() * inertia(target);
        double push = PUSH_PER_SIZE * blowerSize / Math.max(mass, 1.0E-3D) * (1.0D - reach);
        if (target instanceof LivingEntity living) {
            push *= 1.0D - Mth.clamp(living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0D, 1.0D);
        }
        return Math.min(MAX_PUSH, push);
    }

    /** Light things (items, orbs, arrows) fly off; everything else weighs what its size says. */
    private static double inertia(Entity e) {
        if (e instanceof ItemEntity || e instanceof ExperienceOrb || e instanceof Projectile) return 0.25D;
        return 1.0D;
    }

    /** A projectile the wind turned around now belongs to the blower, and a fireball keeps flying the new way. */
    private static void turnProjectile(Projectile projectile, LivingEntity blower, Vec3 dir, Vec3 before, Vec3 after) {
        if (before.dot(dir) < 0.0D && after.dot(dir) > 0.0D) projectile.setOwner(blower);
        //#if MC < 1.20.5
        //$$ if (projectile instanceof net.minecraft.world.entity.projectile.AbstractHurtingProjectile fireball
        //$$         && after.lengthSqr() > 1.0E-6D) {
        //$$     // Before 1.20.5 a fireball accelerates along a fixed vector, not along its motion.
        //$$     double power = Math.sqrt(fireball.xPower * fireball.xPower + fireball.yPower * fireball.yPower
        //$$             + fireball.zPower * fireball.zPower);
        //$$     Vec3 along = after.normalize().scale(power);
        //$$     fireball.xPower = along.x;
        //$$     fireball.yPower = along.y;
        //$$     fireball.zPower = along.z;
        //$$ }
        //#endif
    }

    /** Wisps of air streaming out of the mouth, more of them for a bigger mouth. */
    private static void puffs(ServerLevel level, Vec3 eye, Vec3 dir, double range, float size, LivingEntity blower) {
        int count = Mth.clamp(2 + Math.round(size * 1.5F), 2, 16);
        double speed = Mth.clamp(0.25D * Math.sqrt(size), 0.08D, 1.5D);
        double spread = range * 0.25D;
        var random = blower.getRandom();
        for (int i = 0; i < count; i++) {
            double along = random.nextDouble() * range * 0.6D + 0.3D * size;
            double off = random.nextDouble() * spread * (along / range);
            double x = eye.x + dir.x * along + (random.nextDouble() - 0.5D) * off;
            double y = eye.y + dir.y * along + (random.nextDouble() - 0.5D) * off;
            double z = eye.z + dir.z * along + (random.nextDouble() - 0.5D) * off;
            // count 0: the three offsets are the particle's velocity
            level.sendParticles(ParticleTypes.CLOUD, x, y, z, 0, dir.x, dir.y, dir.z, speed);
        }
    }

    /** Lit candles, campfires and fire in the middle of the gust go out. */
    private static void blowOutFlames(ServerLevel level, ServerPlayer player, Vec3 eye, Vec3 dir, double range) {
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(dir.scale(range)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        boolean candle = state.getBlock() instanceof AbstractCandleBlock && state.getValue(AbstractCandleBlock.LIT);
        boolean campfire = CampfireBlock.isLitCampfire(state);
        boolean fire = state.getBlock() instanceof BaseFireBlock;
        if (!candle && !campfire && !fire) return;
        if (!Services.platform().mayBreakBlock(level, pos, state, player)) return;
        if (candle) {
            AbstractCandleBlock.extinguish(player, state, level, pos);
        } else if (campfire) {
            CampfireBlock.dowse(player, level, pos, state);
            level.setBlock(pos, state.setValue(CampfireBlock.LIT, false), 11);
            level.levelEvent(null, 1009, pos, 0);
        } else {
            level.removeBlock(pos, false);
            level.levelEvent(null, 1009, pos, 0);
        }
    }
}
