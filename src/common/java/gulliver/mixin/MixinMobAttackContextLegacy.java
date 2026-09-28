//#if MC < 1.21.11
//$$ package gulliver.mixin;
//$$
//$$ import gulliver.common.AttackContext;
//$$ import net.minecraft.world.entity.Entity;
//$$ import net.minecraft.world.entity.Mob;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//$$
//$$ /** A mob's attack-knockback bonus is applied inline too (see the Player twin). */
//$$ @Mixin(Mob.class)
//$$ public abstract class MixinMobAttackContextLegacy {
//$$
//#if MC >= 1.21.2
//$$     @Inject(method = "doHurtTarget", at = @At("HEAD"))
//$$     private void gulliver$pushHurt(net.minecraft.server.level.ServerLevel level, Entity target,
//$$                                    CallbackInfoReturnable<Boolean> cir) {
//$$         AttackContext.push((Entity) (Object) this);
//$$     }
//$$
//$$     @Inject(method = "doHurtTarget", at = @At("RETURN"))
//$$     private void gulliver$popHurt(net.minecraft.server.level.ServerLevel level, Entity target,
//$$                                   CallbackInfoReturnable<Boolean> cir) {
//$$         AttackContext.pop();
//$$     }
//#else
//$$     @Inject(method = "doHurtTarget", at = @At("HEAD"))
//$$     private void gulliver$pushHurt(Entity target, CallbackInfoReturnable<Boolean> cir) {
//$$         AttackContext.push((Entity) (Object) this);
//$$     }
//$$
//$$     @Inject(method = "doHurtTarget", at = @At("RETURN"))
//$$     private void gulliver$popHurt(Entity target, CallbackInfoReturnable<Boolean> cir) {
//$$         AttackContext.pop();
//$$     }
//#endif
//$$ }
//#endif
