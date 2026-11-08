package gulliver.client;

import gulliver.Gulliver;
import gulliver.api.IResizeableLiving;
import gulliver.common.GulliverConfig;
import gulliver.debug.SelfTest;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * A scripted tour of the fixes for issues #13, #14, #16, #20, #21, #22 and
 * of particle scaling, for looking at them: run the self-test client with
 * a file named "gulliver-showcase-issues" in the run folder and it plays
 * each scene, saving screenshots (run/screenshots, in the order of the
 * "SHOT" lines in the log) and logging what it measured. Settings that
 * might not exist (a build from before these changes) are set by name and
 * skipped when missing, so the same tour shows the old behaviour too.
 */
public final class ClientShowcaseIssues {
    private ClientShowcaseIssues() {}

    private static Boolean enabled;
    private static int t;
    private static Vec3 arena;
    private static Vec3 cowStart;
    private static double jumpPeak;
    private static double jumpGround;

    public static boolean active(Minecraft mc) {
        if (enabled == null) {
            enabled = java.nio.file.Files.exists(mc.gameDirectory.toPath().resolve("gulliver-showcase-issues"));
        }
        return enabled;
    }

    /** Every client tick in the world; returns true once the tour is over. */
    public static boolean tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        t++;
        switch (t) {
            case 40 -> onServer(mc, sp -> {
                var c = GulliverConfig.INSTANCE.copy();
                c.general.minEntitySize = 1.0D / 32.0D;
                c.general.minEntityBaseSize = 1.0D / 32.0D;
                c.sizeLimit.minPlayerSize = 1.0D / 32.0D;
                c.sizeLimit.minMonsterSize = 1.0D / 32.0D;
                c.sizeLimit.minAnimalSize = 1.0D / 32.0D;
                GulliverConfig.apply(c);
                // The client follows the server's limits: tell it.
                var server = mc.getSingleplayerServer();
                if (server != null) gulliver.network.SizeSync.sendSettingsToAll(server);
            });
            case 50 -> {
                cmd(mc, "time set noon");
                cmd(mc, "weather clear");
                cmd(mc, "gamerule doDaylightCycle false");
                cmd(mc, "gamerule doMobSpawning false");
                cmd(mc, "gamemode creative @a");
                cmd(mc, "clear @a");
                cmd(mc, "kill @e[type=!player]");
                // In slabs: one fill may change at most 32768 blocks.
                for (int y = 0; y < 48; y += 12) {
                    cmd(mc, String.format(Locale.ROOT,
                            "execute as @a at @s run fill ~-22 ~%d ~-10 ~22 ~%d ~40 minecraft:air", y, y + 11));
                }
                cmd(mc, "execute as @a at @s run fill ~-22 ~-6 ~-10 ~22 ~-2 ~40 minecraft:stone");
                cmd(mc, "execute as @a at @s run fill ~-22 ~-1 ~-10 ~22 ~-1 ~40 minecraft:grass_block");
                onServer(mc, sp -> arena = sp.position());
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                hideHud(mc, true);
                look(p, 0.0F, 0.0F);
            }

            // ---- #20: the camera of a crouching 1/32 player ----------------
            case 70 -> {
                mark("camera");
                size(mc, 1.0F / 32.0F);
                cmd(mc, "execute as @a at @s run setblock ~ ~ ~2 minecraft:oak_log");
                look(p, 0.0F, 15.0F);
            }
            case 95 -> shot(mc, "camera-1_32-standing");
            case 96 -> mc.options.keyShift.setDown(true);
            case 120 -> {
                //#if MC >= 1.21.9
                double camY = mc.gameRenderer.mainCamera().position().y;
                //#else
                //$$ double camY = mc.gameRenderer.mainCamera().getPosition().y;
                //#endif
                log("camera t={} eye {} above the feet (entity eye {}), crouching {}", t,
                        round(camY - p.getY()), round(p.getEyeHeight()), p.isCrouching());
                shot(mc, "camera-1_32-crouching");
            }
            case 121 -> look(p, 0.0F, 60.0F);
            case 135 -> shot(mc, "camera-1_32-crouching-look-down");
            case 136 -> look(p, 0.0F, -20.0F);
            case 150 -> shot(mc, "camera-1_32-crouching-look-up");
            case 151 -> {
                mc.options.keyShift.setDown(false);
                size(mc, 1.0F);
                look(p, 0.0F, 0.0F);
                cmd(mc, "execute as @a at @s run setblock ~ ~ ~2 minecraft:air");
            }

            // ---- #22: name tags ----------------------------------------------
            case 170 -> {
                mark("nametags");
                // Name tags are part of the HUD: F1 hides them.
                hideHud(mc, false);
                float[] sizes = {0.125F, 0.5F, 1.0F, 2.0F, 4.0F};
                for (int i = 0; i < sizes.length; i++) {
                    cmd(mc, String.format(Locale.ROOT,
                            "execute as @a at @s run summon husk ~%d ~ ~12 {NoAI:1b,Silent:1b,Rotation:[180f,0f],Tags:[\"gv_tag%d\"]}",
                            -8 + 4 * i, i));
                }
            }
            case 175 -> onServer(mc, sp -> {
                float[] sizes = {0.125F, 0.5F, 1.0F, 2.0F, 4.0F};
                for (Entity e : all(sp)) {
                    for (int i = 0; i < sizes.length; i++) {
                        if (e instanceof LivingEntity le && tags(e).contains("gv_tag" + i)) {
                            ((IResizeableLiving) le).setBaseSize(sizes[i]);
                            le.setCustomName(Component.literal("Size " + SizeFormat.of(sizes[i])));
                            le.setCustomNameVisible(true);
                            le.setYRot(180.0F);
                            le.setYHeadRot(180.0F);
                            le.setYBodyRot(180.0F);
                        }
                    }
                }
            });
            case 176 -> look(p, 0.0F, -12.0F);
            case 205 -> shot(mc, "nametags-row");
            case 206 -> {
                // Down at a tiny's height, next to the tiny zombie.
                size(mc, 0.125F);
                if (arena != null) tp(mc, arena.x - 8.0D, arena.y, arena.z + 11.1D);
                look(p, 0.0F, 0.0F);
            }
            case 235 -> shot(mc, "nametags-tiny-view");
            case 236 -> {
                size(mc, 4.0F);
                if (arena != null) tp(mc, arena.x + 8.0D, arena.y, arena.z + 2.0D);
                look(p, 0.0F, 10.0F);
            }
            case 265 -> shot(mc, "nametags-giant-view");
            case 266 -> hideHud(mc, true);

            // ---- particles -----------------------------------------------------
            case 270 -> {
                mark("particles");
                cmd(mc, "kill @e[type=!player]");
                size(mc, 1.0F);
                if (arena != null) tp(mc, arena.x, arena.y, arena.z);
                look(p, 0.0F, 8.0F);
                cmd(mc, "execute as @a at @s run summon pig ~-4 ~ ~7 {NoAI:1b,Silent:1b,Tags:[\"gv_p0\"]}");
                cmd(mc, "execute as @a at @s run summon pig ~-1 ~ ~7 {NoAI:1b,Silent:1b,Tags:[\"gv_p1\"]}");
                cmd(mc, "execute as @a at @s run summon pig ~4 ~ ~9 {NoAI:1b,Silent:1b,Tags:[\"gv_p2\"]}");
            }
            case 275 -> {
                onServer(mc, sp -> {
                    for (Entity e : all(sp)) {
                        if (!(e instanceof LivingEntity le)) continue;
                        if (tags(e).contains("gv_p0")) ((IResizeableLiving) le).setBaseSize(0.25F);
                        if (tags(e).contains("gv_p2")) ((IResizeableLiving) le).setBaseSize(3.0F);
                    }
                });
                cmd(mc, "effect give @e[type=pig] minecraft:speed 120 1");
                setConfig("client", "particleScaling", false);
            }
            case 300 -> shot(mc, "particles-off");
            case 301 -> {
                cmd(mc, "particle minecraft:poof ~ ~ ~ 0 0 0 0 0");
                setConfig("client", "particleScaling", true);
            }
            case 330 -> shot(mc, "particles-on");
            case 331 -> {
                // A giant's own trail: big player, sprinting dust and potion swirls.
                cmd(mc, "kill @e[type=!player]");
                cmd(mc, "effect give @a minecraft:speed 30 1");
                size(mc, 4.0F);
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                look(p, 0.0F, 20.0F);
            }
            case 345 -> {
                mc.options.keyUp.setDown(true);
                mc.options.keySprint.setDown(true);
            }
            case 365 -> shot(mc, "particles-giant-sprint");
            case 366 -> {
                mc.options.keyUp.setDown(false);
                mc.options.keySprint.setDown(false);
                cmd(mc, "effect clear @a");
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                size(mc, 1.0F);
            }

            // ---- #16: creepers ------------------------------------------------
            case 380 -> {
                mark("creepers");
                if (arena != null) tp(mc, arena.x, arena.y, arena.z);
                look(p, 0.0F, 20.0F);
                float[] sizes = {0.5F, 1.0F, 2.0F};
                int[] xs = {-9, -1, 9};
                for (int i = 0; i < 3; i++) {
                    cmd(mc, String.format(Locale.ROOT,
                            "execute as @a at @s run summon creeper ~%d ~ ~16 {NoAI:1b,Tags:[\"gv_cv%d\"]}", xs[i], i));
                    cmd(mc, String.format(Locale.ROOT,
                            "execute as @a at @s run summon creeper ~%d ~ ~34 {NoAI:1b,Tags:[\"gv_cs%d\"]}", xs[i], i));
                }
            }
            case 385 -> onServer(mc, sp -> {
                float[] sizes = {0.5F, 1.0F, 2.0F};
                for (Entity e : all(sp)) {
                    for (int i = 0; i < 3; i++) {
                        if (e instanceof LivingEntity le && (tags(e).contains("gv_cv" + i) || tags(e).contains("gv_cs" + i))) {
                            ((IResizeableLiving) le).setBaseSize(sizes[i]);
                        }
                    }
                }
            });
            case 395 -> {
                // Near row: vanilla blasts (scaling off), sizes 1/2, 1 and 2.
                setConfig("general", "creeperExplosionScaling", false);
                if (arena != null) tp(mc, arena.x - 1.0D, arena.y + 3.0D, arena.z + 2.0D);
                look(p, 0.0F, 14.0F);
                p.getAbilities().flying = true;
                ignite(mc, "gv_cv");
            }
            case 426, 429, 433 -> shot(mc, "creepers-boom-vanilla");
            case 445 -> {
                // Far row: blasts that follow the size.
                setConfig("general", "creeperExplosionScaling", true);
                if (arena != null) tp(mc, arena.x - 1.0D, arena.y + 3.0D, arena.z + 20.0D);
                look(p, 0.0F, 14.0F);
                ignite(mc, "gv_cs");
            }
            case 476, 479, 483 -> shot(mc, "creepers-boom-scaled");
            case 490 -> {
                if (arena != null) tp(mc, arena.x - 1.0D, arena.y + 28.0D, arena.z + 25.0D);
                cmd(mc, "effect give @a minecraft:slow_falling 30 0 true");
                look(p, 0.0F, 90.0F);
                p.getAbilities().flying = true;
            }
            case 520 -> shot(mc, "creepers-craters-from-above");

            // ---- #14: pushing -------------------------------------------------
            case 530 -> {
                mark("push");
                p.getAbilities().flying = false;
                cmd(mc, "effect clear @a");
                cmd(mc, "kill @e[type=!player]");
                cmd(mc, "gamemode survival @a");
                size(mc, 0.25F);
                if (arena != null) tp(mc, arena.x, arena.y, arena.z - 6.0D);
                look(p, 0.0F, -8.0F);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            }
            case 540 -> summonCow(mc);
            case 550 -> {
                setConfig("general", "sizePushing", false);
                onServer(mc, sp -> cowStart = cow(sp) == null ? null : cow(sp).position());
                mc.options.keyUp.setDown(true);
                mc.options.keySprint.setDown(true);
            }
            case 600 -> shot(mc, "push-off");
            case 620 -> {
                mc.options.keyUp.setDown(false);
                mc.options.keySprint.setDown(false);
                logCowMove(mc, "push setting off");
            }
            case 622 -> {
                cmd(mc, "kill @e[type=!player]");
                if (arena != null) tp(mc, arena.x, arena.y, arena.z - 6.0D);
                look(p, 0.0F, -8.0F);
            }
            case 630 -> summonCow(mc);
            case 640 -> {
                setConfig("general", "sizePushing", true);
                onServer(mc, sp -> cowStart = cow(sp) == null ? null : cow(sp).position());
                mc.options.keyUp.setDown(true);
                mc.options.keySprint.setDown(true);
            }
            case 690 -> shot(mc, "push-on");
            case 710 -> {
                mc.options.keyUp.setDown(false);
                mc.options.keySprint.setDown(false);
                logCowMove(mc, "push setting on");
            }

            // ---- #21: the sprint jump toggle ------------------------------------
            case 720 -> {
                mark("sprintjump");
                cmd(mc, "kill @e[type=!player]");
                cmd(mc, "gamemode survival @a");
                size(mc, 0.125F);
                if (arena != null) tp(mc, arena.x - 10.0D, arena.y, arena.z);
                p.setYRot(-90.0F);
                p.yRotO = -90.0F;
                p.setXRot(10.0F);
                p.xRotO = 10.0F;
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                setConfig("client", "sprintFullJump", true);
            }
            case 735 -> startJumping(mc, p);
            case 790 -> stopJumping(mc, "full-block sprint jump on");
            case 795 -> {
                if (arena != null) tp(mc, arena.x - 10.0D, arena.y, arena.z);
                p.setYRot(-90.0F);
                p.yRotO = -90.0F;
                hideHud(mc, false);
                toggleSprintJump(mc);
            }
            case 800 -> shot(mc, "sprintjump-toggled");
            case 801 -> hideHud(mc, true);
            case 805 -> startJumping(mc, p);
            case 860 -> stopJumping(mc, "full-block sprint jump off");
            case 880 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                hideHud(mc, false);
                setConfig("client", "sprintFullJump", true);
                log("GULLIVER SHOWCASE DONE");
                return true;
            }
            default -> { }
        }
        if ((t >= 548 && t <= 622 || t >= 638 && t <= 712) && t % 6 == 0) {
            onServer(mc, sp -> {
                Mob c = cow(sp);
                if (c != null) {
                    log("push t={} cow at {} moving {} ground {}; player at {} size {}", t, c.position(),
                            c.getDeltaMovement(), c.onGround(), sp.position(),
                            ((gulliver.api.IResizeableEntity) sp).getSizeMultiplier());
                }
            });
        }
        // No advancement pop-ups over the pictures.
        //#if MC >= 26.2
        mc.gui.toastManager().clear();
        //#elif MC >= 1.21.2
        //$$ mc.getToastManager().clear();
        //#else
        //$$ mc.getToasts().clear();
        //#endif
        if ((t > 735 && t < 790) || (t > 805 && t < 860)) {
            if (p.onGround()) jumpGround = p.getY();
            jumpPeak = Math.max(jumpPeak, p.getY() - jumpGround);
        }
        // Hearts over the pigs: one entity event every few ticks, as breeding does.
        if (t > 276 && t < 331 && t % 6 == 0) {
            onServer(mc, sp -> {
                for (Entity e : all(sp)) {
                    if (e instanceof Mob m && tags(e).stream().anyMatch(s -> s.startsWith("gv_p"))) {
                        ((ServerLevel) sp.level()).broadcastEntityEvent(m, (byte) 18);
                    }
                }
            });
        }
        return false;
    }

    // ---- scene helpers ---------------------------------------------------------

    private static void startJumping(Minecraft mc, LocalPlayer p) {
        jumpPeak = 0.0D;
        jumpGround = p.getY();
        mc.options.keyUp.setDown(true);
        mc.options.keySprint.setDown(true);
        mc.options.keyJump.setDown(true);
    }

    private static void stopJumping(Minecraft mc, String what) {
        mc.options.keyUp.setDown(false);
        mc.options.keySprint.setDown(false);
        mc.options.keyJump.setDown(false);
        LocalPlayer p = mc.player;
        log("sprintjump {}: highest jump {} blocks ({} body heights) at size {}", what, round(jumpPeak),
                round(jumpPeak / Math.max(1.0E-4D, p.getBbHeight())), round(((gulliver.api.IResizeableEntity) p).getSizeMultiplier()));
    }

    private static void toggleSprintJump(Minecraft mc) {
        try {
            java.lang.reflect.Method m = KeyInputHandler.class.getDeclaredMethod("toggleSprintJump", Minecraft.class);
            m.setAccessible(true);
            m.invoke(null, mc);
        } catch (ReflectiveOperationException e) {
            log("showcase: no sprint jump toggle in this build");
        }
    }

    private static void summonCow(Minecraft mc) {
        cmd(mc, "execute as @a at @s run summon cow ~ ~ ~2.2 {Silent:1b,Tags:[\"gv_cow\"],Rotation:[90f,0f]}");
        cmd(mc, "effect give @e[type=cow] minecraft:slowness 60 255 true");
    }

    private static Mob cow(ServerPlayer sp) {
        for (Entity e : all(sp)) {
            if (e instanceof Mob m && m.isAlive() && tags(e).contains("gv_cow")) return m;
        }
        return null;
    }

    private static void logCowMove(Minecraft mc, String what) {
        onServer(mc, sp -> {
            Mob cow = cow(sp);
            if (cow == null || cowStart == null) {
                log("push {}: no cow", what);
                return;
            }
            log("push {}: a 0.25 player ran into a size-1 cow for 3.5 s; the cow moved {} blocks", what,
                    round(cow.position().distanceTo(cowStart)));
        });
    }

    private static void ignite(Minecraft mc, String tagPrefix) {
        onServer(mc, sp -> {
            for (Entity e : all(sp)) {
                if (e instanceof net.minecraft.world.entity.monster.Creeper c
                        && tags(e).stream().anyMatch(s -> s.startsWith(tagPrefix))) {
                    c.ignite();
                }
            }
        });
    }

    // ---- plumbing ----------------------------------------------------------------

    /** Sets a config switch by name; quietly skipped in a build that doesn't have it. */
    private static void setConfig(String section, String field, Object value) {
        try {
            Object part = GulliverConfig.class.getField(section).get(GulliverConfig.INSTANCE);
            part.getClass().getField(field).set(part, value);
            log("showcase config {}.{} = {}", section, field, value);
        } catch (ReflectiveOperationException e) {
            log("showcase config {}.{}: not in this build", section, field);
        }
    }

    /** F1: hides the HUD, and with it name tags. */
    private static void hideHud(Minecraft mc, boolean hidden) {
        //#if MC >= 26.2
        if (mc.gui.hud.isHidden() != hidden) mc.gui.hud.toggle();
        //#else
        //$$ mc.options.hideGui = hidden;
        //#endif
    }

    private static java.util.Set<String> tags(Entity e) {
        //#if MC >= 26.1
        return e.entityTags();
        //#else
        //$$ return e.getTags();
        //#endif
    }

    private static Iterable<Entity> all(ServerPlayer sp) {
        return ((ServerLevel) sp.level()).getAllEntities();
    }

    private static void mark(String scene) {
        log("SCENE {} at {} ms", scene, System.currentTimeMillis());
    }

    private static void log(String format, Object... args) {
        Gulliver.LOGGER.info(format, args);
    }

    private static double round(double v) {
        return Math.round(v * 1000.0D) / 1000.0D;
    }

    private static void look(LocalPlayer p, float yaw, float pitch) {
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.yRotO = yaw;
        p.xRotO = pitch;
    }

    private static void tp(Minecraft mc, double x, double y, double z) {
        cmd(mc, String.format(Locale.ROOT, "tp @a %.3f %.3f %.3f", x, y, z));
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

    private static void shot(Minecraft mc, String label) {
        log("SHOT {} at {} ms", label, System.currentTimeMillis());
        //#if MC >= 26.2
        net.minecraft.client.Screenshot.grab(mc.gameDirectory, mc.gameRenderer.mainRenderTarget(), msg -> { });
        //#else
        //$$ net.minecraft.client.Screenshot.grab(mc.gameDirectory, mc.getMainRenderTarget(), msg -> { });
        //#endif
    }
}
