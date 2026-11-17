package gulliver.client;

import gulliver.access.IGulliverWings;
import gulliver.common.WingType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
//#if MC >= 26.1
import net.minecraft.client.gui.GuiGraphicsExtractor;
//#else
//$$ import net.minecraft.client.gui.GuiGraphics;
//#endif

/**
 * The wings' stamina (WingFlight): a slim bar under the crosshair in the
 * wings' own colour, shown while flying and while the wings get their
 * breath back, fading away once they are rested. It turns red as they tire
 * and pulses while they are spent.
 */
public final class WingHud {
    private WingHud() {}

    private static float alpha;
    private static long lastNanos;

    //#if MC >= 26.1
    public static void draw(GuiGraphicsExtractor g, float partialTick) {
    //#else
    //$$ public static void draw(GuiGraphics g, float partialTick) {
    //#endif
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        // Only your own view: not while watching through something else.
        if (p == null || mc.getCameraEntity() != p || ClientShowcaseWings.filming()) return;
        //#if MC < 26.2
        //$$ if (mc.options.hideGui) return;
        //#endif
        IGulliverWings w = (IGulliverWings) p;
        WingType type = w.gulliver$wingType();
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.0F : Math.min(0.1F, (now - lastNanos) / 1.0E9F);
        lastNanos = now;
        float stamina = WingFlight.stamina(partialTick);
        boolean want = type != WingType.NONE && WingAnim.visible(p)
                && (IGulliverWings.flying(w.gulliver$wingFlight()) || stamina < 0.999F);
        alpha = Mth.clamp(alpha + (want ? 4.0F : -1.5F) * dt, 0.0F, 1.0F);
        if (alpha <= 0.01F) return;

        int width = 44;
        int x = g.guiWidth() / 2 - width / 2;
        int y = g.guiHeight() / 2 + 11;
        int base = type == WingType.BUTTERFLY ? w.gulliver$wingColor() : color(type);
        int fillColor = base;
        if (stamina < 0.3F) fillColor = mix(0xE0453A, base, stamina / 0.3F);
        float a = alpha;
        if (WingFlight.spent()) a *= 0.55F + 0.45F * Mth.sin(now / 1.5E8F);
        int filled = Math.round(width * stamina);
        g.fill(x - 1, y - 1, x + width + 1, y + 3, argb(0.55F * a, 0x101014));
        g.fill(x, y, x + filled, y + 2, argb(0.9F * a, fillColor));
        // A faint highlight along the top, and a notch where spent wings may fly again.
        g.fill(x, y, x + filled, y + 1, argb(0.35F * a, 0xFFFFFF));
        int notch = x + Math.round(width * 0.33F);
        if (WingFlight.spent()) g.fill(notch, y - 1, notch + 1, y + 3, argb(0.8F * a, 0xFFFFFF));
    }

    private static int color(WingType type) {
        return switch (type) {
            case BEE -> 0xF2C14E;
            case FAIRY -> 0xE6A8FF;
            default -> 0xA9DDF2;
        };
    }

    private static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int gr = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (gr << 8) | bl;
    }

    private static int argb(float alpha, int rgb) {
        return ((int) (Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F) << 24) | (rgb & 0xFFFFFF);
    }
}
