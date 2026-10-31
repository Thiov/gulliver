package gulliver.client;

import gulliver.Gulliver;
import gulliver.api.IResizeableLiving;
import gulliver.common.ShoulderHelper;
import gulliver.debug.SelfTest;
import gulliver.network.Payloads;
import gulliver.platform.Services;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/**
 * A scripted tour of the breath gust and of eating, for looking at them
 * rather than testing them: run the self-test client with a file named
 * "gulliver-showcase" in the run folder and it plays each scene and saves
 * screenshots (run/screenshots), logging how far the wind moved things,
 * instead of the usual checks. Scenes: gusts at 8, 32, 1 and 1/8; eating a
 * pig in first and third person; a size-8 husk snatching a weakened
 * player.
 */
public final class ClientShowcase {
    private ClientShowcase() {}

    private static Boolean enabled;
    private static int t;
    private static byte blowing = -1;

    public static boolean active(Minecraft mc) {
        if (enabled == null) {
            enabled = java.nio.file.Files.exists(mc.gameDirectory.toPath().resolve("gulliver-showcase"));
        }
        return enabled;
    }

    /** Every client tick in the world; returns true once the tour is over. */
    public static boolean tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        t++;
        if (blowing >= 0 && t % 5 == 0) send(Payloads.CarryAction.BREATH);
        switch (t) {
            case 50 -> onServer(mc, sp -> {
                // Open the size range up to 32 for the colossus scene.
                var c = gulliver.common.GulliverConfig.INSTANCE.copy();
                c.general.maxEntitySize = 32.0D;
                c.general.maxEntityBaseSize = 32.0D;
                c.sizeLimit.maxPlayerSize = 32.0D;
                gulliver.common.GulliverConfig.apply(c);
            });
            case 60 -> {
                cmd(mc, "time set noon");
                cmd(mc, "weather clear");
                cmd(mc, "gamemode creative @a");
                cmd(mc, "clear @a");
                cmd(mc, "execute as @a at @s run fill ~-20 ~ ~-20 ~20 ~9 ~20 minecraft:air");
                cmd(mc, "execute as @a at @s run fill ~-20 ~10 ~-20 ~20 ~19 ~20 minecraft:air");
                cmd(mc, "execute as @a at @s run fill ~-20 ~-1 ~-20 ~20 ~-1 ~20 minecraft:grass_block");
                cmd(mc, "execute as @a at @s run fill ~-6 ~-1 ~5 ~6 ~-1 ~10 minecraft:sand");
                cmd(mc, "execute as @a at @s run fill ~-20 ~-1 ~13 ~20 ~-1 ~20 minecraft:water");
                look(p, 15.0F);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                size(mc, 8.0F);
            }
            case 80 -> {
                cmd(mc, "execute as @a at @s run summon pig ~-2 ~ ~9");
                cmd(mc, "execute as @a at @s run summon pig ~2 ~ ~11");
                cmd(mc, "execute as @a at @s run summon husk ~0 ~ ~8");
            }
            case 88 -> aimAtNearest(mc, p, EntityTypes.HUSK);
            case 90 -> {
                positions(mc, "before the size-8 gust");
                blow();
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            }
            case 100, 112 -> shot(mc);
            case 150 -> {
                positions(mc, "after 3 s of size-8 gust");
                stop();
                                cmd(mc, "kill @e[type=!player]");
                size(mc, 32.0F);
                look(p, 20.0F);
            }
            case 188 -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            case 190 -> blow();
            case 205, 215 -> shot(mc);
            case 230 -> {
                stop();
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                cmd(mc, "kill @e[type=!player]");
                size(mc, 1.0F);
                look(p, 10.0F);
            }
            case 250 -> blow();
            case 265 -> shot(mc);
            case 268 -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            case 276 -> shot(mc);
            case 280 -> {
                stop();
                                cmd(mc, "kill @e[type=!player]");
                size(mc, 0.125F);
            }
            case 296 -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            case 300 -> blow();
            case 315 -> shot(mc);
            case 330 -> {
                stop();
                                cmd(mc, "kill @e[type=!player]");
                size(mc, 8.0F);
                look(p, 0.0F);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            }
            case 340, 390 -> cmd(mc, "clear @a");
            case 345, 395 -> pickUpPig(mc);
            case 352, 360, 400, 404 -> mealOffset(mc, p);
            case 356, 408 -> {
                mealOffset(mc, p);
                shot(mc);
            }
            case 364, 416 -> shot(mc);
            case 392 -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            case 445 -> {
                size(mc, 1.0F);
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                cmd(mc, "gamemode survival @a");
                cmd(mc, "kill @e[type=!player]");
                onServer(mc, sp -> sp.setHealth(5.0F));
            }
            case 455 -> {
                cmd(mc, "execute as @a at @s run summon husk ~ ~ ~4");
                onServer(mc, sp -> {
                    for (Entity e : ((ServerLevel) sp.level()).getAllEntities()) {
                        if (e instanceof Mob m && m.getType() == EntityTypes.HUSK) {
                            ((IResizeableLiving) m).setBaseSize(8.0F);
                            m.setTarget(sp);
                        }
                    }
                });
            }
            case 470, 480, 490, 500, 515 -> {
                shot(mc);
                onServer(mc, sp -> Gulliver.LOGGER.info("showcase t={} player health {} alive {}", t, sp.getHealth(), sp.isAlive()));
            }
            case 560 -> {
                Gulliver.LOGGER.info("GULLIVER SHOWCASE DONE");
                return true;
            }
            default -> { }
        }
        if (t > 345 && t < 390 && t % 4 == 0) send(Payloads.CarryAction.EAT);
        if (t > 395 && t < 440 && t % 4 == 0) send(Payloads.CarryAction.EAT);
        return false;
    }

    private static void aimAtNearest(Minecraft mc, LocalPlayer p, net.minecraft.world.entity.EntityType<?> type) {
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e.getType() != type) continue;
            Vec3 to = e.getBoundingBox().getCenter().subtract(p.getEyePosition());
            float pitch = (float) Math.toDegrees(Math.atan2(-to.y, Math.sqrt(to.x * to.x + to.z * to.z)));
            look(p, pitch);
            Gulliver.LOGGER.info("showcase aim pitch {}", pitch);
            return;
        }
    }

    /** Where the creature in the local player's hand is, relative to the eyes, and how far up the lift is. */
    private static void mealOffset(Minecraft mc, LocalPlayer p) {
        java.util.UUID id = ((gulliver.access.IGulliverShoulderInternal) p).gulliver$getHandEntity();
        if (id == null) {
            Gulliver.LOGGER.info("showcase t={} client hand empty", t);
            return;
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e.getUUID().equals(id)) {
                Vec3 off = e.position().subtract(p.getEyePosition());
                Gulliver.LOGGER.info("showcase t={} meal at eye{} chew {} lift {}", t, off,
                        ((gulliver.access.IGulliverShoulderInternal) p).gulliver$getChewTicks(),
                        gulliver.common.Appetite.lift(p, 0.0F));
            }
        }
    }

    private static void blow() {
        blowing = 1;
        send(Payloads.CarryAction.BREATH);
    }

    private static void stop() {
        blowing = -1;
        send(Payloads.CarryAction.BREATH_STOP);
    }

    private static void send(byte action) {
        Services.platform().sendToServer(new Payloads.CarryAction(action));
    }

    private static void look(LocalPlayer p, float pitch) {
        p.setYRot(0.0F);
        p.setXRot(pitch);
        p.yRotO = 0.0F;
        p.xRotO = pitch;
    }

    private static void size(Minecraft mc, float size) {
        onServer(mc, sp -> ((IResizeableLiving) sp).setBaseSize(size));
    }

    private static void cmd(Minecraft mc, String command) {
        onServer(mc, sp -> SelfTest.runCommand((ServerLevel) sp.level(), command));
    }

    private static void pickUpPig(Minecraft mc) {
        onServer(mc, sp -> {
            Mob pig = EntityTypes.PIG.create((ServerLevel) sp.level()
                    //#if MC >= 1.21.2
                    , net.minecraft.world.entity.EntitySpawnReason.COMMAND
                    //#endif
            );
            if (pig == null) return;
            Vec3 at = sp.position().add(sp.getLookAngle().scale(2.0D));
            pig.setPos(at.x, at.y, at.z);
            sp.level().addFreshEntity(pig);
            boolean held = ShoulderHelper.pickUp(sp, pig);
            Gulliver.LOGGER.info("showcase picked up a pig: {}", held);
        });
    }

    private static void positions(Minecraft mc, String when) {
        onServer(mc, sp -> {
            for (Entity e : ((ServerLevel) sp.level()).getAllEntities()) {
                if (e instanceof Mob) {
                    Gulliver.LOGGER.info("showcase {}: {} at {} (distance {})", when, e.getType().getDescriptionId(),
                            e.position(), Math.round(e.distanceTo(sp) * 10.0D) / 10.0D);
                }
            }
        });
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

    private static void shot(Minecraft mc) {
        //#if MC >= 26.2
        net.minecraft.client.Screenshot.grab(mc.gameDirectory, mc.gameRenderer.mainRenderTarget(), msg -> { });
        //#else
        //$$ net.minecraft.client.Screenshot.grab(mc.gameDirectory, mc.getMainRenderTarget(), msg -> { });
        //#endif
    }
}
