package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.common.GulliverEnvoy;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * BlockTNTGulliver in 1.6.4: a huge player who right-clicks a TNT block
 * with a free hand (or non-igniter, non-pointy item) primes the TNT and
 * removes the block — like the giant lit a fuse with bare hands.
 *
 * 1.6.4 source:
 *   if (player.by() == null && player.isHuge()) {
 *     entitytntprimed = new EntityTNTPrimed(...); player.world.spawn(entitytntprimed);
 *     world.removeBlock(x,y,z); return true;
 *   }
 *   else super.onBlockActivated(...);
 *
 * Modern translation: @Inject HEAD useItemOn — only intercept when the
 * vanilla flint/steel/fire-charge path WOULDN'T fire (i.e. empty hand or
 * something else). Vanilla useItemOn returns PASS for unrelated items
 * which then falls through to ItemStack.useItemOn — by intercepting at
 * HEAD with a huge-only check we run BEFORE vanilla's flint/steel branch
 * could short-circuit, but only if we have grounds to.
 */
@Mixin(TntBlock.class)
public abstract class MixinTntBlock {

    //#if MC >= 1.21.2
    @org.spongepowered.asm.mixin.Shadow
    private static boolean prime(Level level, BlockPos pos, net.minecraft.world.entity.LivingEntity source) {
        throw new AssertionError();
    }
    //#else
    //$$ @org.spongepowered.asm.mixin.Shadow
    //$$ private static void explode(Level level, BlockPos pos, net.minecraft.world.entity.LivingEntity source) {
    //$$     throw new AssertionError();
    //$$ }
    //#endif

    //#if MC >= 1.21.2
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void gulliver$hugeBareHandPrime(ItemStack stack, BlockState state, Level level,
                                             BlockPos pos, Player player, InteractionHand hand,
                                             BlockHitResult hit,
                                             CallbackInfoReturnable<InteractionResult> cir) {
    //#else
    //$$ @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    //$$ private void gulliver$hugeBareHandPrime(BlockState state, Level level, BlockPos pos, Player player,
    //$$                                          InteractionHand hand, BlockHitResult hit,
    //$$                                          CallbackInfoReturnable<InteractionResult> cir) {
    //$$     ItemStack stack = player.getItemInHand(hand);
    //#endif
        if (!((IResizeableEntity) player).isHuge()) return;
        // Don't override the vanilla flint/steel / fire charge path.
        if (stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE)) return;
        // Don't override pointy-tool right-click — the player might be poking
        // at the block with intent.
        if (GulliverEnvoy.isItemPointy(stack)) return;
        // Don't hijack block placement: a huge builder clicking TNT with a
        // block in hand wants to build against it, not detonate it. The
        // 1.6.4 original only primed on a truly EMPTY hand.
        if (stack.getItem() instanceof net.minecraft.world.item.BlockItem) return;
        // Sneak = the universal "suppress special interaction" gesture.
        if (player.isShiftKeyDown()) return;

        if (!level.isClientSide()) {
            //#if MC >= 1.21.2
            // prime() refuses when the tnt_explodes game rule is off; the
            // block must survive then.
            if (prime(level, pos, player)) level.removeBlock(pos, false);
            //#else
            //$$ explode(level, pos, player);
            //$$ level.removeBlock(pos, false);
            //#endif
        }
        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
