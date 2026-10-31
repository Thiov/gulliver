package gulliver.access;

/**
 * Added to {@code TargetingConditions} by MixinTargetingConditionsTinyHide:
 * flags for conditions that must keep seeing small creatures although they
 * are combat conditions (the ones a fleeing goal uses), and for ones that
 * must overlook them although they are not (the ones an idle mob picks
 * something to look at with).
 */
public interface IGulliverTargeting {
    void gulliver$setSeesSmall(boolean seesSmall);

    void gulliver$setOverlooksSmall(boolean overlooksSmall);
}
