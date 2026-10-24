package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.common.GulliverEnvoy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Giant landing impact — two layers fire when a huge entity lands from
 * a real fall:
 *
 * 1. SHOCKWAVE (always): the ground answers the fall. A deep smash
 *    boom, a ring of block-dust kicked up from the surface, and every
 *    much-smaller creature nearby is knocked off its feet — thrown up
 *    and away with falloff by distance. This is the huge-side
 *    counterpart to the tiny terminal-velocity mechanic: gravity
 *    doesn't change for a giant, the WORLD reacts to the giant.
 *    No block damage, so it is not gated by size_griefing.
 *
 * 2. BLOCK CRUSH (grief-gated) — 1.6.4 of.java:1919-1988: blocks under
 *    the footprint with hardness <= 1.0 are destroyed in a column.
 *
 *    Verbatim formula:
 *      r   = ceil(width * 0.5)                  // horizontal radius
 *      lev = floor(log2(sizeMultiplier))         // depth (size 2 -> 1, 4 -> 2, 8 -> 3)
 *      if (fallDist <= 5.0) lev--                // small falls do less
 *
 *    "Shift jump destroys more" comes naturally: fall distance from a
 *    super-jump is higher, so the `<= 5.0` clause doesn't fire and lev
 *    stays at full depth.
 *
 * What counts as "a real fall" grows with the body: the fall the ground
 * feels (distance x the landing block's damage multiplier x the entity's
 * fall_damage_multiplier) has to reach max(3, size) blocks. A giant's
 * ordinary jump (about 0.6 body heights, or up to 16 blocks at size 32)
 * stays under that, a sneak-jump or a drop does not, and a landing on
 * slime (multiplier 0) or hay and honey (0.2) feels like nothing at all.
 * Adventure-mode players crush nothing; players need the claim checks of
 * their loader; and one landing breaks at most CRUSH_BUDGET blocks, nearest
 * the feet first.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityHugeFall {

    /** Most blocks one landing may crush (size 32 would be 2,200). */
    private static final int GULLIVER$CRUSH_BUDGET = 256;
    /** Furthest the shockwave reaches, whatever the size. */
    private static final double GULLIVER$MAX_SHOCK_RADIUS = 40.0D;

    @Inject(method = "causeFallDamage", at = @At("HEAD"))
    private void gulliver$crushOnLanding(
                                           //#if MC >= 1.21.5
                                           double fallDistance,
                                           //#else
                                           //$$ float fallDistance,
                                           //#endif
                                           float multiplier,
                                          net.minecraft.world.damagesource.DamageSource source,
                                          CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        IResizeableEntity sized = (IResizeableEntity) self;
        if (!sized.isHuge()) return;
        if (self.isPassenger()) return;
        Level level = self.level();
        if (level.isClientSide()) return;

        // The fall as the ground feels it. Fall damage multipliers cushion it
        // the same way they cushion fall damage.
        double felt = fallDistance * multiplier;
        //#if MC >= 1.20.5
        felt *= self.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER);
        //#endif
        float size = sized.getSizeMultiplier();
        // Gate: a heavy step or an ordinary jump is not an impact. Real fall
        // damage starts past 3 blocks for a normal body, and a giant's jump
        // is as long as the giant is big (1.6.4's lev-- clause alone still
        // cracked floors on 1-block steps — too aggressive per playtest).
        if (!(multiplier > 0.0F) || felt < Math.max(3.0D, size)) return;

        gulliver$shockwave(self, sized, felt);

        if (!GulliverEnvoy.canSizeGrief(self)) return;
        if (self instanceof Player pl && !pl.getAbilities().mayBuild) return;

        AABB bb = self.getBoundingBox();
        double width = bb.maxX - bb.minX;
        int r = (int) Math.ceil(width * 0.5);
        int lev = (int) Math.floor(Math.log(size) / Math.log(2.0));
        if (felt <= 5.0) lev--;
        if (lev <= 0) return;

        int l = (int) Math.floor((bb.minX + bb.maxX) * 0.5);
        int n = (int) Math.floor((bb.minZ + bb.maxZ) * 0.5);
        int footY = (int) Math.floor(bb.minY - 0.001);
        ServerPlayer player = self instanceof ServerPlayer sp ? sp : null;
        ServerLevel server = (ServerLevel) level;

        // Inclusive lower bound: lev layers total (footY down to
        // footY - lev + 1). Within a layer, nearest column first, so a
        // capped landing is a crater and not a lopsided bite.
        int crushed = 0;
        for (int s = footY; s >= footY - lev + 1 && s > level.getMinY() && crushed < GULLIVER$CRUSH_BUDGET; s--) {
            for (int ring = 0; ring <= r && crushed < GULLIVER$CRUSH_BUDGET; ring++) {
                for (int dp = -ring; dp <= ring && crushed < GULLIVER$CRUSH_BUDGET; dp++) {
                    // On the edge of the square ring: a full row, else only its two ends.
                    int step = Math.abs(dp) == ring ? 1 : Math.max(1, 2 * ring);
                    for (int dq = -ring; dq <= ring && crushed < GULLIVER$CRUSH_BUDGET; dq += step) {
                        BlockPos pos = new BlockPos(l + dp, s, n + dq);
                        BlockState st = level.getBlockState(pos);
                        if (st.isAir()) continue;
                        float h = st.getDestroySpeed(level, pos);
                        if (h < 0.0F || h > 1.0F) continue;  // bedrock or too-hard
                        if (player != null && (!level.mayInteract(player, pos)
                                || !gulliver.platform.Services.platform().mayBreakBlock(server, pos, st, player))) {
                            continue;
                        }
                        if (level.destroyBlock(pos, true, self)) crushed++;
                    }
                }
            }
        }
    }

    /**
     * Ground-shock on landing. Radius scales with body size, punch
     * scales with the fall the ground felt, both fall off with distance
     * from the impact point. Only much-smaller creatures react (< half the
     * giant's size) — a fellow giant doesn't flinch.
     */
    private static void gulliver$shockwave(LivingEntity self, IResizeableEntity sized,
                                            double fallDistance) {
        ServerLevel sl = (ServerLevel) self.level();
        float size = sized.getSizeMultiplier();
        double radius = Math.min(2.5D * size, GULLIVER$MAX_SHOCK_RADIUS);
        double x = self.getX();
        double y = self.getY();
        double z = self.getZ();
        // Impact punch shared by the entity fling and the screen quake.
        double punch = Math.min(1.6D, 0.3D + fallDistance * 0.06D);

        // Deep smash boom — louder and lower the bigger the body.
        // Volume above 1.0 extends the audible radius (16 × volume
        // blocks), so a size-8 slam carries ~56 blocks — you hear the
        // titan land long before you see it. Footsteps and other body
        // sounds already carry further via MixinEntitySound's
        // sizeMultiplierRoot volume scaling.
        float volume = Math.min(3.5F, 0.8F + size * 0.35F);
        float pitch = Math.max(0.4F, 1.0F / sized.getSizeMultiplierRoot());
        sl.playSound(null, x, y, z,
                //#if MC >= 1.21
                SoundEvents.MACE_SMASH_GROUND_HEAVY,
                //#else
                //$$ SoundEvents.GENERIC_EXPLODE,
                //#endif
                self.getSoundSource(), volume, pitch);

        // Screen quake for much-smaller viewers nearby (client-side
        // falloff + size gating in TremorHandler.groundShock).
        gulliver.network.Payloads.GroundShock shock =
                new gulliver.network.Payloads.GroundShock(x, y, z, size, (float) punch);
        gulliver.platform.Services.platform().sendToTrackingAndSelf(self, shock);

        // Dust ring: kick up particles of the block actually landed on.
        BlockPos below = BlockPos.containing(x, y - 0.5D, z);
        BlockState ground = sl.getBlockState(below);
        if (!ground.isAir()) {
            BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, ground);
            int points = Math.min(48, 12 + (int) (size * 4));
            double ringR = radius * 0.6D;
            for (int i = 0; i < points; i++) {
                double ang = (Math.PI * 2.0D / points) * i;
                sl.sendParticles(dust,
                        x + Math.cos(ang) * ringR, y + 0.1D, z + Math.sin(ang) * ringR,
                        3, 0.1D, 0.05D, 0.1D, 0.15D);
            }
        }

        // Knock much-smaller creatures off their feet — up and away,
        // stronger for harder landings and closer bystanders.
        AABB zone = self.getBoundingBox().inflate(radius, 1.0D, radius);
        for (Entity target : sl.getEntities(self, zone)) {
            if (!(target instanceof LivingEntity)) continue;
            IResizeableEntity tsized = (IResizeableEntity) target;
            if (tsized.getSizeMultiplier() >= size * 0.5F) continue;
            if (GulliverEnvoy.isDragonEntity(target)) continue;
            if (target instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            // Never toss what the giant is carrying.
            if (self.getUUID().equals(((gulliver.access.IGulliverShoulderInternal) target)
                    .gulliver$getHoldingEntity())) continue;

            double dist = Math.sqrt((target.getX() - x) * (target.getX() - x)
                    + (target.getZ() - z) * (target.getZ() - z));
            if (dist > radius) continue;
            double falloff = 1.0D - dist / radius;
            double s = punch * falloff;
            double dx = target.getX() - x;
            double dz = target.getZ() - z;
            double horiz = Math.sqrt(dx * dx + dz * dz);
            if (horiz < 1.0E-3D) {
                // Standing dead-center under the giant: pick a stable
                // outward direction from the angle to the world origin.
                dx = 1.0D; dz = 0.0D; horiz = 1.0D;
            }
            dx /= horiz;
            dz /= horiz;
            Vec3 dm = target.getDeltaMovement();
            target.setDeltaMovement(dm.x + dx * s * 0.8D,
                    Math.max(dm.y, 0.15D + s * 0.5D),
                    dm.z + dz * s * 0.8D);
            target.hurtMarked = true;
        }
    }
}
