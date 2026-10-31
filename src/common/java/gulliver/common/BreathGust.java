package gulliver.common;

import gulliver.access.IGulliverShoulderInternal;
import gulliver.api.IResizeableEntity;
import gulliver.platform.Services;
import net.minecraft.core.BlockPos;
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
 * out of the mouth, four of the blower's own lengths long. The wind blows
 * at a speed that grows with the blower (0.35 x sqrt(size) blocks a tick
 * at the mouth) and drags things towards that speed - the lighter they
 * are next to the blower, the harder (items and projectiles weigh a
 * quarter of their size), and things on the ground get lifted off it so
 * they really fly. Nothing as heavy as the blower moves at all, so a
 * giant blows normal mobs away the way a normal player blows a tiny
 * away, and a giant's gust turns arrows and fireballs around (deflected
 * projectiles count as the blower's). Lit candles, campfires and fire straight ahead are blown
 * out, walls stop the wind, and the blower's pets, teammates and anything
 * it carries are left alone, like with trampling.
 *
 * The server only says who is blowing (ActionState); every client draws
 * the gust itself, sized to the blower (client.BreathFx).
 *
 * Blowing costs air: full lungs last three seconds of gust at any size
 * (Gulliver gives a giant bigger lungs; it blows them out just as fast),
 * the gust stops when they are empty and only starts again once the key
 * has been let go. Creative players never run out. Water stops the wind,
 * so nothing under the surface is blown; underwater the breath just goes
 * up as a stream of bubbles, and the air is gone all the same. The key rides on the CarryAction packet, so an older server just
 * ignores it, and the client repeats it while held so a lost "stop" can't
 * leave anyone blowing forever.
 */
public final class BreathGust {
    private BreathGust() {}

    /** Ticks a full breath lasts. */
    static final int BREATH_TICKS = 60;
    /** Air vanilla gives back each tick on land. */
    private static final int AIR_REGEN = 4;
    /** Ticks without a repeat from the client after which the gust stops. */
    private static final int KEEP_ALIVE = 20;
    /** Half-angle of the wind cone (cos 30 degrees). */
    private static final double CONE_COS = 0.866D;
    /** How strongly the wind drags something weightless towards its own speed, per tick. */
    private static final double COUPLING = 0.35D;
    /** Fastest wind, blocks per tick. */
    private static final double MAX_WIND = 2.0D;
    private static final double MAX_RANGE = 48.0D;
    private static final int MAX_BLOWN = 48;

    private static final class State {
        long lastRequest;
        boolean exhausted;
        /** Whether clients were told this body is blowing. */
        boolean shown;
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
        State state = BLOWERS.remove(player.getUUID());
        if (state != null && state.shown) show(player, false);
    }

    private static void show(ServerPlayer player, boolean on) {
        Services.platform().sendToTrackingAndSelf(player,
                new gulliver.network.Payloads.ActionState(player.getId(), gulliver.network.Payloads.ActionState.BREATH, on));
    }

    /** Server tick of every player (from LivingEntity.aiStep). */
    public static void tick(ServerPlayer player) {
        State state = BLOWERS.get(player.getUUID());
        if (state == null) return;
        if (!GulliverConfig.INSTANCE.general.breathGust || !player.isAlive() || player.isSpectator()
                || player.isSleeping() || player.level().getGameTime() - state.lastRequest > KEEP_ALIVE) {
            stop(player);
            return;
        }
        if (!state.exhausted && !player.getAbilities().invulnerable && player.getAirSupply() <= 0) {
            state.exhausted = true;
        }
        boolean blowing = !state.exhausted;
        boolean underwater = player.isEyeInFluid(FluidTags.WATER);
        if (blowing != state.shown) {
            state.shown = blowing;
            show(player, blowing);
            if (blowing && !underwater) huff((ServerLevel) player.level(), player);
        }
        if (!blowing) return;
        if (!player.getAbilities().invulnerable) {
            // Under water vanilla gives nothing back, so the breath alone is the cost.
            int cost = underwater ? Mth.ceil(player.getMaxAirSupply() / (float) BREATH_TICKS) : airCost(player);
            player.setAirSupply(Math.max(0, player.getAirSupply() - cost));
        }
        if (underwater) return; // bubbles, drawn by the clients; no wind
        blow((ServerLevel) player.level(), player, state.ticks++);
    }

    /** Air one tick of gust costs: a full breath in BREATH_TICKS, whatever its size, net of vanilla's regain. */
    static int airCost(LivingEntity blower) {
        return Mth.ceil(blower.getMaxAirSupply() / (float) BREATH_TICKS) + AIR_REGEN;
    }

    /** The intake of breath that starts a gust. */
    private static void huff(ServerLevel level, LivingEntity blower) {
        float size = ((IResizeableEntity) blower).getSizeMultiplier();
        Vec3 eye = blower.getEyePosition();
        level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.HORSE_BREATHE, SoundSource.PLAYERS,
                SizeSound.scaleVolume((IResizeableEntity) blower, 0.7F), Mth.clamp(1.3F / (float) Math.sqrt(size), 0.4F, 2.0F));
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
            if (!windReaches(level, blower, eye, e)) continue;

            Vec3 before = e.getDeltaMovement();
            Vec3 kick = gust(size, e, along(dir, eye, center, e), dist / range);
            if (kick == null) continue;
            Vec3 after = before.add(kick);
            e.setDeltaMovement(after);
            e.hurtMarked = true;
            if (e instanceof Projectile projectile) turnProjectile(projectile, blower, dir, before, after);
            if (++blown >= MAX_BLOWN) break;
        }
        return blown;
    }

    /**
     * Whether the wind gets to {@code e}: walls and water stop it, so what is
     * under water is out of the wind, while whatever pokes out of the water
     * (or over a ledge) still catches it.
     */
    private static boolean windReaches(ServerLevel level, LivingEntity blower, Vec3 eye, Entity e) {
        AABB box = e.getBoundingBox();
        Vec3 center = box.getCenter();
        if (openAir(level, blower, eye, center)) return true;
        return openAir(level, blower, eye, new Vec3(center.x, box.maxY - box.getYsize() * 0.1D, center.z));
    }

    private static boolean openAir(ServerLevel level, LivingEntity blower, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, blower))
                .getType() == HitResult.Type.MISS;
    }

    /** How far a body of this size blows: four of its own lengths. */
    public static double range(float size) {
        return Mth.clamp(4.0D * size, 0.25D, MAX_RANGE);
    }

    /**
     * Which way the wind blows where it meets {@code e}: along the look, but
     * wind that hits the ground spreads out over it, so something standing
     * on the ground is blown away across it rather than pressed into it
     * (looking straight down, it is blown away from the blower).
     */
    static Vec3 along(Vec3 dir, Vec3 eye, Vec3 center, Entity e) {
        if (!e.onGround() || dir.y >= -0.2D) return dir;
        Vec3 flat = new Vec3(dir.x, 0.0D, dir.z);
        if (flat.lengthSqr() < 0.04D) flat = new Vec3(center.x - eye.x, 0.0D, center.z - eye.z);
        if (flat.lengthSqr() < 1.0E-6D) return dir;
        return flat.normalize();
    }

    /** Wind speed at the mouth, blocks per tick: a bigger mouth moves more air. */
    public static double windSpeed(float size) {
        return Math.min(MAX_WIND, 0.35D * Math.sqrt(size));
    }

    /**
     * Velocity the wind adds to {@code target} this tick at {@code reach}
     * (0 at the mouth, 1 at the end of the cone), or null when it doesn't
     * move it: as heavy as the blower or heavier, or already flying with
     * the wind as fast as the wind.
     */
    static Vec3 gust(float blowerSize, Entity target, Vec3 dir, double reach) {
        double mass = ((IResizeableEntity) target).getSizeMultiplier() * inertia(target);
        double lighter = 1.0D - mass / blowerSize;
        if (lighter <= 0.0D) return null;
        double k = COUPLING * lighter;
        if (target instanceof LivingEntity living) {
            k *= 1.0D - Mth.clamp(living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0D, 1.0D);
        }
        double wind = windSpeed(blowerSize) * (1.0D - 0.6D * reach);
        double dv = (wind - target.getDeltaMovement().dot(dir)) * k;
        if (dv < 0.002D) return null;
        double lift = target.onGround() ? wind * k * 0.5D : 0.0D;
        return new Vec3(dir.x * dv, dir.y * dv + lift, dir.z * dv);
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
