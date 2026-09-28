package gulliver.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import gulliver.Gulliver;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gulliver config — Gson POJO, single file at config/gulliver.json.
 *
 * Mirrors the 4 categories from the 1.6.4 GulliverConfigHelper exactly:
 *   [potion]      legacy resizing-potion ID slots (preserved for save-game compat reads,
 *                 even though 26.x potions aren't ID-based)
 *   [general]     dye-resizing toggle, karma-mode toggle, hard min/max clamps
 *   [spawn-size]  per-class default spawn sizes + per-entity-name overrides
 *                 (the 1.6.4 'base-size-spider', 'base-size-entity90' style — represented
 *                 as a flat overrides map keyed by lowercase entity name or 'entity{id}')
 *   [size-limit]  per-class min/max + per-entity-name min/max overrides
 *
 * All fields default to the same values the 1.6.4 mod's defaults (0.125/8.0/...);
 * spawn-size strings preserve the "1.0" / range / height-notation flexibility of
 * the original (parsed by GulliverEnvoy.getSizeFromRangeString in Phase 3(2)).
 */
public final class GulliverConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILENAME = "gulliver.json";

    public Potion potion = new Potion();
    public General general = new General();
    public SpawnSize spawnSize = new SpawnSize();
    public SizeLimit sizeLimit = new SizeLimit();
    public Client client = new Client();

    public static GulliverConfig INSTANCE = new GulliverConfig();

    public static void load() {
        Path file = gulliver.platform.Services.platform().configDir().resolve(FILENAME);
        if (Files.isRegularFile(file)) {
            try (Reader r = Files.newBufferedReader(file)) {
                GulliverConfig parsed = GSON.fromJson(r, GulliverConfig.class);
                if (parsed != null) {
                    INSTANCE = parsed;
                    INSTANCE.fillDefaults();
                }
            } catch (IOException | JsonSyntaxException e) {
                Gulliver.LOGGER.warn("Failed to read {}; using defaults", file, e);
            }
        }
        save();
    }

    public static void save() {
        Path dir = gulliver.platform.Services.platform().configDir();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            Gulliver.LOGGER.error("Failed to create config dir {}", dir, e);
            return;
        }
        Path file = dir.resolve(FILENAME);
        try (Writer w = Files.newBufferedWriter(file)) {
            GSON.toJson(INSTANCE, w);
        } catch (IOException e) {
            Gulliver.LOGGER.error("Failed to write {}", file, e);
        }
    }

    private void fillDefaults() {
        if (potion == null) potion = new Potion();
        if (general == null) general = new General();
        if (spawnSize == null) spawnSize = new SpawnSize();
        if (sizeLimit == null) sizeLimit = new SizeLimit();
        if (client == null) client = new Client();
        if (spawnSize.overrides == null) spawnSize.overrides = new LinkedHashMap<>();
        if (sizeLimit.minOverrides == null) sizeLimit.minOverrides = new LinkedHashMap<>();
        if (sizeLimit.maxOverrides == null) sizeLimit.maxOverrides = new LinkedHashMap<>();
    }

    public static final class Potion {
        public int tinyId = 26;
        public int hugeId = 27;
    }

    public static final class General {
        public boolean enableDyeResizing = true;
        public boolean enableKarmaMode = false;
        public double minEntitySize = 0.125D;
        public double maxEntitySize = 8.0D;
        public double minEntityBaseSize = 0.125D;
        public double maxEntityBaseSize = 8.0D;
        /** Tiny anglers' bobbers grab onto blocks; reeling in pulls you up (1.6.4). */
        public boolean fishingRodGrapple = true;
        /** Largest size whose fishing rod works as a grappling hook (tiny = below 0.3). */
        public double grappleMaxSize = 0.3D;
        /**
         * Anything walking over a creature smaller than this fraction of its
         * own size tramples it (1.6.4 "quite smaller"). 0 disables trampling.
         */
        public double trampleSizeRatio = 0.4D;
    }

    /** Client-only look and feel; ignored by dedicated servers. */
    public static final class Client {
        /**
         * How held items scale with your body:
         *  "classic"      - items grow/shrink by the square root of your size
         *                   (the 1.6.4 look: a tiny's sword looks big)
         *  "proportional" - items scale exactly with your body, so they stay
         *                   in proportion (matches tools worn on your back)
         */
        public String heldItemScaling = "classic";

        public boolean proportionalHeldItems() {
            return "proportional".equalsIgnoreCase(heldItemScaling);
        }
    }

    public static final class SpawnSize {
        public String basePlayerSize = "1.0";
        public String baseAnimalSize = "1.0";
        public String baseMonsterSize = "1.0";
        public String baseNpcSize = "1.0";
        public Map<String, String> overrides = new LinkedHashMap<>();
    }

    public static final class SizeLimit {
        public double minPlayerSize = 0.125D;
        public double maxPlayerSize = 8.0D;
        public double minAnimalSize = 0.125D;
        public double maxAnimalSize = 8.0D;
        public double minMonsterSize = 0.125D;
        public double maxMonsterSize = 8.0D;
        public double minNpcSize = 0.125D;
        public double maxNpcSize = 8.0D;
        public Map<String, Double> minOverrides = new LinkedHashMap<>();
        public Map<String, Double> maxOverrides = new LinkedHashMap<>();
    }
}
