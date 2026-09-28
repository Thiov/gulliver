package gulliver.mixin;

import gulliver.common.SpiderTinyAggro;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/** End-of-level-tick hook (spiders and insects hunting tinies). */
@Mixin(ServerLevel.class)
public abstract class MixinServerLevelTick {

    @Inject(method = "tick", at = @At("TAIL"))
    private void gulliver$endTick(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        SpiderTinyAggro.onLevelTick((ServerLevel) (Object) this);
        gulliver.debug.SelfTest.onServerTick((ServerLevel) (Object) this);
    }
}
