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
 * A scripted tour for filming: tiny creatures staying in view (#26),
 * items restricted by size (#30) and tiny wings, from the metamorphosis to
 * flying with each kind. Run the self-test client with a file named
 * "gulliver-showcase-wings" in the run folder; it plays the scenes (logging
 * "SCENE" lines with their times, for cutting the film) and quits. Keys are
 * pressed by setting their key mappings, as the keyboard would, and a
 * smoothed film camera (a client-side marker the view is taken from) follows
 * or circles the player. With a "gulliver-showcase-slow" file as well the
 * game runs at a quarter speed (/tick rate 5), for a slow renderer to film
 * smoothly; speeding the film up four times gives the real pace.
 */
public final class ClientShowcaseWings {
    private ClientShowcaseWings() {}

    /** Draws tiny creatures with vanilla's distance rule, for the "before" shot of #26. */
    public static boolean vanillaCulling;

    private static Boolean enabled;
    private static int t;
    private static final List<Step> STEPS = new ArrayList<>();
    private static int end;

    private record Step(int from, int to, IntConsumer action) {}

    public static boolean active(Minecraft mc) {
        if (enabled == null) {
            enabled = java.nio.file.Files.exists(mc.gameDirectory.toPath().resolve("gulliver-showcase-wings"));
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
            log("showcase t={} fps={} y={} yaw={} head={} body={} flight={}", t, mc.getFps(),
                    p == null ? 0 : Math.round(p.getY() * 100) / 100.0, p == null ? 0 : p.getYRot(),
                    p == null ? 0 : p.yHeadRot, p == null ? 0 : p.yBodyRot,
                    p == null ? 0 : ((gulliver.access.IGulliverWings) p).gulliver$wingFlight());
        }
        boolean over = t > end + 40;
        if (over) camOff(mc);
        return over;
    }

    private static void script(Minecraft mc) {
        LocalPlayer p = mc.player;
        boolean slow = java.nio.file.Files.exists(mc.gameDirectory.toPath().resolve("gulliver-showcase-slow"));
        // ---- setup -------------------------------------------------------------------
        at(20, () -> {
            mark("start");
            if (slow) cmd(mc, "tick rate 5");
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
            cmd(mc, "advancement revoke @a only gulliver:metamorphosis");
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

        // ---- wings: metamorphosis ---------------------------------------------------------
        int s3 = s2 + 400;
        at(s3, () -> {
            mark("metamorphosis");
            title(mc, "Tiny Wings", "A tiny eats a phantom membrane...");
            size(mc, 0.25F);
            look(p, 0.0F, 10.0F);
            p.yBodyRot = p.yBodyRotO = 0.0F;
            cmd(mc, "item replace entity @a weapon.mainhand with minecraft:phantom_membrane 4");
            camOn(mc, 35.0F, 1.3F, 0.45F, 0.62F);
        });
        eatAt(mc, s3 + 60);
        during(s3 + 60, s3 + 150, i -> camYaw = 35.0F - i * 0.9F);
        at(s3 + 130, () -> sub(mc, "...and grows fly wings"));

        // ---- wings: hover and fly --------------------------------------------------------
        int s4 = s3 + 170;
        at(s4, () -> {
            mark("fly");
            onServer(mc, sp -> sp.teleportTo(arena.x, arena.y + 0.2D, arena.z));
            title(mc, "Fly wings", "Hold jump to take off");
            chase(1.6F, 0.45F, 22.0F);
            look(p, 0.0F, 0.0F);
        });
        // Jump and keep holding: the wings open near the top and climb.
        during(s4 + 20, s4 + 52, i -> key(mc.options.keyJump, true));
        at(s4 + 53, () -> {
            key(mc.options.keyJump, false);
            sub(mc, "Hover: wings beating, gently bobbing in place");
        });
        during(s4 + 60, s4 + 115, i -> camYaw = 22.0F + i * 1.0F);
        at(s4 + 120, () -> sub(mc, "Fly the way you look - sprint to dart"));
        during(s4 + 120, s4 + 200, i -> {
            key(mc.options.keyUp, true);
            look(p, i * 1.6F, -4.0F);
        });
        during(s4 + 200, s4 + 225, i -> {
            key(mc.options.keySprint, true);
            look(p, 128.0F + i * 2.2F, -2.0F);
        });
        at(s4 + 226, () -> {
            key(mc.options.keySprint, false);
            key(mc.options.keyUp, false);
            sub(mc, "Hold jump to climb");
        });
        during(s4 + 230, s4 + 260, i -> key(mc.options.keyJump, true));
        at(s4 + 261, () -> {
            key(mc.options.keyJump, false);
            sub(mc, "Hold sneak to drop");
        });
        during(s4 + 275, s4 + 290, i -> key(mc.options.keyShift, true));
        at(s4 + 291, () -> {
            key(mc.options.keyShift, false);
            camOff(mc);
            sub(mc, "The bar under the crosshair: your wings' stamina");
        });
        during(s4 + 300, s4 + 360, i -> {
            key(mc.options.keyUp, true);
            look(p, 183.0F + i * 2.0F, -3.0F);
        });
        at(s4 + 361, () -> key(mc.options.keyUp, false));
        at(s4 + 420, () -> {
            chase(1.6F, 0.6F, 30.0F);
            sub(mc, "Land to fold them and rest");
        });
        during(s4 + 430, s4 + 500, i -> key(mc.options.keyShift, i < 60));

        // ---- butterfly ------------------------------------------------------------------
        int s5 = s4 + 520;
        at(s5, () -> {
            mark("butterfly");
            onServer(mc, sp -> sp.teleportTo(arena.x, arena.y + 0.2D, arena.z));
            title(mc, "Butterfly wings", "Eat a flower: the wings take its colour");
            cmd(mc, "item replace entity @a weapon.mainhand with minecraft:cornflower 4");
            p.yBodyRot = p.yBodyRotO = p.getYRot();
            orbit(1.5F, 0.4F, 60.0F);
        });
        eatAt(mc, s5 + 40);
        during(s5 + 40, s5 + 200, i -> camYaw = 60.0F + i * 0.7F);
        at(s5 + 120, () -> sub(mc, "At rest they open and close their wings now and then"));
        at(s5 + 200, () -> chase(1.8F, 0.5F, 25.0F));
        during(s5 + 210, s5 + 240, i -> key(mc.options.keyJump, true));
        at(s5 + 241, () -> {
            key(mc.options.keyJump, false);
            sub(mc, "Slow, graceful flaps, gliding in between");
        });
        during(s5 + 250, s5 + 380, i -> {
            key(mc.options.keyUp, true);
            look(p, p.getYRot() + 0.9F, -6.0F);
        });
        at(s5 + 381, () -> key(mc.options.keyUp, false));
        at(s5 + 395, () -> {
            sub(mc, "...or a monarch, from an orange tulip");
            cmd(mc, "item replace entity @a weapon.mainhand with minecraft:orange_tulip 4");
        });
        eatAt(mc, s5 + 410);
        during(s5 + 480, s5 + 560, i -> {
            key(mc.options.keyUp, true);
            look(p, p.getYRot() - 1.0F, -4.0F);
        });
        at(s5 + 561, () -> key(mc.options.keyUp, false));
        during(s5 + 570, s5 + 650, i -> key(mc.options.keyShift, i < 70));

        // ---- bee ------------------------------------------------------------------------
        int s6 = s5 + 670;
        at(s6, () -> {
            mark("bee");
            onServer(mc, sp -> sp.teleportTo(arena.x + 4.0D, arena.y + 0.2D, arena.z - 6.0D));
            title(mc, "Bee wings", "Honeycomb: strong climbers with stamina to spare");
            cmd(mc, "item replace entity @a weapon.mainhand with minecraft:honeycomb 4");
            orbit(1.4F, 0.4F, 40.0F);
        });
        eatAt(mc, s6 + 30);
        during(s6 + 30, s6 + 100, i -> camYaw = 40.0F + i * 0.8F);
        at(s6 + 100, () -> chase(1.7F, 0.45F, 20.0F));
        during(s6 + 110, s6 + 170, i -> key(mc.options.keyJump, true));
        at(s6 + 171, () -> key(mc.options.keyJump, false));
        during(s6 + 180, s6 + 260, i -> {
            key(mc.options.keyUp, true);
            look(p, p.getYRot() - 1.5F, -2.0F);
        });
        at(s6 + 261, () -> key(mc.options.keyUp, false));
        during(s6 + 270, s6 + 350, i -> key(mc.options.keyShift, i < 70));

        // ---- fairy ----------------------------------------------------------------------
        int s7 = s6 + 370;
        at(s7, () -> {
            mark("fairy");
            onServer(mc, sp -> sp.teleportTo(arena.x - 4.0D, arena.y + 0.2D, arena.z - 4.0D));
            title(mc, "Fairy wings", "An amethyst shard: glowing wings and a trail of sparkles");
            cmd(mc, "time set 12600");
            cmd(mc, "item replace entity @a weapon.mainhand with minecraft:amethyst_shard 4");
            orbit(1.4F, 0.4F, 30.0F);
        });
        eatAt(mc, s7 + 40);
        during(s7 + 40, s7 + 110, i -> camYaw = 30.0F + i * 0.8F);
        at(s7 + 110, () -> chase(1.7F, 0.45F, 20.0F));
        during(s7 + 120, s7 + 150, i -> key(mc.options.keyJump, true));
        at(s7 + 151, () -> key(mc.options.keyJump, false));
        during(s7 + 160, s7 + 300, i -> {
            key(mc.options.keyUp, true);
            look(p, p.getYRot() + 1.2F, -3.0F);
        });
        at(s7 + 301, () -> key(mc.options.keyUp, false));
        at(s7 + 320, () -> title(mc, "Gulliver Unbound", "Tiny wings"));
        during(s7 + 330, s7 + 400, i -> key(mc.options.keyShift, i < 60));
        at(s7 + 420, () -> mark("end"));
    }

    // ---- film camera ------------------------------------------------------------------

    private static boolean filming;
    private static boolean camFollowsYaw;
    /** Where the camera sits, as an angle round the player measured from its back (0 = straight behind). */
    private static float camYaw;
    private static float camDist;
    private static float camHeight;
    /** Height on the body the camera aims at, in body heights. */
    private static float camAim = 0.6F;
    private static float camHeading;
    private static Vec3 camPos, camPosO;
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
        camFollowsYaw = false;
        start(mc);
    }

    /** Circles round at a fixed heading (camYaw moved by the script). */
    private static void orbit(float dist, float height, float yaw) {
        camOn(Minecraft.getInstance(), yaw, dist, height, 0.6F);
    }

    /** Follows behind the flyer, turning as it turns. */
    private static void chase(float dist, float height, float yaw) {
        camOn(Minecraft.getInstance(), yaw, dist, height, 0.55F);
        camFollowsYaw = true;
    }

    private static void start(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        if (!filming) {
            camHeading = p.getYRot();
            camPos = camPosO = null;
        }
        filming = true;
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }

    private static void camOff(Minecraft mc) {
        filming = false;
        camPos = camPosO = null;
        mc.options.setCameraType(CameraType.FIRST_PERSON);
    }

    private static Vec3 target(LocalPlayer p) {
        float size = VisualSize.of(p);
        float body = 1.8F * size;
        if (camFollowsYaw) camHeading = Mth.rotLerp(0.12F, camHeading, p.getYRot());
        else camHeading = p.yBodyRot;
        float a = (camHeading + camYaw) * Mth.DEG_TO_RAD;
        // Behind the heading is -forward; camYaw turns round from there.
        double dx = Mth.sin(a) * camDist * body;
        double dz = -Mth.cos(a) * camDist * body;
        return new Vec3(p.getX() + dx, p.getY() + camHeight * body + body * 0.2D, p.getZ() + dz);
    }

    /** Every tick: glide the camera toward its spot and aim it at the player. */
    private static void film(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (!filming || p == null) return;
        Vec3 want = target(p);
        camPosO = camPos == null ? want : camPos;
        Vec3 next = camPos == null ? want : camPos.add(want.subtract(camPos).scale(0.22D));
        camPos = next;
        float size = VisualSize.of(p);
        Vec3 aim = new Vec3(p.getX(), p.getY() + 1.8D * size * camAim, p.getZ());
        Vec3 d = aim.subtract(next);
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

    private static Vec3 arena = Vec3.ZERO;

    /** Default settings plus the tour's own, live but never written to the config file. */
    private static void applyConfig(Minecraft mc) {
        var c = new GulliverConfig();
        c.general.tinyWings = true;
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

    /** Sneak + right-click the air with what is in hand, at this tick. */
    private static void eatAt(Minecraft mc, int tick) {
        at(tick - 6, () -> key(mc.options.keyShift, true));
        at(tick, () -> {
            LocalPlayer p = mc.player;
            if (p != null && mc.gameMode != null) mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
        });
        at(tick + 6, () -> key(mc.options.keyShift, false));
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
