package gulliver.mixin.client;

import gulliver.client.VisualSize;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * The in-a-block overlay follows the player's size (#20).
 *
 * Vanilla darkens the whole screen with the texture of the block the
 * camera is in, and decides that by probing eight points around the eye:
 * across 0.8 of the body's width (which follows the size) and 0.05 above
 * and below the eye (0.1 apart, which only follows vanilla's own scale
 * attribute). A 1/32 player's eye is 0.05 blocks off the ground standing
 * and 0.04 crouching, so the lower probes sank into the floor and a
 * crouching tiny saw nothing but a dark wall of dirt. The probes' height
 * now follows the drawn size like the eye does: 0.1 x size apart, as
 * vanilla's own scale would make it.
 *
 * 1.20.1 to 1.21.11 probe in ScreenEffectRenderer, 26.3 in LevelExtractor.
 */
//#if MC >= 26.3
@Mixin(net.minecraft.client.renderer.extract.LevelExtractor.class)
//#else
//$$ @Mixin(net.minecraft.client.renderer.ScreenEffectRenderer.class)
//#endif
public abstract class MixinViewBlockingProbe {

    @ModifyConstant(method = "getViewBlockingState", constant = @Constant(floatValue = 0.1F), require = 0)
    private static float gulliver$scaleProbeHeight(float spread) {
        Entity player = Minecraft.getInstance().player;
        if (player == null) return spread;
        float size = VisualSize.of(player);
        return size == 1.0F || !(size > 0.0F) ? spread : spread * size;
    }
}
