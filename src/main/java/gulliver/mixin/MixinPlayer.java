package gulliver.mixin;

import gulliver.api.IResizeablePlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Marker — Player implements IResizeablePlayer. Same as the 1.6.4 mod's
 * IResizeablePlayer (no new methods over IResizeableLiving). The 1.6.4
 * Player override of isWeighted (uf.java:1890) is folded into
 * MixinLivingEntity.isWeighted's gliding/rafting short-circuit, since
 * those flags exist on every LivingEntity in the port. The
 * gold-ingot-helmet-metadata-42 special case is a Forge ASM coremod
 * easter-egg and is skipped.
 */
@Mixin(Player.class)
public abstract class MixinPlayer implements IResizeablePlayer {
}
