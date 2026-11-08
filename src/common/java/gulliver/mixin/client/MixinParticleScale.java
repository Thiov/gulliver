package gulliver.mixin.client;

import gulliver.access.IGulliverParticle;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * Lets ParticleScale scale a particle as a whole. Vanilla's scale() grows
 * the sprite (and resets the hitbox to a 0.2 cube times the factor); the
 * hitbox is set back to its own shape times the factor, and the speed and
 * gravity follow, so the particle flies the same path at the new scale in
 * the same time.
 */
@Mixin(Particle.class)
public abstract class MixinParticleScale implements IGulliverParticle {

    @Shadow protected double xd;
    @Shadow protected double yd;
    @Shadow protected double zd;
    @Shadow protected float gravity;
    @Shadow protected float bbWidth;
    @Shadow protected float bbHeight;

    @Shadow public abstract Particle scale(float factor);
    @Shadow protected abstract void setSize(float width, float height);

    @Unique private boolean gulliver$scaled;

    @Override
    public void gulliver$scaleWhole(float size) {
        if (gulliver$scaled) return;
        gulliver$scaled = true;
        float width = bbWidth;
        float height = bbHeight;
        scale(size);
        setSize(width * size, height * size);
        xd *= size;
        yd *= size;
        zd *= size;
        gravity *= size;
    }
}
