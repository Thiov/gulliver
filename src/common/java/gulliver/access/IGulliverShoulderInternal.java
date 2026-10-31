package gulliver.access;

import java.util.UUID;

/**
 * Internal accessor for the shoulder/hand carry slots. Up to 3
 * concurrent passengers per carrier:
 *   - hand          (entity in carrier's hand, in front)
 *   - right shoulder
 *   - left shoulder
 *
 * Plus the reverse-lookup `holdingEntity` UUID on every Entity (the
 * UUID of whoever is carrying me, or null if not carried).
 */
public interface IGulliverShoulderInternal {
    UUID gulliver$getHoldingEntity();
    void gulliver$setHoldingEntity(UUID id);

    UUID gulliver$getHandEntity();
    UUID gulliver$getRightShoulder();
    UUID gulliver$getLeftShoulder();
    void gulliver$setHandEntity(UUID id);
    void gulliver$setRightShoulder(UUID id);
    void gulliver$setLeftShoulder(UUID id);

    /**
     * Convenience: returns true iff the entity is carried in any slot
     * by the given carrier UUID.
     */
    /**
     * Ticks since this body started bringing its hand-held creature to its
     * mouth (Appetite), or -1 while it isn't eating. Kept on both sides:
     * the server counts it, clients count from the ActionState packet.
     */
    int gulliver$getChewTicks();
    void gulliver$setChewTicks(int ticks);

    /**
     * Where a snatched creature was when a monster grabbed it, relative to
     * the monster's feet, or null: the lift to the mouth starts there
     * instead of in the hand, so a grabbed player isn't teleported up.
     */
    net.minecraft.world.phys.Vec3 gulliver$getGrabOffset();
    void gulliver$setGrabOffset(net.minecraft.world.phys.Vec3 offset);

    /** Where this carried entity was last placed, so its render lerp starts from the slot itself. */
    net.minecraft.world.phys.Vec3 gulliver$getLastSlot();
    void gulliver$setLastSlot(net.minecraft.world.phys.Vec3 slot);

    default boolean gulliver$hasAnyCarry() {
        return gulliver$getHandEntity() != null
            || gulliver$getRightShoulder() != null
            || gulliver$getLeftShoulder() != null;
    }
}
