package gulliver.mixin;

import gulliver.common.SpawnSizes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//#if MC >= 1.21.2
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntitySpawnReason;
//#endif

/**
 * Mobs that turn into another kind (zombie to drowned, zombie villager cured,
 * villager to witch, tadpole to frog, pig to zombified piglin, skeleton to
 * stray ...) arrive as a brand new entity. Without this a giant zombie that
 * drowns came back at normal size.
 */
@Mixin(Mob.class)
public abstract class MixinMobConvert {

    //#if MC >= 1.21.2
    @Inject(method = "convertTo(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/entity/ConversionParams;Lnet/minecraft/world/entity/EntitySpawnReason;Lnet/minecraft/world/entity/ConversionParams$AfterConversion;)Lnet/minecraft/world/entity/Mob;",
            at = @At("RETURN"))
    private void gulliver$keepSize(EntityType<?> type, ConversionParams params, EntitySpawnReason reason,
                                   ConversionParams.AfterConversion<?> after, CallbackInfoReturnable<Mob> cir) {
        Mob converted = cir.getReturnValue();
        if (converted != null) SpawnSizes.inherit((Mob) (Object) this, converted);
    }
    //#else
    //$$ @Inject(method = "convertTo(Lnet/minecraft/world/entity/EntityType;Z)Lnet/minecraft/world/entity/Mob;",
    //$$         at = @At("RETURN"))
    //$$ private void gulliver$keepSize(EntityType<?> type, boolean keepEquipment, CallbackInfoReturnable<Mob> cir) {
    //$$     Mob converted = cir.getReturnValue();
    //$$     if (converted != null) SpawnSizes.inherit((Mob) (Object) this, converted);
    //$$ }
    //#endif
}
