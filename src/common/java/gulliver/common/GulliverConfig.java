package gulliver.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import gulliver.Gulliver;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
 *
 * Whatever the file says, {@link #validate()} makes it playable before it goes
 * live: every number finite and inside its range, min never above max, spawn
 * strings that parse. The size range itself is [general.minEntitySize,
 * general.maxEntitySize], which may open up to {@link #MIN_SIZE}..{@link #MAX_SIZE};
 * per-class and per-entity limits can only narrow it.
 */
public final class GulliverConfig {
    /** Smallest size the config may allow (1/32). */
    public static final float MIN_SIZE = 1.0F / 32.0F;
    /** Largest size the config may allow. */
    public static final float MAX_SIZE = 32.0F;
    private static final double DEFAULT_MIN = 0.125D;
    private static final double DEFAULT_MAX = 8.0D;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILENAME = "gulliver.json";

    public Potion potion = new Potion();
    public General general = new General();
    public SpawnSize spawnSize = new SpawnSize();
    public SizeLimit sizeLimit = new SizeLimit();
    public Client client = new Client();

    public static GulliverConfig INSTANCE = new GulliverConfig();

    /**
     * Bumped whenever the live config (or the limits the server announced)
     * changes. Entities cache their size limits against it, so the hot
     * getSizeMultiplier() never touches the config maps.
     */
    private static volatile int generation;
    /** How many values the last load/validation had to correct. */
    private static int lastFixes;

    public static int generation() {
        return generation;
    }

    public static synchronized void bump() {
        generation++;
    }

    public static int lastFixes() {
        return lastFixes;
    }

    /**
     * (Re)reads config/gulliver.json and makes it live. Returns null on
     * success, otherwise a short reason; then the previous settings stay in
     * force (defaults on the first load), the broken file is copied to
     * gulliver.json.broken-&lt;timestamp&gt; and the file itself is left alone.
     */
    public static String load() {
        Path file = gulliver.platform.Services.platform().configDir().resolve(FILENAME);
        if (Files.isRegularFile(file)) {
            try (Reader r = Files.newBufferedReader(file)) {
                GulliverConfig parsed = GSON.fromJson(r, GulliverConfig.class);
                if (parsed == null) parsed = new GulliverConfig();
                lastFixes = parsed.validate();
                INSTANCE = parsed;
                bump();
            } catch (IOException | RuntimeException e) {
                String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                Gulliver.LOGGER.error("Could not read {} ({}); keeping the previous settings and leaving the file as it is",
                        file, reason);
                backUp(file);
                return reason;
            }
        } else {
            lastFixes = 0;
            bump();
        }
        save();
        return null;
    }

    /** Copies an unreadable config next to itself, once per distinct content. */
    private static void backUp(Path file) {
        try {
            Path dir = file.getParent();
            try (DirectoryStream<Path> older = Files.newDirectoryStream(dir, FILENAME + ".broken-*")) {
                for (Path p : older) {
                    if (Files.mismatch(p, file) == -1L) return;
                }
            }
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT));
            Path backup = dir.resolve(FILENAME + ".broken-" + stamp);
            Files.copy(file, backup);
            Gulliver.LOGGER.error("The unreadable config was saved as {}", backup);
        } catch (IOException | RuntimeException e) {
            Gulliver.LOGGER.warn("Could not back up the unreadable config", e);
        }
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
        } catch (IOException | RuntimeException e) {
            Gulliver.LOGGER.error("Failed to write {}", file, e);
        }
    }

    /** Makes an edited copy (the settings screen's draft) the live config and writes it out. */
    public static void apply(GulliverConfig edited) {
        edited.validate();
        INSTANCE = edited;
        bump();
        save();
    }

    /** An independent copy, for editing without touching the live config. */
    public GulliverConfig copy() {
        GulliverConfig c = GSON.fromJson(GSON.toJson(this), GulliverConfig.class);
        c.fillDefaults();
        return c;
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

    // ---- validation ----

    /**
     * Brings every value into a range the game can run with and logs what it
     * changed. Returns the number of corrections.
     */
    public int validate() {
        fillDefaults();
        List<String> fixes = new ArrayList<>();
        General g = general;

        // The size range everything else lives inside.
        g.minEntitySize = sizeValue(g.minEntitySize, DEFAULT_MIN, MIN_SIZE, MAX_SIZE, "general.minEntitySize", fixes);
        g.maxEntitySize = sizeValue(g.maxEntitySize, DEFAULT_MAX, MIN_SIZE, MAX_SIZE, "general.maxEntitySize", fixes);
        if (g.maxEntitySize < g.minEntitySize) {
            fixes.add("general.maxEntitySize " + g.maxEntitySize + " is below minEntitySize; raised to " + g.minEntitySize);
            g.maxEntitySize = g.minEntitySize;
        }
        double lo = g.minEntitySize;
        double hi = g.maxEntitySize;
        g.minEntityBaseSize = sizeValue(g.minEntityBaseSize, lo, lo, hi, "general.minEntityBaseSize", fixes);
        g.maxEntityBaseSize = sizeValue(g.maxEntityBaseSize, hi, lo, hi, "general.maxEntityBaseSize", fixes);
        if (g.maxEntityBaseSize < g.minEntityBaseSize) {
            fixes.add("general.maxEntityBaseSize " + g.maxEntityBaseSize + " is below minEntityBaseSize; raised to " + g.minEntityBaseSize);
            g.maxEntityBaseSize = g.minEntityBaseSize;
        }
        g.grappleMaxSize = sizeValue(g.grappleMaxSize, 0.3D, MIN_SIZE, MAX_SIZE, "general.grappleMaxSize", fixes);
        g.trampleSizeRatio = ratioValue(g.trampleSizeRatio, 0.4D, "general.trampleSizeRatio", fixes);

        SizeLimit l = sizeLimit;
        l.minPlayerSize = limitValue(l.minPlayerSize, lo, hi, "sizeLimit.minPlayerSize", fixes);
        l.maxPlayerSize = limitValue(l.maxPlayerSize, lo, hi, "sizeLimit.maxPlayerSize", fixes);
        l.minAnimalSize = limitValue(l.minAnimalSize, lo, hi, "sizeLimit.minAnimalSize", fixes);
        l.maxAnimalSize = limitValue(l.maxAnimalSize, lo, hi, "sizeLimit.maxAnimalSize", fixes);
        l.minMonsterSize = limitValue(l.minMonsterSize, lo, hi, "sizeLimit.minMonsterSize", fixes);
        l.maxMonsterSize = limitValue(l.maxMonsterSize, lo, hi, "sizeLimit.maxMonsterSize", fixes);
        l.minNpcSize = limitValue(l.minNpcSize, lo, hi, "sizeLimit.minNpcSize", fixes);
        l.maxNpcSize = limitValue(l.maxNpcSize, lo, hi, "sizeLimit.maxNpcSize", fixes);
        l.maxPlayerSize = notBelow(l.maxPlayerSize, l.minPlayerSize, "sizeLimit.maxPlayerSize", fixes);
        l.maxAnimalSize = notBelow(l.maxAnimalSize, l.minAnimalSize, "sizeLimit.maxAnimalSize", fixes);
        l.maxMonsterSize = notBelow(l.maxMonsterSize, l.minMonsterSize, "sizeLimit.maxMonsterSize", fixes);
        l.maxNpcSize = notBelow(l.maxNpcSize, l.minNpcSize, "sizeLimit.maxNpcSize", fixes);
        l.minOverrides = limitMap(l.minOverrides, lo, hi, "sizeLimit.minOverrides", fixes);
        l.maxOverrides = limitMap(l.maxOverrides, lo, hi, "sizeLimit.maxOverrides", fixes);
        for (Map.Entry<String, Double> e : l.maxOverrides.entrySet()) {
            Double min = l.minOverrides.get(e.getKey());
            if (min != null && min > e.getValue() && e.getValue() != 0.0D) {
                fixes.add("sizeLimit.maxOverrides." + e.getKey() + " is below its minimum; raised to " + min);
                e.setValue(min);
            }
        }

        SpawnSize s = spawnSize;
        s.basePlayerSize = spawnString(s.basePlayerSize, true, "spawnSize.basePlayerSize", fixes);
        s.baseAnimalSize = spawnString(s.baseAnimalSize, false, "spawnSize.baseAnimalSize", fixes);
        s.baseMonsterSize = spawnString(s.baseMonsterSize, false, "spawnSize.baseMonsterSize", fixes);
        s.baseNpcSize = spawnString(s.baseNpcSize, false, "spawnSize.baseNpcSize", fixes);
        Map<String, String> overrides = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : s.overrides.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank()) continue;
            String key = normalizeKey(e.getKey());
            overrides.put(key, spawnString(e.getValue(), false, "spawnSize.overrides." + key, fixes));
        }
        s.overrides = overrides;

        Client c = client;
        if (c.heldItemScaling == null || !(c.heldItemScaling.equalsIgnoreCase("classic")
                || c.heldItemScaling.equalsIgnoreCase("proportional"))) {
            fixes.add("client.heldItemScaling must be classic or proportional; using classic");
            c.heldItemScaling = "classic";
        }
        c.screenShake = ratioValue(c.screenShake, 1.0D, "client.screenShake", fixes);

        for (String fix : fixes) Gulliver.LOGGER.warn("gulliver.json: {}", fix);
        return fixes.size();
    }

    /** A size: finite, inside [lo, hi]; the default when it is not a number. */
    private static double sizeValue(double v, double def, double lo, double hi, String name, List<String> fixes) {
        double fixed = v;
        if (Double.isNaN(v) || Double.isInfinite(v)) fixed = def;
        fixed = Math.max(lo, Math.min(hi, fixed));
        if (fixed != v) fixes.add(name + " " + v + " is out of range; using " + fixed);
        return fixed;
    }

    private static double ratioValue(double v, double def, String name, List<String> fixes) {
        double fixed = Double.isNaN(v) || Double.isInfinite(v) ? def : Math.max(0.0D, Math.min(1.0D, v));
        if (fixed != v) fixes.add(name + " " + v + " is out of range; using " + fixed);
        return fixed;
    }

    /** A per-class limit: 0 means "use the general one", anything else must sit inside the general range. */
    private static double limitValue(double v, double lo, double hi, String name, List<String> fixes) {
        if (v == 0.0D) return 0.0D;
        if (Double.isNaN(v) || Double.isInfinite(v) || v < 0.0D) {
            fixes.add(name + " " + v + " is not a size; using the general limit");
            return 0.0D;
        }
        double fixed = Math.max(lo, Math.min(hi, v));
        if (fixed != v) fixes.add(name + " " + v + " is outside the general range; using " + fixed);
        return fixed;
    }

    private static double notBelow(double max, double min, String name, List<String> fixes) {
        if (max != 0.0D && min != 0.0D && max < min) {
            fixes.add(name + " " + max + " is below its minimum; raised to " + min);
            return min;
        }
        return max;
    }

    private static Map<String, Double> limitMap(Map<String, Double> in, double lo, double hi, String name, List<String> fixes) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : in.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank() || e.getValue() == null) continue;
            String key = normalizeKey(e.getKey());
            out.put(key, limitValue(e.getValue(), lo, hi, name + "." + key, fixes));
        }
        return out;
    }

    /** Spawn-size strings that don't parse fall back to 1.0 (never an exception on an entity tick). */
    private static String spawnString(String v, boolean allowHeights, String name, List<String> fixes) {
        if (GulliverEnvoy.isValidSizeString(v, allowHeights)) return v.trim();
        fixes.add(name + " \"" + v + "\" is not a size, range or list; using 1.0");
        return "1.0";
    }

    /** Override keys: lowercase, trimmed; "zombie" or "othermod:zombie". */
    private static String normalizeKey(String key) {
        return key.trim().toLowerCase(Locale.ROOT);
    }

    // ---- sections ----

    public static final class Potion {
        public int tinyId = 26;
        public int hugeId = 27;
    }

    public static final class General {
        public boolean enableDyeResizing = true;
        public boolean enableKarmaMode = false;
        /**
         * The size range every creature lives inside (1/32 to 32 at most);
         * per-class and per-entity limits can only narrow it.
         */
        public double minEntitySize = 0.125D;
        public double maxEntitySize = 8.0D;
        /**
         * The range the base size (what the commands and keys set) may take;
         * potions multiply on top, up to the limits above. Keep these in step
         * with minEntitySize / maxEntitySize when widening the range.
         */
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
        /** Mob loot grows with the mob's size (a giant cow drops more beef). */
        public boolean lootScaling = true;
        /** Mobs at size 4 and above show a boss bar to nearby players. */
        public boolean giantBossBars = false;
        /** Tinies can ride small animals (chickens, cats, wolves, foxes, rabbits). */
        public boolean tinyMounts = true;
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
        /** Screen shake strength, 0 (off) to 1 (full). Also scaled by vanilla's Damage Tilt option. */
        public double screenShake = 1.0D;
        /** Animate size changes on screen (the hitbox still changes at once). */
        public boolean smoothResize = true;
        /** Show your new size on the action bar when it changes. */
        public boolean sizeReadout = true;

        public boolean proportionalHeldItems() {
            return "proportional".equalsIgnoreCase(heldItemScaling);
        }
    }

    public static final class SpawnSize {
        public String basePlayerSize = "1.0";
        public String baseAnimalSize = "1.0";
        public String baseMonsterSize = "1.0";
        public String baseNpcSize = "1.0";
        /** Keyed by entity id ("minecraft:zombie", "othermod:zombie"); a plain path ("zombie") still works. */
        public Map<String, String> overrides = new LinkedHashMap<>();
    }

    public static final class SizeLimit {
        /** 0 in any of these means "use general.minEntitySize / maxEntitySize". */
        public double minPlayerSize = 0.125D;
        public double maxPlayerSize = 8.0D;
        public double minAnimalSize = 0.125D;
        public double maxAnimalSize = 8.0D;
        public double minMonsterSize = 0.125D;
        public double maxMonsterSize = 8.0D;
        public double minNpcSize = 0.125D;
        public double maxNpcSize = 8.0D;
        /** Keyed by entity id ("minecraft:zombie", "othermod:zombie"); a plain path ("zombie") still works. */
        public Map<String, Double> minOverrides = new LinkedHashMap<>();
        public Map<String, Double> maxOverrides = new LinkedHashMap<>();
    }
}
