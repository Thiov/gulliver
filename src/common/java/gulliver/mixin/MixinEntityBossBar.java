package gulliver.mixin;

import gulliver.access.IGulliverBossBar;
import gulliver.common.GiantBossBars;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Holds the boss bar of a giant mob (GiantBossBars) on the entity itself,
 * so it can't outlive it, and takes it down whenever the entity leaves the
 * world. Chunk unloading and changing dimension call setRemoved directly,
 * not remove(), so that is the one place every way out passes through.
 */
@Mixin(Entity.class)
public abstract class MixinEntityBossBar implements IGulliverBossBar {

    /** Never saved. */
    @Unique private ServerBossEvent gulliver$bossBar;

    @Override
    @Unique
    public ServerBossEvent gulliver$getBossBar() {
        return gulliver$bossBar;
    }

    @Override
    @Unique
    public void gulliver$setBossBar(ServerBossEvent bar) {
        gulliver$bossBar = bar;
    }

    @Inject(method = "setRemoved", at = @At("HEAD"))
    private void gulliver$dropBossBar(Entity.RemovalReason reason, CallbackInfo ci) {
        if (gulliver$bossBar != null) {
            GiantBossBars.discard(gulliver$bossBar);
            gulliver$bossBar = null;
        }
    }
}
