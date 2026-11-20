package gulliver.client;

import gulliver.common.StarterSizes;
import gulliver.network.Payloads;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The starter size picker (#32), shown the first time a player joins a
 * world. Cards along the bottom are the sizes on offer (and Custom with a
 * slider, where the server allows it); the stage above shows the player's
 * own model at the picked size next to a column of grass blocks stacked to
 * its height, both drawn to one exact scale so the size reads at a glance:
 * a tiny stands beside a block four times its height, a giant beside a
 * tower of fourteen. The stage
 * glides between sizes, the model follows the mouse, and the side panel
 * says how tall that is and what the size is good (and bad) at.
 *
 * Keyboard works throughout: arrows and Tab move between cards (the stage
 * follows the focused card), Enter on Start confirms, Esc decides later
 * where the server allows it.
 */
public class StarterSizeScreen extends Screen {
    private static final int GOLD = 0xFFFFD24A;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREY = 0xFFB8B8B8;
    private static final int DIM = 0xFF8A8A8A;

    private final Payloads.StarterSizeOffer offer;
    /** Choices plus, when allowed, Custom (last). */
    private final List<Option> options = new ArrayList<>();
    private final List<Card> cards = new ArrayList<>();
    private int selected;
    private float customSize;
    private Button start;
    private CustomSlider slider;

    /** The size the stage shows, gliding towards the selection (in log2). */
    private double shownLog = Double.NaN;
    private long lastFrame;

    private record Option(Component name, boolean custom, float size) {}

    public StarterSizeScreen(Payloads.StarterSizeOffer offer) {
        super(Component.translatable(offer.repick() ? "gulliver.starter.title.repick" : "gulliver.starter.title"));
        this.offer = offer;
        for (int i = 0; i < Math.min(offer.names().size(), offer.sizes().size()); i++) {
            float size = offer.sizes().get(i);
            options.add(new Option(StarterSizes.choiceName(offer.names().get(i), size), false, size));
        }
        float lo = Math.max(offer.customMin(), 1.0F / 32.0F);
        float hi = Math.max(lo, offer.customMax());
        customSize = Math.max(lo, Math.min(hi, 1.0F));
        if (offer.allowCustom()) options.add(new Option(Component.translatable("gulliver.starter.custom"), true, customSize));
        // Start on Normal if there is one (when changing: on the size the player has now),
        // else the size closest to the one the player has now.
        float now = offer.currentSize() > 0.0F ? offer.currentSize() : 1.0F;
        float start = offer.repick() ? now : 1.0F;
        int best = 0;
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).custom) continue;
            if (Math.abs(options.get(i).size - start) < 1.0E-4F) {
                best = i;
                break;
            }
            if (Math.abs(log2(options.get(i).size) - log2(now)) < Math.abs(log2(options.get(best).size) - log2(now))) best = i;
        }
        selected = best;
    }

    private float size(int index) {
        Option o = options.get(index);
        return o.custom ? customSize : o.size;
    }

    // ---- layout ----

    private int panelLeft() {
        return width / 2 - panelWidth() / 2;
    }

    private int panelWidth() {
        return Math.min(width - 24, 380);
    }

    private int bottomButtonsY() {
        return height - 28;
    }

    private int sliderY() {
        return bottomButtonsY() - 26;
    }

    private int cardsY() {
        return (offer.allowCustom() ? sliderY() : bottomButtonsY()) - 40;
    }

    private int panelTop() {
        return 44;
    }

    private int panelBottom() {
        return cardsY() - 8;
    }

    @Override
    protected void init() {
        cards.clear();
        int n = options.size();
        int gap = 6;
        int cardW = Math.min(96, (panelWidth() - gap * (n - 1)) / Math.max(1, n));
        int rowW = n * cardW + (n - 1) * gap;
        int x = width / 2 - rowW / 2;
        for (int i = 0; i < n; i++) {
            Card card = new Card(i, x + i * (cardW + gap), cardsY(), cardW, 32);
            cards.add(addRenderableWidget(card));
        }
        if (offer.allowCustom()) {
            slider = addRenderableWidget(new CustomSlider(width / 2 - 100, sliderY(), 200, 20));
        }
        // Changing a size already picked can always be cancelled.
        boolean later = offer.allowLater() || offer.repick();
        int buttonW = later ? 150 : 200;
        int bx = later ? width / 2 - buttonW - 4 : width / 2 - buttonW / 2;
        start = addRenderableWidget(Button.builder(Component.empty(), b -> confirm())
                .bounds(bx, bottomButtonsY(), buttonW, 20).build());
        if (later) {
            Button laterButton = Button.builder(Component.translatable(offer.repick() ? "gulliver.starter.cancelButton"
                            : "gulliver.starter.laterButton"), b -> onClose())
                    .bounds(width / 2 + 4, bottomButtonsY(), buttonW, 20).build();
            laterButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(
                    offer.repick() ? "gulliver.starter.cancelButton.tooltip" : "gulliver.starter.laterButton.tooltip")));
            addRenderableWidget(laterButton);
        }
        select(selected);
        if (!cards.isEmpty()) setInitialFocus(cards.get(selected));
    }

    private void select(int index) {
        selected = Math.max(0, Math.min(options.size() - 1, index));
        if (start != null) start.setMessage(Component.translatable("gulliver.starter.start", options.get(selected).name));
        if (slider != null) slider.active = options.get(selected).custom;
    }

    @Override
    public void tick() {
        super.tick();
        // Arrow keys and Tab move the focus: the stage shows the focused card.
        if (getFocused() instanceof Card card && card.index != selected) select(card.index);
    }

    private void confirm() {
        StarterSizeClient.choose(size(selected));
        minecraft.gui.setScreen(null);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return offer.allowLater() || offer.repick();
    }

    /** Esc or "Decide later": the server asks again next time. Esc or "Cancel" when changing: nothing changes. */
    @Override
    public void onClose() {
        if (!offer.allowLater() && !offer.repick()) return;
        StarterSizeClient.later();
        super.onClose();
    }

    // ---- drawing ----

    //#if MC >= 26.1
    @Override
    public void extractBackground(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                  float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        paint(new Paint(graphics), mouseX, mouseY);
    }
    //#elif MC >= 1.20.2
    //$$ @Override
    //$$ public void renderBackground(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY,
    //$$                              float partialTick) {
    //$$     super.renderBackground(graphics, mouseX, mouseY, partialTick);
    //$$     paint(new Paint(graphics), mouseX, mouseY);
    //$$ }
    //#else
    //$$ @Override
    //$$ public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    //$$     renderBackground(graphics);
    //$$     paint(new Paint(graphics), mouseX, mouseY);
    //$$     super.render(graphics, mouseX, mouseY, partialTick);
    //$$ }
    //#endif

    private void paint(Paint p, int mouseX, int mouseY) {
        glide();
        int cx = width / 2;
        p.push();
        p.translate(cx, 12);
        p.scale(1.5F);
        p.centered(font, title, 0, 0, GOLD, true);
        p.pop();
        Component sub = Component.translatable(offer.repick() ? "gulliver.starter.subtitle.repick"
                : offer.allowLater() ? "gulliver.starter.subtitle" : "gulliver.starter.subtitle.required");
        p.centered(font, sub, cx, 29, GREY, false);

        int x0 = panelLeft();
        int x1 = x0 + panelWidth();
        int y0 = panelTop();
        int y1 = panelBottom();
        if (y1 - y0 < 40) return; // a tiny window: the cards still work
        p.fill(x0, y0, x1, y1, 0xA0101018);
        p.frame(x0, y0, x1, y1, 0xFF3C3C46);
        int split = x0 + (x1 - x0) * 11 / 20;
        stage(p, x0 + 1, y0 + 1, split, y1 - 1, mouseX, mouseY);
        p.fill(split, y0 + 6, split + 1, y1 - 6, 0xFF3C3C46);
        info(p, split + 8, y0 + 8, x1 - 8, y1 - 6);
    }

    /** Eases the shown size towards the selected one, frame-rate independent. */
    private void glide() {
        double target = log2(size(selected));
        long now = System.nanoTime();
        if (Double.isNaN(shownLog)) {
            shownLog = target;
        } else {
            double dt = Math.min(0.1D, (now - lastFrame) / 1.0E9D);
            shownLog += (target - shownLog) * (1.0D - Math.exp(-dt * 9.0D));
            if (Math.abs(target - shownLog) < 1.0E-3D) shownLog = target;
        }
        lastFrame = now;
    }

    /**
     * The player beside a column of grass blocks stacked to its height, all
     * on one ground line and to one exact scale: the model is drawn at a
     * whole number of pixels per block (the preview only takes whole
     * numbers), and the blocks are sized from that same number, so a block
     * is exactly as wide and tall as one block of the body. A tiny stands
     * beside a single block four times its height; a normal player is just
     * under two; a giant towers next to a column of fourteen.
     */
    private void stage(Paint p, int x0, int y0, int x1, int y1, int mouseX, int mouseY) {
        p.gradient(x0, y0, x1, y1, 0x5531548C, 0x22101018);
        int ground = y1 - 12;
        p.fill(x0, ground, x1, ground + 2, 0xFF4E9A32);
        p.fill(x0, ground + 2, x1, y1, 0xFF5C4027);

        double shown = Math.pow(2.0D, shownLog);
        double tall = 1.8D * shown;
        int room = ground - y0 - 18; // space for the height label over the head
        // Blocks per stage height: the body, or the column of blocks next to it (one more at most).
        double fit = room / Math.max(1.15D, tall + 0.3D);
        // ... and across: a block, a gap and the body (about a block wide at size 1) side by side.
        fit = Math.min(fit, (x1 - x0 - 24) / (1.0D + shown));
        // Whole pixels per block for the body, and the block size taken from it: exactly 1:1.
        int scale = (int) Math.max(1L, Math.round(fit * shown));
        double px = scale / shown;
        // Column and body side by side, centred together.
        int colW = (int) Math.max(2L, Math.round(px));
        int bodyW = Math.max(8, scale);
        int gap = 10;
        int colLeft = (x0 + x1) / 2 - (colW + gap + bodyW) / 2;
        int colRight = colLeft + colW;
        int bodyX = colRight + gap + bodyW / 2;

        // The column of blocks, as tall as the body (at least one block).
        int blocks = Math.max(1, (int) Math.ceil(tall - 1.0E-3D));
        int top = y0 + 2;
        if (px >= 3.0D) {
            for (int b = 0; b < blocks; b++) {
                int by1 = (int) Math.round(ground - b * px);
                int by0 = (int) Math.round(ground - (b + 1) * px);
                if (by1 <= top) break;
                blockFace(p, colLeft, Math.max(top, by0), colRight, by1, by0, px, b == blocks - 1);
                // A faint seam between blocks, so they can be counted.
                if (b > 0) p.fill(colLeft, by1, colRight, by1 + 1, 0x70000000);
            }
        } else {
            // Blocks too small to draw one by one: a bar of dirt, a light line every five blocks.
            int barTop = Math.max(top, (int) Math.round(ground - blocks * px));
            p.fill(colLeft, barTop, colRight, ground, 0xFF79553A);
            p.fill(colLeft, barTop, colRight, barTop + 1, 0xFF5DA03A);
            for (int b = 5; b < blocks; b += 5) {
                int ty = (int) Math.round(ground - b * px);
                if (ty > barTop) p.fill(colLeft, ty, colRight, ty + 1, 0xFFB08A60);
            }
        }
        if (blocks == 1 && px >= 9.0D) {
            Component one = Component.translatable("gulliver.starter.oneBlock");
            p.centered(font, one, (colLeft + colRight) / 2, (int) Math.round(ground - px) - 10, DIM, false);
        }

        // The player, feet on the same ground.
        if (minecraft != null && minecraft.player != null) {
            p.entity(minecraft.player, bodyX, ground, scale, mouseX, mouseY);
        }
        // The top of the head (the model is 1.875 blocks tall at size 1), carried over to the column.
        int head = (int) Math.round(ground - 1.875D * scale);
        for (int x = colRight + 2; x < bodyX - scale * 0.3D - 1; x += 4) p.fill(x, head, x + 2, head + 1, 0x90FFFFFF);
        p.centered(font, Component.literal(height(size(selected))), bodyX, Math.max(y0 + 3, head - 12), WHITE, true);
    }

    /**
     * One block face filling x0..x1, y0..y1, with the game's own texture:
     * dirt, or a grass block's side for the top of the column. A face cut
     * off at the top of the stage shows its lower part ({@code faceTop} is
     * where the whole face would start).
     */
    private static void blockFace(Paint p, int x0, int y0, int x1, int y1, int faceTop, double px, boolean grass) {
        String texture = grass ? "block/grass_block_side" : "block/dirt";
        if (faceTop >= y0) {
            p.texture(texture, x0, y0, x1 - x0, y1 - y0);
            return;
        }
        // Cut off at the top: draw the whole face, clipped to what is on the stage.
        p.scissor(x0, y0, x1, y1);
        p.texture(texture, x0, faceTop, x1 - x0, y1 - faceTop);
        p.endScissor();
    }

    /** Name, size, height and what the size is like. */
    private void info(Paint p, int x0, int y0, int x1, int y1) {
        Option o = options.get(selected);
        float size = size(selected);
        int w = x1 - x0;
        p.push();
        p.translate(x0, y0);
        p.scale(1.5F);
        p.text(font, o.name, 0, 0, GOLD, true);
        p.pop();
        int y = y0 + 17;
        p.text(font, Component.translatable("gulliver.starter.sizeLine", SizeFormat.of(size), height(size)), x0, y, WHITE, false);
        y += font.lineHeight + 7;
        for (Component perk : perks(size)) {
            if (y + font.lineHeight > y1) break;
            p.fill(x0, y + 3, x0 + 3, y + 6, GOLD);
            y += p.wrapped(font, perk, x0 + 7, y, w - 7, GREY) + 3;
        }
    }

    /** A player's height at this size, as people say it: 1.8 m, 22.5 cm. */
    static String height(float size) {
        double metres = 1.8D * size;
        if (metres < 1.0D) return trim(String.format(Locale.ROOT, "%.1f", metres * 100.0D)) + " cm";
        return trim(String.format(Locale.ROOT, metres < 10.0D ? "%.2f" : "%.1f", metres)) + " m";
    }

    private static String trim(String s) {
        if (!s.contains(".")) return s;
        s = s.replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    /** What playing at this size is like: a few lines from the bracket it falls in. */
    static List<Component> perks(float size) {
        String bracket = size <= 0.3F ? "tiny" : size < 0.75F ? "small" : size <= 1.5F ? "normal"
                : size < 4.0F ? "big" : "giant";
        int count = switch (bracket) {
            case "tiny", "giant" -> 4;
            case "normal" -> 2;
            default -> 3;
        };
        List<Component> out = new ArrayList<>();
        for (int i = 1; i <= count; i++) out.add(Component.translatable("gulliver.starter.perk." + bracket + "." + i));
        return out;
    }

    private static double log2(double v) {
        return Math.log(Math.max(v, 1.0E-4D)) / Math.log(2.0D);
    }

    // ---- self-test hooks (ClientSelfTest) ----

    boolean repick() {
        return offer.repick();
    }

    int optionCount() {
        return options.size();
    }

    void pick(int index) {
        select(index);
        // The stage follows the focused card: the focus moves with the pick.
        if (index >= 0 && index < cards.size()) setFocused(cards.get(index));
    }

    void pressStart() {
        confirm();
    }

    // ---- widgets ----

    /** One size on offer: its name and size, framed in gold while picked. */
    private final class Card extends Button {
        final int index;

        Card(int index, int x, int y, int w, int h) {
            super(x, y, w, h, Component.translatable("gulliver.starter.card", options.get(index).name,
                    options.get(index).custom ? Component.translatable("gulliver.starter.custom.range",
                            SizeFormat.of(offer.customMin()), SizeFormat.of(offer.customMax()))
                            : Component.literal(SizeFormat.of(options.get(index).size) + "×")),
                    b -> select(index), DEFAULT_NARRATION);
            this.index = index;
        }

        private void draw(Paint p) {
            boolean picked = index == selected;
            boolean hot = isHoveredOrFocused();
            int x0 = getX();
            int y0 = getY();
            int x1 = x0 + getWidth();
            int y1 = y0 + getHeight();
            p.fill(x0, y0, x1, y1, picked ? 0xC0403410 : hot ? 0xB0303038 : 0xA0101018);
            p.frame(x0, y0, x1, y1, picked ? GOLD : hot ? 0xFFE0E0E0 : 0xFF46464F);
            if (picked) p.frame(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0x80FFD24A);
            Option o = options.get(index);
            int cx = (x0 + x1) / 2;
            p.centered(font, clip(o.name, getWidth() - 6), cx, y0 + 6, picked ? GOLD : WHITE, true);
            Component sub = o.custom ? Component.literal(SizeFormat.of(customSize) + "×")
                    : Component.literal(SizeFormat.of(o.size) + "×");
            p.centered(font, sub, cx, y0 + 19, picked ? 0xFFF0E0B0 : DIM, false);
        }

        private Component clip(Component text, int max) {
            if (font.width(text) <= max) return text;
            String s = text.getString();
            while (s.length() > 1 && font.width(s + "…") > max) s = s.substring(0, s.length() - 1);
            return Component.literal(s + "…");
        }

        //#if MC >= 26.1
        @Override
        protected void extractContents(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                       float partialTick) {
            draw(new Paint(graphics));
        }
        //#elif MC >= 1.21.11
        //$$ @Override
        //$$ protected void renderContents(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY,
        //$$                               float partialTick) {
        //$$     draw(new Paint(graphics));
        //$$ }
        //#else
        //$$ @Override
        //$$ protected void renderWidget(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY,
        //$$                             float partialTick) {
        //$$     draw(new Paint(graphics));
        //$$ }
        //#endif
    }

    /** Any size in the custom range, on a log scale with friendly stops. */
    private final class CustomSlider extends AbstractSliderButton {
        private final double[] stops;

        CustomSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), 0.0D);
            this.stops = stops(offer.customMin(), offer.customMax());
            value = fraction(customSize);
            updateMessage();
        }

        private double fraction(double v) {
            int best = 0;
            for (int i = 1; i < stops.length; i++) {
                if (Math.abs(stops[i] - v) < Math.abs(stops[best] - v)) best = i;
            }
            return stops.length > 1 ? best / (double) (stops.length - 1) : 0.0D;
        }

        @Override
        protected void updateMessage() {
            if (stops == null) return; // the super constructor calls this before the stops exist
            setMessage(Component.translatable("gulliver.starter.custom.slider", SizeFormat.of(customSize) + "×"));
        }

        @Override
        protected void applyValue() {
            customSize = (float) stops[(int) Math.round(value * (stops.length - 1))];
            for (int i = 0; i < options.size(); i++) {
                if (options.get(i).custom) select(i);
            }
            updateMessage();
        }

        /** Sizes worth stopping at between lo and hi: eighths below 1, quarters to 4, halves to 8, then whole. */
        private static double[] stops(float lo, float hi) {
            List<Double> out = new ArrayList<>();
            out.add((double) lo);
            for (double v = 1.0D / 32.0D; v <= 32.0D + 1.0E-6D; ) {
                if (v > lo + 1.0E-6D && v < hi - 1.0E-6D) out.add(v);
                v += v < 0.125D ? 1.0D / 32.0D : v < 1.0D ? 0.0625D : v < 4.0D ? 0.25D : v < 8.0D ? 0.5D : 1.0D;
            }
            if (hi > lo + 1.0E-6D) out.add((double) hi);
            double[] arr = new double[out.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = out.get(i);
            return arr;
        }
    }
}
