package gulliver.mixin.client;

import gulliver.access.IVisualSize;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Per-entity storage for the smooth-resize animation (client only). */
@Mixin(Entity.class)
public abstract class MixinEntityVisualSize implements IVisualSize {

    @Unique private State gulliver$visual;

    @Override
    @Unique
    public State gulliver$visualState() {
        if (gulliver$visual == null) gulliver$visual = new State();
        return gulliver$visual;
    }
}
