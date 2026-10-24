package gulliver.debug;

import gulliver.access.IGulliverEntityInternal;
import gulliver.api.IResizeableEntity;
import gulliver.api.IResizeableLiving;
import gulliver.common.GulliverConfig;
import gulliver.common.GulliverEnvoy;
import gulliver.init.GulliverEffects;
import gulliver.network.Payloads;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
//#if MC >= 1.21.2
import net.minecraft.world.entity.ConversionParams;
//#endif

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Size range, limits, validation, conversion and entity lookup checks of the 1.2 core work; run by SelfTest. */
final class SelfTestCore {
    private SelfTestCore() {}

    private static void check(List<String> failures, boolean ok, String what) {
        if (!ok) failures.add(what);
        gulliver.Gulliver.LOGGER.info("selftest {} {}", ok ? "ok  " : "FAIL", what);
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1.0E-3F * Math.max(1.0F, Math.abs(b));
    }

    static void run(ServerLevel level, List<String> failures) {
        BlockPos at = new BlockPos(24, 0, 24);
        GulliverConfig saved = GulliverConfig.INSTANCE;
        try {
            wideRange(level, failures, at);
            limitsApplyToPotions(level, failures, at);
            overrides(level, failures, at);
            classification(level, failures, at);
            conversion(level, failures, at);
            validation(failures);
            payload(failures);
            brokenFile(failures);
        } finally {
            GulliverConfig.INSTANCE = saved;
            GulliverConfig.bump();
        }
    }

    /** Resizing without bursting the test world's blocks. */
    private static void resize(Mob mob, float size) {
        GulliverEnvoy.withoutGrowthBurst(() -> ((IResizeableLiving) mob).setBaseSize(size));
    }

    private static GulliverConfig install(GulliverConfig cfg) {
        cfg.validate();
        GulliverConfig.INSTANCE = cfg;
        GulliverConfig.bump();
        return cfg;
    }

    /** The absolute range opens up to 1/32..32; giants are found by lookups at their head. */
    private static void wideRange(ServerLevel level, List<String> failures, BlockPos at) {
        GulliverConfig cfg = GulliverConfig.INSTANCE.copy();
        cfg.general.minEntitySize = 1.0D / 32;
        cfg.general.maxEntitySize = 32.0D;
        cfg.general.minEntityBaseSize = 1.0D / 32;
        cfg.general.maxEntityBaseSize = 32.0D;
        cfg.sizeLimit.minAnimalSize = 0.0D;
        cfg.sizeLimit.maxAnimalSize = 0.0D;
        install(cfg);

        Mob cow = SelfTest.spawnMob(level, EntityTypes.COW, at);
        if (cow == null) {
            check(failures, false, "spawn cow for the wide range");
            return;
        }
        float height = cow.getBbHeight();
        resize(cow, 20.0F);
        check(failures, near(((IResizeableEntity) cow).getSizeMultiplier(), 20.0F), "size 20 allowed once the range is widened");
        check(failures, near(cow.getBbHeight(), height * 20.0F), "cow height x20");
        resize(cow, 1.0F / 32);
        check(failures, near(((IResizeableEntity) cow).getSizeMultiplier(), 1.0F / 32), "size 1/32 allowed");
        resize(cow, 100.0F);
        check(failures, near(((IResizeableEntity) cow).getSizeMultiplier(), 32.0F), "size clamped to the widened maximum");
        check(failures, near(((IGulliverEntityInternal) cow).gulliver$getEffectiveDestSize(), 32.0F),
                "the synced size is the effective size");

        // Entity lookups reach a giant's head, far above the section its feet are in.
        GulliverEnvoy.withoutGrowthBurst(cow::tick);
        double x = cow.getX();
        double z = cow.getZ();
        AABB chest = new AABB(x - 0.5D, cow.getY() + 40.0D, z - 0.5D, x + 0.5D, cow.getY() + 41.0D, z + 0.5D);
        List<Entity> found = level.getEntities((Entity) null, chest);
        check(failures, found.contains(cow), "a lookup at a size-32 giant's head finds it");
        cow.discard();
    }

    /** Potions respect the limits (M1) and the commands report the limited size. */
    private static void limitsApplyToPotions(ServerLevel level, List<String> failures, BlockPos at) {
        GulliverConfig cfg = GulliverConfig.INSTANCE.copy();
        cfg.sizeLimit.minAnimalSize = 0.0D;
        cfg.sizeLimit.maxAnimalSize = 2.0D;
        install(cfg);
        Mob cow = SelfTest.spawnMob(level, EntityTypes.COW, at);
        if (cow == null) return;
        cow.addEffect(new MobEffectInstance(GulliverEffects.huge(), 200, 1));
        GulliverEnvoy.withoutGrowthBurst(() -> {
            for (int i = 0; i < 3; i++) cow.tick();
        });
        check(failures, near(((IGulliverEntityInternal) cow).gulliver$getSizePotionMultiplier(), 8.0F),
                "strong huge potion multiplier is stored raw");
        check(failures, near(((IResizeableEntity) cow).getSizeMultiplier(), 2.0F),
                "a strong huge potion stops at the class limit (got " + ((IResizeableEntity) cow).getSizeMultiplier() + ")");
        resize(cow, 8.0F);
        check(failures, near(((IResizeableEntity) cow).getSizeMultiplier(), 2.0F), "base size and potion together stay at the limit");
        cow.discard();
    }

    /** Per-entity overrides by full id, and by plain path as older configs wrote them. */
    private static void overrides(ServerLevel level, List<String> failures, BlockPos at) {
        GulliverConfig cfg = GulliverConfig.INSTANCE.copy();
        cfg.sizeLimit.maxOverrides.put("Minecraft:Pig", 1.5D);
        install(cfg);
        Mob pig = SelfTest.spawnMob(level, EntityTypes.PIG, at);
        if (pig == null) return;
        resize(pig, 8.0F);
        check(failures, near(((IResizeableEntity) pig).getSizeMultiplier(), 1.5F), "override by full id (any case)");
        pig.discard();

        cfg = GulliverConfig.INSTANCE.copy();
        cfg.sizeLimit.maxOverrides.clear();
        cfg.sizeLimit.maxOverrides.put("pig", 1.25D);
        install(cfg);
        pig = SelfTest.spawnMob(level, EntityTypes.PIG, at);
        if (pig == null) return;
        resize(pig, 8.0F);
        check(failures, near(((IResizeableEntity) pig).getSizeMultiplier(), 1.25F), "override by plain path still works");
        pig.discard();
    }

    /** Slimes and the like are monsters for the config brackets. */
    private static void classification(ServerLevel level, List<String> failures, BlockPos at) {
        install(GulliverConfig.INSTANCE.copy());
        Mob slime = SelfTest.spawnMob(level, EntityTypes.SLIME, at);
        if (slime == null) return;
        check(failures, GulliverEnvoy.isMonster(slime) && !GulliverEnvoy.isAnimal(slime), "a slime uses the monster settings");
        slime.discard();
    }

    /** A converted mob keeps its size (M4). */
    private static void conversion(ServerLevel level, List<String> failures, BlockPos at) {
        install(GulliverConfig.INSTANCE.copy());
        Mob zombie = SelfTest.spawnMob(level, EntityTypes.ZOMBIE, at);
        if (zombie == null) return;
        resize(zombie, 3.0F);
        //#if MC >= 1.21.2
        Mob drowned = zombie.convertTo(EntityTypes.DROWNED, ConversionParams.single(zombie, true, true), d -> { });
        //#else
        //$$ Mob drowned = zombie.convertTo(EntityTypes.DROWNED, true);
        //#endif
        check(failures, drowned != null, "zombie converts to a drowned");
        if (drowned != null) {
            check(failures, near(((IResizeableEntity) drowned).getSizeMultiplier(), 3.0F),
                    "the drowned keeps the zombie's size (got " + ((IResizeableEntity) drowned).getSizeMultiplier() + ")");
            drowned.discard();
        }
        zombie.discard();
    }

    /** Hostile config values and spawn strings never get through (D7, D11). */
    private static void validation(List<String> failures) {
        GulliverConfig bad = new GulliverConfig();
        bad.general.maxEntitySize = Double.NaN;
        bad.general.minEntitySize = 100.0D;
        bad.general.trampleSizeRatio = Double.POSITIVE_INFINITY;
        bad.sizeLimit.maxPlayerSize = Double.POSITIVE_INFINITY;
        bad.sizeLimit.minPlayerSize = -3.0D;
        bad.sizeLimit.maxOverrides.put(" Minecraft:Cow ", 400.0D);
        bad.spawnSize.baseAnimalSize = ",";
        bad.spawnSize.basePlayerSize = "-";
        bad.spawnSize.overrides.put("zombie", "x-y");
        int fixes = bad.validate();
        GulliverConfig.General g = bad.general;
        check(failures, fixes > 0, "validation reports its corrections");
        check(failures, g.minEntitySize >= GulliverConfig.MIN_SIZE && g.maxEntitySize <= GulliverConfig.MAX_SIZE
                && g.minEntitySize <= g.maxEntitySize, "general range finite, inside 1/32..32 and ordered");
        check(failures, g.trampleSizeRatio >= 0.0D && g.trampleSizeRatio <= 1.0D, "ratios stay in range");
        check(failures, bad.sizeLimit.maxPlayerSize == 0.0D || bad.sizeLimit.maxPlayerSize <= g.maxEntitySize,
                "class limits stay inside the general range");
        check(failures, bad.sizeLimit.maxOverrides.containsKey("minecraft:cow")
                && bad.sizeLimit.maxOverrides.get("minecraft:cow") <= g.maxEntitySize, "override keys normalised and limited");
        check(failures, "1.0".equals(bad.spawnSize.baseAnimalSize) && "1.0".equals(bad.spawnSize.basePlayerSize)
                && "1.0".equals(bad.spawnSize.overrides.get("zombie")), "unparseable spawn strings fall back to 1.0");

        check(failures, GulliverEnvoy.getSizeFromRangeString(",", false) == 1.0F, "a lone comma is no size");
        check(failures, GulliverEnvoy.getSizeFromRangeString("-", false) == 1.0F, "a lone dash is no size");
        boolean always = true;
        for (int i = 0; i < 400; i++) {
            float roll = GulliverEnvoy.getSizeFromRangeString("0-2", false);
            if (!(roll > 0.0F && roll <= 2.0F)) always = false;
        }
        check(failures, always, "a range starting at 0 never rolls 0");
        check(failures, GulliverEnvoy.isValidSizeString("0-2", false), "a range may start at 0");
    }

    /** The settings packet survives its own wire format. */
    private static void payload(List<String> failures) {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        new Payloads.ServerSettings(false, 1.0F / 32, 32.0F).write(buf);
        Payloads.ServerSettings back = Payloads.ServerSettings.read(buf);
        check(failures, !back.dyeResizing() && back.minSize() == 1.0F / 32 && back.maxSize() == 32.0F,
                "server settings packet round-trips");
        buf.release();
    }

    /** A config that can't be read is backed up and left alone, and the previous settings stay (D3). */
    private static void brokenFile(List<String> failures) {
        Path dir = gulliver.platform.Services.platform().configDir();
        Path file = dir.resolve("gulliver.json");
        String broken = "{ \"general\": { \"maxEntitySize\": \"abc\" } }";
        GulliverConfig before = GulliverConfig.INSTANCE;
        try {
            String original = Files.isRegularFile(file) ? Files.readString(file) : null;
            Files.writeString(file, broken);
            String error = GulliverConfig.load();
            check(failures, error != null, "an unreadable config is reported");
            check(failures, GulliverConfig.INSTANCE == before, "the previous settings stay in force");
            check(failures, broken.equals(Files.readString(file)), "the unreadable file is left as it is");
            boolean backup = false;
            try (DirectoryStream<Path> copies = Files.newDirectoryStream(dir, "gulliver.json.broken-*")) {
                for (Path copy : copies) {
                    backup = true;
                    Files.delete(copy);
                }
            }
            check(failures, backup, "the unreadable file is backed up");
            if (original != null) Files.writeString(file, original);
            else Files.delete(file);
        } catch (IOException e) {
            check(failures, false, "broken config test: " + e);
        }
    }
}
