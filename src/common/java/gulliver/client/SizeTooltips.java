package gulliver.client;

import gulliver.api.IResizeableEntity;
import gulliver.common.GulliverConfig;
import gulliver.common.GulliverEnvoy;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * One short grey line on the items that do something for a tiny player
 * (shown while the local player is tiny), and on the dyes and mushrooms
 * that resize whoever eats or uses them (shown always while dye resizing
 * is on). Each loader's tooltip event calls add().
 */
public final class SizeTooltips {
    private SizeTooltips() {}

    public static void add(ItemStack stack, List<Component> lines) {
        if (stack == null || stack.isEmpty() || lines == null) return;
        String key = keyFor(stack);
        if (key != null) lines.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
    }

    private static String keyFor(ItemStack stack) {
        GulliverConfig config = GulliverConfig.INSTANCE;
        if (config.general.enableDyeResizing) {
            if (stack.is(Items.DYE.cyan()) || stack.is(Items.RED_MUSHROOM)) return "gulliver.tooltip.shrink";
            if (stack.is(Items.DYE.purple()) || stack.is(Items.BROWN_MUSHROOM)) return "gulliver.tooltip.grow";
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return null;
        IResizeableEntity sized = (IResizeableEntity) player;
        if (!sized.isTiny()) return null;
        if (stack.is(Items.PAPER)) return "gulliver.tooltip.glide";
        if (stack.is(Items.LILY_PAD)) return "gulliver.tooltip.raft";
        if (stack.is(Items.SLIME_BALL)) return "gulliver.tooltip.climb";
        if (stack.is(Items.STRING)) return "gulliver.tooltip.rope";
        if (stack.is(Items.FISHING_ROD)) {
            boolean hooks = config.general.fishingRodGrapple && sized.getSizeMultiplier() <= config.general.grappleMaxSize;
            return hooks ? "gulliver.tooltip.grapple" : null;
        }
        if (GulliverEnvoy.isItemPointy(stack)) return "gulliver.tooltip.pointy";
        return null;
    }
}
