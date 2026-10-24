package gulliver.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Easier picking of small creatures with the crosshair.
 *
 * A giant's crosshair has to find a creature an eighth of its size, a
 * hundred blocks of cursor-width away, and at that distance one pixel of
 * aim is wider than the creature. While the crosshair target is being
 * picked (MixinGameRendererPick / MixinMinecraftPick open the window),
 * entities at least four times smaller than the viewer get a pick radius
 * that grows with the size ratio and the viewer's size, up to half a block
 * on every side.
 *
 * Client-side only, and harmless for the server: the server checks the
 * distance to the creature's real hitbox against the reach plus a margin
 * (at least one block, three on recent versions), and half a block of
 * extra radius is at most 0.87 blocks of extra distance at a corner.
 */
public final class PickBoost {
    private PickBoost() {}

    private static final float MAX_RADIUS = 0.5F;

    private static boolean active;

    public static void begin() {
        active = true;
    }

    public static void end() {
        active = false;
    }

    /** Pick radius for a target, given the vanilla one (what getPickRadius returned). */
    public static float radius(Entity target, float vanilla) {
        if (!active) return vanilla;
        Entity viewer = Minecraft.getInstance().getCameraEntity();
        if (viewer == null || viewer == target) return vanilla;
        float viewerSize = VisualSize.logical(viewer);
        float targetSize = VisualSize.logical(target);
        if (!(viewerSize > 0.0F) || !(targetSize > 0.0F)) return vanilla;
        float ratio = viewerSize / targetSize;
        if (ratio < 4.0F) return vanilla;
        // Fades in between ratio 4 and 8, so the boost never pops in.
        float fade = Mth.clamp((ratio - 4.0F) / 4.0F, 0.0F, 1.0F);
        float boost = Mth.clamp(0.0125F * viewerSize * Math.min(ratio, 32.0F), 0.0F, MAX_RADIUS) * fade;
        return Math.max(vanilla, boost);
    }
}
