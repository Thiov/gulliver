package gulliver.init;

import gulliver.Gulliver;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Tags this mod reads; the contents live in data/gulliver/tags and can be extended by data packs. */
public final class GulliverTags {
    private GulliverTags() {}

    /**
     * Floors that give way under a huge body when nothing solid holds
     * them up: glass, panes and wool. Deliberately not "anything that
     * sounds like glass" - portal frames, ice, lamps and lanterns stay put.
     */
    public static final TagKey<Block> BRITTLE = TagKey.create(Registries.BLOCK, Gulliver.id("brittle"));
}
