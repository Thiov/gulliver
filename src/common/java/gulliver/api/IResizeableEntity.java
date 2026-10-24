package gulliver.api;

public interface IResizeableEntity {
    /**
     * The effective size: base x potion x item, clamped to this entity's
     * limits (its per-entity override, its class, the general range; between
     * 1/32 and 32 whatever the config says). Always finite and positive,
     * and cheap enough to call every frame.
     */
    float getSizeMultiplier();

    float getSizeMultiplierRoot();

    void halveSize();

    void doubleSize();

    boolean isTiny();

    boolean isExtraTiny();

    boolean isHuge();

    float getStepHeight();

    /**
     * Reach multiplier — LINEAR in size for all brackets. (The 1.6.4
     * source used sqrt for tinies, but playtesting showed that gave a
     * 0.125x player ~3 blocks of reach — far too long; see
     * MixinEntity.getRangeMultiplier.)
     */
    float getRangeMultiplier();

    /**
     * 1.6.4 nn.java getSizeMovementMultiplier:
     *   !isWeighted -> sqrt(size); else linear size.
     * "Stride per body length per second" stays roughly constant.
     */
    float getSizeMovementMultiplier();

    /**
     * 1.6.4 of.java/uf.java isWeighted: wearing heavy armor in slot,
     * or carrying weighted item. Defaults false on raw Entity.
     */
    boolean isWeighted();

    /**
     * 1.6.4 of.java isSticky: tiny holding slime ball, in webs,
     * or along sticky surface (ladder/sign side).
     */
    boolean isSticky();

    /**
     * 1.6.4 of.java getStepSide: per-stride side flip (-1/0/+1).
     * Used by leaveHugeFootprints to alternate corner pairs.
     */
    int getStepSide();
}
