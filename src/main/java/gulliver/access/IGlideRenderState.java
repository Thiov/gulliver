package gulliver.access;

/**
 * Carries the per-frame gliding / doesUmbrella flags from the entity to
 * the model. HumanoidRenderState implements this via mixin so
 * HumanoidModel.setupAnim can override arm + leg pose without a direct
 * entity reference (modern MC 26.x state pattern hides the entity from
 * the model render path).
 */
public interface IGlideRenderState {
    boolean gulliver$isGliding();
    boolean gulliver$doesUmbrella();
    float gulliver$getSizeMultiplier();
    void gulliver$setGliding(boolean v);
    void gulliver$setDoesUmbrella(boolean v);
    void gulliver$setSizeMultiplier(float v);
}
