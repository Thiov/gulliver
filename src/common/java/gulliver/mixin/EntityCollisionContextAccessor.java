package gulliver.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * EntityCollisionContext.entity is private at runtime (the widened
 * compile-time jar exposes it but the actual runtime jar doesn't).
 * Accessor invoker reads the field through Mixin's generated bridge
 * method, sidestepping the IllegalAccessError that direct field access
 * triggers.
 */
@Mixin(EntityCollisionContext.class)
public interface EntityCollisionContextAccessor {
    @Accessor("entity")
    Entity gulliver$getEntity();
}
