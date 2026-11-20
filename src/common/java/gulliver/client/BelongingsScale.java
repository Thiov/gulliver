package gulliver.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * Cinematic Belongings at any size (#33). Its inventory scene - the bag held
 * in front of you, your hands in it, the camera leaning in - is built
 * entirely around your eye, in block distances made for a normal body. So
 * the whole scene is enlarged (or shrunk) around the eye by your size, as
 * one similarity:
 *  - the scene camera it asks for is moved out from the eye by the size
 *    (PersonalScene.camera), so a giant leans in over a giant's bag;
 *  - everything it draws is scaled by the size around that camera, while
 *    its renderer and its mouse picking are handed the camera where it
 *    would be at normal size (PersonalScene.render, Picking.capture).
 * What it draws therefore looks exactly as at normal size, with the right
 * body around it, and clicking an item still hits that item.
 *
 * Scenes at a block in the world (a chest, a placed backpack) can't follow
 * the body like that: they keep to the size range, like Cinematic
 * Interactions (CinematicCompat).
 */
public final class BelongingsScale {
    private BelongingsScale() {}

    /** Scenes that live on the body (the rest are at a block in the world). */
    private static final Set<String> PERSONAL = Set.of("POUCH", "WORN_BACKPACK", "HELD_BACKPACK");

    /** The eye and size of this frame's scene camera. */
    private static Vec3 eye;
    private static double size = 1.0D;

    private static Method kindOf;
    private static Method posOf, yawOf, pitchOf, rollOf;
    private static Constructor<?> newPose;
    private static boolean broken;

    /** Whether Belongings may turn this screen into a scene (its Target), at the player's size. */
    public static boolean allowed(Object target) {
        if (CinematicCompat.sceneFitsPlayer()) return true;
        return PERSONAL.contains(kind(target));
    }

    /** Whether a playing Belongings scene can follow the player at any size. */
    public static boolean followsBody(Object choreography) {
        try {
            Object kind = choreography.getClass().getMethod("kind").invoke(choreography);
            return kind != null && PERSONAL.contains(((Enum<?>) kind).name());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static String kind(Object target) {
        try {
            if (kindOf == null) kindOf = target.getClass().getMethod("kind");
            Object kind = kindOf.invoke(target);
            return kind == null ? "" : ((Enum<?>) kind).name();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "";
        }
    }

    /** The player's size as drawn this frame (gliding while it changes). */
    private static double playerSize() {
        var player = Minecraft.getInstance().player;
        if (player == null) return 1.0D;
        double s = VisualSize.of(player);
        return s > 0.0D && !Double.isInfinite(s) ? s : 1.0D;
    }

    /**
     * The scene camera Belongings chose around {@code eye} (a Pose), moved
     * out from the eye by the player's size. Null to keep it as it is.
     */
    public static Object camera(Vec3 sceneEye, Object pose) {
        size = playerSize();
        eye = sceneEye;
        if (pose == null || broken || Math.abs(size - 1.0D) < 1.0E-4D) return null;
        try {
            if (newPose == null) {
                Class<?> c = pose.getClass();
                posOf = c.getMethod("pos");
                yawOf = c.getMethod("yaw");
                pitchOf = c.getMethod("pitch");
                rollOf = c.getMethod("roll");
                newPose = c.getConstructor(Vec3.class, float.class, float.class, float.class);
            }
            Vec3 pos = (Vec3) posOf.invoke(pose);
            Vec3 scaled = sceneEye.add(pos.subtract(sceneEye).scale(size));
            return newPose.newInstance(scaled, yawOf.invoke(pose), pitchOf.invoke(pose), rollOf.invoke(pose));
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            gulliver.Gulliver.LOGGER.warn("Cinematic Belongings changed; its scene won't follow the player's size", e);
            return null;
        }
    }

    /** No Belongings scene is playing: nothing to map. */
    public static void reset() {
        eye = null;
    }

    /** How much the scene is drawn enlarged this frame. */
    public static float drawScale() {
        return eye == null || broken ? 1.0F : (float) size;
    }

    /** The real camera position mapped back to where it would be at normal size (for its renderer and picking). */
    public static Vec3 normalCamera(Vec3 camera) {
        if (eye == null || broken || camera == null || Math.abs(size - 1.0D) < 1.0E-4D) return camera;
        return eye.add(camera.subtract(eye).scale(1.0D / size));
    }
}
