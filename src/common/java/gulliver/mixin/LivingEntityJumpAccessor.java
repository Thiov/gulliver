package gulliver.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The jump input of a player; LivingEntity.isJumping() only exists from 1.21.2 on. */
@Mixin(LivingEntity.class)
public interface LivingEntityJumpAccessor {
    @Accessor("jumping")
    boolean gulliver$isJumping();
}
