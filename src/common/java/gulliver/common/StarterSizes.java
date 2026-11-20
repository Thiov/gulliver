package gulliver.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import gulliver.Gulliver;
import gulliver.api.IResizeableEntity;
import gulliver.api.IResizeableLiving;
import gulliver.network.Payloads;
import gulliver.platform.Services;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Starter sizes (#32): the first time a player joins a world (a new one or
 * one they played before Gulliver asked), they get a screen to pick the
 * size they start at from the sizes the config offers (Tiny 1/8, Normal 1,
 * Giant 8 by default), once, without being an operator or having cheats.
 *
 * Who has picked is kept per world in gulliver_starter_sizes.json next to
 * level.dat, so it survives deaths and isn't lost with a player file, and
 * an operator can let a player pick again (/gulliver starter reset). A
 * player who closes the screen (when allowLater) is asked again on their
 * next join, or opens it themself with /gulliver starter. Where the
 * config allows re-picking (starterSize.allowRepick) and the world's game
 * rule gulliver:starter_repick does too, /gulliver starter opens it again
 * for players who have picked, to change their size.
 *
 * The server never trusts the size a client sends: it must be one of the
 * offered sizes, or inside the custom range when that is on, and it is
 * clamped to the player limits like any other resize. Choosing is a spawn,
 * not growth: nothing around bursts, a giant without room is moved to
 * where it fits (BodyFit), and it starts with the hearts its size gives.
 */
public final class StarterSizes {
    private StarterSizes() {}

    private static final String FILENAME = "gulliver_starter_sizes.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** Choice names the client translates (gulliver.starter.choice.NAME); others are shown as written. */
    public static final Set<String> KNOWN_NAMES = Set.of("tiny", "small", "normal", "big", "giant", "huge", "colossal");

    /** uuid -> the size picked, for the world the server below runs. */
    private static Map<String, Double> chosen = new LinkedHashMap<>();
    private static MinecraftServer loadedFor;

    // ---- events ----

    /** A player joined (after the server's settings were sent). */
    public static void onJoin(ServerPlayer player) {
        if (shouldAsk(player)) offer(player);
    }

    /** Whether this player is still to pick a size. */
    public static boolean shouldAsk(ServerPlayer player) {
        GulliverConfig.StarterSize st = GulliverConfig.INSTANCE.starterSize;
        return st.enabled && hasOptions(st) && !hasChosen(player);
    }

    private static boolean hasOptions(GulliverConfig.StarterSize st) {
        return !st.choices.isEmpty() || st.allowCustom;
    }

    /** Whether this player, having picked, may pick again (config and the world's game rule). */
    public static boolean canRepick(ServerPlayer player) {
        GulliverConfig.StarterSize st = GulliverConfig.INSTANCE.starterSize;
        return st.enabled && st.allowRepick && hasOptions(st)
                && gulliver.init.GulliverGameRules.starterRepick((net.minecraft.server.level.ServerLevel) player.level());
    }

    public static boolean hasChosen(ServerPlayer player) {
        return records(player.level().getServer()).containsKey(key(player));
    }

    /** Opens the picker on the player's screen. */
    public static void offer(ServerPlayer player) {
        offer(player, false);
    }

    /** Opens the picker; {@code repick}: to change a size already picked. */
    public static void offer(ServerPlayer player, boolean repick) {
        GulliverConfig cfg = GulliverConfig.INSTANCE;
        GulliverConfig.StarterSize st = cfg.starterSize;
        List<String> names = new ArrayList<>();
        List<Float> sizes = new ArrayList<>();
        for (GulliverConfig.StarterChoice c : st.choices) {
            names.add(c.name == null ? "" : c.name);
            sizes.add((float) c.size);
        }
        Services.platform().sendToPlayer(player, new Payloads.StarterSizeOffer(names, sizes, st.allowCustom,
                (float) cfg.customMin(), (float) cfg.customMax(), st.allowLater,
                ((IResizeableEntity) player).getSizeMultiplier(), repick));
    }

    /** The player's answer from the picker. */
    public static void onChoice(ServerPlayer player, float requested, boolean later) {
        GulliverConfig.StarterSize st = GulliverConfig.INSTANCE.starterSize;
        if (!st.enabled) {
            player.sendSystemMessage(Component.translatable("gulliver.starter.disabled"));
            return;
        }
        boolean repick = hasChosen(player);
        if (repick) {
            // Closing the picker when changing: nothing changes, nothing to say.
            if (later) return;
            if (!canRepick(player)) {
                player.sendSystemMessage(Component.translatable("gulliver.starter.already"));
                return;
            }
        }
        if (later) {
            if (st.allowLater) {
                player.sendSystemMessage(Component.translatable("gulliver.starter.later"));
            } else {
                offer(player); // has to pick: the screen comes back
            }
            return;
        }
        float size = resolve(GulliverConfig.INSTANCE, requested);
        if (Float.isNaN(size)) {
            // Stale screen after a config reload, or a forged packet: show what is on offer now.
            offer(player, repick);
            return;
        }
        apply(player, size);
        record(player, ((IResizeableEntity) player).getSizeMultiplier());
        Gulliver.LOGGER.info("{} picked starter size {}", player.getName().getString(), size);
        player.sendSystemMessage(Component.translatable(repick ? "gulliver.starter.changed" : "gulliver.starter.chosen",
                displayName(st, size), format(((IResizeableEntity) player).getSizeMultiplier())));
    }

    /** Lets a player pick again; asks at once when they are online. Returns whether they had picked. */
    public static boolean reset(MinecraftServer server, ServerPlayer player) {
        boolean had = records(server).remove(key(player)) != null;
        if (had) save(server);
        if (shouldAsk(player)) offer(player);
        return had;
    }

    // ---- choosing ----

    /**
     * The size a request stands for: one of the offered sizes (matched
     * loosely, floats travel), or a custom size inside the custom range;
     * NaN when it is neither.
     */
    public static float resolve(GulliverConfig cfg, float requested) {
        GulliverConfig.StarterSize st = cfg.starterSize;
        if (!(requested > 0.0F) || Float.isInfinite(requested)) return Float.NaN;
        for (GulliverConfig.StarterChoice c : st.choices) {
            if (Math.abs(c.size - requested) <= c.size * 1.0E-3D) return (float) c.size;
        }
        double lo = cfg.customMin();
        double hi = cfg.customMax();
        if (st.allowCustom && requested >= lo * (1.0D - 1.0E-3D) && requested <= hi * (1.0D + 1.0E-3D)) {
            return (float) Math.max(lo, Math.min(hi, requested));
        }
        return Float.NaN;
    }

    private static void apply(ServerPlayer player, float size) {
        LivingEntityHealth before = new LivingEntityHealth(player.getHealth(), player.getMaxHealth());
        // Starting at a size is a spawn, not growth: no bursting through the spawn area.
        GulliverEnvoy.withoutGrowthBurst(() -> ((IResizeableLiving) player).setBaseSize(size));
        // A giant starts with the hearts its size gives it, as a spawned giant does.
        if (before.health >= before.max - 1.0E-3F) player.setHealth(player.getMaxHealth());
        // Spawned where a normal body fits: find a giant room, or shrink it till it fits.
        BodyFit.settle(player);
    }

    private record LivingEntityHealth(float health, float max) {}

    /** The name a choice is shown with (the client translates known names; chat does the same). */
    public static Component displayName(GulliverConfig.StarterSize st, float size) {
        for (GulliverConfig.StarterChoice c : st.choices) {
            if (Math.abs(c.size - size) <= c.size * 1.0E-3D) return choiceName(c.name, size);
        }
        return Component.translatable("gulliver.starter.custom");
    }

    public static Component choiceName(String name, float size) {
        if (name == null || name.isBlank()) return Component.literal(format(size) + "×");
        String key = name.trim().toLowerCase(Locale.ROOT);
        if (KNOWN_NAMES.contains(key)) return Component.translatable("gulliver.starter.choice." + key);
        return Component.literal(name.trim());
    }

    /** Up to 3 decimals, trailing zeros dropped: 0.125, 1, 8. */
    public static String format(double v) {
        String s = String.format(Locale.ROOT, "%.3f", v).replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    // ---- storage ----

    private static String key(ServerPlayer player) {
        return player.getUUID().toString();
    }

    private static void record(ServerPlayer player, float size) {
        MinecraftServer server = player.level().getServer();
        records(server).put(key(player), (double) size);
        save(server);
    }

    private static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(FILENAME);
    }

    private static Map<String, Double> records(MinecraftServer server) {
        if (server != loadedFor) {
            loadedFor = server;
            chosen = load(server);
        }
        return chosen;
    }

    private static Map<String, Double> load(MinecraftServer server) {
        Path path = file(server);
        if (!Files.isRegularFile(path)) return new LinkedHashMap<>();
        try (Reader r = Files.newBufferedReader(path)) {
            Map<String, Double> read = GSON.fromJson(r, new TypeToken<LinkedHashMap<String, Double>>() {}.getType());
            return read == null ? new LinkedHashMap<>() : new LinkedHashMap<>(read);
        } catch (IOException | RuntimeException e) {
            // Unreadable: nobody counts as having picked, which only means being asked again.
            Gulliver.LOGGER.error("Could not read {}; players will be asked for a starter size again", path, e);
            return new LinkedHashMap<>();
        }
    }

    private static void save(MinecraftServer server) {
        Path path = file(server);
        try {
            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path)) {
                GSON.toJson(chosen, w);
            }
        } catch (IOException | RuntimeException e) {
            Gulliver.LOGGER.error("Could not write {}", path, e);
        }
    }
}
