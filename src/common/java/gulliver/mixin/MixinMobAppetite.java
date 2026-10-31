package gulliver.mixin;

import gulliver.common.Appetite;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//#if MC >= 1.21.2
import net.minecraft.server.level.ServerLevel;
//#endif

/** A hostile giant's melee strike on weakened prey a quarter its size swallows it (Appetite). */
@Mixin(Mob.class)
public abstract class MixinMobAppetite {

    //#if MC >= 1.21.2
    @Inject(method = "doHurtTarget(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void gulliver$swallow(ServerLevel level, Entity target, CallbackInfoReturnable<Boolean> cir) {
    //#else
    //$$ @Inject(method = "doHurtTarget(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    //$$ private void gulliver$swallow(Entity target, CallbackInfoReturnable<Boolean> cir) {
    //#endif
        Mob self = (Mob) (Object) this;
        if (!self.level().isClientSide() && Appetite.swallowsInFight(self, target)) {
            cir.setReturnValue(Appetite.eat(self, (LivingEntity) target));
        }
    }
}
