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
    default boolean gulliver$hasAnyCarry() {
        return gulliver$getHandEntity() != null
            || gulliver$getRightShoulder() != null
            || gulliver$getLeftShoulder() != null;
    }
}
