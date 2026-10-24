package gulliver.common;

import gulliver.api.IResizeableEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;

import java.util.ArrayList;

/**
 * Boss bars for giant mobs ({@code general.giantBossBars}): a mob at size
 * 4 or more shows its name and health to the players near it, so a size-8
 * creeper announces itself. Players, and the mobs that already have a bar
 * of their own (Ender Dragon, Wither), don't get one.
 *
 * The bar lives in a field of the entity (MixinLivingEntityHugeEffects), so
 * it can't outlive it, and is dropped when the entity dies, is removed
 * (unloaded, changed dimension, discarded), shrinks below 4, or the option
 * is switched off.
 */
public final class GiantBossBars {
    private GiantBossBars() {}

    /** Smallest size that gets a bar. */
    public static final float MIN_SIZE = 4.0F;
    private static final int UPDATE_INTERVAL = 10;

    public static boolean wants(LivingEntity entity) {
        return GulliverConfig.INSTANCE.general.giantBossBars
                && entity instanceof Mob
                && !(entity instanceof EnderDragon) && !(entity instanceof WitherBoss)
                && entity.isAlive()
                && ((IResizeableEntity) entity).getSizeMultiplier() >= MIN_SIZE;
    }

    /** How far away players still see the bar: grows with the mob (size 4: 64 blocks, 8: 112, 16 and up: 192). */
    public static double range(float size) {
        return Math.min(192.0D, 16.0D + 12.0D * size);
    }

    /**
     * One server tick of a living entity that has a bar or might need one.
     * Returns the bar to keep (null when there is none).
     */
    public static ServerBossEvent update(LivingEntity entity, ServerBossEvent bar) {
        if (!wants(entity)) {
            if (bar != null) bar.removeAllPlayers();
            return null;
        }
        if (bar != null && entity.tickCount % UPDATE_INTERVAL != 0) return bar;
        if (!(entity.level() instanceof ServerLevel level)) return bar;

        if (bar == null) {
            bar = newBar(entity);
        }
        float maxHealth = entity.getMaxHealth();
        bar.setProgress(maxHealth > 0.0F ? Math.max(0.0F, Math.min(1.0F, entity.getHealth() / maxHealth)) : 0.0F);
        Component name = entity.getDisplayName();
        if (!name.equals(bar.getName())) bar.setName(name);

        double range = range(((IResizeableEntity) entity).getSizeMultiplier());
        double rangeSq = range * range;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(entity) <= rangeSq) {
                bar.addPlayer(player);
            } else {
                bar.removePlayer(player);
            }
        }
        // Players that left the level or the game since the last look.
        for (ServerPlayer player : new ArrayList<>(bar.getPlayers())) {
            if (player.level() != level || player.hasDisconnected()) bar.removePlayer(player);
        }
        return bar;
    }

    /** Takes the bar away from everybody (entity death or removal). */
    public static void discard(ServerBossEvent bar) {
        if (bar != null) bar.removeAllPlayers();
    }

    private static ServerBossEvent newBar(LivingEntity entity) {
        //#if MC >= 26.1
        return new ServerBossEvent(entity.getUUID(), entity.getDisplayName(),
                BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
        //#else
        //$$ return new ServerBossEvent(entity.getDisplayName(),
        //$$         BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
        //#endif
    }
}
