package gulliver.client;

import gulliver.api.IResizeableEntity;
import gulliver.common.GulliverConfig;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Cinematic Interactions and the mods in its style (#33): Cinematic
 * Cookery, Cinematic Delight and Cinematic Belongings. All of them stage
 * their scenes (workstations, cooking, the inventory as a 3D bag) in fixed
 * block distances for a normal-size body, so they only play while the
 * player is near normal size (client.cinematicMinSize to cinematicMaxSize);
 * otherwise the normal screen or block use happens (the Cinematic* mixins).
 * A scene already playing when the player's size leaves that range (a
 * potion wearing off, a resize) is ended the way the player would leave
 * it. Everything here is a no-op without those mods.
 */
public final class CinematicCompat {
    private CinematicCompat() {}

    /** Each mod's scene class; each has public static active() and leave(). */
    private static final String[] SCENES = {
            "dev.dyslexic.cinematic.client.Scene",             // Cinematic Interactions
            "com.example.cinematiccookery.CookScene",          // Cinematic Cookery
            "com.arbuzobr1.cinematicdelight.CookScene",        // Cinematic Delight
            "com.cinematicbelongings.scene.PersonalScene" };   // Cinematic Belongings

    private record Scene(String name, Method active, Method leave) {}

    /** null until looked up; then the installed mods' scenes. */
    private static List<Scene> scenes;

    /** Whether a cinematic scene suits the local player's size. */
    public static boolean sceneFitsPlayer() {
        var player = Minecraft.getInstance().player;
        if (player == null) return true;
        return fits(((IResizeableEntity) player).getSizeMultiplier());
    }

    static boolean fits(float size) {
        GulliverConfig.Client c = GulliverConfig.INSTANCE.client;
        return size >= c.cinematicMinSize - 1.0E-4D && size <= c.cinematicMaxSize + 1.0E-4D;
    }

    /** End of every client tick: a scene the player has outgrown (or shrunk out of) ends. */
    public static void tick(Minecraft client) {
        if (scenes != null && scenes.isEmpty()) return;
        if (!BELONGINGS.equals(activeScene())) BelongingsScale.reset();
        if (client.player == null || sceneFitsPlayer()) return;
        leaveScenes(false);
    }

    private static final String BELONGINGS = "com.cinematicbelongings.scene.PersonalScene";

    /** The mod whose scene is playing, or null (the film tour). */
    public static String activeScene() {
        for (Scene s : scenes()) {
            try {
                if (Boolean.TRUE.equals(s.active().invoke(null))) return s.name();
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }
        return null;
    }

    /** Leaves whatever scene is playing (also the film tour). */
    public static void leaveScene() {
        leaveScenes(true);
    }

    /** {@code all}: also Belongings' scenes on the body, which otherwise follow any size. */
    private static void leaveScenes(boolean all) {
        for (Scene s : scenes()) {
            try {
                if (!Boolean.TRUE.equals(s.active().invoke(null))) continue;
                if (!all && s.name().equals(BELONGINGS)
                        && BelongingsScale.followsBody(s.active().getDeclaringClass().getMethod("choreography").invoke(null))) {
                    continue;
                }
                s.leave().invoke(null);
            } catch (ReflectiveOperationException | RuntimeException e) {
                gulliver.Gulliver.LOGGER.warn("Could not end a {} scene; leaving it alone from now on", s.name(), e);
                scenes.remove(s);
                return;
            }
        }
    }

    private static List<Scene> scenes() {
        if (scenes == null) {
            scenes = new ArrayList<>();
            for (String name : SCENES) {
                try {
                    Class<?> c = Class.forName(name, false, CinematicCompat.class.getClassLoader());
                    scenes.add(new Scene(name, c.getMethod("active"), c.getMethod("leave")));
                } catch (ReflectiveOperationException | LinkageError e) {
                    // Not installed.
                }
            }
        }
        return scenes;
    }
}
