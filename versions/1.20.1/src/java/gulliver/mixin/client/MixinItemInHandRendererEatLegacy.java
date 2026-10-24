package gulliver.mixin.client;

import gulliver.api.IResizeableEntity;
import gulliver.common.SizeScaling;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 1.20.1 first-person eating: the bobbing and the food flying up to the
 * mouth are driven by remaining ticks divided by the stack's use duration.
 * Gulliver stretches the remaining ticks by the eater's size (see
 * MixinLivingEntityUseDurationLegacy) but 1.20.1's ItemStack has no
 * per-entity duration, so the ratio went far above 1 and the food was
 * drawn off-screen for most of a tiny's meal. Use the same scaled duration
 * here. (From 1.20.5 ItemStack.getUseDuration(entity) is scaled directly.)
 */
@Mixin(ItemInHandRenderer.class)
public abstract class MixinItemInHandRendererEatLegacy {

    @Shadow @Final private Minecraft minecraft;

    @Redirect(method = "applyEatTransform",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getUseDuration()I"))
    private int gulliver$scaledUseDuration(ItemStack stack) {
        int duration = stack.getUseDuration();
        LocalPlayer player = minecraft.player;
        if (player == null) return duration;
        UseAnim anim = stack.getUseAnimation();
        if (anim != UseAnim.EAT && anim != UseAnim.DRINK) return duration;
        float size = ((IResizeableEntity) player).getSizeMultiplier();
        return size == 1.0F ? duration : SizeScaling.scaledTicks(duration, size);
    }
}
