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
    private static int t;
    private static final List<Step> STEPS = new ArrayList<>();
    private static int end;

    private record Step(int from, int to, IntConsumer action) {}

    public static boolean active(Minecraft mc) {
        if (enabled == null) {
            blocksTour = java.nio.file.Files.exists(mc.gameDirectory.toPath().resolve("gulliver-showcase-blocks"));
            enabled = blocksTour || java.nio.file.Files.exists(mc.gameDirectory.toPath().resolve("gulliver-showcase-fixes"));
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
        for (Step s : STEPS) {
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
