package gulliver.mixin.iface;

import java.util.UUID;

/**
 * Internal accessor for the shoulder-entity @Unique fields. Mirrors
 * the 1.6.4 ASM-injected fields:
 *   Entity.holdingEntity  — UUID of the entity carrying me, or null
 *   Player.heldEntity     — UUID of the entity I'm carrying, or null
 *
 * These are stored as UUIDs (not direct refs) so they survive entity
 * unload and re-resolve via level.getEntity(uuid) lazily.
 *
 * Not part of the public IResizeable* API — purely a port-side mechanism
 * to coordinate between shoulder mixins.
 */
public interface IGulliverShoulderInternal {
    UUID gulliver$getHoldingEntity();
    UUID gulliver$getHeldEntity();

    void gulliver$setHoldingEntity(UUID id);
    void gulliver$setHeldEntity(UUID id);
}
