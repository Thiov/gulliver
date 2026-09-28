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
    boolean gulliver$isRafting();
    boolean gulliver$hasHandPassenger();
    float gulliver$getSizeMultiplier();
    /** Arm holding the umbrella / glide item (either hand works), or null. */
    net.minecraft.world.entity.HumanoidArm gulliver$getPropArm();
    void gulliver$setPropArm(net.minecraft.world.entity.HumanoidArm arm);
    void gulliver$setGliding(boolean v);
    void gulliver$setDoesUmbrella(boolean v);
    void gulliver$setRafting(boolean v);
    void gulliver$setHandPassenger(boolean v);
    void gulliver$setSizeMultiplier(float v);
}
