package gulliver.common;

import gulliver.api.IResizeableEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Items a player may not use at some sizes (#30), set by the server in
 * {@code itemRestrictions}: guns only for normal-size players, no
 * swords for tinies, no armor for giants, anything a modpack wants.
 *
 * Each rule names items (ids, a whole mod with "mod:*", patterns with
 * "*", or item tags as "#namespace:tag") and a size range. A "deny" rule
 * restricts them while the player's size is inside the range, an "allow"
 * rule while it is outside (so they work only inside). The server catches a
 * restricted item when it is held, worn or used (each a switch) and then
 * drops it out of the player's hands like the 1.6.4 mod did ("drop"),
 * shatters it like a worn-out tool ("break"), or only refuses to use it
 * and takes worn armor off into the inventory ("deny"). The action bar
 * says why, unless that is switched off. Creative and spectator players
 * are left alone unless the config says otherwise.
 */
public final class ItemRestrictions {
    private ItemRestrictions() {}

    private static final String BREAK = GulliverConfig.ItemRestrictions.BREAK;
    private static final String DENY = GulliverConfig.ItemRestrictions.DENY;

    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** When each player was last told, so the action bar isn't spammed. */
    private static final Map<UUID, Long> TOLD = new HashMap<>();

    // ---- compiled rules ----------------------------------------------------

    private record Matcher(String exact, Pattern pattern, TagKey<Item> tag) {
        boolean matches(ItemStack stack, String id) {
            if (tag != null) return stack.is(tag);
            if (exact != null) return exact.equals(id);
            return pattern.matcher(id).matches();
        }
    }

    private record Compiled(List<Matcher> items, double min, double max, boolean allow, String penalty) {
        /** Whether these items are restricted for a body of this size. */
        boolean restricts(float size) {
            boolean inside = (min <= 0.0D || size >= min - 1.0E-4D) && (max <= 0.0D || size <= max + 1.0E-4D);
            return allow != inside;
        }
    }

    private static List<Compiled> compiled = List.of();
    private static int compiledGeneration = -1;

    private static List<Compiled> rules() {
        int gen = GulliverConfig.generation();
        if (gen != compiledGeneration) {
            compiled = compile(GulliverConfig.INSTANCE.itemRestrictions);
            compiledGeneration = gen;
        }
        return compiled;
    }

    private static List<Compiled> compile(GulliverConfig.ItemRestrictions cfg) {
        List<Compiled> out = new ArrayList<>();
        if (cfg == null || cfg.rules == null) return out;
        for (GulliverConfig.ItemRule rule : cfg.rules) {
            if (rule == null || rule.items == null) continue;
            List<Matcher> matchers = new ArrayList<>();
            for (String raw : rule.items) {
                Matcher m = matcher(raw);
                if (m != null) matchers.add(m);
            }
            if (matchers.isEmpty()) continue;
            out.add(new Compiled(matchers, rule.minSize, rule.maxSize, GulliverConfig.ItemRule.ALLOW.equals(rule.mode),
                    rule.penalty == null || rule.penalty.isEmpty() ? cfg.penalty() : rule.penalty));
        }
        return out;
    }

    /** "minecraft:stick", "stick" (= minecraft:stick), "tacz:*", "*_sword", "#minecraft:swords". */
    static Matcher matcher(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return null;
        if (s.startsWith("#")) {
            Identifier id = Identifier.tryParse(s.substring(1).contains(":") ? s.substring(1) : "minecraft:" + s.substring(1));
            return id == null ? null : new Matcher(null, null, TagKey.create(Registries.ITEM, id));
        }
        if (s.contains("*")) {
            // A bare pattern ("*_sword") matches in every namespace.
            String glob = s.contains(":") ? s : "*:" + s;
            StringBuilder re = new StringBuilder();
            for (String part : glob.split("\\*", -1)) {
                if (re.length() > 0) re.append(".*");
                re.append(Pattern.quote(part));
            }
            return new Matcher(null, Pattern.compile(re.toString()), null);
        }
        return new Matcher(s.contains(":") ? s : "minecraft:" + s, null, null);
    }

    // ---- queries -----------------------------------------------------------

    /** Whether this stack is restricted for this player at their current size. */
    public static boolean isRestricted(Player player, ItemStack stack) {
        return penaltyFor(player, stack) != null;
    }

    /**
     * What happens to this stack in this player's hands at their current
     * size: the first matching rule's penalty, or null when it is allowed.
     */
    public static String penaltyFor(Player player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        GulliverConfig.ItemRestrictions cfg = GulliverConfig.INSTANCE.itemRestrictions;
        if (cfg == null || !cfg.enabled) return null;
        if (cfg.exemptCreative && (player.isCreative() || player.isSpectator())) return null;
        List<Compiled> rules = rules();
        if (rules.isEmpty()) return null;
        float size = ((IResizeableEntity) player).getSizeMultiplier();
        String id = String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        for (Compiled rule : rules) {
            if (!rule.restricts(size)) continue;
            for (Matcher m : rule.items()) {
                if (m.matches(stack, id)) return rule.penalty();
            }
        }
        return null;
    }

    private static GulliverConfig.ItemRestrictions cfg() {
        return GulliverConfig.INSTANCE.itemRestrictions;
    }

    // ---- server hooks --------------------------------------------------------

    /** Every server tick for every player: held and worn items. */
    public static void tick(ServerPlayer player) {
        GulliverConfig.ItemRestrictions cfg = cfg();
        if (cfg == null || !cfg.enabled || cfg.rules == null || cfg.rules.isEmpty()) return;
        if (!player.isAlive() || player.tickCount % 5 != 0) return;
        if (cfg.checkHeld) {
            for (InteractionHand hand : InteractionHand.values()) {
                String penalty = penaltyFor(player, player.getItemInHand(hand));
                // "deny" lets you hold it; only using it is refused.
                if (penalty != null && !DENY.equals(penalty)) {
                    punish(player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND, penalty);
                }
            }
        }
        if (cfg.checkWorn) {
            for (EquipmentSlot slot : ARMOR) {
                String penalty = penaltyFor(player, player.getItemBySlot(slot));
                if (penalty != null) punish(player, slot, penalty);
            }
        }
    }

    /**
     * The player tries to use, place, swing or mine with the item in this
     * hand. Returns true when that is refused (and the item has been dealt
     * with). Server side only; the client never knows the rules.
     */
    public static boolean refuseUse(Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer sp)) return false;
        GulliverConfig.ItemRestrictions cfg = cfg();
        if (cfg == null || !cfg.enabled || !cfg.checkUsed) return false;
        String penalty = penaltyFor(sp, sp.getItemInHand(hand));
        if (penalty == null) return false;
        punish(sp, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND, penalty);
        return true;
    }

    private static void punish(ServerPlayer player, EquipmentSlot slot, String penalty) {
        ItemStack stack = player.getItemBySlot(slot);
        if (stack.isEmpty()) return;
        tell(player);
        boolean armor = slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND;
        if (DENY.equals(penalty)) {
            if (!armor) return;
            // Taken off into the inventory, or onto the ground when that is full.
            ItemStack off = stack.copy();
            player.setItemSlot(slot, ItemStack.EMPTY);
            if (!player.getInventory().add(off)) dropAway(player, off);
            return;
        }
        player.setItemSlot(slot, ItemStack.EMPTY);
        if (BREAK.equals(penalty)) {
            shatter(player, slot, stack);
        } else {
            dropAway(player, stack);
        }
        player.inventoryMenu.broadcastChanges();
    }

    /** Falls out of the player's hands: a short toss forward, not picked up again at once. */
    private static void dropAway(ServerPlayer player, ItemStack stack) {
        ServerLevel level = (ServerLevel) player.level();
        double y = player.getEyeY() - 0.3D * ((IResizeableEntity) player).getSizeMultiplier();
        ItemEntity item = new ItemEntity(level, player.getX(), y, player.getZ(), stack);
        item.setPickUpDelay(40);
        net.minecraft.world.phys.Vec3 look = player.getLookAngle();
        item.setDeltaMovement(look.x * 0.2D, 0.15D, look.z * 0.2D);
        level.addFreshEntity(item);
    }

    /**
     * Breaks like a tool worn down to nothing: the snap and its shards, for
     * everyone around. (Vanilla's own break event reads the item from the
     * slot on the client, which is already empty by then.)
     */
    private static void shatter(ServerPlayer player, EquipmentSlot slot, ItemStack stack) {
        ServerLevel level = (ServerLevel) player.level();
        float size = ((IResizeableEntity) player).getSizeMultiplier();
        level.sendParticles(new net.minecraft.core.particles.ItemParticleOption(
                        //#if MC >= 26.1
                        net.minecraft.core.particles.ParticleTypes.ITEM, stack.getItem()),
                        //#else
                        //$$ net.minecraft.core.particles.ParticleTypes.ITEM, stack),
                        //#endif
                player.getX(), player.getY() + player.getBbHeight() * 0.6D, player.getZ(),
                12, 0.15D * size, 0.15D * size, 0.15D * size, 0.08D * Math.max(0.25D, size));
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                //#if MC >= 1.21.2
                net.minecraft.sounds.SoundEvents.ITEM_BREAK.value(),
                //#else
                //$$ net.minecraft.sounds.SoundEvents.ITEM_BREAK,
                //#endif
                SoundSource.PLAYERS, 0.8F, 0.8F + level.getRandom().nextFloat() * 0.4F);
    }

    private static void tell(ServerPlayer player) {
        GulliverConfig.ItemRestrictions cfg = cfg();
        if (cfg == null || !cfg.showMessage) return;
        long now = player.level().getGameTime();
        Long told = TOLD.get(player.getUUID());
        if (told != null && now - told < 20L && now >= told) return;
        TOLD.put(player.getUUID(), now);
        Component text = Component.translatable("gulliver.restricted");
        //#if MC >= 26.1
        player.sendOverlayMessage(text);
        //#else
        //$$ player.displayClientMessage(text, true);
        //#endif
    }

    public static void forget(ServerPlayer player) {
        TOLD.remove(player.getUUID());
    }
}
