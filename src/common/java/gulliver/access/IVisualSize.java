package gulliver.access;

/**
 * Client-side animation state of an entity's drawn size, stored on every
 * Entity by MixinEntityVisualSize (see gulliver.client.VisualSize). The
 * hitbox and the server's size change at once; this only drives what is
 * drawn.
 */
public interface IVisualSize {
    State gulliver$visualState();

    final class State {
        /** False until the first frame the entity was drawn. */
        public boolean seen;
        /** Size the current transition started from, and the one it heads for. */
        public float from = 1.0F;
        public float to = 1.0F;
        /** Client clock (ticks plus partial tick) the transition started at. */
        public double start;
        /** Client clock of the last frame this entity was asked for, to snap after a long absence. */
        public double lastSeen;
    }
}
