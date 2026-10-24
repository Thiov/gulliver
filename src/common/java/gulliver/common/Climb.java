package gulliver.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Wall contact for the climbing powers (slime-ball and soft-surface
 * climbing for tinies, string / lead climbing for everyone else).
 *
 * Contact is read from the real collision shapes, looked up with the
 * entity's own collision context (so Gulliver's snow and carpet shapes
 * apply), in a thin shell around the sides of the body. Slabs, carpets,
 * snow layers, fences and the like count as wall; the floor under the
 * feet and a ceiling over the head do not, because the shell stops a hair
 * short of the bottom and the top of the body.
 *
 * Also carries a climbing body over the lip of whatever it climbed: the
 * top of a wall that ends in a slab or a carpet would otherwise leave the
 * body hanging a hair below the edge, falling back and bouncing.
 */
public final class Climb {
    private Climb() {}

    /** What the sides of a body touch. */
    public static final class Contact {
        static final Contact NONE = new Contact(false, 0.0F, Double.NaN, Double.NaN, null);

        /** Some collision shape touches a side. */
        public final boolean solid;
        /** Best soft-surface climbing rate among the touched blocks (0 when not asked for). */
        public final float softRate;
        /** World Y of the lowest and of the highest top among the touched sides. */
        public final double lowTop, highTop;
        /** The touched side with the lowest top (the way over the edge). */
        public final Direction lowSide;

        Contact(boolean solid, float softRate, double lowTop, double highTop, Direction lowSide) {
            this.solid = solid;
            this.softRate = softRate;
            this.lowTop = lowTop;
            this.highTop = highTop;
            this.lowSide = lowSide;
        }
    }

    private static final Direction[] SIDES = { Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH };

    /** Vanilla's airborne ladder climb: (0.2 - gravity) * drag, per tick. */
    public static final double VANILLA_CLIMB = (0.2D - 0.08D) * 0.98D;

    /** How close a side has to be to count as touching: a quarter of the body width. */
    public static double reach(LivingEntity entity) {
        return Mth.clamp(entity.getBbWidth() * 0.25D, 0.004D, 0.15D);
    }

    /** Whether this body may climb at all right now (the cases where vanilla's own ladder check says no). */
    public static boolean canClimb(LivingEntity entity) {
        if (entity.isSpectator() || entity.isFallFlying() || entity.isPassenger()) return false;
        return !(entity instanceof Player p && p.getAbilities().flying);
    }

    public static boolean touchingWall(LivingEntity entity, double reach) {
        return probe(entity, reach, false).solid;
    }

    public static Contact probe(LivingEntity entity, double reach, boolean wantRate) {
        AABB box = entity.getBoundingBox();
        double height = box.maxY - box.minY;
        double trim = Math.min(0.03125D, height * 0.1D);
        double y0 = box.minY + trim;
        double y1 = box.maxY - trim;
        if (!(y1 > y0) || !(reach > 0.0D)) return Contact.NONE;

        Level level = entity.level();
        CollisionContext ctx = CollisionContext.of(entity);
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        boolean solid = false;
        float rate = 0.0F;
        double low = Double.POSITIVE_INFINITY;
        double high = Double.NEGATIVE_INFINITY;
        Direction lowSide = null;
        for (Direction side : SIDES) {
            AABB shell = switch (side) {
                case EAST -> new AABB(box.maxX, y0, box.minZ, box.maxX + reach, y1, box.maxZ);
                case WEST -> new AABB(box.minX - reach, y0, box.minZ, box.minX, y1, box.maxZ);
                case SOUTH -> new AABB(box.minX, y0, box.maxZ, box.maxX, y1, box.maxZ + reach);
                default -> new AABB(box.minX, y0, box.minZ - reach, box.maxX, y1, box.minZ);
            };
            double top = Double.NEGATIVE_INFINITY;
            for (int x = Mth.floor(shell.minX); x <= Mth.floor(shell.maxX); x++) {
                for (int y = Mth.floor(shell.minY); y <= Mth.floor(shell.maxY); y++) {
                    for (int z = Mth.floor(shell.minZ); z <= Mth.floor(shell.maxZ); z++) {
                        mp.set(x, y, z);
                        BlockState st = level.getBlockState(mp);
                        if (st.isAir()) continue;
                        VoxelShape shape = st.getCollisionShape(level, mp, ctx);
                        if (shape.isEmpty()) continue;
                        boolean touched = false;
                        for (AABB part : shape.toAabbs()) {
                            if (part.move(x, y, z).intersects(shell)) {
                                touched = true;
                                top = Math.max(top, part.maxY + y);
                            }
                        }
                        if (touched && wantRate) {
                            rate = Math.max(rate, GulliverEnvoy.blockClimbingRateForTiny(level, mp));
                        }
                    }
                }
            }
            if (top > Double.NEGATIVE_INFINITY) {
                solid = true;
                high = Math.max(high, top);
                if (top < low) {
                    low = top;
                    lowSide = side;
                }
            }
        }
        return solid ? new Contact(true, rate, low, high, lowSide) : Contact.NONE;
    }

    /**
     * Sets up the hop that carries a climbing body over the lip of a wall:
     * when the feet are within reach of the top of everything they touch, the
     * body presses into the wall, and there is room on top, it gets just
     * enough upward speed (and a nudge) to clear the edge. Returns whether
     * it hopped.
     */
    public static boolean hopOverLedge(LivingEntity entity, Contact wall, double reach) {
        if (!wall.solid || wall.lowSide == null || !entity.horizontalCollision) return false;
        AABB box = entity.getBoundingBox();
        double height = box.maxY - box.minY;
        double rise = wall.highTop - box.minY;
        if (!(rise > 0.0D) || rise > Math.min(0.45D * height, 0.6D)) return false;

        double forward = Math.max(reach, entity.getBbWidth() * 0.5D);
        AABB landing = box.move(wall.lowSide.getStepX() * forward, rise + 0.01D, wall.lowSide.getStepZ() * forward);
        if (!entity.level().noCollision(entity, landing)) return false;

        // Rise a little more than the lip so the body spends a few ticks above
        // it, drifting onto the top, before gravity brings it down there.
        double up = Math.min(0.42D, Math.sqrt(2.0D * 0.08D * (rise + 0.15D * height)));
        double nudge = 0.03D * Math.sqrt(Math.max(0.0D, entity.getBbWidth() / 0.6D));
        Vec3 dm = entity.getDeltaMovement();
        entity.setDeltaMovement(dm.x + wall.lowSide.getStepX() * nudge, Math.max(dm.y, up),
                dm.z + wall.lowSide.getStepZ() * nudge);
        return true;
    }
}
