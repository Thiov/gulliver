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

    public static void tick(Minecraft mc) {
        if (!SelfTest.ENABLED) return;
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        ticksInWorld++;
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
                        "local player grew to 4");
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                resizeOnServer(mc, 1.0F);
            }
            case 260 -> {
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
