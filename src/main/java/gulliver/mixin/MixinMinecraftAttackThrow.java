package gulliver.mixin;

import gulliver.access.IGulliverShoulderInternal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Left-click throw: when the local player is carrying a shoulder entity
 * and presses the attack key, send `/shoulderentity throw` and consume
 * the click so vanilla doesn't also process it as an attack swing.
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftAttackThrow {

    @Shadow public LocalPlayer player;

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void gulliver$throwHeldOnAttack(CallbackInfoReturnable<Boolean> cir) {
        if (player == null) return;
        if (((IGulliverShoulderInternal) player).gulliver$getHandEntity() == null) return;
        // Send command to server; server side ShoulderHelper.throwHeld
        // does the actual throw + packet broadcast.
        if (player.connection != null) {
            player.connection.sendCommand("shoulderentity throw");
        }
        cir.setReturnValue(true);
    }
}
