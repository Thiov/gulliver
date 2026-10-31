package gulliver.debug;

import gulliver.api.IResizeableLiving;
import gulliver.common.Appetite;
import gulliver.common.BreathGust;
import gulliver.common.GulliverConfig;
import gulliver.common.GulliverEnvoy;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Checks for the fixes to issues #8 to #12 and giant appetite; run by SelfTest. */
final class SelfTestIssues {
    private SelfTestIssues() {}

    private static void check(List<String> failures, boolean ok, String what) {
        if (!ok) failures.add(what);
        gulliver.Gulliver.LOGGER.info("selftest {} {}", ok ? "ok  " : "FAIL", what);
    }

    static void run(ServerLevel level, List<String> failures) {
        trampling(level, failures);
        breath(level, failures);
        boats(level, failures);
        appetite(level, failures);
    }

    /** #9: sneaking and crawling tread carefully; #8: the damage setting scales trampling. */
    private static void trampling(ServerLevel level, List<String> failures) {
        BlockPos at = new BlockPos(8, 0, 8);
        Mob zombie = SelfTest.spawnMob(level, EntityTypes.ZOMBIE, at);
        Mob pig = SelfTest.spawnMob(level, EntityTypes.PIG, at);
        if (zombie == null || pig == null) return;
        ((IResizeableLiving) pig).setBaseSize(0.25F);
        pig.setPos(zombie.getX(), zombie.getY(), zombie.getZ());
        GulliverEnvoy.noteBodySize(pig);

        zombie.setShiftKeyDown(true);
        float hp = pig.getHealth();
        GulliverEnvoy.stepOnSmallerEntities(zombie, 0.1D);
        check(failures, pig.getHealth() == hp, "a sneaking zombie doesn't trample");
        zombie.setShiftKeyDown(false);

        zombie.setPose(Pose.SWIMMING);
        GulliverEnvoy.stepOnSmallerEntities(zombie, 0.1D);
        check(failures, pig.getHealth() == hp, "a crawling zombie doesn't trample");
        zombie.setPose(Pose.STANDING);

        GulliverConfig.General g = GulliverConfig.INSTANCE.general;
        double saved = g.trampleDamage;
        try {
            GulliverEnvoy.stepOnSmallerEntities(zombie, 0.1D);
            float once = hp - pig.getHealth();
            pig.setHealth(pig.getMaxHealth());
            SelfTest.clearHurtCooldown(pig);
            hp = pig.getHealth();
            g.trampleDamage = 3.0D;
            GulliverEnvoy.stepOnSmallerEntities(zombie, 0.1D);
            float thrice = hp - pig.getHealth();
            check(failures, once > 0.0F && Math.abs(thrice - 3.0F * once) < 0.01F,
                    "trampleDamage 3 triples trample damage (" + once + " -> " + thrice + ")");
            pig.setHealth(pig.getMaxHealth());
            SelfTest.clearHurtCooldown(pig);
            hp = pig.getHealth();
            g.trampleDamage = 0.0D;
            GulliverEnvoy.stepOnSmallerEntities(zombie, 0.1D);
            check(failures, pig.getHealth() == hp, "trampleDamage 0 turns trampling off");
        } finally {
            g.trampleDamage = saved;
        }
        zombie.discard();
        pig.discard();
    }

    /** #10: a giant's breath pushes a normal pig away and turns an arrow around. */
    private static void breath(ServerLevel level, List<String> failures) {
        BlockPos at = new BlockPos(20, 0, 8);
        Mob giant = SelfTest.spawnMob(level, EntityTypes.ZOMBIE, at);
        Mob pig = SelfTest.spawnMob(level, EntityTypes.PIG, at);
        Entity arrow = create(level, EntityTypes.ARROW);
        if (giant == null || pig == null || arrow == null) return;
        // High in the open sky: nothing in the way of the wind.
        ((IResizeableLiving) giant).setBaseSize(8.0F);
        giant.setPos(20.5D, 230.0D, 8.5D);
        giant.setDeltaMovement(Vec3.ZERO);
        Vec3 eye = giant.getEyePosition();
        pig.setPos(eye.x, eye.y - 4.0D, eye.z + 6.0D);
        pig.setDeltaMovement(Vec3.ZERO);
        giant.lookAt(EntityAnchorArgument.Anchor.EYES, pig.getBoundingBox().getCenter());
        Vec3 dir = giant.getLookAngle();

        int moved = BreathGust.blow(level, giant, 1);
        check(failures, moved >= 1 && pig.getDeltaMovement().dot(dir) > 0.05D,
                "a size-8 breath pushes a normal pig away (" + pig.getDeltaMovement().dot(dir) + ")");

        // The pig would also take the wind; move it out of the cone.
        pig.setPos(eye.x + 30.0D, eye.y, eye.z);
        Vec3 arrowAt = eye.add(dir.scale(8.0D));
        arrow.setPos(arrowAt.x, arrowAt.y, arrowAt.z);
        level.addFreshEntity(arrow);
        arrow.setDeltaMovement(dir.scale(-2.0D));
        for (int i = 0; i < 8; i++) BreathGust.blow(level, giant, 2 + i);
        check(failures, arrow.getDeltaMovement().dot(dir) > 0.0D && ((Projectile) arrow).getOwner() == giant,
                "a size-8 breath turns an incoming arrow around and owns it");

        check(failures, Math.abs(BreathGust.range(1.0F) - 4.0D) < 1.0E-9D && BreathGust.range(32.0F) <= 48.0D,
                "breath range is four body lengths, capped");
        arrow.discard();
        giant.discard();
        pig.discard();
    }

    /** #12: a tiny in a boat sits in the hull, not under it, and keeps its air. */
    private static void boats(ServerLevel level, List<String> failures) {
        // A 3x3 pool, two deep, in a stone basin high above the terrain.
        BlockPos o = new BlockPos(24, 210, 24);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                level.setBlock(o.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 2);
                for (int y = 0; y <= 1; y++) {
                    boolean wall = Math.abs(x) == 2 || Math.abs(z) == 2;
                    level.setBlock(o.offset(x, y, z),
                            wall ? Blocks.STONE.defaultBlockState() : Blocks.WATER.defaultBlockState(), 2);
                }
                for (int y = 2; y <= 4; y++) level.setBlock(o.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        try {
            for (EntityType<? extends Mob> type : List.of(EntityTypes.ZOMBIE, EntityTypes.PIG)) {
                Entity boat = create(level, boatType());
                Mob rider = SelfTest.spawnMob(level, type, o);
                if (boat == null || rider == null) continue;
                boat.setPos(o.getX() + 0.5D, o.getY() + 1.5D, o.getZ() + 0.5D);
                level.addFreshEntity(boat);
                ((IResizeableLiving) rider).setBaseSize(0.125F);
                boolean seated = rider.startRiding(boat);
                for (int i = 0; i < 60; i++) {
                    boat.tick();
                    if (rider.getVehicle() == boat) rider.rideTick();
                }
                String name = EntityType.getKey(type).getPath();
                check(failures, seated && rider.getVehicle() == boat && !rider.isEyeInFluid(FluidTags.WATER)
                                && rider.getAirSupply() == rider.getMaxAirSupply()
                                && rider.getY() >= boat.getY() - 0.05D,
                        "a 1/8 " + name + " sits in a boat with dry eyes (y " + (rider.getY() - boat.getY())
                                + ", air " + rider.getAirSupply() + ")");
                rider.discard();
                boat.discard();
            }
        } finally {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    for (int y = -1; y <= 1; y++) level.setBlock(o.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    /** Giants eat: who may eat whom, and a hostile giant swallowing weakened prey through its real attack. */
    private static void appetite(ServerLevel level, List<String> failures) {
        BlockPos at = new BlockPos(32, 0, 8);
        Mob giant = SelfTest.spawnMob(level, EntityTypes.ZOMBIE, at);
        Mob pig = SelfTest.spawnMob(level, EntityTypes.PIG, at);
        Mob normal = SelfTest.spawnMob(level, EntityTypes.ZOMBIE, at);
        if (giant == null || pig == null || normal == null) return;
        ((IResizeableLiving) giant).setBaseSize(8.0F);
        check(failures, Appetite.canEat(giant, pig), "a size-8 giant may eat a normal pig");
        check(failures, !Appetite.canEat(normal, pig), "a normal zombie eats nobody");
        ((IResizeableLiving) pig).setBaseSize(4.0F);
        check(failures, !Appetite.canEat(giant, pig), "a meal must be a quarter of the eater's size");
        ((IResizeableLiving) pig).setBaseSize(1.0F);
        check(failures, !Appetite.swallowsInFight(giant, pig), "a healthy pig is fought, not swallowed");

        pig.setHealth(3.0F);
        giant.setHealth(50.0F);
        Vec3 where = pig.position();
        //#if MC >= 1.21.2
        boolean hit = giant.doHurtTarget(level, pig);
        //#else
        //$$ boolean hit = giant.doHurtTarget(pig);
        //#endif
        List<ItemEntity> loot = level.getEntitiesOfClass(ItemEntity.class,
                new net.minecraft.world.phys.AABB(where, where).inflate(3.0D));
        // (the giant's growth may have broken some dirt; the pig's own loot is pork)
        boolean noLoot = loot.stream().noneMatch(i -> i.getItem().is(net.minecraft.world.item.Items.PORKCHOP));
        check(failures, hit && pig.isRemoved() && giant.getHealth() > 50.0F && noLoot,
                "a hostile giant swallows a weakened pig whole and heals (removed " + pig.isRemoved()
                        + ", health " + giant.getHealth() + ", loot " + loot.stream()
                        .map(i -> i.getItem().getItem().toString()).toList() + ")");
        giant.discard();
        normal.discard();
        if (!pig.isRemoved()) pig.discard();
    }

    private static EntityType<?> boatType() {
        //#if MC >= 1.21.2
        return EntityTypes.OAK_BOAT;
        //#else
        //$$ return EntityTypes.BOAT;
        //#endif
    }

    private static Entity create(ServerLevel level, EntityType<?> type) {
        //#if MC >= 1.21.2
        return type.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        //#else
        //$$ return type.create(level);
        //#endif
    }
}
