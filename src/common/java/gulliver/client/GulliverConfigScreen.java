package gulliver.client;

import gulliver.common.GulliverConfig;
import gulliver.common.GulliverEnvoy;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/**
 * In-game settings, opened from Mod Menu (Fabric) or the mod list's
 * Config button (NeoForge, Forge). Edits a copy of the config; Done (or
 * Esc) makes it live and writes config/gulliver.json, Cancel drops it.
 *
 * Built only from vanilla widgets, which draw themselves, so the same
 * screen runs on every supported Minecraft version.
 */
public class GulliverConfigScreen extends Screen {
    private enum Page { GAMEPLAY, SPAWN, LIMITS, CLIENT }

    /** Size-limit slider stops: finer below 1, coarser above. */
    private static final double[] LIMIT_SIZES = {
            0.125, 0.25, 0.375, 0.5, 0.625, 0.75, 0.875, 1, 1.25, 1.5, 1.75, 2, 2.5, 3, 3.5, 4, 5, 6, 7, 8 };

    private static final int ROW = 24;
    private static final int COLUMN = 150;

    private final Screen parent;
    private final GulliverConfig draft;
    private Page page = Page.GAMEPLAY;
    /** Spawn-size fields that don't parse; Done stays disabled while any do. */
    private final Set<String> invalid = new HashSet<>();
    private Button done;

    public GulliverConfigScreen(Screen parent) {
        super(Component.translatable("gulliver.config.title"));
        this.parent = parent;
        this.draft = GulliverConfig.INSTANCE.copy();
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int left = cx - COLUMN - 5;
        int right = cx + 5;

        addRenderableWidget(label(title, cx, 12, true));

        Page[] pages = Page.values();
        int tabWidth = 76;
        int tabsX = cx - (pages.length * tabWidth + (pages.length - 1) * 2) / 2;
        for (int i = 0; i < pages.length; i++) {
            Page p = pages[i];
            Button tab = Button.builder(Component.translatable("gulliver.config.tab." + p.name().toLowerCase(Locale.ROOT)),
                    b -> {
                        page = p;
                        rebuildWidgets();
                    }).bounds(tabsX + i * (tabWidth + 2), 28, tabWidth, 20).build();
            tab.active = p != page;
            addRenderableWidget(tab);
        }

        int y = 60;
        switch (page) {
            case GAMEPLAY -> initGameplay(left, right, y);
            case SPAWN -> initSpawn(left, right, y);
            case LIMITS -> initLimits(left, right, y);
            case CLIENT -> initClient(left, y);
        }

        Component note = null;
        if (page != Page.CLIENT && minecraft != null && minecraft.getConnection() != null
                && !minecraft.hasSingleplayerServer()) {
            note = Component.translatable("gulliver.config.note.server");
        } else if (page == Page.SPAWN || page == Page.LIMITS) {
            note = Component.translatable("gulliver.config.note.overrides");
        }
        if (note != null) {
            MultiLineTextWidget text = new MultiLineTextWidget(left, height - 56, note, font)
                    .setMaxWidth(2 * COLUMN + 10)
                    .setCentered(true);
            text.setX(cx - text.getWidth() / 2);
            addRenderableWidget(text);
        }

        done = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> saveAndClose())
                .bounds(left, height - 28, COLUMN, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gulliver.config.cancel"), b -> back())
                .bounds(right, height - 28, COLUMN, 20).build());
        done.active = invalid.isEmpty();
    }

    // ---- pages ----

    private void initGameplay(int left, int right, int y) {
        GulliverConfig.General g = draft.general;
        addRenderableWidget(toggle(left, y, "dyeResizing", g.enableDyeResizing, v -> g.enableDyeResizing = v));
        addRenderableWidget(toggle(right, y, "karmaMode", g.enableKarmaMode, v -> g.enableKarmaMode = v));
        y += ROW;
        addRenderableWidget(toggle(left, y, "grapple", g.fishingRodGrapple, v -> g.fishingRodGrapple = v));
        addRenderableWidget(slider(right, y, range(0.125, 1.0, 0.025), g.grappleMaxSize,
                v -> Component.translatable("gulliver.config.grappleMaxSize", fmt(v)),
                v -> g.grappleMaxSize = v, "grappleMaxSize"));
        y += ROW;
        addRenderableWidget(slider(left, y, range(0.0, 0.9, 0.05), g.trampleSizeRatio,
                v -> v <= 0.0 ? Component.translatable("gulliver.config.trample.off")
                        : Component.translatable("gulliver.config.trample", fmt(v)),
                v -> g.trampleSizeRatio = v, "trample"));
    }

    private void initSpawn(int left, int right, int y) {
        GulliverConfig.SpawnSize s = draft.spawnSize;
        sizeField(left, y, "player", s.basePlayerSize, true, v -> s.basePlayerSize = v);
        sizeField(right, y, "animal", s.baseAnimalSize, false, v -> s.baseAnimalSize = v);
        y += ROW + 12;
        sizeField(left, y, "monster", s.baseMonsterSize, false, v -> s.baseMonsterSize = v);
        sizeField(right, y, "npc", s.baseNpcSize, false, v -> s.baseNpcSize = v);
    }

    private void initLimits(int left, int right, int y) {
        GulliverConfig.SizeLimit l = draft.sizeLimit;
        limitRow(left, right, y, "player", l.minPlayerSize, l.maxPlayerSize,
                v -> l.minPlayerSize = v, v -> l.maxPlayerSize = v);
        y += ROW;
        limitRow(left, right, y, "animal", l.minAnimalSize, l.maxAnimalSize,
                v -> l.minAnimalSize = v, v -> l.maxAnimalSize = v);
        y += ROW;
        limitRow(left, right, y, "monster", l.minMonsterSize, l.maxMonsterSize,
                v -> l.minMonsterSize = v, v -> l.maxMonsterSize = v);
        y += ROW;
        limitRow(left, right, y, "npc", l.minNpcSize, l.maxNpcSize,
                v -> l.minNpcSize = v, v -> l.maxNpcSize = v);
    }

    private void initClient(int left, int y) {
        GulliverConfig.Client c = draft.client;
        Button held = Button.builder(heldItemsLabel(c.proportionalHeldItems()), b -> {
            c.heldItemScaling = c.proportionalHeldItems() ? "classic" : "proportional";
            b.setMessage(heldItemsLabel(c.proportionalHeldItems()));
        }).bounds(left, y, 2 * COLUMN + 10, 20).build();
        held.setTooltip(Tooltip.create(Component.translatable("gulliver.config.heldItems.tooltip")));
        addRenderableWidget(held);
    }

    // ---- widgets ----

    private Button toggle(int x, int y, String key, boolean initial, Consumer<Boolean> set) {
        boolean[] state = { initial };
        Component name = Component.translatable("gulliver.config." + key);
        Button b = Button.builder(onOff(name, initial), btn -> {
            state[0] = !state[0];
            set.accept(state[0]);
            btn.setMessage(onOff(name, state[0]));
        }).bounds(x, y, COLUMN, 20).build();
        b.setTooltip(Tooltip.create(Component.translatable("gulliver.config." + key + ".tooltip")));
        return b;
    }

    private void sizeField(int x, int y, String kind, String value, boolean allowHeights, Consumer<String> set) {
        addRenderableWidget(label(Component.translatable("gulliver.config.spawn." + kind), x, y, false));
        EditBox box = new EditBox(font, x, y + 11, COLUMN, 18, Component.translatable("gulliver.config.spawn." + kind));
        box.setMaxLength(64);
        box.setValue(value);
        box.setTooltip(Tooltip.create(Component.translatable("gulliver.config.spawn.tooltip")));
        box.setTextColor(valid(value, allowHeights) ? 0xFFE0E0E0 : 0xFFFF5555);
        box.setResponder(text -> {
            boolean ok = valid(text, allowHeights);
            box.setTextColor(ok ? 0xFFE0E0E0 : 0xFFFF5555);
            if (ok) {
                invalid.remove(kind);
                set.accept(text.trim());
            } else {
                invalid.add(kind);
            }
            if (done != null) done.active = invalid.isEmpty();
        });
        addRenderableWidget(box);
    }

    private void limitRow(int left, int right, int y, String kind, double min, double max,
                          DoubleConsumer setMin, DoubleConsumer setMax) {
        Component name = Component.translatable("gulliver.config.spawn." + kind);
        addRenderableWidget(slider(left, y, LIMIT_SIZES, min,
                v -> Component.translatable("gulliver.config.limit.min", name, fmt(v)), setMin, null));
        addRenderableWidget(slider(right, y, LIMIT_SIZES, max,
                v -> Component.translatable("gulliver.config.limit.max", name, fmt(v)), setMax, null));
    }

    private AbstractWidget slider(int x, int y, double[] stops, double initial, DoubleFunction<Component> label,
                                  DoubleConsumer set, String tooltipKey) {
        StopSlider s = new StopSlider(x, y, COLUMN, stops, initial, label, set);
        if (tooltipKey != null) {
            s.setTooltip(Tooltip.create(Component.translatable("gulliver.config." + tooltipKey + ".tooltip")));
        }
        return s;
    }

    private StringWidget label(Component text, int x, int y, boolean centered) {
        int w = font.width(text);
        return new StringWidget(centered ? x - w / 2 : x, y, w, 9, text, font);
    }

    // ---- closing ----

    private void saveAndClose() {
        GulliverConfig.SizeLimit l = draft.sizeLimit;
        // A min above its max would clamp everything to the min: follow it.
        l.maxPlayerSize = Math.max(l.maxPlayerSize, l.minPlayerSize);
        l.maxAnimalSize = Math.max(l.maxAnimalSize, l.minAnimalSize);
        l.maxMonsterSize = Math.max(l.maxMonsterSize, l.minMonsterSize);
        l.maxNpcSize = Math.max(l.maxNpcSize, l.minNpcSize);
        GulliverConfig.INSTANCE = draft;
        GulliverConfig.save();
        back();
    }

    private void back() {
        minecraft.gui.setScreen(parent);
    }

    /** Esc keeps the edits, like vanilla option screens, unless a field is broken. */
    @Override
    public void onClose() {
        if (invalid.isEmpty()) saveAndClose();
        else back();
    }

    //#if MC < 1.20.2
    //$$ @Override
    //$$ public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    //$$     renderBackground(graphics);
    //$$     super.render(graphics, mouseX, mouseY, partialTick);
    //$$ }
    //#endif

    // ---- self-test hooks (ClientSelfTest) ----

    void showPage(int index) {
        page = Page.values()[index];
        rebuildWidgets();
    }

    GulliverConfig draft() {
        return draft;
    }

    void pressDone() {
        saveAndClose();
    }

    // ---- helpers ----

    private static boolean valid(String text, boolean allowHeights) {
        return GulliverEnvoy.isValidSizeString(text.trim(), allowHeights);
    }

    private static Component onOff(Component name, boolean on) {
        return Component.translatable("options.generic_value", name,
                on ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }

    private static Component heldItemsLabel(boolean proportional) {
        return Component.translatable("options.generic_value", Component.translatable("gulliver.config.heldItems"),
                Component.translatable(proportional ? "gulliver.config.heldItems.proportional"
                        : "gulliver.config.heldItems.classic"));
    }

    private static String fmt(double v) {
        String s = String.format(Locale.ROOT, "%.3f", v);
        s = s.replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    private static double[] range(double from, double to, double step) {
        int n = (int) Math.round((to - from) / step) + 1;
        double[] out = new double[n];
        for (int i = 0; i < n; i++) out[i] = Math.round((from + i * step) * 1000.0) / 1000.0;
        return out;
    }

    /**
     * A slider over fixed stops. The config value only changes once the
     * slider is moved, so a hand-edited value between stops survives.
     */
    private static final class StopSlider extends AbstractSliderButton {
        private final double[] stops;
        private final DoubleFunction<Component> label;
        private final DoubleConsumer set;
        private double shown;

        StopSlider(int x, int y, int w, double[] stops, double initial, DoubleFunction<Component> label,
                   DoubleConsumer set) {
            super(x, y, w, 20, Component.empty(), fraction(stops, initial));
            this.stops = stops;
            this.label = label;
            this.set = set;
            this.shown = initial;
            updateMessage();
        }

        private static double fraction(double[] stops, double v) {
            int best = 0;
            for (int i = 1; i < stops.length; i++) {
                if (Math.abs(stops[i] - v) < Math.abs(stops[best] - v)) best = i;
            }
            return stops.length > 1 ? best / (double) (stops.length - 1) : 0.0;
        }

        private double stop() {
            return stops[(int) Math.round(value * (stops.length - 1))];
        }

        @Override
        protected void updateMessage() {
            // Before the first move this shows the configured value itself.
            // (Null while the super constructor runs, in versions that call this there.)
            if (label != null) setMessage(label.apply(shown));
        }

        @Override
        protected void applyValue() {
            shown = stop();
            set.accept(shown);
            updateMessage();
        }
    }
}
