package gulliver.mixin.client;

import gulliver.client.PickBoost;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pick radius of entities much smaller than the viewer; see PickBoost (only active while the crosshair target is picked). */
@Mixin(Entity.class)
public abstract class MixinEntityPickRadius {

    @Inject(method = "getPickRadius", at = @At("RETURN"), cancellable = true, require = 0)
    private void gulliver$easierToPick(CallbackInfoReturnable<Float> cir) {
        float vanilla = cir.getReturnValueF();
        float boosted = PickBoost.radius((Entity) (Object) this, vanilla);
        if (boosted != vanilla) cir.setReturnValue(boosted);
    }
}
