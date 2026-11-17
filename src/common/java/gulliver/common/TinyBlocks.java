package gulliver.common;

import gulliver.api.IResizeableEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Small blocks are solid for tinies (#19). Buttons, levers, pressure
 * plates and signs have no collision, which is right for a normal body but
 * not for one too light to press a plate or push a button: a tiny now
 * stands on them, bumps into them and can climb them (a wall button is a
 * ledge, a sign post a pillar). Their shape as drawn is their shape to walk
 * on, so a button pressed by someone else sinks under a tiny's feet.
 *
 * Pressure plates are springs to a tiny: standing on one while something
 * heavier presses it, the tiny sinks with it; when the plate is let go it
 * pops back up and throws the tiny into the air, hardest from stone and
 * iron, gentlest from wood, and higher the smaller it is.
 *
 * Decided by the server (general.smallBlockCollision), which clients follow
 * (SyncedSettings) so movement is predicted the same on both sides.
 */
public final class TinyBlocks {
    private TinyBlocks() {}

    private static boolean enabled(Entity entity) {
        return entity.level().isClientSide() ? SyncedSettings.smallBlockCollision()
                : GulliverConfig.INSTANCE.general.smallBlockCollision;
    }

    /** Whether this block, normally walked through, is solid for this entity. */
    public static boolean solidFor(Block block, Entity entity) {
        if (!(block instanceof BasePressurePlateBlock || block instanceof ButtonBlock
                || block instanceof LeverBlock || block instanceof SignBlock)) {
            return false;
        }
        return entity instanceof LivingEntity && !entity.isSpectator()
                && ((IResizeableEntity) entity).isTiny() && enabled(entity);
    }

    /** How hard a plate springs back: stone and iron hardest, wood gentlest. */
    private static double spring(Block block) {
        String id = BuiltInRegistries.BLOCK.getKey(block).getPath();
        if (id.contains("heavy_weighted")) return 1.2D;
        if (id.contains("light_weighted")) return 0.85D;
        if (id.contains("stone")) return 1.0D;
        return 0.65D;
    }

    /** A pressure plate came back up: tinies standing on it are thrown into the air. */
    public static void plateReleased(Level level, BlockPos pos, Block block) {
        if (!(level instanceof ServerLevel server) || !GulliverConfig.INSTANCE.general.smallBlockCollision) return;
        AABB top = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0D, pos.getY() + 0.25D, pos.getZ() + 1.0D);
        double spring = spring(block);
        boolean thrown = false;
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, top,
                e -> !e.isSpectator() && ((IResizeableEntity) e).isTiny() && !e.isPassenger())) {
            float size = Math.max(0.03F, ((IResizeableEntity) e).getSizeMultiplier());
            // Lighter bodies fly higher: about two blocks for a 1/4 tiny off stone.
            double up = 0.55D * spring * Math.max(0.8D, Math.min(1.6D, Math.sqrt(0.25D / size)));
            double sx = (server.getRandom().nextDouble() - 0.5D) * 0.08D;
            double sz = (server.getRandom().nextDouble() - 0.5D) * 0.08D;
            Vec3 v = e.getDeltaMovement();
            e.setDeltaMovement(v.x + sx, Math.max(v.y, up), v.z + sz);
            e.resetFallDistance();
            e.hurtMarked = true;
            thrown = true;
        }
        if (thrown) {
            server.playSound(null, pos.getX() + 0.5D, pos.getY() + 0.1D, pos.getZ() + 0.5D,
                    net.minecraft.sounds.SoundEvents.SLIME_BLOCK_STEP, SoundSource.BLOCKS, 0.5F, 1.6F);
        }
    }
}
