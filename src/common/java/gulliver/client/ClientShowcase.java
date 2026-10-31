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
 * instead of the usual checks. Scenes: gusts at 8, 32, 4 and 16 (and the
 * view right after switching to third person at 32); eating a pig in
 * first and third person; a size-8 husk grabbing a player with both
 * hands; wind skimming over water, bubbles when blowing under it, and
 * wind blown up at the clouds.
 */
public final class ClientShowcase {
    private ClientShowcase() {}

    private static Boolean enabled;
    private static int t;
    private static byte blowing = -1;
    private static Vec3 arena;

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
                onServer(mc, sp -> arena = sp.position());
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
            }
            case 100, 112, 124 -> shot(mc);
            case 150 -> {
                positions(mc, "after 3 s of size-8 gust");
                stop();
                cmd(mc, "kill @e[type=!player]");
                size(mc, 32.0F);
                look(p, 20.0F);
            }
            case 188 -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            case 189, 191, 192, 194 -> shot(mc); // anything see-through right after the switch?
            case 190 -> blow();
            case 205, 215 -> shot(mc);
            case 230 -> {
                stop();
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                cmd(mc, "kill @e[type=!player]");
                size(mc, 4.0F);
                look(p, 10.0F);
            }
            case 250 -> blow();
            case 265 -> shot(mc);
            case 280 -> {
                stop();
                cmd(mc, "kill @e[type=!player]");
                size(mc, 16.0F);
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            }
            case 300 -> blow();
            case 315 -> shot(mc);
            case 330 -> {
                stop();
                cmd(mc, "kill @e[type=!player]");
                size(mc, 8.0F);
                look(p, 0.0F);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            }
            case 340, 412 -> cmd(mc, "clear @a");
            case 345, 415 -> pickUpPig(mc);
            case 369 -> Gulliver.LOGGER.info("showcase held pig drifted at most {} from its slot while turning", drift);
            case 380, 384, 388 -> {
                mealOffset(mc, p);
                shot(mc);
            }
            case 410 -> {
                look(p, 0.0F);
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            }
            case 432, 440 -> shot(mc);
            case 470 -> {
                size(mc, 1.0F);
                look(p, 0.0F);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                cmd(mc, "gamemode survival @a");
                cmd(mc, "kill @e[type=!player]");
                onServer(mc, sp -> sp.setHealth(sp.getMaxHealth()));
            }
            case 475 -> {
                cmd(mc, "execute as @a at @s run summon husk ~ ~ ~5 {NoAI:1b,Rotation:[180f,0f]}");
                onServer(mc, sp -> {
                    for (Entity e : ((ServerLevel) sp.level()).getAllEntities()) {
                        if (e instanceof Mob m && m.getType() == EntityTypes.HUSK) ((IResizeableLiving) m).setBaseSize(8.0F);
                    }
                });
            }
            case 488 -> {
                // The husk overlooks a player an eighth its size...
                onServer(mc, sp -> Gulliver.LOGGER.info("showcase husk notices the player: {}", huskSees(sp)));
                // Facing away, front camera: the husk towers behind the player.
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                look(p, 12.0F);
                p.setYRot(180.0F);
                p.yRotO = 180.0F;
            }
            case 490 -> onServer(mc, sp -> {
                // ...until it's hurt by it; then a grab that lands (most miss) does this.
                for (Entity e : ((ServerLevel) sp.level()).getAllEntities()) {
                    if (e instanceof Mob m && m.getType() == EntityTypes.HUSK) {
                        m.setLastHurtByMob(sp);
                        Gulliver.LOGGER.info("showcase hurt husk notices the player: {}", huskSees(sp));
                        gulliver.common.Appetite.snatch(m, sp);
                    }
                }
            });
            case 492, 498, 506, 514, 522, 532 -> shot(mc);
            case 540 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                look(p, 0.0F);
            }
            case 545 -> {
                shot(mc);
                onServer(mc, sp -> Gulliver.LOGGER.info("showcase t={} player health {} held {}",
                        t, sp.getHealth(), ((gulliver.access.IGulliverShoulderInternal) sp).gulliver$getHoldingEntity() != null));
            }
            case 548 -> {
                // From the side: a husk grabbing a pig at its feet and lifting it to its mouth.
                cmd(mc, "gamemode creative @a");
                cmd(mc, "kill @e[type=!player]");
                if (arena != null) {
                    cmd(mc, String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f", arena.x, arena.y, arena.z));
                    cmd(mc, String.format(java.util.Locale.ROOT, "summon husk %.2f %.2f %.2f {NoAI:1b,Rotation:[180f,0f]}",
                            arena.x + 24.0D, arena.y, arena.z));
                    cmd(mc, String.format(java.util.Locale.ROOT, "summon pig %.2f %.2f %.2f {NoAI:1b}",
                            arena.x + 24.0D, arena.y, arena.z - 5.0D));
                }
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                look(p, -15.0F);
                p.setYRot(-90.0F);
                p.yRotO = -90.0F;
            }
            case 550 -> onServer(mc, sp -> {
                for (Entity e : ((ServerLevel) sp.level()).getAllEntities()) {
                    if (e instanceof Mob m && m.getType() == EntityTypes.HUSK) ((IResizeableLiving) m).setBaseSize(8.0F);
                }
            });
            case 562 -> onServer(mc, sp -> {
                Mob husk = null, pig = null;
                for (Entity e : ((ServerLevel) sp.level()).getAllEntities()) {
                    if (e instanceof Mob m && m.getType() == EntityTypes.HUSK) husk = m;
                    if (e instanceof Mob m && m.getType() == EntityTypes.PIG) pig = m;
                }
                if (husk != null && pig != null) gulliver.common.Appetite.snatch(husk, pig);
                Gulliver.LOGGER.info("showcase side grab: husk {} pig {} held {}", husk != null, pig != null,
                        pig != null && ((gulliver.access.IGulliverShoulderInternal) pig).gulliver$getHoldingEntity() != null);
            });
            case 575 -> onServer(mc, sp -> {
                for (Entity e : ((ServerLevel) sp.level()).getAllEntities()) {
                    if (e instanceof Mob m && (m.getType() == EntityTypes.PIG || m.getType() == EntityTypes.HUSK)) {
                        Gulliver.LOGGER.info("showcase side grab t=575: {} at {} size {} held {} chew {}", m.getType().getDescriptionId(),
                                m.position(), ((gulliver.api.IResizeableEntity) m).getSizeMultiplier(),
                                ((gulliver.access.IGulliverShoulderInternal) m).gulliver$getHoldingEntity() != null,
                                ((gulliver.access.IGulliverShoulderInternal) m).gulliver$getChewTicks());
                    }
                }
            });
            case 564, 572, 582, 594, 604 -> shot(mc);
            case 620 -> {
                cmd(mc, "gamemode creative @a");
                cmd(mc, "kill @e[type=!player]");
                size(mc, 4.0F);
                if (arena != null) {
                    cmd(mc, String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f", arena.x, arena.y, arena.z));
                    cmd(mc, "execute as @a at @s run fill ~-6 ~-12 ~5 ~6 ~-1 ~17 minecraft:water");
                }
                look(p, 35.0F);
            }
            case 635 -> blow();
            case 650, 658 -> shot(mc);
            case 665 -> {
                stop();
                if (arena != null) {
                    cmd(mc, String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f", arena.x, arena.y - 11.5D, arena.z + 11.0D));
                }
                look(p, -10.0F);
            }
            case 675 -> blow();
            case 688, 694 -> shot(mc);
            case 700 -> {
                onServer(mc, sp -> Gulliver.LOGGER.info("showcase underwater: eye in water {}, air {}",
                        sp.isEyeInFluid(net.minecraft.tags.FluidTags.WATER), sp.getAirSupply()));
                stop();
            }
            case 705 -> {
                // Up at the clouds: the wind must stay in front of them.
                size(mc, 16.0F);
                if (arena != null) {
                    cmd(mc, String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f", arena.x, arena.y, arena.z));
                }
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                look(p, -28.0F);
            }
            case 718 -> blow();
            case 738, 746 -> shot(mc);
            case 750 -> stop();
            case 760 -> {
                Gulliver.LOGGER.info("GULLIVER SHOWCASE DONE");
                return true;
            }
            default -> { }
        }
        // Sweep the size-8 gust from left to right: the wind should trail, not follow like a beam.
        if (t > 92 && t < 140) p.setYRot(-40.0F + (t - 92) * 1.7F);
        // Hold the pig and turn: it must stay on its slot every tick.
        if (t > 345 && t < 369) {
            p.setYRot(p.getYRot() + 4.0F);
            trackDrift(mc, p);
        }
        if (t >= 370 && t < 405 && t % 4 == 0) send(Payloads.CarryAction.EAT);
        if (t > 418 && t < 455 && t % 4 == 0) send(Payloads.CarryAction.EAT);
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
                float toMeal = (float) Math.toDegrees(Math.atan2(-off.x, off.z));
                Gulliver.LOGGER.info("showcase t={} meal at eye{} ({} degrees off the view) chew {} lift {}", t, off,
                        Math.round(net.minecraft.util.Mth.wrapDegrees(toMeal - p.getYRot())),
                        ((gulliver.access.IGulliverShoulderInternal) p).gulliver$getChewTicks(),
                        gulliver.common.Appetite.lift(p, 0.0F));
            }
        }
    }

    private static double drift;

    /** How far the held creature is from the slot its carrier last put it in. */
    private static void trackDrift(Minecraft mc, LocalPlayer p) {
        java.util.UUID id = ((gulliver.access.IGulliverShoulderInternal) p).gulliver$getHandEntity();
        if (id == null) return;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!e.getUUID().equals(id)) continue;
            Vec3 slot = ((gulliver.access.IGulliverShoulderInternal) e).gulliver$getLastSlot();
            if (slot != null) drift = Math.max(drift, slot.distanceTo(e.position()));
        }
    }

    private static boolean huskSees(ServerPlayer sp) {
        for (Entity e : ((ServerLevel) sp.level()).getAllEntities()) {
            if (e instanceof Mob m && m.getType() == EntityTypes.HUSK) {
                net.minecraft.world.entity.ai.targeting.TargetingConditions c =
                        net.minecraft.world.entity.ai.targeting.TargetingConditions.forCombat().ignoreLineOfSight();
                //#if MC >= 1.21.2
                return c.test((ServerLevel) sp.level(), m, sp);
                //#else
                //$$ return c.test(m, sp);
                //#endif
            }
        }
        return false;
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
