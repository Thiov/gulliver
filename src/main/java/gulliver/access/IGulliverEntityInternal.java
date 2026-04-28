package gulliver.access;

/**
 * Internal getters/setters for the @Unique size fields stored on Entity
 * by MixinEntity. Lets sibling mixins (MixinLivingEntity etc.) read/write
 * the same fields without each duplicating the storage.
 *
 * Not part of the public IResizeableEntity API — that mirrors the 1.6.4
 * mod's contract exactly. This is purely a port-side mechanism.
 */
public interface IGulliverEntityInternal {
    float gulliver$getSizeBaseMultiplier();
    float gulliver$getSizePotionMultiplier();
    float gulliver$getSizeItemMultiplier();

    void gulliver$setSizeBaseMultiplier(float v);
    void gulliver$setSizePotionMultiplier(float v);
    void gulliver$setSizeItemMultiplier(float v);
}
