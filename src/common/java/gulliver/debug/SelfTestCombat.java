package gulliver.debug;

import gulliver.api.IResizeableEntity;
import gulliver.api.IResizeableLiving;
import gulliver.common.AttackContext;
import gulliver.common.CollateralBreaking;
import gulliver.common.GiantBossBars;
import gulliver.common.GulliverConfig;
import gulliver.common.LootScaling;
import gulliver.common.SizeScaling;
import gulliver.init.GulliverTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
//#if MC >= 1.21.10
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SimpleExplosionDamageCalculator;

import java.util.Optional;
//#endif

import java.util.List;

/** Behaviour checks for the combat, items and mob-AI package; run by SelfTest. */
final class SelfTestCombat {
    private SelfTestCombat() {}

    private static void check(List<String> failures, boolean ok, String what) {
        if (!ok) failures.add(what);
        gulliver.Gulliver.LOGGER.info("selftest {} {}", ok ? "ok  " : "FAIL", what);
    }

    static void run(ServerLevel level, List<String> failures) {
        BlockPos at = new BlockPos(8, 0, 8);
        int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 8, 8);

        // Size-range guards: ratios, use times, food.
        check(failures, SizeScaling.clampRatio(64.0F) == 8.0F && SizeScaling.clampRatio(1.0F / 64.0F) == 0.125F,
                "hit ratios are capped at 8x either way");
        check(failures, SizeScaling.scaledTicks(32, 32.0F) == 4 && SizeScaling.scaledTicks(32, 1.0F / 32.0F) == 256
                && SizeScaling.scaledTicks(32, 0.5F) == 64, "use times stay within 4 ticks .. 8x vanilla");
        check(failures, SizeScaling.chargeFactor(32.0F, 20) == 5.0F && SizeScaling.chargeFactor(0.125F, 20) == 0.125F,
                "draw factors follow the clamped use times");

        // The attacker context never leaks past its call, and only speaks for its own victim.
        Mob a = SelfTest.spawnMob(level, EntityTypes.ZOMBIE, at);
        Mob b = SelfTest.spawnMob(level, EntityTypes.ZOMBIE, at);
        Mob c = SelfTest.spawnMob(level, EntityTypes.PIG, at);
        if (a != null && b != null && c != null) {
            AttackContext.push(a, b);
            AttackContext.push(b, c);
            AttackContext.push(null, b);                       // an inner call that never popped
            check(failures, AttackContext.get(c) == b && AttackContext.get(a) == null, "attack frames match their victim");
            AttackContext.pop(c);                              // closes c's frame and the leaked one above it
            check(failures, AttackContext.get(c) == null && AttackContext.get(b) == a, "closing a frame drops leaks above it");
            AttackContext.reset();
            check(failures, AttackContext.get(b) == null, "reset forgets every frame");
            a.discard();
            b.discard();
            c.discard();
        }

        // Brittle floors: glass yes (wool comes in through #minecraft:wool); portal frames, ice, lamps no. Unbreakables are never broken.
        check(failures, Blocks.GLASS.defaultBlockState().is(GulliverTags.BRITTLE)
                        && Blocks.GLASS_PANE.defaultBlockState().is(GulliverTags.BRITTLE)
                        && Blocks.TINTED_GLASS.defaultBlockState().is(GulliverTags.BRITTLE),
                "glass and panes are brittle");
        check(failures, !Blocks.END_PORTAL_FRAME.defaultBlockState().is(GulliverTags.BRITTLE)
                        && !Blocks.ICE.defaultBlockState().is(GulliverTags.BRITTLE)
                        && !Blocks.GLOWSTONE.defaultBlockState().is(GulliverTags.BRITTLE)
                        && !Blocks.REDSTONE_LAMP.defaultBlockState().is(GulliverTags.BRITTLE),
                "portal frames, ice and lamps are not");
        BlockPos pos = new BlockPos(8, top + 6, 8);
        level.setBlockAndUpdate(pos, Blocks.BEDROCK.defaultBlockState());
        check(failures, CollateralBreaking.breakBlock(level, pos, null, CollateralBreaking.Mode.BODY)
                        == CollateralBreaking.Result.SKIPPED && level.getBlockState(pos).is(Blocks.BEDROCK),
                "collateral breaking leaves unbreakable blocks alone");
        level.setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState());
        check(failures, CollateralBreaking.breakBlock(level, pos, null, CollateralBreaking.Mode.BODY)
                        == CollateralBreaking.Result.BROKEN && level.getBlockState(pos).isAir(),
                "collateral breaking removes glass");
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());

        // Loot and experience grow with the mob, with exact expectation (stochastic rounding).
        boolean lootBefore = GulliverConfig.INSTANCE.general.lootScaling;
        GulliverConfig.INSTANCE.general.lootScaling = true;
        Mob cow = SelfTest.spawnMob(level, EntityTypes.COW, at);
        if (cow != null) {
            double[] sums = new double[3];
            float[] sizes = {0.5F, 1.0F, 8.0F};
            for (int s = 0; s < sizes.length; s++) {
                ((IResizeableLiving) cow).setBaseSize(sizes[s]);
                LootScaling.begin(cow);
                for (int i = 0; i < 400; i++) sums[s] += LootScaling.scaleDrop(cow, new ItemStack(Items.BEEF, 2)).getCount();
                LootScaling.end();
            }
            check(failures, Math.abs(sums[0] / 400.0 - 1.0) < 0.2, "half-size cow drops half the beef (avg " + sums[0] / 400.0 + ")");
            check(failures, sums[1] / 400.0 == 2.0, "normal cow drops its beef untouched");
            check(failures, sums[2] / 400.0 > 6.0 && sums[2] / 400.0 < 8.0, "size-8 cow drops about 3.5x (avg " + sums[2] / 400.0 + ")");
            if (a != null) {
                LootScaling.begin(cow);
                check(failures, LootScaling.scaleDrop(a, new ItemStack(Items.BEEF, 2)).getCount() == 2,
                        "only the dying mob's own spawns are scaled");
                LootScaling.end();
            }
            long xp = 0;
            for (int i = 0; i < 400; i++) xp += LootScaling.scaleExperience(cow, 10);
            check(failures, xp / 400.0 > 30.0 && xp / 400.0 < 40.0, "size-8 cow gives about 3.5x experience (avg " + xp / 400.0 + ")");
            GulliverConfig.INSTANCE.general.lootScaling = false;
            check(failures, LootScaling.scaleExperience(cow, 10) == 10, "loot scaling can be switched off");
            cow.discard();
        }
        GulliverConfig.INSTANCE.general.lootScaling = lootBefore;

        // Boss bars: only size 4+, only while the option is on, gone when it shrinks.
        boolean barsBefore = GulliverConfig.INSTANCE.general.giantBossBars;
        Mob giant = SelfTest.spawnMob(level, EntityTypes.ZOMBIE, at);
        if (giant != null) {
            GulliverConfig.INSTANCE.general.giantBossBars = true;
            ((IResizeableLiving) giant).setBaseSize(4.0F);
            check(failures, GiantBossBars.wants(giant), "a size-4 mob wants a boss bar");
            ServerBossEvent bar = GiantBossBars.update(giant, null);
            check(failures, bar != null, "the boss bar is created");
            ((IResizeableLiving) giant).setBaseSize(2.0F);
            check(failures, GiantBossBars.update(giant, bar) == null, "shrinking below 4 drops the bar");
            ((IResizeableLiving) giant).setBaseSize(8.0F);
            GulliverConfig.INSTANCE.general.giantBossBars = false;
            check(failures, !GiantBossBars.wants(giant), "no bar while the option is off");
            giant.discard();
        }
        GulliverConfig.INSTANCE.general.giantBossBars = barsBefore;

        //#if MC >= 1.21.10
        // A wind burst throws a tiny three times as hard as a normal body, from the same distance.
        Mob tiny = SelfTest.spawnMob(level, EntityTypes.ZOMBIE, at);
        Mob normal = SelfTest.spawnMob(level, EntityTypes.ZOMBIE, at);
        if (tiny != null && normal != null) {
            ((IResizeableLiving) tiny).setBaseSize(0.125F);
            double y = top + 30;
            tiny.setPos(7.5D, y, 8.5D);
            normal.setPos(9.5D, y, 8.5D);
            tiny.setDeltaMovement(0.0D, 0.0D, 0.0D);
            normal.setDeltaMovement(0.0D, 0.0D, 0.0D);
            level.explode(null, null, new SimpleExplosionDamageCalculator(false, false, Optional.empty(), Optional.empty()),
                    8.5D, y, 8.5D, 1.2F, false, Level.ExplosionInteraction.TRIGGER,
                    ParticleTypes.GUST_EMITTER_SMALL, ParticleTypes.GUST_EMITTER_LARGE,
                    WeightedList.of(), SoundEvents.WIND_CHARGE_BURST);
            double pushTiny = tiny.getDeltaMovement().length();
            double pushNormal = normal.getDeltaMovement().length();
            check(failures, pushNormal > 0.05D && pushTiny / pushNormal > 2.5D && pushTiny / pushNormal < 3.5D,
                    "wind burst: tiny pushed 3x a normal body (" + pushTiny + " vs " + pushNormal + ")");
            double size = ((IResizeableEntity) tiny).getSizeMultiplier();
            check(failures, size < 0.2D, "the tiny stayed tiny");
            tiny.discard();
            normal.discard();
        }
        //#endif
    }
}
