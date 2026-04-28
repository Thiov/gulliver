package gulliver.api;

public interface IResizeableLiving extends IResizeableEntity {
    float getSizePotionMultiplier();

    float getSizeItemMultiplier();

    void setBaseSize(float size);

    void adjustBaseSize(float factor);

    /**
     * 1.6.4 of.java per-tick flag: tiny holding glideable item, airborne,
     * not in plant -> slow-fall + heat updraft lift.
     */
    boolean isGliding();

    /**
     * 1.6.4 uf.java per-tick flag: holding paper/lily-pad, size <= 0.5,
     * in rain, not on ladder -> shelters from rain damage.
     */
    boolean doesUmbrella();

    /**
     * 1.6.4 uf.java per-tick flag: tiny holding paper above water -> raft.
     */
    boolean isRafting();

    /**
     * 1.6.4 of.java set during impeded movement (cobweb/sweet-berry/snow).
     * Modifies friction calc.
     */
    boolean isStruggling();
}
