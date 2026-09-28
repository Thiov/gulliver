package gulliver.mixin.client;

import gulliver.client.GulliverClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** End-of-client-tick hook shared by every loader. */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftTick {

    @Inject(method = "tick", at = @At("TAIL"))
    private void gulliver$endTick(CallbackInfo ci) {
        GulliverClient.onClientTick((Minecraft) (Object) this);
    }
}
