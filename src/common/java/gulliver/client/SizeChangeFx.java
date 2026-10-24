package gulliver.client;

import net.minecraft.world.entity.Entity;

/**
 * Client-side reaction to a size change the server announced for an
 * entity that was already visible (not its first size packet).
 */
public final class SizeChangeFx {
    private SizeChangeFx() {}

    public static void onSizeChanged(Entity entity, float oldSize, float newSize) {
    }
}
