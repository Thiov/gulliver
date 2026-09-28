package gulliver.mixin.client;

import gulliver.access.IGulliverShoulderInternal;
import gulliver.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Right-click on nothing while carrying in the (empty) hand sets the
 * creature down. Vanilla sends no use packet for an empty hand aimed at
 * the air, so the use-item events never see this click.
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftUseSetDown {

    @Shadow public LocalPlayer player;
    @Shadow public HitResult hitResult;
    @Shadow private int rightClickDelay;

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void gulliver$setDownOnEmptyClick(CallbackInfo ci) {
        if (player == null || ((IGulliverShoulderInternal) player).gulliver$getHandEntity() == null) return;
        if (!player.getMainHandItem().isEmpty()) return; // an item in hand goes through the use-item event
        if (hitResult != null && hitResult.getType() != HitResult.Type.MISS) return; // block/entity paths
        if (gulliver.platform.Services.platform().sendToServer(
                new Payloads.CarryAction(Payloads.CarryAction.SET_DOWN))) {
            rightClickDelay = 4;
            player.swing(InteractionHand.MAIN_HAND);
            ci.cancel();
        }
    }
}
