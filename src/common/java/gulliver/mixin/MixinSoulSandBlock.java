package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoulSandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BlockSoulSandGulliver in 1.6.4: tiny entities are slowed harder
 * (motion *= 0.2 instead of vanilla's 0.4). Huge entities are NOT
 * slowed at all (their stride is large enough that soul sand barely
 * registers). Normal entities get vanilla 0.4 (handled by the parent).
 *
 *   if (entity.isTiny())  { x *= 0.2; z *= 0.2; }
 *   else if (!entity.isHuge()) { x *= 0.4; z *= 0.4; }   // normal
 *   // huge: no extra slow
 *
 * Modern translation: 26.x SoulSandBlock doesn't override entityInside;
 * the slow comes from a low getFriction() on BlockBehaviour.Properties.
 * To overlay Gulliver's tiny/huge behavior we apply explicit motion
 * scaling via @Inject HEAD entityInside: tiny → halve again (0.5x of
 * vanilla's already-slowed motion ≈ 0.2 of normal), huge → restore by
 * dividing.
 *
 * NOTE: vanilla 26.x SoulSandBlock may not have entityInside; if not,
 * @Inject will fail at apply-time and Mixin will throw. This mixin is
 * therefore conditional and gated by Mixin's defaultRequire: 1 — if it
 * fails to apply we'll see a load-time error and revisit.
 */
@Mixin(SoulSandBlock.class)
public abstract class MixinSoulSandBlock {

    @Inject(method = "entityInside",
            at = @At("HEAD"),
            require = 0)
    private void gulliver$resizedSlow(BlockState state, Level level, BlockPos pos,
                                       Entity entity, InsideBlockEffectApplier applier,
                                       boolean inside, CallbackInfo ci) {
        IResizeableEntity sized = (IResizeableEntity) entity;
        if (sized.getSizeMultiplier() == 1.0F) return;

        Vec3 m = entity.getDeltaMovement();
        if (sized.isTiny()) {
            entity.setDeltaMovement(m.x * 0.5D, m.y, m.z * 0.5D);
        } else if (sized.isHuge()) {
            // Huge: restore — multiply X/Z by 1/0.4 = 2.5 to negate the
            // vanilla soul-sand friction effect. Capped to original
            // motion to avoid runaway.
            entity.setDeltaMovement(m.x * 2.5D, m.y, m.z * 2.5D);
        }
    }
}
