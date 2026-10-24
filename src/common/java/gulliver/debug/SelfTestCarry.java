package gulliver.debug;

import gulliver.access.IGulliverShoulderInternal;
import gulliver.api.IResizeableLiving;
import gulliver.common.TinyMounts;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;

import java.util.List;
import java.util.UUID;

/**
 * Riding rules and carry cleanup, on mobs (the headless server has no
 * player to ride with): giants in toys, the tiny-mount list and ratios,
 * and an orphaned carry on a mob without AI being let go from its own tick.
 */
final class SelfTestCarry {
    private SelfTestCarry() {}

    private static void check(List<String> failures, boolean ok, String what) {
        if (!ok) failures.add(what);
        gulliver.Gulliver.LOGGER.info("selftest {} {}", ok ? "ok  " : "FAIL", what);
    }

    static void run(ServerLevel level, List<String> failures) {
        BlockPos at = new BlockPos(24, 0, 24);

        // Riding by size: a giant can't board something much smaller than itself.
        Mob chicken = SelfTest.spawnMob(level, EntityTypes.CHICKEN, at);
        Mob rider = SelfTest.spawnMob(level, EntityTypes.ZOMBIE, at);
        if (chicken != null && rider != null) {
            ((IResizeableLiving) rider).setBaseSize(4.0F);
            check(failures, !rider.startRiding(chicken), "a size-4 rider can't board a size-1 mount");
            ((IResizeableLiving) rider).setBaseSize(1.0F);
            check(failures, rider.startRiding(chicken) && rider.getVehicle() == chicken,
                    "a size-1 rider boards a size-1 mount");
            // ...and one that outgrows its mount is put off.
            ((IResizeableLiving) rider).setBaseSize(4.0F);
            rider.tick();
            check(failures, rider.getVehicle() == null, "a rider that outgrew its mount is dismounted");
            // Forced rides (commands, string) are left alone.
            //#if MC >= 1.21.5
            boolean forced = rider.startRiding(chicken, true, false);
            //#else
            //$$ boolean forced = rider.startRiding(chicken, true);
            //#endif
            rider.tick();
            check(failures, forced && rider.getVehicle() == chicken, "a forced ride survives the size rule");
            rider.stopRiding();

            // The tiny-mount list and ratios.
            check(failures, TinyMounts.specOf(chicken) != null && TinyMounts.controllerOf(chicken) == null,
                    "a chicken is a tiny mount, with nobody steering it");
            ((IResizeableLiving) rider).setBaseSize(0.25F);
            check(failures, TinyMounts.canBoard(rider, chicken), "a 0.25 rider may climb on a size-1 chicken");
            ((IResizeableLiving) rider).setBaseSize(0.5F);
            check(failures, !TinyMounts.canBoard(rider, chicken), "a 0.5 rider may not (the chicken is only 2x)");
            rider.discard();
            chicken.discard();
        }
        Mob cow = SelfTest.spawnMob(level, EntityTypes.COW, at);
        Mob tiny = SelfTest.spawnMob(level, EntityTypes.ZOMBIE, at);
        if (cow != null && tiny != null) {
            ((IResizeableLiving) tiny).setBaseSize(0.1F);
            check(failures, TinyMounts.specOf(cow) == null && !TinyMounts.canBoard(tiny, cow),
                    "a cow is not a tiny mount");
            cow.discard();
            tiny.discard();
        }

        // A carry whose carrier is gone is let go from the carried mob's own tick,
        // also when the mob has no AI (its move() never runs).
        Mob pig = SelfTest.spawnMob(level, EntityTypes.PIG, at);
        if (pig != null) {
            pig.setNoAi(true);
            IGulliverShoulderInternal held = (IGulliverShoulderInternal) pig;
            held.gulliver$setHoldingEntity(UUID.randomUUID());
            pig.tick();
            check(failures, held.gulliver$getHoldingEntity() == null, "an orphaned carry on a NoAI mob is released");
            pig.discard();
        }
    }
}
