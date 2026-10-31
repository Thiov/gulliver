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
    /** The size the model was drawn at when the state was extracted (the animated size, not the hitbox's). */
    float gulliver$getSizeMultiplier();
    /**
     * LivingEntityRenderState.scale right after extraction; a GUI preview that
     * normalises the scale afterwards shows up as state.scale differing from it.
     */
    float gulliver$getExtractScale();
    /** Arm holding the umbrella / glide item (either hand works), or null. */
    net.minecraft.world.entity.HumanoidArm gulliver$getPropArm();
    void gulliver$setPropArm(net.minecraft.world.entity.HumanoidArm arm);
    void gulliver$setGliding(boolean v);
    void gulliver$setDoesUmbrella(boolean v);
    void gulliver$setRafting(boolean v);
    void gulliver$setHandPassenger(boolean v);
    void gulliver$setSizeMultiplier(float v);
    void gulliver$setExtractScale(float v);
    /** Eating (Appetite): how far the hand is up at the mouth, 0..1, and the depth of the current bite, 0..1. */
    float gulliver$getEatLift();
    float gulliver$getEatBite();
    void gulliver$setEat(float lift, float bite);
    /** A monster eating: how far its hands have closed on the prey (EatPose.mobGrip), 0 otherwise. */
    float gulliver$getMobGrip();
    void gulliver$setMobGrip(float grip);
}
