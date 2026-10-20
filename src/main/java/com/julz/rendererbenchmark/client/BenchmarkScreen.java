package com.julz.rendererbenchmark.client;

import com.julz.rendererbenchmark.config.ResultStore;
import com.julz.rendererbenchmark.core.BenchmarkResult;
import com.julz.rendererbenchmark.core.RunMetrics;
import com.julz.rendererbenchmark.core.Scenario;
import com.julz.rendererbenchmark.core.ShareText;
import com.julz.rendererbenchmark.core.Verdict;
import net.minecraft.client.PreferredGraphicsApi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Map;

/**
 * The whole mod on one screen: the setup the game runs with, a button to switch renderer, the
 * latest benchmark on each renderer side by side, and what to do next.
 */
public class BenchmarkScreen extends Screen {
    private static final String PREFIX = "renderer-benchmark.screen.";
    private static final int VULKAN_COLOR = 0xFFFF6A50;
    private static final int OPENGL_COLOR = 0xFF5C9BFF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFA0A0A0;
    private static final int WARNING = 0xFFFFC040;
    private static final int ERROR = 0xFFFF6060;
    private static final int LINE = 11;
    private static final int SWITCH_WIDTH = 130;

    private final Screen parent;
    private Map<String, BenchmarkResult> results;
    private String current;
    private String other;
    private String vulkanProblem;
    private boolean fellBack;
    private Component status = Component.empty();

    public BenchmarkScreen(Screen parent) {
        super(Component.translatable("renderer-benchmark.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.results = ResultStore.load();
        this.current = Renderers.current();
        this.other = Renderers.other(this.current);
        this.vulkanProblem = Renderers.vulkanProblem();
        // Vulkan was selected but the game started on OpenGL: it fell back, or a launch option forces OpenGL.
        this.fellBack = Renderers.preference(this.minecraft) == PreferredGraphicsApi.VULKAN && Renderers.OPENGL.equals(this.current);

        int left = left();
        Button switchButton = addRenderableWidget(Button.builder(Component.translatable(PREFIX + "switch", this.other),
                        button -> confirmSwitch())
                .bounds(left + contentWidth() - SWITCH_WIDTH, setupTop() - 6, SWITCH_WIDTH, 20).build());
        switchButton.active = !(Renderers.VULKAN.equals(this.other) && this.vulkanProblem != null);

        int center = this.width / 2;
        int bottom = this.height - 26;
        addRenderableWidget(Button.builder(Component.translatable(PREFIX + "run", BenchmarkController.estimatedMinutes()),
                        button -> confirmRun())
                .bounds(center - 154, bottom, 120, 20).build());
        Button copy = addRenderableWidget(Button.builder(Component.translatable(PREFIX + "copy"), button -> copy())
                .bounds(center - 30, bottom, 100, 20).build());
        copy.active = !this.results.isEmpty();
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(center + 74, bottom, 80, 20).build());
    }

    private int contentWidth() {
        return Math.min(400, this.width - 20);
    }

    private int left() {
        return (this.width - contentWidth()) / 2;
    }

    private int top() {
        return Math.max(6, (this.height - 236) / 2);
    }

    private int setupTop() {
        return top() + 22;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int left = left();
        int width = contentWidth();
        graphics.centeredText(this.font, this.title, this.width / 2, top(), TEXT);

        // Your setup
        int y = setupTop();
        graphics.text(this.font, Component.translatable(PREFIX + "renderer"), left, y, MUTED);
        graphics.text(this.font, this.current, left + 60, y, colorOf(this.current));
        y += LINE + 4;
        String gpu = this.font.plainSubstrByWidth(Renderers.gpu(), width - 60);
        graphics.text(this.font, Component.translatable(PREFIX + "gpu"), left, y, MUTED);
        graphics.text(this.font, gpu, left + 60, y, TEXT);
        y += LINE;
        graphics.text(this.font, Component.translatable(PREFIX + "settings"), left, y, MUTED);
        graphics.text(this.font, this.font.plainSubstrByWidth(BenchmarkController.settingsSummary(this.minecraft), width - 60),
                left + 60, y, TEXT);
        y += LINE;
        graphics.text(this.font, Component.translatable(PREFIX + "whileFlying"), left + 60, y, MUTED);
        y += LINE + 8;

        // Results table
        BenchmarkResult openGl = this.results.get(Renderers.OPENGL);
        BenchmarkResult vulkan = this.results.get(Renderers.VULKAN);
        int colA = left + width * 30 / 100;
        int colB = left + width * 52 / 100;
        int colDiff = left + width * 74 / 100;
        graphics.text(this.font, Component.translatable(PREFIX + "scene"), left, y, MUTED);
        graphics.text(this.font, Renderers.OPENGL, colA, y, OPENGL_COLOR);
        graphics.text(this.font, Renderers.VULKAN, colB, y, VULKAN_COLOR);
        graphics.text(this.font, Component.translatable(PREFIX + "difference"), colDiff, y, MUTED);
        y += LINE;
        graphics.fill(left, y - 2, left + width, y - 1, 0x40FFFFFF);
        for (Scenario scenario : Scenario.values()) {
            row(graphics, y, left, colA, colB, colDiff, Component.translatable("renderer-benchmark.scenario." + scenario.id()),
                    openGl != null ? openGl.scene(scenario) : null, vulkan != null ? vulkan.scene(scenario) : null, TEXT);
            y += LINE;
        }
        graphics.fill(left, y - 2, left + width, y - 1, 0x40FFFFFF);
        row(graphics, y, left, colA, colB, colDiff, Component.translatable(PREFIX + "overall"),
                openGl != null ? openGl.overall : null, vulkan != null ? vulkan.overall : null, TEXT);
        y += LINE;
        graphics.text(this.font, Component.translatable(PREFIX + "units"), colA, y, MUTED);
        y += LINE + 4;

        // What to do next
        y = hint(graphics, left, y, width, openGl, vulkan);
        graphics.centeredText(this.font, this.status, this.width / 2, this.height - 38, TEXT);
    }

    private void row(GuiGraphicsExtractor graphics, int y, int left, int colA, int colB, int colDiff, Component name,
                     RunMetrics openGl, RunMetrics vulkan, int color) {
        graphics.text(this.font, name, left, y, color);
        graphics.text(this.font, ShareText.cell(openGl), colA, y, TEXT);
        graphics.text(this.font, ShareText.cell(vulkan), colB, y, TEXT);
        if (openGl != null && vulkan != null && openGl.frameCount() > 0 && vulkan.frameCount() > 0) {
            Verdict verdict = Verdict.compare(Renderers.OPENGL, openGl, Renderers.VULKAN, vulkan);
            if (verdict.isTie()) {
                graphics.text(this.font, Component.translatable(PREFIX + "same"), colDiff, y, MUTED);
            } else {
                String text = String.format(Locale.ROOT, "%s +%.0f%%", verdict.winner(), verdict.percentFaster());
                graphics.text(this.font, text, colDiff, y, colorOf(verdict.winner()));
            }
        }
    }

    private int hint(GuiGraphicsExtractor graphics, int left, int y, int width, BenchmarkResult openGl, BenchmarkResult vulkan) {
        if (this.fellBack) {
            return wrapped(graphics, Component.translatable(PREFIX + "fellBack"), left, y, width, ERROR);
        }
        if (Renderers.VULKAN.equals(this.other) && this.vulkanProblem != null) {
            return wrapped(graphics, Component.translatable(PREFIX + "noVulkan", this.vulkanProblem), left, y, width, ERROR);
        }
        BenchmarkResult mine = this.results.get(this.current);
        BenchmarkResult theirs = this.results.get(this.other);
        if (mine == null && theirs == null) {
            return wrapped(graphics, Component.translatable(PREFIX + "hint.none", this.current, this.other), left, y, width, MUTED);
        }
        if (theirs == null) {
            return wrapped(graphics, Component.translatable(PREFIX + "hint.switch", this.other), left, y, width, WARNING);
        }
        if (mine == null) {
            return wrapped(graphics, Component.translatable(PREFIX + "hint.runHere", this.current), left, y, width, WARNING);
        }
        Verdict verdict = Verdict.compare(Renderers.OPENGL, openGl.overall, Renderers.VULKAN, vulkan.overall);
        Component headline;
        int color;
        if (verdict.isTie()) {
            headline = Component.translatable("renderer-benchmark.verdict.tie");
            color = TEXT;
        } else {
            Component verdictText = Component.translatable("renderer-benchmark.verdict.faster." + verdict.stutter().name().toLowerCase(Locale.ROOT),
                    verdict.winner(), String.format(Locale.ROOT, "%.0f", verdict.percentFaster()));
            Component next = verdict.winner().equals(this.current)
                    ? Component.translatable(PREFIX + "hint.onWinner")
                    : Component.translatable(PREFIX + "hint.switchToWinner", verdict.winner());
            headline = Component.empty().append(verdictText).append(" ").append(next);
            color = colorOf(verdict.winner());
        }
        y = wrapped(graphics, headline, left, y, width, color);
        if (!openGl.settings.equals(vulkan.settings)) {
            y = wrapped(graphics, Component.translatable(PREFIX + "settingsDiffer"), left, y, width, WARNING);
        }
        return y;
    }

    private int wrapped(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int color) {
        graphics.textWithWordWrap(this.font, text, x, y, width, color);
        return y + this.font.split(text, width).size() * this.font.lineHeight;
    }

    private static int colorOf(String renderer) {
        return Renderers.VULKAN.equals(renderer) ? VULKAN_COLOR : OPENGL_COLOR;
    }

    private void confirmRun() {
        this.minecraft.gui.setScreen(new ConfirmScreen(start -> {
            if (start) {
                BenchmarkController.start(this.minecraft);
            } else {
                this.minecraft.gui.setScreen(this);
            }
        },
                Component.translatable(PREFIX + "confirmRun.title", this.current),
                Component.translatable(PREFIX + "confirmRun.message", BenchmarkController.estimatedMinutes()),
                Component.translatable(PREFIX + "confirmRun.start"),
                CommonComponents.GUI_CANCEL));
    }

    /** The renderer can only change at startup, so switching means quitting. */
    private void confirmSwitch() {
        this.minecraft.gui.setScreen(new ConfirmScreen(quit -> {
            if (quit) {
                Renderers.setPreference(this.minecraft, Renderers.preferenceFor(this.other));
                if (this.minecraft.level != null) {
                    this.minecraft.disconnectWithSavingScreen();
                }
                this.minecraft.stop();
            } else {
                this.minecraft.gui.setScreen(this);
            }
        },
                Component.translatable(PREFIX + "confirmSwitch.title", this.other),
                Component.translatable(PREFIX + "confirmSwitch.message", this.other),
                Component.translatable(PREFIX + "confirmSwitch.quit"),
                CommonComponents.GUI_CANCEL));
    }

    private void copy() {
        this.minecraft.keyboardHandler.setClipboard(
                ShareText.format(this.results.get(Renderers.OPENGL), this.results.get(Renderers.VULKAN)));
        this.status = Component.translatable(PREFIX + "copied");
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
