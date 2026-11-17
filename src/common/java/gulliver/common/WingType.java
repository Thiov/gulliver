package gulliver.common;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;

/**
 * The kinds of wings a tiny can grow (TinyWings), what grows them and how
 * each one flies. Speeds are in blocks per tick.
 *
 *  - Fly (phantom membrane): the fastest and nimblest, a blurred buzz,
 *    hovers dead still.
 *  - Bee (honeycomb): climbs best and lasts longest, a rounder buzz.
 *  - Butterfly (any small flower, coloured like it): slow, visible
 *    flaps, glides for free but bobs when it hovers.
 *  - Fairy (amethyst shard): shimmering, glowing wings that leave a
 *    trail of sparkles.
 */
public enum WingType {
    NONE(0, "none", 0.0F, false, false, 0.0F, 0.0F, 0.0F, 1.0F, 0.1F, 1.0F, 0.0F, 0xFFFFFF),
    FLY(1, "fly", 30.0F, true, false, 0.32F, 0.30F, 0.20F, 1.0F, 0.075F, 0.55F, 1.1F, 0xFFFFFF),
    BEE(2, "bee", 24.0F, true, true, 0.27F, 0.22F, 0.24F, 1.25F, 0.085F, 0.75F, 0.95F, 0xFFFFFF),
    BUTTERFLY(3, "butterfly", 3.2F, false, true, 0.21F, 0.11F, 0.15F, 1.0F, 0.022F, 1.7F, 1.3F, 0xF08A22),
    FAIRY(4, "fairy", 8.0F, false, true, 0.26F, 0.18F, 0.20F, 1.1F, 0.04F, 1.0F, 1.35F, 0xFFFFFF);

    /** Wire id. */
    public final byte id;
    public final String key;
    /** Wing beats per second while flapping. */
    public final float beatHz;
    /** Too fast to see: drawn as a blurred fan of wings. */
    public final boolean blurred;
    /** Has a second, smaller pair behind the first. */
    public final boolean hindwings;
    /** Cruising speed. */
    public final float cruise;
    /** How quickly it changes speed and direction (share of the gap closed per tick). */
    public final float agility;
    /** Climbing speed while jump is held. */
    public final float climb;
    /** Stamina lasts this many times as long. */
    public final float endurance;
    /** Sink speed with the wings held open and still. */
    public final float glideSink;
    /** How much it bobs up and down while hovering. */
    public final float bob;
    /** Forewing length at size 1, in blocks. */
    public final float span;
    /** Default tint (butterflies take their flower's). */
    public final int color;

    WingType(int id, String key, float beatHz, boolean blurred, boolean hindwings, float cruise, float agility,
             float climb, float endurance, float glideSink, float bob, float span, int color) {
        this.id = (byte) id;
        this.key = key;
        this.beatHz = beatHz;
        this.blurred = blurred;
        this.hindwings = hindwings;
        this.cruise = cruise;
        this.agility = agility;
        this.climb = climb;
        this.endurance = endurance;
        this.glideSink = glideSink;
        this.bob = bob;
        this.span = span;
        this.color = color;
    }

    public static WingType byId(int id) {
        for (WingType t : values()) if (t.id == id) return t;
        return NONE;
    }

    public static WingType byName(String name) {
        for (WingType t : values()) if (t.key.equalsIgnoreCase(name)) return t;
        return null;
    }

    /** Butterfly colours by flower; a modded flower gets the monarch's orange. */
    private static final Map<String, Integer> FLOWER_COLORS = Map.ofEntries(
            Map.entry("minecraft:dandelion", 0xF6D743),
            Map.entry("minecraft:poppy", 0xE0352E),
            Map.entry("minecraft:blue_orchid", 0x35B4E6),
            Map.entry("minecraft:allium", 0xB86BE0),
            Map.entry("minecraft:azure_bluet", 0xC9DCF4),
            Map.entry("minecraft:red_tulip", 0xE8452F),
            Map.entry("minecraft:orange_tulip", 0xF08A22),
            Map.entry("minecraft:white_tulip", 0xF2F2F2),
            Map.entry("minecraft:pink_tulip", 0xF2A6CF),
            Map.entry("minecraft:oxeye_daisy", 0xF7F1D2),
            Map.entry("minecraft:cornflower", 0x4C6FE6),
            Map.entry("minecraft:lily_of_the_valley", 0xF6F6FB),
            Map.entry("minecraft:wither_rose", 0x5A5560),
            Map.entry("minecraft:torchflower", 0xF59A30),
            Map.entry("minecraft:open_eyeblossom", 0xF29B3C),
            Map.entry("minecraft:closed_eyeblossom", 0x9C9AA6),
            Map.entry("minecraft:pink_petals", 0xF5B5D8),
            Map.entry("minecraft:wildflowers", 0xF3DD6B));

    /** What eating this stack grows, or NONE when it grows nothing. */
    public static WingType fromCatalyst(ItemStack stack) {
        if (stack.isEmpty()) return NONE;
        if (stack.is(Items.PHANTOM_MEMBRANE)) return FLY;
        if (stack.is(Items.HONEYCOMB)) return BEE;
        if (stack.is(Items.AMETHYST_SHARD)) return FAIRY;
        if (stack.is(net.minecraft.tags.BlockItemTags.SMALL_FLOWERS.item())
                || FLOWER_COLORS.containsKey(String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem())))) {
            return BUTTERFLY;
        }
        return NONE;
    }

    /** The colour wings grown from this stack take. */
    public static int colorOf(WingType type, ItemStack stack) {
        if (type != BUTTERFLY) return type.color;
        Integer c = FLOWER_COLORS.get(String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem())));
        return c != null ? c : BUTTERFLY.color;
    }
}
