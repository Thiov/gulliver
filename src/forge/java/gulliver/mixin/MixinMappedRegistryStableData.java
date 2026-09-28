//#if MC >= 1.20.5
package gulliver.mixin;

import com.mojang.serialization.Lifecycle;
import gulliver.Gulliver;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Forge only. Vanilla marks data-pack registry entries that come from a
 * pack without "known pack" info as experimental, and Forge's mod packs
 * carry none — so Gulliver's two damage types made every world show the
 * "Worlds using Experimental Settings" screen. Fabric and NeoForge tag mod
 * packs themselves; here our own entries (and only ours) are registered
 * as stable.
 */
@Mixin(MappedRegistry.class)
public abstract class MixinMappedRegistryStableData {

    @ModifyVariable(method = "register(Lnet/minecraft/resources/ResourceKey;Ljava/lang/Object;Lnet/minecraft/core/RegistrationInfo;)Lnet/minecraft/core/Holder$Reference;",
            at = @At("HEAD"), argsOnly = true)
    private RegistrationInfo gulliver$stableOwnEntries(RegistrationInfo info, ResourceKey<?> key) {
        if (info.lifecycle() != Lifecycle.stable()
                && Gulliver.MOD_ID.equals(key.identifier().getNamespace())) {
            return new RegistrationInfo(info.knownPackInfo(), Lifecycle.stable());
        }
        return info;
    }
}
//#endif
