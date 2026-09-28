package gulliver.client;

import gulliver.Gulliver;
import gulliver.api.IResizeableEntity;
import gulliver.debug.SelfTest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.util.ArrayList;
import java.util.List;

/**
 * Client half of the -Dgulliver.selftest run (see SelfTest). Once in a
 * world: applies every client mixin, shrinks and grows the local player
 * through the integrated server, lets a few frames render at each size
 * and in third person, then quits the game.
 */
public final class ClientSelfTest {
    private ClientSelfTest() {}

    private static int ticksInWorld;
    private static final List<String> FAILURES = new ArrayList<>();

    private static int ticksTotal;
    private static double grappleStartY;
    private static double grapplePeakY;

    /**
     * Server side: shrink the player to 0.125, hand them a fishing rod,
     * build a stone pillar next to them and throw a bobber at its upper
     * side — it should bite into the pillar and stay there.
     */
    private static void setUpGrapple(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        java.util.UUID id = mc.player.getUUID();
        server.execute(() -> {
            var sp = server.getPlayerList().getPlayer(id);
            if (sp == null) return;
            ((gulliver.api.IResizeableLiving) sp).setBaseSize(0.125F);
            sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FISHING_ROD));
            var level = (net.minecraft.server.level.ServerLevel) sp.level();
            net.minecraft.core.BlockPos base = sp.blockPosition().east(2);
            for (int dy = 0; dy < 5; dy++) {
                level.setBlockAndUpdate(base.above(dy), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            }
            var hook = new net.minecraft.world.entity.projectile.FishingHook(sp, level, 0, 0);
            hook.setPos(base.getX() - 0.4D, sp.getY() + 3.5D, base.getZ() + 0.5D);
            hook.setDeltaMovement(0.4D, 0.0D, 0.0D);
            level.addFreshEntity(hook);
        });
    }

    public static void tick(Minecraft mc) {
        if (!SelfTest.ENABLED) return;
        // Never leave a window hanging: bail out if we don't reach a world
        // (a stray confirmation screen, a failed quick-play, ...).
        if (++ticksTotal > 2400 && ticksInWorld < 300) {
            Gulliver.LOGGER.error("GULLIVER SELFTEST CLIENT FAIL [stuck on {}]",
                    mc.screen == null ? "no screen" : mc.screen.getClass().getName());
            mc.stop();
            return;
        }
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        ticksInWorld++;
        if (ticksInWorld > 262 && ticksInWorld < 285) grapplePeakY = Math.max(grapplePeakY, p.getY());
        switch (ticksInWorld) {
            case 100 -> {
                try {
                    MixinEnvironment.getCurrentEnvironment().audit();
                    Gulliver.LOGGER.info("selftest ok   client mixin audit");
                } catch (Throwable t) {
                    FAILURES.add("client mixin audit: " + t);
                    Gulliver.LOGGER.error("client mixin audit failed", t);
                }
                resizeOnServer(mc, 0.25F);
            }
            case 140 -> {
                check(Math.abs(((IResizeableEntity) p).getSizeMultiplier() - 0.25F) < 1.0E-3F,
                        "local player shrank to 0.25 (got " + ((IResizeableEntity) p).getSizeMultiplier() + ")");
                check(Math.abs(p.getBbHeight() - 1.8F * 0.25F) < 0.05F, "local player hitbox shrank");
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            }
            case 180 -> resizeOnServer(mc, 4.0F);
            case 220 -> {
                check(Math.abs(((IResizeableEntity) p).getSizeMultiplier() - 4.0F) < 1.0E-3F,
                        "local player grew to 4 (got " + ((IResizeableEntity) p).getSizeMultiplier() + ")");
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                resizeOnServer(mc, 1.0F);
            }
            case 240 -> setUpGrapple(mc);
            case 262 -> {
                var server = mc.getSingleplayerServer();
                if (server != null) server.execute(() -> {
                    var sp = server.getPlayerList().getPlayer(p.getUUID());
                    boolean anchored = sp != null && sp.fishing != null
                            && sp.fishing.getDeltaMovement().lengthSqr() < 1.0E-6D;
                    gulliver.Gulliver.LOGGER.info("selftest {} grapple hook anchored on the wall", anchored ? "ok  " : "FAIL");
                    if (!anchored) FAILURES.add("grapple hook anchored");
                    if (sp != null && sp.fishing != null) sp.fishing.retrieve(sp.getMainHandItem());
                });
                grappleStartY = p.getY();
                grapplePeakY = p.getY();
            }
            case 285 -> {
                double rise = grapplePeakY - grappleStartY;
                check(rise > 1.0D, "reeling in an anchored hook pulls the tiny up (rose " + rise + ")");
                resizeOnServer(mc, 1.0F);
            }
            case 300 -> {
                if (FAILURES.isEmpty()) Gulliver.LOGGER.info("GULLIVER SELFTEST CLIENT PASS");
                else Gulliver.LOGGER.error("GULLIVER SELFTEST CLIENT FAIL {}", FAILURES);
                mc.stop();
            }
            default -> { }
        }
    }

    /** Resize the local player's server-side twin; the size packet brings it back. */
    private static void resizeOnServer(Minecraft mc, float size) {
        var server = mc.getSingleplayerServer();
        if (server == null) {
            FAILURES.add("no integrated server");
            return;
        }
        java.util.UUID id = mc.player.getUUID();
        server.execute(() -> {
            var sp = server.getPlayerList().getPlayer(id);
            if (sp != null) ((gulliver.api.IResizeableLiving) sp).setBaseSize(size);
        });
    }

    private static void check(boolean ok, String what) {
        if (!ok) FAILURES.add(what);
        Gulliver.LOGGER.info("selftest {} {}", ok ? "ok  " : "FAIL", what);
    }
}
