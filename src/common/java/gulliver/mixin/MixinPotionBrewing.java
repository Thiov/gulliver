package gulliver.mixin;

import gulliver.init.GulliverPotions;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.Potions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Brewing recipes, added alongside vanilla's own mixes so every loader
 * picks them up the same way:
 *   awkward + red mushroom   -> Ensmallening
 *   awkward + brown mushroom -> Embiggening
 *   + redstone -> long, + glowstone -> strong
 */
@Mixin(PotionBrewing.class)
public abstract class MixinPotionBrewing {

    @Inject(method = "addVanillaMixes", at = @At("TAIL"))
    private static void gulliver$addMixes(PotionBrewing.Builder builder, CallbackInfo ci) {
        if (GulliverPotions.TINY == null) return; // registration failed; nothing to brew
        builder.addMix(Potions.AWKWARD, Items.RED_MUSHROOM, GulliverPotions.holder(GulliverPotions.TINY));
        builder.addMix(Potions.AWKWARD, Items.BROWN_MUSHROOM, GulliverPotions.holder(GulliverPotions.HUGE));
        builder.addMix(GulliverPotions.holder(GulliverPotions.TINY), Items.REDSTONE, GulliverPotions.holder(GulliverPotions.LONG_TINY));
        builder.addMix(GulliverPotions.holder(GulliverPotions.TINY), Items.GLOWSTONE_DUST, GulliverPotions.holder(GulliverPotions.STRONG_TINY));
        builder.addMix(GulliverPotions.holder(GulliverPotions.HUGE), Items.REDSTONE, GulliverPotions.holder(GulliverPotions.LONG_HUGE));
        builder.addMix(GulliverPotions.holder(GulliverPotions.HUGE), Items.GLOWSTONE_DUST, GulliverPotions.holder(GulliverPotions.STRONG_HUGE));
    }
}
