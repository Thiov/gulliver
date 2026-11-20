package gulliver.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * The few drawing calls Gulliver's own screens need, spelled once for every
 * supported Minecraft: GuiGraphics (1.20 to 1.21.11) became
 * GuiGraphicsExtractor in 26.1, its pose turned 2D in 1.21.6, and the
 * player preview changed its arguments in 1.20.2. Colours are always ARGB
 * (1.21.6+ draws nothing with a zero alpha).
 */
public final class Paint {
    //#if MC >= 26.1
    public final net.minecraft.client.gui.GuiGraphicsExtractor g;

    public Paint(net.minecraft.client.gui.GuiGraphicsExtractor g) {
        this.g = g;
    }
    //#else
    //$$ public final net.minecraft.client.gui.GuiGraphics g;
    //$$
    //$$ public Paint(net.minecraft.client.gui.GuiGraphics g) {
    //$$     this.g = g;
    //$$ }
    //#endif

    public void fill(int x0, int y0, int x1, int y1, int argb) {
        g.fill(x0, y0, x1, y1, argb);
    }

    public void gradient(int x0, int y0, int x1, int y1, int top, int bottom) {
        g.fillGradient(x0, y0, x1, y1, top, bottom);
    }

    /** A one-pixel frame inside the box. */
    public void frame(int x0, int y0, int x1, int y1, int argb) {
        fill(x0, y0, x1, y0 + 1, argb);
        fill(x0, y1 - 1, x1, y1, argb);
        fill(x0, y0 + 1, x0 + 1, y1 - 1, argb);
        fill(x1 - 1, y0 + 1, x1, y1 - 1, argb);
    }

    public void text(Font font, Component text, int x, int y, int argb, boolean shadow) {
        //#if MC >= 26.1
        g.text(font, text, x, y, argb, shadow);
        //#else
        //$$ g.drawString(font, text, x, y, argb, shadow);
        //#endif
    }

    public void text(Font font, FormattedCharSequence text, int x, int y, int argb, boolean shadow) {
        //#if MC >= 26.1
        g.text(font, text, x, y, argb, shadow);
        //#else
        //$$ g.drawString(font, text, x, y, argb, shadow);
        //#endif
    }

    public void centered(Font font, Component text, int cx, int y, int argb, boolean shadow) {
        text(font, text, cx - font.width(text) / 2, y, argb, shadow);
    }

    /** Word-wrapped and centred on cx; returns the height used. */
    public int wrappedCentered(Font font, Component text, int cx, int y, int width, int argb) {
        int line = 0;
        for (FormattedCharSequence seq : font.split(text, width)) {
            text(font, seq, cx - font.width(seq) / 2, y + line * (font.lineHeight + 1), argb, false);
            line++;
        }
        return line * (font.lineHeight + 1);
    }

    /** Word-wrapped from x; returns the height used. */
    public int wrapped(Font font, Component text, int x, int y, int width, int argb) {
        int line = 0;
        for (FormattedCharSequence seq : font.split(text, width)) {
            text(font, seq, x, y + line * (font.lineHeight + 1), argb, false);
            line++;
        }
        return line * (font.lineHeight + 1);
    }

    /** Only what is drawn inside x0..x1, y0..y1 shows, until endScissor. */
    public void scissor(int x0, int y0, int x1, int y1) {
        g.enableScissor(x0, y0, x1, y1);
    }

    public void endScissor() {
        g.disableScissor();
    }

    public void push() {
        //#if MC >= 1.21.6
        g.pose().pushMatrix();
        //#else
        //$$ g.pose().pushPose();
        //#endif
    }

    public void pop() {
        //#if MC >= 1.21.6
        g.pose().popMatrix();
        //#else
        //$$ g.pose().popPose();
        //#endif
    }

    public void translate(float x, float y) {
        //#if MC >= 1.21.6
        g.pose().translate(x, y);
        //#else
        //$$ g.pose().translate(x, y, 0.0F);
        //#endif
    }

    public void scale(float s) {
        //#if MC >= 1.21.6
        g.pose().scale(s, s);
        //#else
        //$$ g.pose().scale(s, s, 1.0F);
        //#endif
    }

    /** A vanilla texture, e.g. "block/dirt", stretched over w x h (nearest-neighbour, so pixels stay crisp). */
    public void texture(String vanillaPath, int x, int y, int w, int h) {
        //#if MC >= 1.21
        net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.fromNamespaceAndPath(
                "minecraft", "textures/" + vanillaPath + ".png");
        //#else
        //$$ net.minecraft.resources.Identifier id = new net.minecraft.resources.Identifier(
        //$$         "minecraft", "textures/" + vanillaPath + ".png");
        //#endif
        //#if MC >= 1.21.6
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, id, x, y, 0.0F, 0.0F, w, h, 16, 16, 16, 16);
        //#else
        //$$ g.blit(id, x, y, w, h, 0.0F, 0.0F, 16, 16, 16, 16);
        //#endif
    }

    public void item(ItemStack stack, int x, int y) {
        //#if MC >= 26.1
        g.item(stack, x, y);
        //#else
        //$$ g.renderItem(stack, x, y);
        //#endif
    }

    /**
     * A living entity standing with its feet at (cx, feetY), drawn
     * {@code scale} pixels per block (as tall as the screen's own preview
     * makes a normal body at that scale) and looking at the mouse.
     */
    public void entity(LivingEntity entity, int cx, int feetY, int scale, float mouseX, float mouseY) {
        //#if MC >= 1.20.2
        // The model is centred in the box: make the box as tall as the body plus room, centred on its middle.
        int half = Math.max(12, Math.round(scale * 1.2F));
        int bodyHalf = Math.round(scale * 0.9F);
        int x0 = cx - half;
        int x1 = cx + half;
        int y0 = feetY - bodyHalf - half;
        int y1 = feetY - bodyHalf + half;
        //#if MC >= 26.1
        InventoryScreen.extractEntityInInventoryFollowsMouse(g, x0, y0, x1, y1, scale, 0.0F, mouseX, mouseY, entity);
        //#else
        //$$ InventoryScreen.renderEntityInInventoryFollowsMouse(g, x0, y0, x1, y1, scale, 0.0F, mouseX, mouseY, entity);
        //#endif
        //#else
        //$$ // 1.20.1 takes the feet position and the mouse relative to the head.
        //$$ InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx, feetY, scale,
        //$$         cx - mouseX, feetY - scale * 1.6F - mouseY, entity);
        //#endif
    }
}
