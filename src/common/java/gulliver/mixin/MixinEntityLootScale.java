package gulliver.mixin;

import gulliver.common.LootScaling;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The spawn of an item stack from an entity is where the death loot of a
 * mob becomes visible: while MixinLivingEntityLoot has the window open for
 * this mob, the stack it is about to spawn is resized by its size
 * (LootScaling). Outside the window, or for any other entity, this is a
 * single thread-local read.
 */
@Mixin(Entity.class)
public abstract class MixinEntityLootScale {

    //#if MC >= 1.21.2
    // The float-offset and plain overloads delegate to this one.
    @ModifyVariable(method = "spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/entity/item/ItemEntity;",
                    at = @At("HEAD"), argsOnly = true)
    private ItemStack gulliver$scaleDrop(ItemStack stack) {
    //#else
    //$$ @ModifyVariable(method = "spawnAtLocation(Lnet/minecraft/world/item/ItemStack;F)Lnet/minecraft/world/entity/item/ItemEntity;",
    //$$                 at = @At("HEAD"), argsOnly = true)
    //$$ private ItemStack gulliver$scaleDrop(ItemStack stack) {
    //#endif
        return LootScaling.scaleDrop((Entity) (Object) this, stack);
    }
}
