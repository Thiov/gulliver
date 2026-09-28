package gulliver.mixin;

import gulliver.common.GiantAoe;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Giant fist craters (GiantAoe): look at the block before it breaks, and
 * shatter its neighbours only if the break really happened. Loader
 * independent — every loader funnels player block breaks through here.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class MixinServerPlayerGameModeBreak {

    @Shadow @Final protected ServerPlayer player;

    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void gulliver$beforeBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        GiantAoe.beforeBreak(player.level(), player, pos);
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void gulliver$afterBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            GiantAoe.afterBreak(player.level(), player, pos);
        } else {
            GiantAoe.clearBreak();
        }
    }
}
