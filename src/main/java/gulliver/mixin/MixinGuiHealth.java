package gulliver.mixin;

import gulliver.access.IGulliverEntityInternal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppress heart-blink animations during a resize tween. When the player
 * shrinks, our proportional HP scaling drops the absolute HP value each
 * tick to keep the % full constant (visible as fewer hearts, all full).
 * The HUD compares current HP against the previous tick's HP and triggers
 * the blink/pulse animation any time it goes down — making the resize
 * look like taking damage to the player.
 *
 * Cancel Gui.extractPlayerHealth's heart-tracking entirely while the
 * local player's size is still lerping (base != dest). Once the tween
 * settles, the HUD resumes normal blink behavior.
 */
@Mixin(Gui.class)
public abstract class MixinGuiHealth {

    @Inject(method = "extractPlayerHealth", at = @At("HEAD"), cancellable = true)
    private void gulliver$skipDuringResize(CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        IGulliverEntityInternal i = (IGulliverEntityInternal) player;
        float base = i.gulliver$getSizeBaseMultiplier();
        float dest = i.gulliver$getSizeBaseDestMultiplier();
        if (base != dest) {
            // Resize in progress — skip the HP comparison logic that
            // would otherwise see a "damage" event. We still want the
            // hearts to render with the (changing) HP and max values,
            // but skipping extractPlayerHealth means the Gui's
            // displayHealth/lastHealth tracking won't update either.
            // Render hearts from raw player.getHealth() / getMaxHealth()
            // each frame — proportional scaling keeps them all full.
            ci.cancel();
        }
    }
}
