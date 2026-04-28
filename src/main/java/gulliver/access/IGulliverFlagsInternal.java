package gulliver.access;

/**
 * Cross-mixin accessor for the per-tick LivingEntity feel-flags
 * (isGliding / couldUseUmbrella / isRafting / isStruggling) that
 * MixinLivingEntity stores as @Unique fields.
 *
 * Kept in gulliver.access, not gulliver.mixin, because Mixin's
 * classloader rejects non-mixin classes that ship under its package
 * wildcard (see prior commit 0796d38 in this repo).
 */
public interface IGulliverFlagsInternal {
    void gulliver$setGlidingFlag(boolean v);
    void gulliver$setCouldUseUmbrella(boolean v);
    void gulliver$setRaftingFlag(boolean v);
    void gulliver$setStruggling(boolean v);
}
