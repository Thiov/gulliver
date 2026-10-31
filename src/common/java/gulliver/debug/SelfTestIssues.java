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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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

        // Water stops the wind: the same pig under the surface stays put.
        BlockPos wet = BlockPos.containing(pig.position());
        for (BlockPos pos : BlockPos.betweenClosed(wet.offset(-1, -1, -1), wet.offset(1, 2, 1))) {
            level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
        }
        pig.setDeltaMovement(Vec3.ZERO);
        BreathGust.blow(level, giant, 1);
        check(failures, pig.getDeltaMovement().lengthSqr() < 1.0E-6D,
                "the wind stops at the water: a pig under it isn't blown (" + pig.getDeltaMovement() + ")");
        for (BlockPos pos : BlockPos.betweenClosed(wet.offset(-1, -1, -1), wet.offset(1, 2, 1))) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }

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
        pig.setPos(eye.x + 30.0D, eye.y, eye.z);
        check(failures, BreathGust.bigEnough(giant) && !BreathGust.bigEnough(pig), "only giants blow a gust (from size 4)");
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

    /**
     * Bigger things eat smaller things: who may eat whom, and a hostile giant
     * snatching weakened prey through its real attack, lifting it to its
     * mouth and swallowing it - or dropping it when it gets hurt.
     */
    private static void appetite(ServerLevel level, List<String> failures) {
        BlockPos at = new BlockPos(32, 0, 8);
        Mob giant = SelfTest.spawnMob(level, EntityTypes.ZOMBIE, at);
        Mob pig = SelfTest.spawnMob(level, EntityTypes.PIG, at);
        Mob normal = SelfTest.spawnMob(level, EntityTypes.ZOMBIE, at);
        if (giant == null || pig == null || normal == null) return;
        ((IResizeableLiving) giant).setBaseSize(8.0F);
        check(failures, Appetite.canEat(giant, pig), "a size-8 giant may eat a normal pig");
        check(failures, !Appetite.canEat(normal, pig), "a zombie can't eat a pig its own size");
        ((IResizeableLiving) pig).setBaseSize(0.25F);
        check(failures, Appetite.canEat(normal, pig), "a normal zombie may eat a quarter-size pig (size difference)");
        ((IResizeableLiving) pig).setBaseSize(4.0F);
        check(failures, !Appetite.canEat(giant, pig), "a meal must be a quarter of the eater's size");
        ((IResizeableLiving) pig).setBaseSize(0.25F);
        check(failures, !Appetite.swallowsInFight(normal, pig), "a normal zombie fights a healthy quarter-size pig");
        pig.setHealth(pig.getMaxHealth() * Appetite.SWALLOW_HEALTH);
        check(failures, Appetite.swallowsInFight(normal, pig), "...and snatches it once it is weakened");
        pig.setHealth(pig.getMaxHealth());
        ((IResizeableLiving) pig).setBaseSize(0.125F);
        check(failures, !sees(level, normal, pig), "a normal zombie overlooks a size-1/8 pig (Gulliver's tiny rule)");
        ((IResizeableLiving) pig).setBaseSize(1.0F);
        check(failures, !sees(level, giant, pig), "a size-8 giant overlooks a normal-size creature");
        check(failures, !looksAt(level, giant, pig), "...and doesn't even look at it");
        check(failures, looksAt(level, normal, pig), "a creature its own size is worth a look");
        giant.setLastHurtByMob(pig);
        check(failures, sees(level, giant, pig) && looksAt(level, giant, pig), "...until it gets hurt by it");
        giant.setLastHurtByMob(null);
        check(failures, Appetite.swallowsInFight(giant, pig), "a giant fighting small prey snatches it at any health");
        check(failures, Math.abs(gulliver.common.SizeScaling.mobMissChance(8.0F, 1.0F) - 0.875F) < 1.0E-6F
                        && gulliver.common.SizeScaling.mobMissChance(1.0F, 1.0F) == 0.0F
                        && gulliver.common.SizeScaling.mobMissChance(32.0F, 0.125F) == 0.9F,
                "a mob misses something an eighth its size seven times in eight, at most nine in ten");

        pig.setHealth(3.0F);
        giant.setHealth(50.0F);
        Vec3 where = pig.position();
        int tries = grab(level, giant, pig);
        boolean held = giant.getUUID().equals(((gulliver.access.IGulliverShoulderInternal) pig).gulliver$getHoldingEntity());
        check(failures, held && Appetite.isChewing(giant) && pig.isAlive() && pig.getHealth() == 3.0F,
                "a hostile giant snatches a pig into its hand first (missed grabs don't hurt)");
        int misses = tries - 1;
        for (int i = 0; i < Appetite.lastBite(giant) + 2 && !pig.isRemoved(); i++) {
            Appetite.tick(giant);
            gulliver.common.ShoulderHelper.positionPassengers(giant);
        }
        List<ItemEntity> loot = level.getEntitiesOfClass(ItemEntity.class,
                new net.minecraft.world.phys.AABB(where, where).inflate(16.0D));
        // (the giant's growth may have broken some dirt; the pig's own loot is pork)
        boolean noLoot = loot.stream().noneMatch(i -> i.getItem().is(net.minecraft.world.item.Items.PORKCHOP));
        check(failures, pig.isRemoved() && giant.getHealth() > 50.0F && noLoot && !Appetite.isChewing(giant),
                "...then lifts it to its mouth and swallows it whole, healing (removed " + pig.isRemoved()
                        + ", health " + giant.getHealth() + ", no pork " + noLoot + ")");

        Mob second = SelfTest.spawnMob(level, EntityTypes.PIG, at);
        if (second != null) {
            second.setHealth(3.0F);
            misses += grab(level, giant, second) - 1;
            for (int i = 0; i < 5; i++) Appetite.tick(giant);
            //#if MC >= 1.21.2
            giant.hurtServer(level, level.damageSources().generic(), 2.0F);
            //#else
            //$$ giant.hurt(level.damageSources().generic(), 2.0F);
            //#endif
            check(failures, second.isAlive() && !Appetite.isChewing(giant)
                            && ((gulliver.access.IGulliverShoulderInternal) second).gulliver$getHoldingEntity() == null,
                    "a giant that gets hurt drops the creature it was about to eat");
            second.discard();
        }

        // Armor is what keeps a small meal from being eaten: it takes more bites,
        // and a meal that comes through every bite is spat out, hurt but alive.
        Mob armored = SelfTest.spawnMob(level, EntityTypes.PIG, at);
        if (armored != null) {
            armored.getAttribute(Attributes.ARMOR).setBaseValue(20.0D);
            armored.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(12.0D);
            armored.setHealth(2.5F);
            misses += grab(level, giant, armored) - 1;
            for (int i = 0; i < Appetite.firstBite(giant); i++) Appetite.tick(giant);
            boolean afterFirst = armored.isAlive();
            for (int i = 0; i < Appetite.lastBite(giant) && !armored.isRemoved(); i++) Appetite.tick(giant);
            check(failures, afterFirst && armored.isRemoved(), "an armored meal takes more than one bite");
            if (!armored.isRemoved()) armored.discard();
        }
        Mob tough = SelfTest.spawnMob(level, EntityTypes.PIG, at);
        if (tough != null) {
            tough.getAttribute(Attributes.ARMOR).setBaseValue(20.0D);
            tough.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(12.0D);
            tough.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 600, 3));
            tough.setHealth(3.0F);
            misses += grab(level, giant, tough) - 1;
            for (int i = 0; i < Appetite.lastBite(giant) + 2; i++) Appetite.tick(giant);
            check(failures, tough.isAlive() && !Appetite.isChewing(giant) && tough.getHealth() < 3.0F
                            && ((gulliver.access.IGulliverShoulderInternal) tough).gulliver$getHoldingEntity() == null,
                    "a meal that comes through every bite is spat out, hurt (health " + tough.getHealth() + ")");
            tough.discard();
        }
        check(failures, misses > 0, "grabs at something an eighth the giant's size miss now and then (" + misses + " misses)");
        giant.discard();
        normal.discard();
        if (!pig.isRemoved()) pig.discard();
    }

    /** Strikes until the giant holds {@code prey} (each grab may miss); how many it took. */
    private static int grab(ServerLevel level, Mob giant, Mob prey) {
        int tries = 0;
        while (tries < 400 && !giant.getUUID().equals(((gulliver.access.IGulliverShoulderInternal) prey).gulliver$getHoldingEntity())) {
            attack(level, giant, prey);
            tries++;
        }
        return tries;
    }

    /** Whether an idle {@code mob} would pick {@code target} to look at (LookAtPlayerGoal's conditions). */
    private static boolean looksAt(ServerLevel level, Mob mob, LivingEntity target) {
        net.minecraft.world.entity.ai.targeting.TargetingConditions look =
                net.minecraft.world.entity.ai.targeting.TargetingConditions.forNonCombat().range(64.0D);
        ((gulliver.access.IGulliverTargeting) look).gulliver$setOverlooksSmall(true);
        //#if MC >= 1.21.2
        return look.test(level, mob, target);
        //#else
        //$$ return look.test(mob, target);
        //#endif
    }

    /** Whether {@code attacker}'s hostile targeting sees {@code target} (line of sight aside). */
    private static boolean sees(ServerLevel level, Mob attacker, LivingEntity target) {
        net.minecraft.world.entity.ai.targeting.TargetingConditions combat =
                net.minecraft.world.entity.ai.targeting.TargetingConditions.forCombat().ignoreLineOfSight();
        //#if MC >= 1.21.2
        return combat.test(level, attacker, target);
        //#else
        //$$ return combat.test(attacker, target);
        //#endif
    }

    private static boolean attack(ServerLevel level, Mob attacker, Entity target) {
        //#if MC >= 1.21.2
        return attacker.doHurtTarget(level, target);
        //#else
        //$$ return attacker.doHurtTarget(target);
        //#endif
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
