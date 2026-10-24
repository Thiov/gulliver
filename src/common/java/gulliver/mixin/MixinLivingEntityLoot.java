package gulliver.mixin;

import gulliver.common.LootScaling;
//#if MC >= 1.21.2
import net.minecraft.server.level.ServerLevel;
//#endif
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Loot scaling by size (LootScaling): opens a window around the loot-table
 * roll of a dying mob, so MixinEntityLootScale knows which item spawns to
 * adjust (equipment and custom death loot are rolled outside it), and
 * scales the experience it gives. If another mod cancels the method before
 * our RETURN hook runs, the window stays open only for the same mob's
 * spawns on the same thread, which is harmless.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityLoot {

    //#if MC >= 1.21.2
    @Inject(method = "dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;Z)V",
            at = @At("HEAD"))
    private void gulliver$beginLoot(ServerLevel level, DamageSource source, boolean playerKilled, CallbackInfo ci) {
        LootScaling.begin((LivingEntity) (Object) this);
    }

    @Inject(method = "dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;Z)V",
            at = @At("RETURN"))
    private void gulliver$endLoot(ServerLevel level, DamageSource source, boolean playerKilled, CallbackInfo ci) {
        LootScaling.end();
    }
    //#else
    //$$ @Inject(method = "dropFromLootTable(Lnet/minecraft/world/damagesource/DamageSource;Z)V", at = @At("HEAD"))
    //$$ private void gulliver$beginLoot(DamageSource source, boolean playerKilled, CallbackInfo ci) {
    //$$     LootScaling.begin((LivingEntity) (Object) this);
    //$$ }
    //$$
    //$$ @Inject(method = "dropFromLootTable(Lnet/minecraft/world/damagesource/DamageSource;Z)V", at = @At("RETURN"))
    //$$ private void gulliver$endLoot(DamageSource source, boolean playerKilled, CallbackInfo ci) {
    //$$     LootScaling.end();
    //$$ }
    //#endif

    @ModifyArg(method = "dropExperience",
               at = @At(value = "INVOKE",
                        target = "Lnet/minecraft/world/entity/ExperienceOrb;award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V"),
               index = 2)
    private int gulliver$scaleExperience(int amount) {
        return LootScaling.scaleExperience((LivingEntity) (Object) this, amount);
    }
}
