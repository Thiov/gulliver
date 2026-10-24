package gulliver.mixin;

import gulliver.common.GiantExtents;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Entities are filed under the chunk section of their feet, and a lookup
 * only visits sections up to 4 blocks below and 2 to the side of the box it
 * searches. The upper body of anything taller than that was invisible to
 * arrows, tridents and picks. The same storage serves the server and the
 * client level on every version, so one widening covers both. Only the
 * choice of sections changes: the exact box test per entity still uses the
 * original box.
 */
@Mixin(EntitySectionStorage.class)
public abstract class MixinEntitySectionStorage {

    @ModifyVariable(method = "forEachAccessibleNonEmptySection", at = @At("HEAD"), argsOnly = true)
    private AABB gulliver$searchBigBodies(AABB box) {
        return GiantExtents.widen(box);
    }
}
