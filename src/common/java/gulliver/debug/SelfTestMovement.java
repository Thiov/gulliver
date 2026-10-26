package gulliver.debug;

import gulliver.api.IResizeableLiving;
import gulliver.common.Climb;
import gulliver.common.GulliverEnvoy;
import gulliver.common.Movement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.List;

/** Movement checks (climbing, jump and step curves); run by SelfTest. */
final class SelfTestMovement {
    private SelfTestMovement() {}

    private static void check(List<String> failures, boolean ok, String what) {
        if (!ok) failures.add(what);
        gulliver.Gulliver.LOGGER.info("selftest {} {}", ok ? "ok  " : "FAIL", what);
    }

    static void run(ServerLevel level, List<String> failures) {
        // Pure curves first: nothing here may blow up for any size.
        check(failures, Movement.sneakBoost(1.0F) == 1.0F && Movement.sneakBoost(0.99F) < 1.001F
                && Math.abs(Movement.sneakBoost(0.5F) - 1.5F) < 1.0E-5F
                && Math.abs(Movement.sneakBoost(8.0F) - 1.5F) < 1.0E-5F,
                "sneak-jump boost is continuous at size 1");
        boolean stepsOk = Movement.stepHeight(0.6F, 1.0F) == 0.6F
                && Math.abs(Movement.stepHeight(0.6F, 0.125F) - 0.075F) < 1.0E-5F
                && Math.abs(Movement.stepHeight(0.6F, 32.0F) - 19.2F) < 1.0E-3F;
        for (float bad : new float[] { 0.0F, -1.0F, Float.NaN, Float.POSITIVE_INFINITY, Float.MIN_VALUE, Float.MAX_VALUE }) {
            float h = Movement.stepHeight(0.6F, bad);
            stepsOk &= !Float.isNaN(h) && !Float.isInfinite(h);
        }
        check(failures, stepsOk, "step height terminates and stays finite for any size");
        check(failures, Movement.speedMultiplier(8.0F, true) == 8.0F && Movement.speedMultiplier(32.0F, true) <= 12.01F
                && Math.abs(Movement.speedMultiplier(32.0F, false) - (float) Math.sqrt(32.0D)) < 1.0E-4F
                && Movement.speedMultiplier(Float.NaN, true) == 1.0F,
                "weighted speed is capped at the top of the size range");

        // A little scaffold high above the terrain, cleared first.
        BlockPos o = new BlockPos(8, 200, 8);
        for (int x = -4; x <= 6; x++) {
            for (int y = -3; y <= 8; y++) {
                for (int z = -4; z <= 6; z++) {
                    level.setBlock(o.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        double reach = 0.01D;

        // Issue #6: a slime-ball tiny climbs the side of a bottom slab ...
        level.setBlock(o.offset(1, 0, 0), Blocks.STONE_SLAB.defaultBlockState(), 2);
        Mob tiny = tinyAt(level, o.getX() + 1.0D - 0.0375D - 1.0E-4D, o.getY() + 0.1D, o.getZ() + 0.5D);
        if (tiny != null) {
            tiny.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SLIME_BALL));
            check(failures, tiny.onClimbable(), "slime-ball tiny beside a bottom slab can climb");
            check(failures, !Climb.touchingWall(tiny, 0.0D), "an empty reach touches nothing");

            // ... and gets over its top edge without dropping out of the climb.
            tiny.setPos(tiny.getX(), o.getY() + 0.45D, tiny.getZ());
            tiny.horizontalCollision = true;
            boolean hopped = Climb.hopOverLedge(tiny, Climb.probe(tiny, reach, false), reach);
            check(failures, hopped && tiny.getDeltaMovement().y > 0.05D, "climbing tiny hops over a slab lip");
            tiny.setDeltaMovement(0.0D, 0.0D, 0.0D);
            tiny.setPos(tiny.getX(), o.getY() + 0.1D, tiny.getZ());
            check(failures, !Climb.hopOverLedge(tiny, Climb.probe(tiny, reach, false), reach),
                    "no hop while the top is far above");
            tiny.horizontalCollision = false;

            // Without the slime ball it is just a tiny next to a wall.
            tiny.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            check(failures, !tiny.onClimbable(), "no slime ball, no climb");
            tiny.discard();
        }

        // ... but not out in the open on soul sand (full support shape, no wall).
        level.setBlock(o.offset(-3, -1, -3), Blocks.SOUL_SAND.defaultBlockState(), 2);
        Mob open = tinyAt(level, o.getX() - 2.5D, o.getY() - 1.0D + 0.875D, o.getZ() - 2.5D);
        if (open != null) {
            open.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SLIME_BALL));
            check(failures, !open.onClimbable(), "slime-ball tiny on soul sand in the open does not climb");
            open.discard();
        }

        // A snow layer is a wall too (and its shape is Gulliver's own for an extra-tiny).
        level.setBlock(o.offset(3, 0, 3), Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 3), 2);
        Mob snowed = tinyAt(level, o.getX() + 3.0D - 0.0375D - 1.0E-4D, o.getY(), o.getZ() + 3.5D);
        if (snowed != null) {
            snowed.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SLIME_BALL));
            check(failures, snowed.onClimbable(), "slime-ball tiny beside a snow layer can climb");
            snowed.discard();
        }

        // H6: a lead is a rope only while pressing into the wall.
        level.setBlock(o.offset(-1, 0, 2), Blocks.STONE.defaultBlockState(), 2);
        Mob roper = SelfTest.spawnMob(level, EntityTypes.ZOMBIE, o);
        if (roper != null) {
            roper.setPos(o.getX() + 0.3D + 1.0E-3D, o.getY(), o.getZ() + 2.5D);
            roper.setNoGravity(true);
            roper.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.LEAD));
            check(failures, GulliverEnvoy.isHoldingStringOrLeash(roper), "a lead is a rope for a normal body");
            roper.horizontalCollision = false;
            check(failures, !roper.onClimbable(), "standing beside a wall with a lead is no climb");
            roper.horizontalCollision = true;
            check(failures, roper.onClimbable(), "pushing into a wall with a lead climbs");
            roper.discard();
        }

        level.setBlock(o.offset(1, 0, 0), Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(o.offset(-3, -1, -3), Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(o.offset(3, 0, 3), Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(o.offset(-1, 0, 2), Blocks.AIR.defaultBlockState(), 2);

        // Step height is scaled once (before 1.20.5 LivingEntity.maxUpStep
        // calls super, which is scaled already).
        Mob stepper = SelfTest.spawnMob(level, EntityTypes.ZOMBIE, o);
        if (stepper != null) {
            ((IResizeableLiving) stepper).setBaseSize(2.0F);
            float step = stepper.maxUpStep();
            check(failures, Math.abs(step - Movement.stepHeight(0.6F, 2.0F)) < 1.0E-4F,
                    "a size-2 body's step height is scaled once (" + step + ")");
            stepper.discard();
        }

        walks(level, failures);

        // Tripwire: a tiny waiting in a tripped wire doesn't keep it powered.
        BlockPos wire = o.offset(0, 0, -3);
        net.minecraft.world.level.block.state.BlockState tripped =
                Blocks.TRIPWIRE.defaultBlockState().setValue(TripWireBlock.POWERED, true);
        for (boolean tinyInside : new boolean[] { true, false }) {
            level.setBlock(wire, tripped, 2);
            Mob visitor = tinyAt(level, wire.getX() + 0.5D, wire.getY(), wire.getZ() + 0.5D);
            if (visitor != null) {
                if (!tinyInside) ((IResizeableLiving) visitor).setBaseSize(1.0F);
                visitor.setPos(wire.getX() + 0.5D, wire.getY(), wire.getZ() + 0.5D);
                level.getBlockState(wire).tick(level, wire, level.getRandom());
                boolean powered = level.getBlockState(wire).getValue(TripWireBlock.POWERED);
                check(failures, powered != tinyInside,
                        tinyInside ? "a tiny in a tripped wire lets it go" : "a normal body keeps a tripped wire powered");
                visitor.discard();
            }
        }
        level.setBlock(wire, Blocks.AIR.defaultBlockState(), 2);

        jumpsAndFalls(level, failures);

        // Snow: eight layers are a full block to an extra-tiny (SHAPES has nine entries).
        BlockPos snowPos = o.offset(0, 0, 4);
        Mob extraTiny = tinyAt(level, snowPos.getX() + 0.5D, snowPos.getY() + 3.0D, snowPos.getZ() + 0.5D);
        Mob regular = SelfTest.spawnMob(level, EntityTypes.PIG, o);
        if (extraTiny != null && regular != null) {
            for (int layers : new int[] { 3, 8 }) {
                net.minecraft.world.level.block.state.BlockState snow =
                        Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers);
                level.setBlock(snowPos, snow, 2);
                double tall = snow.getCollisionShape(level, snowPos, CollisionContext.of(extraTiny)).max(Direction.Axis.Y);
                double normal = snow.getCollisionShape(level, snowPos, CollisionContext.of(regular)).max(Direction.Axis.Y);
                check(failures, Math.abs(tall - Math.min(1.0D, layers / 8.0D)) < 1.0E-6D
                                && Math.abs(normal - (layers - 1) / 8.0D) < 1.0E-6D,
                        "snow with " + layers + " layers: extra-tiny stands at " + tall + ", normal body at " + normal);
            }
            level.setBlock(snowPos, Blocks.AIR.defaultBlockState(), 2);
        }
        if (extraTiny != null) extraTiny.discard();
        if (regular != null) regular.discard();

        // M7: who rain can drown.
        Mob pig = SelfTest.spawnMob(level, EntityTypes.PIG, o);
        Mob undead = SelfTest.spawnMob(level, EntityTypes.ZOMBIE, o);
        if (pig != null && undead != null) {
            check(failures, GulliverEnvoy.rainCanDrown(pig), "rain can drown a pig");
            check(failures, !GulliverEnvoy.rainCanDrown(undead), "rain can't drown an undead breather");
            pig.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 100));
            check(failures, !GulliverEnvoy.rainCanDrown(pig), "Water Breathing stops rain drowning");
        }
        if (pig != null) pig.discard();
        if (undead != null) undead.discard();

        // M9 / low list: a giant's landing and its farmland.
        BlockPos g = o.offset(0, 0, 0);
        Mob giant = SelfTest.spawnMob(level, EntityTypes.ZOMBIE, g);
        if (giant != null) {
            ((IResizeableLiving) giant).setBaseSize(4.0F);
            giant.setNoGravity(true);
            giant.setPos(g.getX() + 0.5D, g.getY(), g.getZ() + 0.5D);
            fillDirt(level, g, true);
            fall(giant, 3.5D, 1.0F);
            check(failures, countDirt(level, g) == 50, "a giant's ordinary hop crushes nothing");
            fall(giant, 8.0D, 0.0F);
            check(failures, countDirt(level, g) == 50, "a giant landing on slime crushes nothing");
            fall(giant, 8.0D, 1.0F);
            check(failures, countDirt(level, g) == 0, "a giant's real fall crushes its footprint (left " + countDirt(level, g) + ")");

            BlockPos field = g.offset(5, -1, 0);
            net.minecraft.world.level.block.state.BlockState farm = Blocks.FARMLAND.defaultBlockState();
            level.setBlock(field, farm, 2);
            farm.getBlock().fallOn(level, farm, field, giant, fallArg(1.0D));
            check(failures, level.getBlockState(field).is(Blocks.DIRT), "a giant tramples farmland");
            level.setBlock(field, farm, 2);
            Mob smallFry = tinyAt(level, field.getX() + 0.5D, field.getY() + 1.0D, field.getZ() + 0.5D);
            if (smallFry != null) {
                farm.getBlock().fallOn(level, farm, field, smallFry, fallArg(5.0D));
                check(failures, level.getBlockState(field).is(Blocks.FARMLAND), "a tiny never tramples farmland");
                smallFry.discard();
            }
            level.setBlock(field, Blocks.AIR.defaultBlockState(), 2);
            giant.discard();
        }
    }

    /** What a scripted walk did. */
    private static final class Walk {
        int reached = -1;     // ticks until the body stood at the goal, -1 if it never did
        int climbTicks;       // ticks it counted as climbing
        double maxY = -1.0E9; // highest feet height after the first settling ticks
        StringBuilder trace = new StringBuilder();
    }

    /**
     * A zombie of the given size, holding {@code held}, with no AI of its own,
     * is told to walk to a goal over a stone (or soul sand) floor, past or into
     * a one-block-wide stone column at x = 11 that may carry a bottom slab. The
     * real tick runs, so jumping, stepping, climbing and falling are the game's.
     */
    private static Walk walk(ServerLevel level, float size, net.minecraft.world.item.Item held, int wallHeight,
                             boolean lip, boolean soulSand, double sx, double sy, double sz,
                             double gx, double gy, double gz, int maxTicks) {
        BlockPos o = new BlockPos(8, 200, 8);
        for (int x = 4; x <= 14; x++) {
            for (int y = -1; y <= 6; y++) {
                for (int z = 4; z <= 12; z++) {
                    level.setBlock(new BlockPos(x, o.getY() + y, z),
                            y == -1 ? (soulSand ? Blocks.SOUL_SAND : Blocks.STONE).defaultBlockState()
                                    : Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        for (int y = 0; y < wallHeight; y++) level.setBlock(new BlockPos(11, o.getY() + y, 8), Blocks.STONE.defaultBlockState(), 2);
        if (lip) level.setBlock(new BlockPos(11, o.getY() + wallHeight, 8), Blocks.STONE_SLAB.defaultBlockState(), 2);

        Walk w = new Walk();
        Mob mob = SelfTest.spawnMob(level, EntityTypes.ZOMBIE, o);
        if (mob == null) return w;
        ((IResizeableLiving) mob).setBaseSize(size);
        mob.setInvulnerable(true); // daylight would burn it
        mob.removeAllGoals(g -> true);
        if (held != null) mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(held));
        mob.setPos(sx, sy, sz);
        for (int t = 0; t < maxTicks; t++) {
            mob.getMoveControl().setWantedPosition(gx, gy, gz, 1.0D);
            mob.tick();
            boolean climbing = mob.onClimbable();
            if (climbing) w.climbTicks++;
            if (t >= 6) w.maxY = Math.max(w.maxY, mob.getY());
            if (t % 8 == 0 || t < 24) {
                w.trace.append(String.format("t%d x=%.3f y=%.3f vy=%.3f ground=%b hc=%b climb=%b%n", t, mob.getX(), mob.getY(),
                        mob.getDeltaMovement().y, mob.onGround(), mob.horizontalCollision, climbing));
            }
            double dx = mob.getX() - gx, dz = mob.getZ() - gz;
            if (dx * dx + dz * dz < 0.04D && Math.abs(mob.getY() - gy) < 0.05D && mob.onGround()) {
                w.reached = t;
                break;
            }
        }
        mob.discard();
        for (int x = 4; x <= 14; x++) {
            for (int y = -1; y <= 6; y++) {
                for (int z = 4; z <= 12; z++) {
                    level.setBlock(new BlockPos(x, o.getY() + y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        return w;
    }

    private static void walks(ServerLevel level, List<String> failures) {
        double y0 = 200.0D;
        double tinyFlush = 11.0D - 0.0375D - 0.01D;

        Walk a = walk(level, 0.125F, Items.SLIME_BALL, 3, true, false, tinyFlush, y0, 8.5D, 11.5D, y0 + 3.5D, 8.5D, 600);
        check(failures, a.reached >= 0, "walk: slime-ball tiny climbs a slab-topped wall and stands on it");
        if (a.reached < 0) gulliver.Gulliver.LOGGER.info("walk trace\n{}", a.trace);
        Walk b = walk(level, 0.125F, Items.SLIME_BALL, 3, false, false, tinyFlush, y0, 8.5D, 11.5D, y0 + 3.0D, 8.5D, 600);
        check(failures, b.reached >= 0, "walk: slime-ball tiny climbs a plain wall and stands on it");
        if (b.reached < 0) gulliver.Gulliver.LOGGER.info("walk trace\n{}", b.trace);
        Walk c = walk(level, 0.125F, Items.SLIME_BALL, 0, true, false, tinyFlush, y0, 8.5D, 11.5D, y0 + 0.5D, 8.5D, 600);
        check(failures, c.reached >= 0, "walk: slime-ball tiny climbs a slab on the floor and stands on it");
        if (c.reached < 0) gulliver.Gulliver.LOGGER.info("walk trace\n{}", c.trace);
        // Out in the open on soul sand: it just walks, it doesn't hop its way there.
        double sand = y0 - 1.0D + 0.875D;
        Walk d = walk(level, 0.125F, Items.SLIME_BALL, 0, false, true, 5.5D, sand, 5.5D, 9.5D, sand, 5.5D, 900);
        check(failures, d.reached >= 0 && d.climbTicks == 0 && d.maxY < sand + 0.03D,
                "walk: slime-ball tiny crosses soul sand without climbing or hopping (maxY " + (d.maxY - sand) + ")");
        if (d.reached < 0 || d.climbTicks > 0) gulliver.Gulliver.LOGGER.info("walk trace\n{}", d.trace);
        // A normal body with a lead scales a slab-topped wall by pressing into it ...
        Walk e = walk(level, 1.0F, Items.LEAD, 3, true, false, 11.0D - 0.3D - 0.01D, y0, 8.5D, 11.5D, y0 + 3.5D, 8.5D, 600);
        check(failures, e.reached >= 0, "walk: lead-holding body climbs a slab-topped wall and stands on it");
        if (e.reached < 0) gulliver.Gulliver.LOGGER.info("walk trace\n{}", e.trace);
        // ... and walks along it without noticing it.
        Walk f = walk(level, 1.0F, Items.LEAD, 3, false, false, 10.7D, y0, 5.5D, 10.7D, y0, 11.5D, 600);
        check(failures, f.reached >= 0 && f.climbTicks == 0,
                "walk: lead-holding body walks past a wall without climbing it (" + f.climbTicks + " climb ticks)");
        if (f.reached < 0 || f.climbTicks > 0) gulliver.Gulliver.LOGGER.info("walk trace\n{}", f.trace);
        // Issue #7: a normal body steps up onto a bottom slab without jumping.
        Walk s = walk(level, 1.0F, null, 0, true, false, 9.5D, y0, 8.5D, 11.5D, y0 + 0.5D, 8.5D, 200);
        check(failures, s.reached >= 0 && s.maxY < y0 + 0.55D,
                "walk: a normal body steps up onto a slab without jumping (maxY " + (s.maxY - y0) + ")");
        if (s.reached < 0) gulliver.Gulliver.LOGGER.info("walk trace\n{}", s.trace);
    }

    private static void jumpsAndFalls(ServerLevel level, List<String> failures) {
        BlockPos o = new BlockPos(8, 200, 8);
        //#if MC >= 1.21.2
        Mob jumper = SelfTest.spawnMob(level, EntityTypes.ZOMBIE, o);
        if (jumper != null) {
            jumper.setNoGravity(true);
            jumper.setOnGround(true);
            jumper.setShiftKeyDown(true);
            double[] v = new double[2];
            float[] sizes = { 1.0F, 0.99F };
            for (int i = 0; i < sizes.length; i++) {
                ((IResizeableLiving) jumper).setBaseSize(sizes[i]);
                jumper.setDeltaMovement(0.0D, 0.0D, 0.0D);
                jumper.jumpFromGround();
                v[i] = jumper.getDeltaMovement().y;
            }
            check(failures, Math.abs(v[0] - 0.42D) < 1.0E-3D && Math.abs(v[1] - v[0]) < 0.01D,
                    "a sneak-jump at 0.99 is no higher than at 1 (" + v[0] + " vs " + v[1] + ")");

            // The sprint-jump boost follows the walking gait: 0.2 * sqrt(0.125) at 1/8.
            jumper.setShiftKeyDown(false);
            jumper.setSprinting(true);
            ((IResizeableLiving) jumper).setBaseSize(0.125F);
            jumper.setYRot(0.0F);
            jumper.setDeltaMovement(0.0D, 0.0D, 0.0D);
            jumper.jumpFromGround();
            double boost = jumper.getDeltaMovement().horizontalDistance();
            check(failures, Math.abs(boost - 0.2D * Math.sqrt(0.125D)) < 0.002D,
                    "a sprint-jump at 1/8 gets 0.2 * sqrt(size) (got " + boost + ")");
            jumper.discard();
        }
        //#endif

        //#if MC >= 1.20.5
        // The fall threshold starts from the safe_fall_distance attribute.
        Mob faller = SelfTest.spawnMob(level, EntityTypes.ZOMBIE, o);
        if (faller != null) {
            ((IResizeableLiving) faller).setBaseSize(0.5F);
            faller.setHealth(faller.getMaxHealth());
            SelfTest.clearHurtCooldown(faller);
            float before = faller.getHealth();
            fall(faller, 8.0D, 1.0F);
            float lostDefault = before - faller.getHealth();
            faller.setHealth(faller.getMaxHealth());
            SelfTest.clearHurtCooldown(faller);
            faller.getAttribute(Attributes.SAFE_FALL_DISTANCE).setBaseValue(10.0D);
            before = faller.getHealth();
            fall(faller, 8.0D, 1.0F);
            float lostSafe = before - faller.getHealth();
            check(failures, lostDefault >= 1.0F && lostSafe == 0.0F,
                    "safe_fall_distance counts for a half-size fall (lost " + lostDefault + ", then " + lostSafe + ")");
            faller.discard();
        }
        //#endif
    }

    /** Fills the footprint under a size-4 body (5x5, two deep) with dirt, or empties it. */
    private static void fillDirt(ServerLevel level, BlockPos at, boolean dirt) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = 1; y <= 2; y++) {
                    level.setBlock(at.offset(x, -y, z), dirt ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    private static int countDirt(ServerLevel level, BlockPos at) {
        int n = 0;
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = 1; y <= 2; y++) {
                    if (level.getBlockState(at.offset(x, -y, z)).is(Blocks.DIRT)) n++;
                }
            }
        }
        return n;
    }

    //#if MC >= 1.21.5
    private static double fallArg(double distance) { return distance; }
    //#else
    //$$ private static float fallArg(double distance) { return (float) distance; }
    //#endif

    private static void fall(Mob mob, double distance, float multiplier) {
        mob.causeFallDamage(fallArg(distance), multiplier, mob.damageSources().fall());
    }

    private static Mob tinyAt(ServerLevel level, double x, double y, double z) {
        Mob mob = SelfTest.spawnMob(level, EntityTypes.ZOMBIE, new BlockPos(8, 200, 8));
        if (mob == null) return null;
        ((IResizeableLiving) mob).setBaseSize(0.125F);
        mob.setNoGravity(true);
        mob.setPos(x, y, z);
        return mob;
    }
}
