package gulliver.access;

/** Added to every Particle by MixinParticleScale (see gulliver.client.ParticleScale). */
public interface IGulliverParticle {
    /** Scales the particle whole, once: sprite, hitbox, speed and gravity. */
    void gulliver$scaleWhole(float size);
}
