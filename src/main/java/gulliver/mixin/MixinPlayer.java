package gulliver.mixin;

import gulliver.api.IResizeablePlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Marker — Player implements IResizeablePlayer. Same as the 1.6.4 mod's
 * IResizeablePlayer (no new methods over IResizeableLiving).
 */
@Mixin(Player.class)
public abstract class MixinPlayer implements IResizeablePlayer {
}
