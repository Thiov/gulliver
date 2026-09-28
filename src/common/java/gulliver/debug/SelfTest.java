package gulliver.debug;

import gulliver.Gulliver;
import gulliver.access.IGulliverEntityInternal;
import gulliver.api.IResizeableEntity;
import gulliver.api.IResizeableLiving;
import gulliver.init.GulliverEffects;
import gulliver.init.GulliverGameRules;
import gulliver.init.GulliverPotions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.util.ArrayList;
import java.util.List;

/**
 * Development self-test, enabled only with -Dgulliver.selftest=true.
 * Runs once on the (dedicated or integrated) server a few seconds after
 * start: forces every mixin to apply, exercises resizing, potions,
 * brewing, commands and the game rule, logs one PASS/FAIL line and, on a
 * dedicated server, stops it. The client half lives in ClientSelfTest.
 */
public final class SelfTest {
    private SelfTest() {}

    public static final boolean ENABLED = Boolean.getBoolean("gulliver.selftest");
    private static boolean ran;

    public static void onServerTick(ServerLevel level) {
        if (!ENABLED || ran) return;
        MinecraftServer server = level.getServer();
        if (level != server.overworld() || server.getTickCount() < 60) return;
        ran = true;
        List<String> failures = new ArrayList<>();
        try {
            run(level, failures);
        } catch (Throwable t) {
            Gulliver.LOGGER.error("Gulliver self-test crashed", t);
            failures.add("crash: " + t);
        }
        if (failures.isEmpty()) {
            Gulliver.LOGGER.info("GULLIVER SELFTEST SERVER PASS");
        } else {
            Gulliver.LOGGER.error("GULLIVER SELFTEST SERVER FAIL {}", failures);
        }
        if (server.isDedicatedServer()) server.halt(false);
    }

    private static void check(List<String> failures, boolean ok, String what) {
        if (!ok) failures.add(what);
        Gulliver.LOGGER.info("selftest {} {}", ok ? "ok  " : "FAIL", what);
    }

    private static void run(ServerLevel level, List<String> failures) {
        // 1. Every mixin (client ones only exist on the client) must apply.
        MixinEnvironment.getCurrentEnvironment().audit();
        check(failures, true, "mixin audit");

        // 2. Registries.
        check(failures, BuiltInRegistries.MOB_EFFECT.getKey(GulliverEffects.TINY) != null, "tiny effect registered");
        check(failures, BuiltInRegistries.POTION.getKey(GulliverPotions.TINY) != null, "tiny potion registered");
        //#if MC >= 1.20.5
        check(failures, level.getServer().potionBrewing().isBrewablePotion(GulliverPotions.holder(GulliverPotions.TINY)),
                "tiny potion brewable");
        //#endif
        check(failures, GulliverGameRules.sizeGriefing(level), "size_griefing defaults to true");

        // 3. Resize a mob and check dimensions + attributes follow.
        BlockPos spawn = BlockPos.ZERO;
        Mob zombie = spawnMob(level, EntityType.ZOMBIE, spawn);
        check(failures, zombie != null, "spawn zombie");
        if (zombie != null) {
            float w = zombie.getBbWidth();
            float h = zombie.getBbHeight();
            ((IResizeableLiving) zombie).setBaseSize(4.0F);
            check(failures, Math.abs(zombie.getBbHeight() - h * 4.0F) < 1.0E-3F, "zombie height x4");
            check(failures, Math.abs(zombie.getBbWidth() - w * 4.0F) < 1.0E-3F, "zombie width x4");
            check(failures, zombie.getMaxHealth() > 79.0F, "giant zombie max health scaled");
            ((IResizeableLiving) zombie).setBaseSize(0.25F);
            check(failures, Math.abs(zombie.getBbHeight() - h * 0.25F) < 1.0E-3F, "zombie height x0.25");
            check(failures, ((IResizeableEntity) zombie).isTiny(), "0.25 counts as tiny");
            ((IResizeableLiving) zombie).setBaseSize(100.0F);
            check(failures, ((IResizeableEntity) zombie).getSizeMultiplier() <= 8.0F, "size clamped to max");
            zombie.discard();
        }

        // 4. The resizing effect drives the potion multiplier.
        Mob cow = spawnMob(level, EntityType.COW, spawn);
        if (cow != null) {
            cow.addEffect(new MobEffectInstance(GulliverEffects.tiny(), 100, 0));
            for (int i = 0; i < 3; i++) cow.tick();
            float potion = ((IGulliverEntityInternal) cow).gulliver$getSizePotionMultiplier();
            check(failures, Math.abs(potion - 0.25F) < 1.0E-3F, "tiny effect -> potion multiplier 0.25 (got " + potion + ")");
            cow.removeAllEffects();
            check(failures, ((IGulliverEntityInternal) cow).gulliver$getSizePotionMultiplier() == 1.0F,
                    "effect removal resets potion multiplier");
            cow.discard();
        }

        // 5. Commands parse and run.
        Mob pig = spawnMob(level, EntityType.PIG, spawn);
        if (pig != null) {
            runCommand(level, "entitybasesize " + pig.getId() + " 2");
            check(failures, Math.abs(((IResizeableEntity) pig).getSizeMultiplier() - 2.0F) < 1.0E-3F,
                    "/entitybasesize resizes");
            runCommand(level, "entityhalfsize " + pig.getId());
            check(failures, Math.abs(((IResizeableEntity) pig).getSizeMultiplier() - 1.0F) < 1.0E-3F,
                    "/entityhalfsize halves");
            pig.discard();
        }

        SelfTestExtras.run(level, failures);
    }

    public static Mob spawnMob(ServerLevel level, EntityType<? extends Mob> type, BlockPos at) {
        //#if MC >= 1.21.2
        Mob mob = type.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        //#else
        //$$ Mob mob = type.create(level);
        //#endif
        if (mob == null) return null;
        mob.setPos(at.getX() + 0.5D, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                at.getX(), at.getZ()), at.getZ() + 0.5D);
        level.addFreshEntity(mob);
        return mob;
    }

    public static void runCommand(ServerLevel level, String command) {
        MinecraftServer server = level.getServer();
        //#if MC >= 1.20.3
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
        //#else
        //$$ server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
        //#endif
    }
}
