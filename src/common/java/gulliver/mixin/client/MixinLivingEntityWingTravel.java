package gulliver.mixin.client;

import gulliver.client.WingFlight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While the local player flies on its wings, WingFlight moves it instead of vanilla's travel (and gravity). */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityWingTravel {

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void gulliver$flyOnWings(Vec3 input, CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance().player && WingFlight.travel((LocalPlayer) (Object) this)) {
            ci.cancel();
        }
    }
}
