package gulliver.common;

import gulliver.Gulliver;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Grants Gulliver's code-triggered advancements. Each one has a single
 * "code" criterion with the impossible trigger, so only these calls
 * complete it; awarding twice is a cheap no-op in vanilla.
 */
public final class GulliverAdvancements {
    private GulliverAdvancements() {}

    public static final String SMALL_WORLD = "small_world";
    public static final String HEAD_IN_THE_CLOUDS = "head_in_the_clouds";
    public static final String PAPER_PLANE = "paper_plane";
    public static final String UP_WE_GO = "up_we_go";
    public static final String POCKET_FRIEND = "pocket_friend";
    public static final String TINY_RIDER = "tiny_rider";
    public static final String WATCH_YOUR_STEP = "watch_your_step";
    public static final String FEE_FI_FO_FUM = "fee_fi_fo_fum";

    public static void award(ServerPlayer player, String name) {
        var server = ((ServerLevel) player.level()).getServer();
        //#if MC >= 1.20.2
        var advancement = server.getAdvancements().get(Gulliver.id(name));
        //#else
        //$$ var advancement = server.getAdvancements().getAdvancement(Gulliver.id(name));
        //#endif
        if (advancement != null) player.getAdvancements().award(advancement, "code");
    }

    /** Size milestones, checked whenever a player's size settles on a new value. */
    public static void onPlayerSize(ServerPlayer player, float size) {
        if (size <= 0.125F + 1.0E-4F) award(player, SMALL_WORLD);
        if (size >= 8.0F - 1.0E-4F) award(player, HEAD_IN_THE_CLOUDS);
    }
}
