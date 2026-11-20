package gulliver.client;

import gulliver.Gulliver;
import gulliver.api.IResizeableLiving;
import gulliver.common.GulliverConfig;
import gulliver.debug.SelfTest;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Scripted tours for filming. Run the self-test client with one of these
 * files in the run folder; it plays the scenes (logging "SCENE" lines with
 * their times, for cutting the film) and quits:
 *  - "gulliver-showcase-fixes": tiny creatures staying in view (#26) and
 *    items restricted by size (#30);
 *  - "gulliver-showcase-new": the starter size picker (#32), the breath
 *    gust putting out fires (#34) and Cinematic Interactions stations at
 *    giant and tiny size (#33, needs that mod in run/mods);
 *  - "gulliver-showcase-blocks": small blocks solid for tinies (#19):
 *    walking over buttons and plates, standing on a lever and a sign on a
 *    wall, and pressure plates springing tinies into the air. Keys are
 * pressed by setting their key mappings, as the keyboard would, and a
 * smoothed film camera (a client-side marker the view is taken from) follows
 * or circles the player. With a "gulliver-showcase-slow" file as well the
 * game runs slowed down (/tick rate 5, or the rate written in the file), for a slow renderer to film
 * smoothly; speeding the film up by 20 / rate gives the real pace.
 */
public final class ClientShowcaseFilm {
    private ClientShowcaseFilm() {}

    /** Draws tiny creatures with vanilla's distance rule, for the "before" shot of #26. */
    public static boolean vanillaCulling;

    private static Boolean enabled;
    private static boolean blocksTour;
    private static boolean newTour;
    /** The starter size picker scene is on: the offer it asks for must not be dismissed. */
    private static boolean picker;

    public static boolean pickerScene() {
        return picker;
    }
    private static int t;
    private static final List<Step> STEPS = new ArrayList<>();
    private static int end;

    private record Step(int from, int to, IntConsumer action) {}

    public static boolean active(Minecraft mc) {
        if (enabled == null) {
            blocksTour = java.nio.file.Files.exists(mc.gameDirectory.toPath().resolve("gulliver-showcase-blocks"));
            newTour = java.nio.file.Files.exists(mc.gameDirectory.toPath().resolve("gulliver-showcase-new"));
            enabled = blocksTour || newTour
                    || java.nio.file.Files.exists(mc.gameDirectory.toPath().resolve("gulliver-showcase-fixes"));
        }
        return enabled;
    }

    private static void at(int tick, Runnable r) {
        STEPS.add(new Step(tick, tick, i -> r.run()));
        end = Math.max(end, tick);
    }

    /** Every tick from..to; gets the tick counted from {@code from}. */
    private static void during(int from, int to, IntConsumer r) {
        STEPS.add(new Step(from, to, r));
        end = Math.max(end, to);
    }

    /** Every client tick in the world; returns true once the tour is over. */
    public static boolean tick(Minecraft mc) {
        if (STEPS.isEmpty()) script(mc);
        t++;
        for (Step s : new ArrayList<>(STEPS)) {
            if (t >= s.from() && t <= s.to()) s.action().accept(t - s.from());
        }
        film(mc);
        if (t % 100 == 0) {
            LocalPlayer p = mc.player;
            log("showcase t={} fps={} y={} yaw={} head={} body={}", t, mc.getFps(),
                    p == null ? 0 : Math.round(p.getY() * 100) / 100.0, p == null ? 0 : p.getYRot(),
                    p == null ? 0 : p.yHeadRot, p == null ? 0 : p.yBodyRot);
        }
        boolean over = t > end + 40;
        if (over) camOff(mc);
        return over;
    }

    private static void script(Minecraft mc) {
        LocalPlayer p = mc.player;
        java.nio.file.Path slowFile = mc.gameDirectory.toPath().resolve("gulliver-showcase-slow");
        boolean slow = java.nio.file.Files.exists(slowFile);
        int rate = 5;
        try {
            if (slow) rate = Math.max(1, Integer.parseInt(java.nio.file.Files.readString(slowFile).trim()));
        } catch (java.io.IOException | NumberFormatException e) {
            rate = 5;
        }
        int tickRate = rate;
        // ---- setup -------------------------------------------------------------------
        at(20, () -> {
            mark("start");
            if (slow) cmd(mc, "tick rate " + tickRate);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        });
        at(30, () -> applyConfig(mc));
        at(36, () -> {
            cmd(mc, "time set 6000");
            cmd(mc, "weather clear 99999");
            cmd(mc, "gamerule doDaylightCycle false");
            cmd(mc, "gamerule doWeatherCycle false");
            cmd(mc, "gamerule doMobSpawning false");
            cmd(mc, "gamerule announceAdvancements false");
            cmd(mc, "gamemode survival @a");
            cmd(mc, "recipe give @a *");
            cmd(mc, "advancement grant @a everything");
            cmd(mc, "effect give @a minecraft:resistance 99999 4 true");
            cmd(mc, "effect give @a minecraft:saturation 99999 0 true");
            cmd(mc, "clear @a");
            cmd(mc, "kill @e[type=!player]");
        });
        // A meadow: everything above the ground cleared well over the treetops, then flowers.
        for (int i = 0; i < 40; i++) {
            int y = i * 4;
            at(40 + i / 2, () -> cmd(mc, String.format(Locale.ROOT,
                    "execute as @a at @s run fill ~-40 ~%d ~-40 ~40 ~%d ~48 minecraft:air", y, y + 3)));
        }
        at(63, () -> {
            cmd(mc, "execute as @a at @s run fill ~-40 ~-4 ~-40 ~40 ~-2 ~48 minecraft:dirt");
            cmd(mc, "execute as @a at @s run fill ~-40 ~-1 ~-40 ~40 ~-1 ~48 minecraft:grass_block");
        });
        at(66, () -> meadow(mc));
        at(70, () -> {
            onServer(mc, sp -> {
                sp.setYRot(0.0F);
                sp.setXRot(0.0F);
            });
            cmd(mc, "kill @e[type=item]");
        });

        if (blocksTour) {
            // Later than the other tour: the server is still busy with its own self-test early on.
            blocks(mc, 200);
            return;
        }
        if (newTour) {
            String only = "";
            try {
                only = java.nio.file.Files.readString(mc.gameDirectory.toPath().resolve("gulliver-showcase-new")).trim();
            } catch (java.io.IOException e) {
                only = "";
            }
            // "addons" in the file: only the scenes of the mods in Cinematic Interactions' style.
            if (only.equals("addons")) {
                at(200, () -> applyConfig(mc));
                addons(mc, p, 220);
            } else if (only.equals("bag")) {
                at(200, () -> applyConfig(mc));
                int e = bagAtEverySize(mc, p, 220);
                at(e, () -> mark("end"));
            } else if (only.equals("craft")) {
                at(200, () -> applyConfig(mc));
                int e = craftAtTable(mc, p, 220);
                at(e, () -> mark("end"));
            } else if (only.equals("proper")) {
                // The mods in Cinematic Interactions' style used for real: crafting, cooking, cutting,
                // and Belongings' bag clicked with the mouse at every size.
                at(200, () -> applyConfig(mc));
                proper(mc, p, 220);
            } else {
                newFeatures(mc, 200);
            }
            return;
        }

        // ---- #26: tiny creatures stay in view -------------------------------------------
        int s1 = 90;
        at(s1, () -> {
            mark("render-distance");
            applyConfig(mc);
            onServer(mc, sp -> sp.teleportTo(arena.x, arena.y, arena.z + 18.0D));
            title(mc, "Fix #26", "Tiny creatures no longer vanish at a distance");
            look(p, 0.0F, 3.0F);
            cmd(mc, "item replace entity @a weapon.mainhand with minecraft:spyglass");
            String[] mobs = {"villager", "sheep", "villager", "pig", "villager", "chicken", "villager"};
            for (int i = 0; i < mobs.length; i++) {
                cmd(mc, String.format(Locale.ROOT,
                        "execute as @a at @s run summon %s ~%d ~ ~6 {NoAI:1b,Silent:1b,Tags:[\"gv_tiny\"],Rotation:[180f,0f]}",
                        mobs[i], i * 2 - 6));
            }
        });
        at(s1 + 4, () -> cmd(mc, "gulliver size @e[tag=gv_tiny] 0.25"));
        // Watch them through a spyglass while backing away.
        at(s1 + 40, () -> {
            key(mc.options.keyUse, true);
            if (mc.gameMode != null) mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
        });
        at(s1 + 50, () -> {
            vanillaCulling = true;
            title(mc, "Before", "vanilla culling: 1/4-size mobs vanish past ~16 blocks");
        });
        during(s1 + 70, s1 + 150, i -> back(p, -0.25D));
        at(s1 + 190, () -> {
            vanillaCulling = false;
            title(mc, "After", "drawn as far away as at normal size");
        });
        during(s1 + 230, s1 + 290, i -> back(p, 0.25D));
        during(s1 + 295, s1 + 360, i -> back(p, -0.25D));
        // Keep the spyglass on the row of mobs as the distance changes.
        during(s1 + 30, s1 + 380, i -> {
            double dz = arena.z + 24.0D - p.getZ();
            double dy = p.getEyeY() - (arena.y + 0.15D);
            look(p, 0.0F, (float) Math.toDegrees(Math.atan2(dy, Math.max(0.5D, dz))));
        });
        at(s1 + 380, () -> key(mc.options.keyUse, false));
        at(s1 + 400, () -> cmd(mc, "kill @e[tag=gv_tiny]"));

        // ---- #30: restricted items ------------------------------------------------------
        int s2 = s1 + 410;
        at(s2, () -> {
            mark("item-restrictions");
            applyConfig(mc);
            onServer(mc, sp -> sp.teleportTo(arena.x, arena.y, arena.z));
            look(p, 0.0F, 0.0F);
            p.yBodyRot = p.yBodyRotO = 0.0F;
            cmd(mc, "item replace entity @a weapon.mainhand with minecraft:diamond_sword");
            cmd(mc, "item replace entity @a armor.chest with minecraft:iron_chestplate");
            camOn(mc, 155.0F, 2.3F, 0.75F, 0.75F);
            title(mc, "#30", "Items restricted by size");
        });
        at(s2 + 60, () -> sub(mc, "Rule: no diamond swords or axes below size 0.5 (\"drop\", classic)"));
        at(s2 + 100, () -> size(mc, 0.25F));
        at(s2 + 180, () -> sub(mc, "Rule: iron chestplates only up to size 2 (\"break\", modern)"));
        at(s2 + 200, () -> size(mc, 1.0F));
        at(s2 + 240, () -> cmd(mc, "kill @e[type=item]"));
        at(s2 + 250, () -> size(mc, 3.0F));
        at(s2 + 350, () -> {
            size(mc, 1.0F);
            cmd(mc, "kill @e[type=item]");
            cmd(mc, "clear @a");
        });

        at(s2 + 400, () -> mark("end"));
    }

    // ---- #32, #34, #33 -------------------------------------------------------------------

    private static void newFeatures(Minecraft mc, int s) {
        LocalPlayer p = mc.player;

        // ---- #32: the starter size picker ----
        at(s, () -> {
            mark("starter-size");
            size(mc, 1.0F);
            onServer(mc, sp -> sp.teleportTo(arena.x, arena.y, arena.z));
            look(p, 0.0F, 0.0F);
            clearChat(mc);
            title(mc, "#32", "Pick your starting size on your first join");
        });
        at(s + 70, () -> {
            picker = true;
            // Forget this player's pick, which asks again: the same as a first join.
            var server = mc.getSingleplayerServer();
            onServer(mc, sp -> {
                if (server != null) gulliver.common.StarterSizes.reset(server, sp);
            });
        });
        int[] picks = {0, 1, 2, 3, 2};
        for (int i = 0; i < picks.length; i++) {
            int index = picks[i];
            at(s + 130 + i * 55, () -> {
                if (mc.gui.screen() instanceof StarterSizeScreen screen && index < screen.optionCount()) screen.pick(index);
            });
        }
        at(s + 130 + picks.length * 55 + 20, () -> {
            if (mc.gui.screen() instanceof StarterSizeScreen screen) screen.pressStart();
            picker = false;
            look(p, 0.0F, 0.0F);
            camOn(mc, 150.0F, 1.6F, 0.4F, 0.55F);
        });
        int s2 = s + 130 + picks.length * 55 + 140;

        // ---- #34: the breath gust puts out fires ----
        at(s2, () -> {
            mark("breath-fire");
            camOff(mc);
            onServer(mc, sp -> sp.teleportTo(arena.x + 0.5D, arena.y, arena.z - 10.0D));
            cmd(mc, "gamerule fire_spread_radius_around_player 0");
            cmd(mc, "gamerule doFireTick false");
            int x = Mth.floor(arena.x), y = Mth.floor(arena.y), z = Mth.floor(arena.z);
            // A bare field ahead: grass, a plank wall at the far end.
            cmd(mc, String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air", x - 9, y, z - 4, x + 9, y + 8, z + 16));
            cmd(mc, String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:oak_planks", x - 7, y, z + 14, x + 7, y + 5, z + 14));
            for (int fx = -6; fx <= 6; fx += 2) {
                for (int fz = -1; fz <= 11; fz += 3) {
                    cmd(mc, String.format(Locale.ROOT, "setblock %d %d %d minecraft:netherrack", x + fx, y - 1, z + fz));
                }
            }
            lightFires(mc);
            clearChat(mc);
            title(mc, "#34", "The breath gust puts out fires");
            look(p, 0.0F, 25.0F);
        });
        at(s2 + 20, () -> size(mc, 8.0F));
        // Burning pigs among the flames.
        at(s2 + 60, () -> {
            for (int i = -2; i <= 2; i++) {
                cmd(mc, String.format(Locale.ROOT,
                        "summon pig %.1f %.1f %.1f {NoAI:1b,Silent:1b,Tags:[\"gv_burn\"]}",
                        arena.x + i * 2.5D, arena.y, arena.z + 9.0D));
            }
        });
        at(s2 + 100, () -> onServer(mc, sp -> {
            for (var e : ((ServerLevel) sp.level()).getEntities((net.minecraft.world.entity.Entity) null,
                    sp.getBoundingBox().inflate(60.0D), e -> tags(e).contains("gv_burn"))) {
                e.setRemainingFireTicks(400);
            }
        }));
        at(s2 + 110, () -> sub(mc, "Hold G: every flame the wind reaches goes out"));
        during(s2 + 120, s2 + 175, i -> {
            key(KeyInputHandler.BREATH, true);
            look(p, -18.0F + i * 0.65F, 40.0F);
        });
        at(s2 + 176, () -> key(KeyInputHandler.BREATH, false));
        at(s2 + 190, () -> {
            lightFires(mc);
            onServer(mc, sp -> {
                for (var e : ((ServerLevel) sp.level()).getEntities((net.minecraft.world.entity.Entity) null,
                        sp.getBoundingBox().inflate(60.0D), e -> tags(e).contains("gv_burn"))) {
                    e.setRemainingFireTicks(400);
                }
            });
        });
        at(s2 + 200, () -> {
            // Over the giant's shoulder, onto the field it blows at.
            camOn(mc, 40.0F, 0.55F, 1.15F, 0.0F);
            sub(mc, "Wall fires, campfires, candles and burning mobs too");
        });
        during(s2 + 230, s2 + 285, i -> {
            key(KeyInputHandler.BREATH, true);
            look(p, 18.0F - i * 0.65F, 24.0F);
        });
        at(s2 + 286, () -> key(KeyInputHandler.BREATH, false));
        int s3 = s2 + 360;
        at(s3 - 20, () -> {
            camOff(mc);
            cmd(mc, "kill @e[tag=gv_burn]");
            cmd(mc, "kill @e[type=item]");
        });

        // ---- #33: Cinematic Interactions at any size ----
        at(s3, () -> {
            mark("cinematic");
            size(mc, 1.0F);
            int x = Mth.floor(arena.x), y = Mth.floor(arena.y), z = Mth.floor(arena.z);
            cmd(mc, String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air", x - 10, y, z - 12, x + 10, y + 8, z + 16));
            cmd(mc, String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:grass_block", x - 10, y - 1, z - 12, x + 10, y - 1, z + 16));
            cmd(mc, "kill @e[type=item]");
            clearChat(mc);
            cmd(mc, String.format(Locale.ROOT, "setblock %d %d %d minecraft:crafting_table", x, y, z + 2));
            onServer(mc, sp -> sp.teleportTo(arena.x + 0.5D, arena.y, z + 0.6D));
            cmd(mc, "item replace entity @a weapon.mainhand with minecraft:stick");
            title(mc, "#33", "Cinematic Interactions at any size");
        });
        // Normal size: the cinematic scene plays.
        at(s3 + 60, () -> {
            sub(mc, "Normal size: the cinematic scene plays");
            lookAtTable(p, z(s3));
        });
        at(s3 + 70, () -> key(mc.options.keyUse, true));
        at(s3 + 72, () -> key(mc.options.keyUse, false));
        at(s3 + 200, CinematicCompat::leaveScene);
        // Giant: the normal window instead.
        at(s3 + 240, () -> onServer(mc, sp -> sp.teleportTo(arena.x + 0.5D, arena.y, Mth.floor(arena.z) - 4.0D)));
        at(s3 + 246, () -> {
            size(mc, 8.0F);
            sub(mc, "Giant: the normal crafting window instead");
        });
        at(s3 + 290, () -> lookAtTable(p, z(s3)));
        at(s3 + 300, () -> key(mc.options.keyUse, true));
        at(s3 + 302, () -> key(mc.options.keyUse, false));
        at(s3 + 370, () -> p.closeContainer());
        // Tiny: the normal window too.
        at(s3 + 390, () -> {
            size(mc, 0.125F);
            sub(mc, "Tiny: the normal window too");
        });
        at(s3 + 396, () -> onServer(mc, sp -> sp.teleportTo(arena.x + 0.5D, arena.y, Mth.floor(arena.z) + 1.85D)));
        at(s3 + 440, () -> lookAtTable(p, z(s3)));
        at(s3 + 450, () -> key(mc.options.keyUse, true));
        at(s3 + 452, () -> key(mc.options.keyUse, false));
        at(s3 + 520, () -> p.closeContainer());
        // A scene playing when the size changes ends.
        at(s3 + 540, () -> onServer(mc, sp -> sp.teleportTo(arena.x + 0.5D, arena.y, Mth.floor(arena.z) + 0.6D)));
        at(s3 + 546, () -> size(mc, 1.0F));
        at(s3 + 580, () -> {
            sub(mc, "Growing during a scene ends it");
            lookAtTable(p, z(s3));
        });
        at(s3 + 590, () -> key(mc.options.keyUse, true));
        at(s3 + 592, () -> key(mc.options.keyUse, false));
        // 2.5: well past the scene range, and still clear of the table.
        at(s3 + 680, () -> size(mc, 2.5F));
        at(s3 + 780, () -> size(mc, 1.0F));
        addons(mc, p, s3 + 800);
    }

    private static void addons(Minecraft mc, LocalPlayer p, int s4) {

        // ---- #33: the mods in Cinematic Interactions' style, where installed ----
        if (installed("com.cinematicbelongings.scene.PersonalScene")) s4 = belongings(mc, p, s4);
        if (installed("com.example.cinematiccookery.CookScene")) {
            s4 = station(mc, p, s4, "Cinematic Cookery", "kaleidoscope_cookery:stove[lit=true]", "kaleidoscope_cookery:pot",
                    "the cooking scene plays", "the pot works as without the mod");
            s4 = station(mc, p, s4, "Cinematic Cookery", null, "kaleidoscope_cookery:chopping_board",
                    "the chopping scene plays", "the board works as without the mod");
        }
        if (installed("com.arbuzobr1.cinematicdelight.CookScene")) {
            s4 = station(mc, p, s4, "Cinematic Delight", "minecraft:campfire[lit=true]", "farmersdelight:cooking_pot",
                    "the cooking scene plays", "the pot works as without the mod");
            s4 = station(mc, p, s4, "Cinematic Delight", null, "farmersdelight:cutting_board", "the cutting scene plays",
                    "the board works as without the mod");
        }
        int end = s4;
        at(end, () -> {
            size(mc, 1.0F);
            mark("end");
        });
    }

    /** Logs whether a scene started, and if not, what the mod's own tryBegin says when asked directly. */
    private static void diagnose(Minecraft mc, String block) {
        log("showcase {}: hit {} scene active {}", block, mc.hitResult == null ? null : mc.hitResult.getType(),
                CinematicCompat.activeScene());
        if (CinematicCompat.activeScene() != null || !(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)) return;
        for (String name : new String[] {"com.example.cinematiccookery.CookScene", "com.arbuzobr1.cinematicdelight.CookScene"}) {
            try {
                Class<?> c = Class.forName(name);
                for (java.lang.reflect.Method m : c.getMethods()) {
                    if (!m.getName().equals("tryBegin")) continue;
                    Object r = m.invoke(null, mc.level, hit.getBlockPos(), mc.player);
                    log("showcase {}: {}.tryBegin -> {}, debug {}", block, name, r,
                            c.getMethod("debugLine").invoke(null));
                    var be = mc.level.getBlockEntity(hit.getBlockPos());
                    Class<?> station = Class.forName(name.replace("CookScene", "Station"));
                    Object st = station.getMethod("of", net.minecraft.world.level.block.entity.BlockEntity.class).invoke(null, be);
                    log("showcase {}: block entity {}, station {}, distance {}, screen {}, camera {}", block,
                            be == null ? null : be.getClass().getName(), st,
                            Math.sqrt(mc.player.distanceToSqr(Vec3.atCenterOf(hit.getBlockPos()))), mc.gui.screen(),
                            mc.options.getCameraType());
                }
            } catch (ReflectiveOperationException | LinkageError e) {
                // not installed
            }
        }
    }

    // ---- the cinematic mods used for real ---------------------------------------------

    private static void proper(Minecraft mc, LocalPlayer p, int s) {
        if (installed("dev.dyslexic.cinematic.client.Scene")) s = craftAtTable(mc, p, s);
        if (installed("com.cinematicbelongings.scene.PersonalScene")) s = bagAtEverySize(mc, p, s);
        if (installed("com.example.cinematiccookery.CookScene")) {
            String scene = "com.example.cinematiccookery.CookScene";
            s = cook(mc, p, s, "Cinematic Cookery: stir-frying beef", scene, "kaleidoscope_cookery:stove[lit=true]",
                    "kaleidoscope_cookery:pot", new String[] {"kaleidoscope_cookery:oil", "minecraft:beef 2",
                            "kaleidoscope_cookery:kitchen_shovel"},
                    new int[] {0, 1, 2, 2, 2, 2, 2, 2, 2, 2}, 30);
            s = cook(mc, p, s, "Cinematic Cookery: carving a chicken", scene, null, "kaleidoscope_cookery:chopping_board",
                    new String[] {"minecraft:cooked_chicken", "kaleidoscope_cookery:iron_kitchen_knife"},
                    new int[] {0, 1, 1, 1, 1, 1}, 30);
        }
        if (installed("com.arbuzobr1.cinematicdelight.CookScene")) {
            String scene = "com.arbuzobr1.cinematicdelight.CookScene";
            s = cook(mc, p, s, "Cinematic Delight: tomato sauce", scene, "minecraft:campfire[lit=true]",
                    "farmersdelight:cooking_pot", new String[] {"farmersdelight:tomato 2", "minecraft:bowl"},
                    new int[] {0, 0, -1, -1, -1, -1, -1, -1, 1, 1}, 30);
            s = cook(mc, p, s, "Cinematic Delight: mincing beef", scene, null, "farmersdelight:cutting_board",
                    new String[] {"minecraft:beef", "farmersdelight:iron_knife"}, new int[] {0, 1, -1, -1}, 30);
        }
        int end = s;
        at(end, () -> {
            size(mc, 1.0F);
            mark("end");
        });
    }

    /** Cinematic Interactions at normal size: logs into planks, planks into sticks, with its own hands. */
    private static int craftAtTable(Minecraft mc, LocalPlayer p, int s) {
        at(s, () -> {
            mark("craft");
            camOff(mc);
            clearChat(mc);
            size(mc, 1.0F);
            int x = Mth.floor(arena.x), y = Mth.floor(arena.y), z = Mth.floor(arena.z);
            cmd(mc, String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air", x - 3, y, z, x + 3, y + 3, z + 4));
            cmd(mc, String.format(Locale.ROOT, "setblock %d %d %d minecraft:crafting_table", x, y, z + 2));
            cmd(mc, "clear @a");
            cmd(mc, "item replace entity @a hotbar.0 with minecraft:oak_log 4");
            onServer(mc, sp -> sp.teleportTo(arena.x + 0.5D, arena.y, z + 0.6D));
            title(mc, "#33", "Cinematic Interactions: crafting at normal size");
        });
        at(s + 50, () -> lookAtTable(p, Mth.floor(arena.z)));
        at(s + 60, () -> key(mc.options.keyUse, true));
        at(s + 62, () -> key(mc.options.keyUse, false));
        // The table keeps the inventory open: items are picked up from it onto the cursor
        // (a normal container click), laid on a cell of the table and crafted there.
        String table = "dev.dyslexic.cinematic.client.Scene";
        // Steps: pick up a stack (container click), hover a table cell and right-click (one item),
        // put the rest back, hover the result and click (craft), put the result in the hotbar.
        Object[][] steps = {
                {"pick", "minecraft:oak_log", "Logs on the table"}, {"cell", 4}, {"put", 36},
                {"craft", "Craft the planks"}, {"put", 37},
                {"pick", "minecraft:oak_planks", "Planks into sticks"}, {"cell", 1}, {"cell", 4}, {"put", 37},
                {"craft", "Craft the sticks"}, {"put", 38}};
        int t = s + 170;
        for (Object[] step : steps) {
            at(t, () -> {
                Object station = staticField(table, "station");
                if (station == null) {
                    log("showcase craft: no station");
                    return;
                }
                switch ((String) step[0]) {
                    case "pick" -> {
                        if (step[2] != null) sub(mc, (String) step[2]);
                        containerClick(mc, p, menuSlotOf(p, (String) step[1]));
                    }
                    case "put" -> containerClick(mc, p, (Integer) step[1]);
                    case "cell" -> {
                        setField(station, "hoverResult", false);
                        setField(station, "hoverCell", (Integer) step[1]);
                        invokeOn(station, "click", 1, false);
                    }
                    case "craft" -> {
                        sub(mc, (String) step[1]);
                        setField(station, "hoverCell", -1);
                        setField(station, "hoverResult", true);
                        invokeOn(station, "click", 0, false);
                    }
                    default -> { }
                }
                log("showcase craft {} -> carried {}, hotbar {}", java.util.Arrays.toString(step),
                        p.containerMenu.getCarried(), p.getInventory().getItem(0) + " / " + p.getInventory().getItem(1)
                                + " / " + p.getInventory().getItem(2));
            });
            t += step[0].equals("craft") ? 70 : 30;
        }
        at(t + 30, () -> log("showcase craft: inventory {}", p.getInventory().getItem(1) + " / " + p.getInventory().getItem(2)));
        at(t + 40, CinematicCompat::leaveScene);
        return t + 100;
    }

    /** Cinematic Belongings' bag at every size, its items moved with the real mouse. */
    private static int bagAtEverySize(Minecraft mc, LocalPlayer p, int s) {
        at(s, () -> {
            mark("bag");
            camOff(mc);
            clearChat(mc);
            size(mc, 1.0F);
            onServer(mc, sp -> sp.teleportTo(arena.x + 0.5D, arena.y, arena.z - 6.0D));
            cmd(mc, "clear @a");
            cmd(mc, "item replace entity @a hotbar.0 with minecraft:bread 12");
            cmd(mc, "item replace entity @a hotbar.1 with minecraft:iron_pickaxe");
            cmd(mc, "item replace entity @a inventory.4 with minecraft:oak_log 20");
            title(mc, "#33", "Cinematic Belongings at every size");
        });
        float[] sizes = {0.125F, 0.25F, 0.5F, 1.0F, 2.0F, 4.0F, 8.0F};
        String[] names = {"1/8", "1/4", "1/2", "1", "2", "4", "8"};
        int t = s + 60;
        for (int i = 0; i < sizes.length; i++) {
            float size = sizes[i];
            String name = names[i];
            int at = t;
            at(at, () -> {
                size(mc, size);
                sub(mc, "Size " + name + ": moving bread with the mouse");
            });
            at(at + 40, () -> {
                look(p, 0.0F, 10.0F);
                mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(p));
            });
            // Inventory menu: 36 = first hotbar slot, 20 = an empty inventory slot.
            // Belongings' hand reaches into the bag before the item moves: leave it time.
            at(at + 100, () -> mouseOnSlot(mc, 36, name));
            at(at + 106, () -> mouseClick(mc));
            at(at + 150, () -> mouseOnSlot(mc, 20, name));
            at(at + 156, () -> mouseClick(mc));
            at(at + 200, () -> log("showcase bag size {}: after moving with the mouse: hotbar 0 {}, slot 20 {}, carried {}",
                    name, p.getInventory().getItem(0), p.getInventory().getItem(20), p.containerMenu.getCarried()));
            at(at + 205, () -> mouseOnSlot(mc, 20, name));
            at(at + 211, () -> mouseClick(mc));
            at(at + 255, () -> mouseOnSlot(mc, 36, name));
            at(at + 261, () -> mouseClick(mc));
            at(at + 305, () -> {
                log("showcase bag size {}: moved back: hotbar 0 {}, slot 20 {}", name, p.getInventory().getItem(0),
                        p.getInventory().getItem(20));
                if (mc.gui.screen() != null) mc.gui.screen().onClose();
            });
            t += 335;
        }
        at(t, () -> size(mc, 1.0F));
        return t + 30;
    }

    /** Puts the real mouse cursor where Belongings draws a menu slot, and logs whether it agrees. */
    private static void mouseOnSlot(Minecraft mc, int menuSlot, String size) {
        //#if MC >= 26.1
        try {
            Object spot = invokeStatic("com.cinematicbelongings.bag.Bag", "spot", menuSlot);
            if (spot == null) {
                log("showcase bag size {}: no spot {}", size, menuSlot);
                return;
            }
            Vec3 pos = (Vec3) invokeOn(spot, "pos");
            double[] gui = (double[]) invokeStatic("com.cinematicbelongings.render.Picking", "toGui", pos);
            var window = mc.getWindow();
            double x = gui[0] * window.getScreenWidth() / window.getGuiScaledWidth();
            double y = gui[1] * window.getScreenHeight() / window.getGuiScaledHeight();
            java.lang.reflect.Field fx = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
            java.lang.reflect.Field fy = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
            fx.setAccessible(true);
            fy.setAccessible(true);
            fx.setDouble(mc.mouseHandler, x);
            fy.setDouble(mc.mouseHandler, y);
            mc.execute(() -> log("showcase bag size {}: cursor on slot {} -> hovered {}", size, menuSlot,
                    invokeStatic("com.cinematicbelongings.bag.Bag", "hovered")));
        } catch (ReflectiveOperationException | RuntimeException e) {
            log("showcase bag size {}: could not move the mouse: {}", size, e.toString());
        }
        //#endif
    }

    /** A left click (SDL button 1) at the cursor, delivered to the open screen as the mouse handler does. */
    private static void mouseClick(Minecraft mc) {
        //#if MC >= 26.1
        var screen = mc.gui.screen();
        if (screen == null) return;
        var window = mc.getWindow();
        double x = mc.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
        double y = mc.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight();
        var event = new net.minecraft.client.input.MouseButtonEvent(x, y, new net.minecraft.client.input.MouseButtonInfo(1, 0));
        Object hovered = invokeStatic("com.cinematicbelongings.bag.Bag", "hovered");
        log("showcase click: phase {}, bag valid {}", staticField("com.cinematicbelongings.scene.PersonalScene", "phase"),
                invokeStatic("com.cinematicbelongings.bag.Bag", "valid"));
        boolean pressed = screen.mouseClicked(event, false);
        Object afterPress = mc.player == null ? null : mc.player.containerMenu.getCarried().copy();
        // A real click: the button comes up a moment later.
        net.minecraft.client.gui.screens.Screen pressedOn = screen;
        at(t + 3, () -> {
            boolean released = pressedOn.mouseReleased(event);
            log("showcase click released {}: carried {}", released, mc.player == null ? null : mc.player.containerMenu.getCarried());
        });
        log("showcase click at {},{} on hovered {}: screen {}, pressed {}, carried after press {}", Math.round(x),
                Math.round(y), hovered, screen.getClass().getSimpleName(), pressed, afterPress);
        //#endif
    }

    /**
     * A cooking mod's block used for real: the items go in the hotbar, the
     * block is used (the scene starts), and each step selects a hotbar slot
     * and acts with it (-1: just wait).
     */
    private static int cook(Minecraft mc, LocalPlayer p, int s, String what, String scene, String under, String block,
                            String[] hotbar, int[] steps, int gap) {
        float lift = under == null ? 0.0F : 1.0F;
        at(s, () -> {
            mark("cook " + block);
            camOff(mc);
            clearChat(mc);
            size(mc, 1.0F);
            int x = Mth.floor(arena.x), y = Mth.floor(arena.y), z = Mth.floor(arena.z);
            cmd(mc, String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air", x - 3, y, z, x + 3, y + 3, z + 4));
            if (under != null) cmd(mc, String.format(Locale.ROOT, "setblock %d %d %d %s", x, y, z + 2, under));
            cmd(mc, String.format(Locale.ROOT, "setblock %d %d %d %s", x, y + (under == null ? 0 : 1), z + 2, block));
            cmd(mc, "kill @e[type=item]");
            cmd(mc, "clear @a");
            for (int i = 0; i < hotbar.length; i++) {
                cmd(mc, "item replace entity @a hotbar." + i + " with " + hotbar[i]);
            }
            onServer(mc, sp -> sp.teleportTo(arena.x + 0.5D, arena.y, z + 0.6D));
            title(mc, "#33", what);
        });
        at(s + 50, () -> lookAtBlock(p, Mth.floor(arena.z), lift));
        at(s + 60, () -> key(mc.options.keyUse, true));
        at(s + 62, () -> key(mc.options.keyUse, false));
        int t = s + 150;
        for (int step : steps) {
            int at = t;
            if (step >= 0) {
                at(at, () -> invokeStatic(scene, "select", step));
                at(at + 6, () -> {
                    invokeStatic(scene, "act");
                    log("showcase cook {} slot {}: status {}", block, step, invokeStatic(scene, "potStatus"));
                });
            }
            t += gap;
        }
        at(t + 40, CinematicCompat::leaveScene);
        return t + 100;
    }

    /** The inventory menu slot holding the first stack of this item (hotbar 0-8 is 36-44), or -1. */
    private static int menuSlotOf(LocalPlayer p, String item) {
        for (int i = 0; i < 36; i++) {
            var stack = p.getInventory().getItem(i);
            if (!stack.isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()
                    .equals(item)) {
                return i < 9 ? 36 + i : i;
            }
        }
        return -1;
    }

    /** A left click on a slot of the open container, as the inventory screen sends it (any version). */
    private static void containerClick(Minecraft mc, LocalPlayer p, int slot) {
        if (slot < 0 || mc.gameMode == null) return;
        for (java.lang.reflect.Method m : mc.gameMode.getClass().getMethods()) {
            Class<?>[] params = m.getParameterTypes();
            if (params.length == 5 && params[0] == int.class && params[1] == int.class && params[2] == int.class
                    && params[3].isEnum()) {
                try {
                    Object pickup = null;
                    for (Object c : params[3].getEnumConstants()) if (((Enum<?>) c).name().equals("PICKUP")) pickup = c;
                    m.invoke(mc.gameMode, p.containerMenu.containerId, slot, 0, pickup, p);
                    return;
                } catch (ReflectiveOperationException | RuntimeException e) {
                    log("showcase: container click failed: {}", e.toString());
                }
            }
        }
    }

    private static Object invokeStatic(String className, String name, Object... args) {
        try {
            Class<?> c = Class.forName(className);
            for (java.lang.reflect.Method m : c.getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == args.length
                        && java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                    return m.invoke(null, args);
                }
            }
            log("showcase: no {}.{}", className, name);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            log("showcase: {}.{} failed: {}", className, name, e.toString());
        }
        return null;
    }

    private static Object invokeOn(Object target, String name, Object... args) {
        try {
            for (java.lang.reflect.Method m : target.getClass().getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == args.length) return m.invoke(target, args);
            }
            log("showcase: no {}.{}", target.getClass().getName(), name);
        } catch (ReflectiveOperationException | RuntimeException e) {
            log("showcase: {}.{} failed: {}", target.getClass().getName(), name, e.toString());
        }
        return null;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            java.lang.reflect.Field f = target.getClass().getField(name);
            f.set(target, value);
        } catch (ReflectiveOperationException | RuntimeException e) {
            log("showcase: set {} failed: {}", name, e.toString());
        }
    }

    private static Object staticField(String className, String name) {
        try {
            return Class.forName(className).getField(name).get(null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }

    private static boolean installed(String className) {
        try {
            Class.forName(className, false, ClientShowcaseFilm.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** Cinematic Belongings: the inventory as a 3D bag at normal size, the plain inventory at giant and tiny size. */
    private static int belongings(Minecraft mc, LocalPlayer p, int s) {
        at(s, () -> {
            mark("belongings");
            camOff(mc);
            clearChat(mc);
            size(mc, 1.0F);
            onServer(mc, sp -> sp.teleportTo(arena.x + 0.5D, arena.y, arena.z - 6.0D));
            look(p, 0.0F, 10.0F);
            cmd(mc, "clear @a");
            cmd(mc, "give @a minecraft:bread 12");
            cmd(mc, "give @a minecraft:oak_log 20");
            cmd(mc, "give @a minecraft:iron_pickaxe");
            title(mc, "#33", "Cinematic Belongings at any size");
        });
        String[][] steps = {{"1.0", "Normal size: the 3D bag"}, {"8.0", "Giant: a giant's bag in giant hands"},
                {"0.125", "Tiny: a tiny bag in tiny hands"}};
        int t = s + 60;
        for (String[] step : steps) {
            float size = Float.parseFloat(step[0]);
            int at = t;
            at(at, () -> {
                size(mc, size);
                sub(mc, step[1]);
            });
            at(at + 40, () -> {
                look(p, 0.0F, 10.0F);
                mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(p));
            });
            at(at + 130, () -> {
                if (mc.gui.screen() != null) mc.gui.screen().onClose();
            });
            t += 170;
        }
        // Growing with the bag open: the scene grows along.
        int g = t;
        at(g, () -> {
            size(mc, 1.0F);
            sub(mc, "Growing with the bag open");
        });
        at(g + 40, () -> {
            look(p, 0.0F, 10.0F);
            mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(p));
        });
        at(g + 90, () -> size(mc, 6.0F));
        at(g + 170, () -> size(mc, 0.25F));
        at(g + 250, () -> {
            if (mc.gui.screen() != null) mc.gui.screen().onClose();
        });
        at(g + 270, () -> size(mc, 1.0F));
        return g + 300;
    }

    /**
     * One block of a cooking mod in front of the player: used at normal size
     * (its scene plays, then is left), as a giant and as a tiny (no scene).
     */
    private static int station(Minecraft mc, LocalPlayer p, int s, String mod, String under, String block,
                               String normalText, String otherText) {
        float lift = under == null ? 0.0F : 1.0F;
        at(s, () -> {
            mark("station " + block);
            camOff(mc);
            clearChat(mc);
            size(mc, 1.0F);
            int x = Mth.floor(arena.x), y = Mth.floor(arena.y), z = Mth.floor(arena.z);
            cmd(mc, String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air", x - 3, y, z, x + 3, y + 3, z + 4));
            if (under != null) cmd(mc, String.format(Locale.ROOT, "setblock %d %d %d %s", x, y, z + 2, under));
            cmd(mc, String.format(Locale.ROOT, "setblock %d %d %d %s", x, y + (under == null ? 0 : 1), z + 2, block));
            cmd(mc, "kill @e[type=item]");
            onServer(mc, sp -> sp.teleportTo(arena.x + 0.5D, arena.y, z + 0.6D));
            cmd(mc, "clear @a");
            title(mc, "#33", mod + " at any size");
        });
        // Normal: the scene.
        at(s + 50, () -> {
            sub(mc, "Normal size: " + normalText);
            lookAtBlock(p, Mth.floor(arena.z), lift);
        });
        at(s + 60, () -> key(mc.options.keyUse, true));
        at(s + 62, () -> key(mc.options.keyUse, false));
        at(s + 80, () -> diagnose(mc, block));
        at(s + 190, CinematicCompat::leaveScene);
        // Giant: no scene.
        at(s + 230, () -> onServer(mc, sp -> sp.teleportTo(arena.x + 0.5D, arena.y, Mth.floor(arena.z) - 4.0D)));
        at(s + 236, () -> {
            size(mc, 8.0F);
            sub(mc, "Giant: no scene, " + otherText);
        });
        at(s + 280, () -> lookAtBlock(p, Mth.floor(arena.z), lift));
        at(s + 290, () -> key(mc.options.keyUse, true));
        at(s + 292, () -> key(mc.options.keyUse, false));
        at(s + 350, () -> {
            if (mc.gui.screen() != null) p.closeContainer();
        });
        // Tiny: no scene.
        at(s + 370, () -> {
            size(mc, 0.125F);
            sub(mc, "Tiny: no scene, " + otherText);
        });
        at(s + 376, () -> onServer(mc, sp -> sp.teleportTo(arena.x + 0.5D, arena.y, Mth.floor(arena.z) + 1.85D)));
        at(s + 420, () -> {
            cmd(mc, "item replace entity @a weapon.mainhand with minecraft:stick");
            lookAtBlock(p, Mth.floor(arena.z), lift);
        });
        at(s + 430, () -> key(mc.options.keyUse, true));
        at(s + 432, () -> key(mc.options.keyUse, false));
        at(s + 490, () -> {
            if (mc.gui.screen() != null) p.closeContainer();
            onServer(mc, sp -> sp.teleportTo(arena.x + 0.5D, arena.y, Mth.floor(arena.z) + 0.6D));
        });
        at(s + 496, () -> size(mc, 1.0F));
        return s + 520;
    }

    /** Clears the advancement pop-ups the tour's setup leaves queued (whatever the version calls the toast list). */
    private static void clearToasts(Minecraft mc) {
        for (String getter : new String[] {"getToastManager", "getToasts"}) {
            try {
                Object toasts = Minecraft.class.getMethod(getter).invoke(mc);
                toasts.getClass().getMethod("clear").invoke(toasts);
                return;
            } catch (ReflectiveOperationException | RuntimeException e) {
                // try the other name
            }
        }
    }

    private static void clearChat(Minecraft mc) {
        clearToasts(mc);
        //#if MC >= 26.2
        mc.gui.hud.getChat().clearMessages(false);
        //#else
        //$$ mc.gui.getChat().clearMessages(false);
        //#endif
    }

    /** Fire across the netherrack field and up the plank wall, a row of campfires and one of candles. */
    private static void lightFires(Minecraft mc) {
        int x = Mth.floor(arena.x), y = Mth.floor(arena.y), z = Mth.floor(arena.z);
        for (int fx = -6; fx <= 6; fx += 2) {
            for (int fz = -1; fz <= 11; fz += 3) {
                cmd(mc, String.format(Locale.ROOT, "setblock %d %d %d minecraft:fire", x + fx, y, z + fz));
            }
            for (int fy = 1; fy <= 4; fy += 1) {
                cmd(mc, String.format(Locale.ROOT, "setblock %d %d %d minecraft:fire", x + fx, y + fy, z + 13));
            }
        }
        for (int fx = -5; fx <= 5; fx += 2) {
            cmd(mc, String.format(Locale.ROOT, "setblock %d %d %d minecraft:campfire[lit=true]", x + fx, y, z + 3));
            cmd(mc, String.format(Locale.ROOT, "setblock %d %d %d minecraft:candle[lit=true,candles=4]", x + fx + 1, y, z + 6));
        }
    }

    private static java.util.Set<String> tags(net.minecraft.world.entity.Entity e) {
        //#if MC >= 26.1
        return e.entityTags();
        //#else
        //$$ return e.getTags();
        //#endif
    }

    private static int z(int unused) {
        return Mth.floor(arena.z);
    }

    /** Like lookAtTable, at a block {@code lift} blocks up (a pot on its stove). */
    private static void lookAtBlock(LocalPlayer p, int z, float lift) {
        Vec3 target = new Vec3(Mth.floor(arena.x) + 0.5D, Mth.floor(arena.y) + 0.15D + lift, z + 2.5D);
        Vec3 d = target.subtract(p.getEyePosition());
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) (-Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        look(p, yaw, pitch);
    }

    /** Turns the player to look at the middle of the crafting table two blocks north of the arena. */
    private static void lookAtTable(LocalPlayer p, int z) {
        Vec3 table = new Vec3(Mth.floor(arena.x) + 0.5D, Mth.floor(arena.y) + 0.75D, z + 2.5D);
        Vec3 d = table.subtract(p.getEyePosition());
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) (-Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        look(p, yaw, pitch);
    }

    // ---- #19: small blocks are solid for tinies -------------------------------------------

    private static void blocks(Minecraft mc, int s) {
        LocalPlayer p = mc.player;
        // A little course on the runway in front of the arena's centre (the player faces +Z).
        at(s, () -> {
            mark("small-blocks");
            applyConfig(mc);
            size(mc, 0.25F);
            tpBlock(mc, 0.5D, 0.0D, 0.5D);
            look(p, 0.0F, 15.0F);
            p.yBodyRot = p.yBodyRotO = 0.0F;
            set(mc, 0, 0, 2, "stone_button[face=floor,facing=north]");
            set(mc, 0, 0, 3, "oak_pressure_plate");
            set(mc, 0, 0, 4, "lever[face=floor,facing=north]");
            set(mc, 0, 0, 5, "stone_pressure_plate");
            set(mc, 0, 0, 6, "oak_sign[rotation=8]");
            onServer(mc, sp -> {
                int bx = Mth.floor(arena.x), by = Mth.floor(arena.y), bz = Mth.floor(arena.z);
                SelfTest.runCommand((ServerLevel) sp.level(), String.format(Locale.ROOT,
                        "fill %d %d %d %d %d %d minecraft:stone_bricks", bx - 3, by, bz + 9, bx + 3, by + 2, bz + 9));
            });
            set(mc, -1, 1, 8, "lever[face=wall,facing=north]");
            set(mc, 1, 1, 8, "oak_wall_sign[facing=north]");
            set(mc, 0, 2, 8, "stone_button[face=wall,facing=north]");
            title(mc, "#19", "Buttons, levers, plates and signs are solid for tinies");
        });
        // A new shot only once the server has put the tiny in place.
        at(s + 10, () -> camAt(mc, 1.45D, 0.45D, 3.3D));
        at(s + 50, () -> sub(mc, "Walking over a button, a plate and a lever - not through them"));
        during(s + 60, s + 150, i -> {
            key(mc.options.keyUp, true);
            key(mc.options.keyJump, i % 14 < 3);
        });
        at(s + 151, () -> {
            key(mc.options.keyUp, false);
            key(mc.options.keyJump, false);
            sub(mc, "...and the sign post is in the way");
        });

        // Ledges: a lever and a sign on a wall (their tops: 13/16 and 12.5/16 up the block).
        int l = s + 230;
        at(l, () -> {
            size(mc, 0.125F);
            sub(mc, "A lever on a wall is a ledge to stand on");
            tpBlock(mc, -0.5D, 1.0D + 13.0D / 16.0D + 0.01D, 8.0D + 13.0D / 16.0D);
            look(p, 180.0F, 20.0F);
            p.yBodyRot = p.yBodyRotO = 180.0F;
        });
        at(l + 10, () -> camAt(mc, 0.1D, 2.05D, 7.4D));
        at(l + 100, () -> {
            sub(mc, "...so is a sign");
            tpBlock(mc, 1.5D, 1.0D + 12.5D / 16.0D + 0.01D, 8.0D + 15.0D / 16.0D);
            look(p, 180.0F, 20.0F);
            p.yBodyRot = p.yBodyRotO = 180.0F;
        });
        at(l + 110, () -> camAt(mc, 0.9D, 2.0D, 7.5D));

        // Pressure plates are springs.
        int f = l + 190;
        at(f, () -> {
            mark("plate-spring");
            size(mc, 0.25F);
            title(mc, "Pressure plates", "Too light to press one - but it springs you up when it comes back");
            tpBlock(mc, 0.76D, 0.07D, 5.76D);
            look(p, 0.0F, 10.0F);
            p.yBodyRot = p.yBodyRotO = 0.0F;
            summonPresser(mc, "chicken", 5);
        });
        at(f + 10, () -> camAt(mc, 2.6D, 0.55D, 5.4D));
        at(f + 40, () -> sub(mc, "A chicken presses the stone plate, the tiny sinks with it..."));
        at(f + 80, () -> {
            sub(mc, "...the chicken leaves, the plate pops back up: stone throws hard");
            cmd(mc, "tp @e[tag=gv_press] ~ -100 ~");
        });
        at(f + 85, () -> cmd(mc, "kill @e[tag=gv_press]"));
        // Wood: an apple on the oak plate.
        int w = f + 220;
        at(w, () -> {
            tpBlock(mc, 0.76D, 0.07D, 3.76D);
            look(p, 0.0F, 10.0F);
            p.yBodyRot = p.yBodyRotO = 0.0F;
            summonPresser(mc, "item", 3);
            sub(mc, "An apple on a wooden plate: wood springs back gentler");
        });
        at(w + 10, () -> camAt(mc, 2.6D, 0.55D, 3.4D));
        at(w + 70, () -> cmd(mc, "kill @e[tag=gv_press]"));
        at(w + 200, () -> {
            title(mc, "Gulliver Unbound", "Solid small blocks");
            mark("end");
        });
        at(w + 260, () -> camOff(mc));
    }

    // The course is placed from the arena's centre block, read on the server thread in turn with
    // the setup that records it (the server may run behind the client's script).

    /** A block of the course, by its offset from the arena's centre block. */
    private static void set(Minecraft mc, int dx, int dy, int dz, String block) {
        onServer(mc, sp -> SelfTest.runCommand((ServerLevel) sp.level(), String.format(Locale.ROOT,
                "setblock %d %d %d minecraft:%s",
                Mth.floor(arena.x) + dx, Mth.floor(arena.y) + dy, Mth.floor(arena.z) + dz, block)));
    }

    /** Teleports the player to a spot given from the arena's centre block corner. */
    private static void tpBlock(Minecraft mc, double dx, double dy, double dz) {
        onServer(mc, sp -> sp.teleportTo(Mth.floor(arena.x) + dx, Mth.floor(arena.y) + dy, Mth.floor(arena.z) + dz));
    }

    /** Something heavy enough on the far corner of the plate dz blocks ahead: a chicken, or an apple (wood only). */
    private static void summonPresser(Minecraft mc, String what, int dz) {
        // Dropped in, so it lands on the plate and presses it (a plate notices what moves into it).
        String nbt = what.equals("item")
                ? "{Item:{id:\"minecraft:apple\",count:1},PickupDelay:32767,Motion:[0.0d,-0.1d,0.0d],Tags:[\"gv_press\"]}"
                : "{Silent:1b,Tags:[\"gv_press\"],attributes:[{id:\"minecraft:movement_speed\",base:0.0d}]}";
        // Both drop onto the plate: a plate only notices what moves into it (so no NoAI mob, which never moves;
        // this chicken just can't walk off).
        double up = 0.3D;
        onServer(mc, sp -> SelfTest.runCommand((ServerLevel) sp.level(), String.format(Locale.ROOT,
                "summon %s %.3f %.3f %.3f %s", what, Mth.floor(arena.x) + 0.27D, Mth.floor(arena.y) + up,
                Mth.floor(arena.z) + dz + 0.27D, nbt)));
    }

    private static void onServer(Consumer<ServerPlayer> action, Minecraft mc) {
        onServer(mc, action);
    }

    // ---- film camera ------------------------------------------------------------------

    private static boolean filming;
    /** Where the camera sits, as an angle round the player measured from its back (0 = straight behind). */
    private static float camYaw;
    private static float camDist;
    private static float camHeight;
    /** Height on the body the camera aims at, in body heights. */
    private static float camAim = 0.6F;
    private static Vec3 camPos, camPosO;
    /** Where a standing camera stands, or null for one that follows. */
    private static Vec3 camAnchor;
    /** Where the camera looks, gliding after the player. */
    private static Vec3 camLook;
    private static float camYawNow, camYawO, camPitchNow, camPitchO;

    /** Whether the film camera is on (the stamina bar stays out of the shot). */
    public static boolean filming() {
        return filming;
    }

    /**
     * The film camera this frame {x, y, z, yaw, pitch} (MixinCameraFilm), or
     * null when nothing is being filmed.
     */
    public static float[] shot() {
        if (!filming || camPos == null || camPosO == null) return null;
        float pt = VisualSize.viewPartial();
        Vec3 p = camPosO.lerp(camPos, pt);
        return new float[]{(float) p.x, (float) p.y, (float) p.z,
                Mth.rotLerp(pt, camYawO, camYawNow), Mth.lerp(pt, camPitchO, camPitchNow)};
    }

    /** A camera fixed relative to where the player faces (yaw 180 = in front), distance and height in body sizes. */
    private static void camOn(Minecraft mc, float yaw, float dist, float height, float aim) {
        camYaw = yaw;
        camDist = dist;
        camHeight = height;
        camAim = aim;
        camAnchor = null;
        start(mc);
    }

    private static void start(Minecraft mc) {
        if (mc.player == null) return;
        if (!filming) {
            camPos = camPosO = null;
            camLook = null;
        }
        filming = true;
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }

    /** A camera standing still (offset from the arena's centre block corner), only turning to follow the player. */
    private static void camAt(Minecraft mc, double dx, double dy, double dz) {
        camAnchor = new Vec3(dx, dy, dz);
        camAim = 0.5F;
        start(mc);
        camPos = camPosO = null;
    }

    private static void camOff(Minecraft mc) {
        filming = false;
        camAnchor = null;
        camPos = camPosO = null;
        mc.options.setCameraType(CameraType.FIRST_PERSON);
    }

    private static Vec3 target(LocalPlayer p) {
        float size = VisualSize.of(p);
        float body = 1.8F * size;
        float a = (p.yBodyRot + camYaw) * Mth.DEG_TO_RAD;
        // Behind the heading is -forward; camYaw turns round from there.
        double dx = Mth.sin(a) * camDist * body;
        double dz = -Mth.cos(a) * camDist * body;
        return new Vec3(p.getX() + dx, p.getY() + camHeight * body + body * 0.2D, p.getZ() + dz);
    }

    /** Every tick: glide the camera toward its spot and aim it at the player. */
    private static void film(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (!filming || p == null) return;
        Vec3 want = camAnchor != null
                ? camAnchor.add(Mth.floor(arena.x), Mth.floor(arena.y), Mth.floor(arena.z)) : target(p);
        camPosO = camPos == null ? want : camPos;
        Vec3 next = camPos == null ? want : camPos.add(want.subtract(camPos).scale(0.22D));
        camPos = next;
        float size = VisualSize.of(p);
        Vec3 want2 = new Vec3(p.getX(), p.getY() + 1.8D * size * camAim, p.getZ());
        // The aim glides after the player too, so the camera turns calmly instead of twitching at every hop.
        camLook = camLook == null || camPosO == want ? want2 : camLook.add(want2.subtract(camLook).scale(0.3D));
        Vec3 d = camLook.subtract(next);
        camYawO = camYawNow;
        camPitchO = camPitchNow;
        camYawNow = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
        camPitchNow = (float) (-Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        if (camPosO == want) {
            camYawO = camYawNow;
            camPitchO = camPitchNow;
        }
    }

    // ---- scene helpers ----------------------------------------------------------------

    private static volatile Vec3 arena = Vec3.ZERO;

    /** Default settings plus the tour's own, live but never written to the config file. */
    private static void applyConfig(Minecraft mc) {
        var c = new GulliverConfig();
        c.itemRestrictions.enabled = true;
        c.itemRestrictions.rules.clear();
        var swords = new GulliverConfig.ItemRule();
        swords.items.add("minecraft:diamond_sword");
        swords.items.add("#minecraft:axes");
        swords.mode = GulliverConfig.ItemRule.DENY;
        swords.maxSize = 0.5D;
        c.itemRestrictions.rules.add(swords);
        var armor = new GulliverConfig.ItemRule();
        armor.items.add("minecraft:iron_chestplate");
        armor.mode = GulliverConfig.ItemRule.DENY;
        armor.minSize = 2.0001D;
        armor.penalty = GulliverConfig.ItemRestrictions.BREAK;
        c.itemRestrictions.rules.add(armor);
        c.starterSize.allowCustom = true;
        c.validate();
        onServer(mc, sp -> {
            GulliverConfig.INSTANCE = c;
            GulliverConfig.bump();
            var server = mc.getSingleplayerServer();
            if (server != null) gulliver.network.SizeSync.sendSettingsToAll(server);
        });
    }

    /** Flowers and tall grass all around, keeping a runway clear, and the arena's centre remembered. */
    private static void meadow(Minecraft mc) {
        onServer(mc, sp -> {
            arena = sp.position();
            ServerLevel level = (ServerLevel) sp.level();
            var rnd = new java.util.Random(4);
            String[] flowers = {"poppy", "dandelion", "cornflower", "allium", "azure_bluet", "oxeye_daisy",
                    "orange_tulip", "pink_tulip", "lily_of_the_valley", "short_grass", "short_grass", "short_grass",
                    "short_grass", "fern"};
            for (int i = 0; i < 700; i++) {
                int x = rnd.nextInt(80) - 40, z = rnd.nextInt(88) - 40;
                if (Math.abs(x) < 3 && z > -3 && z < 32) continue;
                if (x * x + z * z < 9) continue;
                String f = flowers[rnd.nextInt(flowers.length)];
                SelfTest.runCommand(level, String.format(Locale.ROOT,
                        "execute as @a at @s run setblock ~%d ~ ~%d minecraft:%s", x, z, f));
            }
            // A few trees on the edge.
            int[][] trees = {{30, 30}, {-32, 22}, {28, -28}, {-30, -26}, {4, 44}, {-12, 44}};
            for (int[] tr : trees) {
                for (int y = 0; y < 6; y++) {
                    SelfTest.runCommand(level, String.format(Locale.ROOT,
                            "execute as @a at @s run setblock ~%d ~%d ~%d minecraft:oak_log", tr[0], y, tr[1]));
                }
                SelfTest.runCommand(level, String.format(Locale.ROOT,
                        "execute as @a at @s run fill ~%d ~4 ~%d ~%d ~7 ~%d minecraft:oak_leaves[persistent=true] keep",
                        tr[0] - 2, tr[1] - 2, tr[0] + 2, tr[1] + 2));
            }
        });
    }

    /** Glides the player along +Z (backwards away from the row when negative), facing the same way. */
    private static void back(LocalPlayer p, double dz) {
        p.setPos(p.getX(), p.getY(), p.getZ() + dz);
    }

    private static void key(KeyMapping key, boolean down) {
        key.setDown(down);
    }

    private static void title(Minecraft mc, String title, String subtitle) {
        cmd(mc, "title @a times 10 70 20");
        cmd(mc, "title @a subtitle " + quote(subtitle));
        cmd(mc, "title @a title " + quote(title));
    }

    private static void sub(Minecraft mc, String text) {
        cmd(mc, "title @a actionbar " + quote(text));
    }

    private static String quote(String s) {
        return "\"" + s.replace("\"", "\\\"") + "\"";
    }

    private static void mark(String scene) {
        log("SCENE {} at {} ms tick {}", scene, System.currentTimeMillis(), t);
    }

    private static void log(String format, Object... args) {
        Gulliver.LOGGER.info(format, args);
    }

    private static void look(LocalPlayer p, float yaw, float pitch) {
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.yRotO = yaw;
        p.xRotO = pitch;
        p.yHeadRot = yaw;
    }

    private static void size(Minecraft mc, float size) {
        onServer(mc, sp -> ((IResizeableLiving) sp).setBaseSize(size));
    }

    private static void cmd(Minecraft mc, String command) {
        onServer(mc, sp -> SelfTest.runCommand((ServerLevel) sp.level(), command));
    }

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) return;
        java.util.UUID id = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            if (sp != null) action.accept(sp);
        });
    }
}
