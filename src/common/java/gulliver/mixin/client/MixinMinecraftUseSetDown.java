package gulliver.mixin.client;

import gulliver.common.ShoulderHelper;
import gulliver.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import gulliver.access.IGulliverShoulderInternal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Right-click on nothing while carrying in the (empty) hand sets the
 * creature down; while sneaking it takes a bite instead (Appetite: held
 * like eating food; the server sets down what can't be eaten). Vanilla
 * sends no use packet for an empty hand aimed at the air, so the use-item
 * events never see this click.
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftUseSetDown {

    @Shadow public LocalPlayer player;
    @Shadow public HitResult hitResult;
    @Shadow private int rightClickDelay;

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void gulliver$setDownOnEmptyClick(CallbackInfo ci) {
        if (player == null || !ShoulderHelper.handOccupied(player)) return;
        if (!player.getMainHandItem().isEmpty()) return; // an item in hand goes through the use-item event
        // Block and entity clicks have their own paths - except the creature
        // in your own hand, which is right in front of you while you eat it.
        if (hitResult != null && hitResult.getType() != HitResult.Type.MISS
                && !(hitResult instanceof EntityHitResult ehr && player.getUUID().equals(
                        ((IGulliverShoulderInternal) ehr.getEntity()).gulliver$getHoldingEntity()))) return;
        boolean eat = player.isShiftKeyDown();
        byte action = eat ? Payloads.CarryAction.EAT : Payloads.CarryAction.SET_DOWN;
        if (gulliver.platform.Services.platform().sendToServer(new Payloads.CarryAction(action))) {
            rightClickDelay = 4;
            if (!eat) {
                //#if MC >= 26.3
                player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
                //#else
                //$$ player.swing(InteractionHand.MAIN_HAND);
                //#endif
            }
            ci.cancel();
        }
    }
}
