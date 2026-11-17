package gulliver.mixin;

import gulliver.access.IGulliverWings;
import gulliver.common.WingType;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Wing state on every player (IGulliverWings); saved through MixinLivingEntitySaveSize. */
@Mixin(Player.class)
public abstract class MixinPlayerWings implements IGulliverWings {

    @Unique private WingType gulliver$wingType = WingType.NONE;
    @Unique private int gulliver$wingColor = 0xFFFFFF;
    @Unique private byte gulliver$wingFlight = FOLDED;
    @Unique private Object gulliver$wingAnim;

    @Override @Unique public WingType gulliver$wingType() { return gulliver$wingType; }
    @Override @Unique public int gulliver$wingColor() { return gulliver$wingColor; }

    @Override
    @Unique
    public void gulliver$setWings(WingType type, int color) {
        gulliver$wingType = type == null ? WingType.NONE : type;
        gulliver$wingColor = color & 0xFFFFFF;
        if (gulliver$wingType == WingType.NONE) gulliver$wingFlight = FOLDED;
    }

    @Override @Unique public byte gulliver$wingFlight() { return gulliver$wingFlight; }
    @Override @Unique public void gulliver$setWingFlight(byte state) { gulliver$wingFlight = state; }
    @Override @Unique public Object gulliver$wingAnim() { return gulliver$wingAnim; }
    @Override @Unique public void gulliver$setWingAnim(Object anim) { gulliver$wingAnim = anim; }
}
