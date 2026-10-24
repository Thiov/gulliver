package gulliver.access;

/**
 * Added to {@code TargetingConditions} by MixinTargetingConditionsTinyHide:
 * a flag marking conditions that must keep seeing small creatures even
 * though they are combat conditions (the ones a fleeing goal uses).
 */
public interface IGulliverTargeting {
    void gulliver$setSeesSmall(boolean seesSmall);
}
