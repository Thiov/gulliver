package gulliver.access;

import net.minecraft.server.level.ServerBossEvent;

/** Added to every {@code Entity} by MixinEntityBossBar: the boss bar a giant mob currently shows (see GiantBossBars). */
public interface IGulliverBossBar {
    ServerBossEvent gulliver$getBossBar();

    void gulliver$setBossBar(ServerBossEvent bar);
}
