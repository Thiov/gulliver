package gulliver.access;

import net.minecraft.world.phys.Vec3;

/** Grappling-hook anchor stored on FishingHook by MixinFishingHook. */
public interface IGulliverHookInternal {
    Vec3 gulliver$getAnchor();
    void gulliver$setAnchor(Vec3 anchor);
}
