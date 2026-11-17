package gulliver.access;

import gulliver.common.WingType;

/**
 * A player's wings (TinyWings), added to every Player by MixinPlayerWings.
 * The type and colour are saved with the player; the flight state is what
 * the player's own client says it is doing, passed on to everyone who can
 * see it so its wings beat and buzz the same for them.
 */
public interface IGulliverWings {
    /** Not flying: on the ground, folded. */
    byte FOLDED = 0;
    /** Flapping in place. */
    byte HOVER = 1;
    /** Flying along. */
    byte CRUISE = 2;
    /** Flapping hard to climb (jump held). */
    byte CLIMB = 3;
    /** Wings half shut, dropping (sneak held). */
    byte DIVE = 4;
    /** Wings held open and still: a butterfly's glide, or tired wings. */
    byte GLIDE = 5;
    /** Darting (sprint held). */
    byte DASH = 6;

    WingType gulliver$wingType();

    int gulliver$wingColor();

    void gulliver$setWings(WingType type, int color);

    byte gulliver$wingFlight();

    void gulliver$setWingFlight(byte state);

    /** Client only: the wings' animation state (gulliver.client.WingAnim.State). */
    Object gulliver$wingAnim();

    void gulliver$setWingAnim(Object anim);

    static boolean flying(byte state) {
        return state != FOLDED;
    }
}
