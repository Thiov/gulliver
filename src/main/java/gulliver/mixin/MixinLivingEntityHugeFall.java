package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.common.GulliverEnvoy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
 * a real fall (>= 3 blocks):
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
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityHugeFall {

    @Inject(method = "causeFallDamage", at = @At("HEAD"))
    private void gulliver$crushOnLanding(double fallDistance, float multiplier,
                                          net.minecraft.world.damagesource.DamageSource source,
                                          CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        IResizeableEntity sized = (IResizeableEntity) self;
        if (!sized.isHuge()) return;
        if (self.isPassenger()) return;
        Level level = self.level();
        if (level.isClientSide()) return;

        // Gate: any fall under 3 blocks is a heavy step, not an impact.
        // Real fall damage starts past 3 anyway, so this is the natural
        // cutoff (1.6.4's lev-- clause alone still cracked floors on
        // 1-block steps — too aggressive per playtest).
        if (fallDistance < 3.0) return;

        gulliver$shockwave(self, sized, fallDistance);

        if (!GulliverEnvoy.canSizeGrief(self)) return;

        float size = sized.getSizeMultiplier();
        AABB bb = self.getBoundingBox();
        double width = bb.maxX - bb.minX;
        int r = (int) Math.ceil(width * 0.5);
        int lev = (int) Math.floor(Math.log(size) / Math.log(2.0));
        if (fallDistance <= 5.0) lev--;
        if (lev <= 0) return;

        int l = (int) Math.floor((bb.minX + bb.maxX) * 0.5);
        int n = (int) Math.floor((bb.minZ + bb.maxZ) * 0.5);
        int footY = (int) Math.floor(bb.minY - 0.001);

        // Inclusive lower bound: lev layers total (footY down to
        // footY - lev + 1).
        for (int s = footY; s >= footY - lev + 1 && s > level.getMinY(); s--) {
            for (int p = l - r; p <= l + r; p++) {
                for (int q = n - r; q <= n + r; q++) {
                    BlockPos pos = new BlockPos(p, s, q);
                    BlockState st = level.getBlockState(pos);
                    if (st.isAir()) continue;
                    float h = st.getDestroySpeed(level, pos);
                    if (h < 0.0F || h > 1.0F) continue;  // bedrock or too-hard
                    level.destroyBlock(pos, true, self);
                }
            }
        }
    }

    /**
     * Ground-shock on landing. Radius scales with body size, punch
     * scales with fall distance, both fall off with distance from the
     * impact point. Only much-smaller creatures react (< half the
     * giant's size) — a fellow giant doesn't flinch.
     */
    private static void gulliver$shockwave(LivingEntity self, IResizeableEntity sized,
                                            double fallDistance) {
        ServerLevel sl = (ServerLevel) self.level();
        float size = sized.getSizeMultiplier();
        double radius = 2.5D * size;
        double x = self.getX();
        double y = self.getY();
        double z = self.getZ();

        // Deep smash boom — louder and lower the bigger the body.
        float volume = Math.min(2.0F, 0.6F + size * 0.15F);
        float pitch = Math.max(0.4F, 1.0F / sized.getSizeMultiplierRoot());
        sl.playSound(null, x, y, z, SoundEvents.MACE_SMASH_GROUND_HEAVY,
                self.getSoundSource(), volume, pitch);

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
        double punch = Math.min(1.6D, 0.3D + fallDistance * 0.06D);
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
