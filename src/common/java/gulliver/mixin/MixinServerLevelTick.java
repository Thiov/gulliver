package gulliver.mixin;

import gulliver.common.AttackContext;
import gulliver.common.CollateralBreaking;
import gulliver.common.EatContext;
import gulliver.common.SpiderTinyAggro;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/**
 * Level-tick hooks:
 *  - the context stacks (attacker for knockback, eater for food) are
 *    emptied at both ends of every level tick, so a call that another mod
 *    cancelled before our RETURN hook ran can never leave a stale entry
 *    behind for longer than the rest of its own tick;
 *  - end of tick: growth bursts continue, and spiders and insects hunt
 *    tinies.
 */
@Mixin(ServerLevel.class)
public abstract class MixinServerLevelTick {

    @Inject(method = "tick", at = @At("HEAD"))
    private void gulliver$startTick(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        AttackContext.reset();
        EatContext.clear();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void gulliver$endTick(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        AttackContext.reset();
        EatContext.clear();
        CollateralBreaking.tick((ServerLevel) (Object) this);
        SpiderTinyAggro.onLevelTick((ServerLevel) (Object) this);
        gulliver.debug.SelfTest.onServerTick((ServerLevel) (Object) this);
    }
}
