package com.julz.rendererbenchmark.client;

import com.julz.rendererbenchmark.config.ComparisonState;
import com.julz.rendererbenchmark.core.RunMetrics;
import com.julz.rendererbenchmark.core.ShareText;
import com.julz.rendererbenchmark.core.Verdict;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToDoubleFunction;

/**
 * The answer: which renderer is faster, the numbers behind it side by side, and buttons to keep
 * the winner, copy the result as text or save it as an image.
 */
public class ResultsScreen extends Screen {
    private static final String PREFIX = "renderer-benchmark.results.";
    private static final int VULKAN_COLOR = 0xFFE5533D;
    private static final int OPENGL_COLOR = 0xFF4C8BF5;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFA0A0A0;
    private static final int WARNING = 0xFFFFC040;
    private static final int ROW_HEIGHT = 24;
    private static final int LABEL_WIDTH = 92;
    private static final int VALUE_WIDTH = 40;

    private final Screen parent;
    private final ComparisonState state;
    /** True right after a comparison, when the player still has to pick a renderer. */
    private final boolean decide;
    private final RunMetrics first;
    private final RunMetrics second;
    private final Verdict verdict;
    private final List<AbstractWidget> buttons = new ArrayList<>();
    private Component status = Component.empty();
    private boolean capturing;
    private int framesSinceHidden;

    public ResultsScreen(Screen parent, ComparisonState state, boolean decide) {
        super(Component.translatable(PREFIX + "title"));
        this.parent = parent;
        this.state = state;
        this.decide = decide;
        this.first = RunMetrics.median(state.firstRuns);
        this.second = RunMetrics.median(state.secondRuns);
        this.verdict = Verdict.compare(state.firstRenderer, first, state.secondRenderer, second);
    }

    @Override
    protected void init() {
        this.buttons.clear();
        int center = this.width / 2;
        int bottom = this.height - 28;
        if (this.decide) {
            if (!this.verdict.isTie()) {
                add(Button.builder(Component.translatable(PREFIX + "use", this.verdict.winner()), button -> choose(this.verdict.winner()))
                        .bounds(center - 154, bottom - 24, 150, 20).build());
                add(Button.builder(keepLabel(), button -> choose(null))
                        .bounds(center + 4, bottom - 24, 150, 20).build());
            } else {
                add(Button.builder(keepLabel(), button -> choose(null))
                        .bounds(center - 75, bottom - 24, 150, 20).build());
            }
            add(Button.builder(Component.translatable(PREFIX + "copy"), button -> copy())
                    .bounds(center - 154, bottom, 150, 20).build());
            add(Button.builder(Component.translatable(PREFIX + "saveImage"), button -> startCapture())
                    .bounds(center + 4, bottom, 150, 20).build());
        } else {
            add(Button.builder(Component.translatable(PREFIX + "copy"), button -> copy())
                    .bounds(center - 154, bottom, 100, 20).build());
            add(Button.builder(Component.translatable(PREFIX + "saveImage"), button -> startCapture())
                    .bounds(center - 50, bottom, 100, 20).build());
            add(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                    .bounds(center + 54, bottom, 100, 20).build());
        }
        setButtonsVisible(!this.capturing);
    }

    private void add(Button button) {
        this.buttons.add(addRenderableWidget(button));
    }

    private Component keepLabel() {
        Component original = Renderers.parsePreference(this.state.originalPreference).caption();
        return Component.translatable(PREFIX + "keep", original);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        if (this.capturing) {
            this.framesSinceHidden++;
        }
        int contentWidth = Math.min(360, this.width - 20);
        int left = (this.width - contentWidth) / 2;
        int y = 10;
        graphics.centeredText(this.font, this.title, this.width / 2, y, TEXT);
        y += 14;
        graphics.centeredText(this.font, headline(), this.width / 2, y, verdictColor());
        y += 16;

        // Legend
        int legendX = left + LABEL_WIDTH;
        legendX = legend(graphics, legendX, y, this.state.firstRenderer);
        legend(graphics, legendX + 12, y, this.state.secondRenderer);
        y += 14;

        y = metricRow(graphics, left, y, contentWidth, Component.translatable(PREFIX + "average"), RunMetrics::averageFps, "%.0f");
        y = metricRow(graphics, left, y, contentWidth, Component.translatable(PREFIX + "onePercentLow"), RunMetrics::onePercentLowFps, "%.0f");
        y = metricRow(graphics, left, y, contentWidth, Component.translatable(PREFIX + "pointOnePercentLow"), RunMetrics::pointOnePercentLowFps, "%.0f");
        y = metricRow(graphics, left, y, contentWidth, Component.translatable(PREFIX + "spikes"), m -> m.spikes(), "%.0f");
        y += 4;

        graphics.text(this.font, Component.translatable(PREFIX + "gpu", gpuText()), left, y, MUTED);
        y += 11;
        graphics.text(this.font, Component.translatable(PREFIX + "settings", BenchmarkController.settingsSummary(this.state)), left, y, MUTED);
        y += 11;
        if (!this.state.finishedAt.isEmpty()) {
            graphics.text(this.font, Component.translatable(PREFIX + "finishedAt", this.state.finishedAt), left, y, MUTED);
            y += 11;
        }
        if (!this.state.firstWindow.equals(this.state.secondWindow)) {
            graphics.text(this.font, Component.translatable(PREFIX + "windowChanged", this.state.firstWindow, this.state.secondWindow), left, y, WARNING);
            y += 11;
        }
        if (!this.capturing) {
            graphics.centeredText(this.font, this.status, this.width / 2, this.height - 66, TEXT);
        }
    }

    private int legend(GuiGraphicsExtractor graphics, int x, int y, String renderer) {
        graphics.fill(x, y, x + 8, y + 8, colorOf(renderer));
        graphics.text(this.font, renderer, x + 11, y, TEXT);
        return x + 11 + this.font.width(renderer);
    }

    /** One metric: its name on the left, and a bar for each renderer, scaled to the larger value. */
    private int metricRow(GuiGraphicsExtractor graphics, int left, int y, int contentWidth, Component label,
                          ToDoubleFunction<RunMetrics> metric, String format) {
        double a = metric.applyAsDouble(this.first);
        double b = metric.applyAsDouble(this.second);
        double max = Math.max(1, Math.max(a, b));
        int barLeft = left + LABEL_WIDTH;
        int barWidth = contentWidth - LABEL_WIDTH - VALUE_WIDTH;
        graphics.text(this.font, label, left, y + 5, TEXT);
        bar(graphics, barLeft, y, barWidth, a / max, this.state.firstRenderer, String.format(Locale.ROOT, format, a));
        bar(graphics, barLeft, y + 10, barWidth, b / max, this.state.secondRenderer, String.format(Locale.ROOT, format, b));
        return y + ROW_HEIGHT;
    }

    private void bar(GuiGraphicsExtractor graphics, int x, int y, int width, double fraction, String renderer, String value) {
        int length = Math.max(1, (int) Math.round(width * fraction));
        graphics.fill(x, y, x + width, y + 8, 0x40FFFFFF);
        graphics.fill(x, y, x + length, y + 8, colorOf(renderer));
        graphics.text(this.font, value, x + width + 4, y, TEXT);
    }

    private static int colorOf(String renderer) {
        return Renderers.VULKAN.equals(renderer) ? VULKAN_COLOR : OPENGL_COLOR;
    }

    private Component headline() {
        if (this.verdict.isTie()) {
            return Component.translatable("renderer-benchmark.verdict.tie");
        }
        String percent = String.format(Locale.ROOT, "%.0f", this.verdict.percentFaster());
        return Component.translatable("renderer-benchmark.verdict.faster." + this.verdict.stutter().name().toLowerCase(Locale.ROOT),
                this.verdict.winner(), percent);
    }

    private int verdictColor() {
        return this.verdict.isTie() ? TEXT : colorOf(this.verdict.winner());
    }

    private String gpuText() {
        if (this.state.secondGpu.isEmpty() || this.state.secondGpu.equals(this.state.firstGpu)) {
            return this.state.firstGpu;
        }
        // Laptops can switch GPUs between the two starts, which matters for the result.
        return this.state.firstRenderer + ": " + this.state.firstGpu + ", " + this.state.secondRenderer + ": " + this.state.secondGpu;
    }

    private void copy() {
        String text = ShareText.format(this.state.firstRenderer, this.first, this.state.secondRenderer, this.second,
                this.verdict, gpuText(), BenchmarkController.settingsSummary(this.state));
        this.minecraft.keyboardHandler.setClipboard(text);
        this.status = Component.translatable(PREFIX + "copied");
    }

    /** Hides the buttons, waits for a frame without them, then saves that frame to the screenshots folder. */
    private void startCapture() {
        this.capturing = true;
        this.framesSinceHidden = 0;
        setButtonsVisible(false);
    }

    @Override
    public void tick() {
        if (this.capturing && this.framesSinceHidden >= 2) {
            this.capturing = false;
            String name = "renderer-benchmark-" + this.state.finishedAt.replaceAll("[^0-9]", "") + ".png";
            Screenshot.grab(this.minecraft.gameDirectory, name, this.minecraft.gameRenderer.mainRenderTarget(), 1,
                    message -> this.minecraft.execute(() -> this.status = message));
            setButtonsVisible(true);
        }
    }

    private void setButtonsVisible(boolean visible) {
        for (AbstractWidget button : this.buttons) {
            button.visible = visible;
        }
    }

    /** Keeps the given renderer, or the player's own setting when null. */
    private void choose(String renderer) {
        BenchmarkController.finish(this.minecraft, this.state, renderer);
        String next = renderer != null ? renderer : Renderers.startsOn(Renderers.parsePreference(this.state.originalPreference));
        if (next.equals(Renderers.current())) {
            this.minecraft.gui.setScreen(this.parent);
        } else {
            this.minecraft.gui.setScreen(new NoticeScreen(this.parent,
                    Component.translatable("renderer-benchmark.nextStart.title"),
                    Component.translatable("renderer-benchmark.nextStart.message", next)));
        }
    }

    @Override
    public void onClose() {
        if (this.decide) {
            // Leaving without a choice keeps the player's own setting.
            choose(null);
        } else {
            this.minecraft.gui.setScreen(this.parent);
        }
    }
}
