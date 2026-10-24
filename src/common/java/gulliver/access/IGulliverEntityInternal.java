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
    float gulliver$getSizeBaseDestMultiplier();
    float gulliver$getSizePotionMultiplier();
    float gulliver$getSizeItemMultiplier();

    void gulliver$setSizeBaseMultiplier(float v);
    void gulliver$setSizeBaseDestMultiplier(float v);
    void gulliver$setSizePotionMultiplier(float v);
    void gulliver$setSizeItemMultiplier(float v);

    /** True once spawn-size config has been applied (or the entity came from a save). */
    boolean gulliver$isSizeInitialized();
    void gulliver$setSizeInitialized(boolean v);

    /**
     * The size the entity is heading to (destination base x potion x item),
     * clamped to its limits: what the server tells clients.
     */
    float gulliver$getEffectiveDestSize();

    /** The cached size limits (per-entity override, class, general), as of the current config. */
    float gulliver$getMinSize();
    float gulliver$getMaxSize();

    /** Clamps a size to this entity's limits (NaN counts as 1). */
    float gulliver$clampSize(float size);

    /**
     * The size the entity's dimensions, attributes and clients were last
     * brought in line with; the tick compares it with the live size to
     * catch changes nobody announced (a config reload).
     */
    float gulliver$getAppliedSize();
    void gulliver$setAppliedSize(float size);

    /** Ticks left to find room for a body that did not fit where it arrived (see BodyFit). */
    int gulliver$getSettleTicks();
    void gulliver$setSettleTicks(int ticks);
}
