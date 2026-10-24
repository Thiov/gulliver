package gulliver.debug;

import gulliver.api.IResizeableLiving;
import gulliver.common.Climb;
import gulliver.common.GulliverEnvoy;
import gulliver.common.Movement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
        Mob carpeted = tinyAt(level, o.getX() + 3.0D - 0.0375D - 1.0E-4D, o.getY(), o.getZ() + 3.5D);
        if (carpeted != null) {
            carpeted.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SLIME_BALL));
            check(failures, carpeted.onClimbable(), "slime-ball tiny beside a snow layer can climb");
            carpeted.discard();
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
