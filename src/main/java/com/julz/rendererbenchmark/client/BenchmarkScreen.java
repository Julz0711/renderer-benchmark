package com.julz.rendererbenchmark.client;

import com.julz.rendererbenchmark.config.ComparisonState;
import com.julz.rendererbenchmark.config.ModConfig;
import com.julz.rendererbenchmark.core.Route;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Where a comparison starts: what it does, how long it takes, and the last result. */
public class BenchmarkScreen extends Screen {
    private static final String PREFIX = "renderer-benchmark.screen.";
    private static final int TEXT_WIDTH = 300;
    /** World creation and loading, per renderer, a rough guess for the time estimate. */
    private static final int LOAD_SECONDS = 20;

    private final Screen parent;
    private String vulkanProblem;
    private ComparisonState pending;
    private ComparisonState last;

    public BenchmarkScreen(Screen parent) {
        super(Component.translatable("renderer-benchmark.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.vulkanProblem = Renderers.vulkanProblem();
        this.pending = ComparisonState.load();
        this.last = ComparisonState.loadLast();
        int center = this.width / 2;
        int y = this.height / 2 + 10;

        if (this.pending != null && this.pending.phase == ComparisonState.Phase.RESTART) {
            // The first half is done and the player chose to restart later.
            addRenderableWidget(Button.builder(Component.translatable(PREFIX + "cancelPending"), button -> {
                BenchmarkController.cancel(this.minecraft, this.pending);
                rebuildWidgets();
            }).bounds(center - 100, y, 200, 20).build());
        } else {
            Button runs = addRenderableWidget(Button.builder(runsLabel(), button -> {
                ModConfig config = ModConfig.getInstance();
                config.runsPerRenderer = config.runsPerRenderer % ModConfig.MAX_RUNS + 1;
                ModConfig.save();
                button.setMessage(runsLabel());
            }).bounds(center - 100, y, 200, 20).build());
            runs.active = this.vulkanProblem == null;
            Button compare = addRenderableWidget(Button.builder(Component.translatable(PREFIX + "compare"), button -> confirmStart())
                    .bounds(center - 100, y + 24, 200, 20).build());
            compare.active = this.vulkanProblem == null;
        }
        Button lastResult = addRenderableWidget(Button.builder(Component.translatable(PREFIX + "lastResult"),
                        button -> this.minecraft.gui.setScreen(new ResultsScreen(this, this.last, false)))
                .bounds(center - 100, y + 48, 98, 20).build());
        lastResult.active = this.last != null && this.last.hasBothResults();
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(center + 2, y + 48, 98, 20).build());
    }

    private static Component runsLabel() {
        return Component.translatable(PREFIX + "runs", ModConfig.getInstance().runsPerRenderer);
    }

    private void confirmStart() {
        ModConfig config = ModConfig.getInstance();
        double perRenderer = config.warmupSeconds + config.runsPerRenderer * Route.DEFAULT.durationSeconds() + LOAD_SECONDS;
        long minutes = Math.max(1, Math.round(2 * perRenderer / 60));
        String current = Renderers.current();
        this.minecraft.gui.setScreen(new ConfirmScreen(start -> {
            if (start) {
                BenchmarkController.start(this.minecraft);
            } else {
                this.minecraft.gui.setScreen(this);
            }
        },
                Component.translatable(PREFIX + "confirm.title"),
                Component.translatable(PREFIX + "confirm.message", current, Renderers.other(current), minutes),
                Component.translatable(PREFIX + "confirm.start"),
                CommonComponents.GUI_CANCEL));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int width = Math.min(TEXT_WIDTH, this.width - 40);
        int left = (this.width - width) / 2;
        int y = Math.max(10, this.height / 2 - 100);
        graphics.centeredText(this.font, this.title, this.width / 2, y, 0xFFFFFFFF);
        y += 18;
        y += textHeight(graphics, Component.translatable(PREFIX + "intro"), left, y, width, 0xFFD0D0D0) + 6;
        graphics.text(this.font, Component.translatable(PREFIX + "current", Renderers.current(), Renderers.gpu()), left, y, 0xFFA0A0A0);
        y += 14;
        if (this.pending != null && this.pending.phase == ComparisonState.Phase.RESTART) {
            textHeight(graphics, Component.translatable(PREFIX + "pending", this.pending.secondRenderer), left, y, width, 0xFFFFC040);
        } else if (this.vulkanProblem != null) {
            textHeight(graphics, Component.translatable(PREFIX + "noVulkan", this.vulkanProblem), left, y, width, 0xFFFF6060);
        }
    }

    private int textHeight(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int color) {
        graphics.textWithWordWrap(this.font, text, x, y, width, color);
        return this.font.split(text, width).size() * this.font.lineHeight;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
