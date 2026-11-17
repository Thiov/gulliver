package gulliver.mixin.client;

import gulliver.client.ClientShowcaseFilm;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The film camera of the showcase tours (ClientShowcaseFilm): while a
 * scene is being filmed the third-person view is taken from where the
 * showcase puts it. Does nothing otherwise.
 */
@Mixin(Camera.class)
public abstract class MixinCameraFilm {

    @Shadow protected abstract void setPosition(Vec3 position);

    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "alignWithEntity", at = @At("TAIL"), require = 0)
    private void gulliver$film(CallbackInfo ci) {
        float[] shot = ClientShowcaseFilm.shot();
        if (shot == null) return;
        setPosition(new Vec3(shot[0], shot[1], shot[2]));
        setRotation(shot[3], shot[4]);
    }
}
