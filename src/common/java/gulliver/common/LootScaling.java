package gulliver.common;

import gulliver.api.IResizeableEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Loot that grows with the mob ({@code general.lootScaling}): a tiny
 * chicken drops fewer feathers (sometimes none), a size-8 cow about three
 * times the beef.
 *
 *   size below 1: factor = size, per stack, rounded stochastically
 *   size above 1: factor = size^0.6, at most 4  (2: 1.5x, 4: 2.3x, 8: 3.5x)
 *
 * Only what the mob's own loot table drops on death, and the experience
 * it gives. Equipment, inventories and anything the table does not roll
 * are untouched, so nothing here can duplicate what a mob carried. A
 * stack never grows past its maximum, bosses (the unique drops of the
 * Wither, Warden and Dragon would be a free beacon each) and players are
 * left alone.
 *
 * The window is opened around LivingEntity.dropFromLootTable
 * (MixinLivingEntityLoot) and the drops are adjusted where they are
 * spawned (MixinEntityLootScale).
 */
public final class LootScaling {
    private LootScaling() {}

    /** The mob whose death loot is being rolled right now, if scaling applies to it. */
    private static final ThreadLocal<LivingEntity> DYING = new ThreadLocal<>();

    public static void begin(LivingEntity entity) {
        if (applies(entity)) DYING.set(entity);
    }

    public static void end() {
        DYING.remove();
    }

    private static boolean applies(LivingEntity entity) {
        return GulliverConfig.INSTANCE.general.lootScaling
                && !(entity instanceof Player) && !(entity instanceof ArmorStand)
                && !(entity instanceof EnderDragon) && !(entity instanceof WitherBoss) && !(entity instanceof Warden);
    }

    /** The multiplier for a mob of this size. */
    public static double factor(float size) {
        if (!(size > 0.0F)) return 1.0D;
        return size < 1.0F ? size : Math.min(4.0D, Math.pow(size, 0.6D));
    }

    /** What actually drops where {@code spawner} spawns {@code stack}: the same stack, a resized copy, or nothing. */
    public static ItemStack scaleDrop(Entity spawner, ItemStack stack) {
        LivingEntity dying = DYING.get();
        if (dying == null || dying != spawner || stack.isEmpty()) return stack;
        double factor = factor(((IResizeableEntity) dying).getSizeMultiplier());
        if (factor == 1.0D) return stack;
        int count = Math.min(stack.getMaxStackSize(),
                SizeScaling.stochasticRound(stack.getCount() * factor, dying.getRandom()));
        if (count == stack.getCount()) return stack;
        if (count <= 0) return ItemStack.EMPTY;
        ItemStack scaled = stack.copy();
        scaled.setCount(count);
        return scaled;
    }

    /** The experience a dying mob gives. */
    public static int scaleExperience(LivingEntity dying, int amount) {
        if (amount <= 0 || !applies(dying)) return amount;
        double factor = factor(((IResizeableEntity) dying).getSizeMultiplier());
        return factor == 1.0D ? amount : SizeScaling.stochasticRound(amount * factor, dying.getRandom());
    }
}
