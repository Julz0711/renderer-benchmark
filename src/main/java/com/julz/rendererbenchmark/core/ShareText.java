package com.julz.rendererbenchmark.core;

import java.util.Locale;

/** The results as plain text for Discord, Reddit or a bug report. */
public final class ShareText {
    private ShareText() {
    }

    /** Either result may be null when only one renderer has been measured. */
    public static String format(BenchmarkResult a, BenchmarkResult b) {
        BenchmarkResult any = a != null ? a : b;
        StringBuilder text = new StringBuilder();
        text.append("**Renderer Benchmark** (Minecraft 26.3)\n");
        if (a != null && b != null) {
            text.append(headline(Verdict.compare(a.renderer, a.overall, b.renderer, b.overall))).append('\n');
        }
        text.append("```\n");
        text.append(String.format(Locale.ROOT, "%-14s %15s %15s%n", "avg / 1% low",
                a != null ? a.renderer : "", b != null ? b.renderer : ""));
        for (Scenario scenario : Scenario.values()) {
            text.append(String.format(Locale.ROOT, "%-14s %15s %15s%n", scenario.englishName(),
                    cell(a != null ? a.scene(scenario) : null), cell(b != null ? b.scene(scenario) : null)));
        }
        text.append(String.format(Locale.ROOT, "%-14s %15s %15s%n", "Overall",
                cell(a != null ? a.overall : null), cell(b != null ? b.overall : null)));
        text.append("```\n");
        text.append("GPU: ").append(any.gpu).append('\n');
        text.append("Settings: ").append(any.settings);
        return text.toString();
    }

    /** Average and 1% low FPS, such as "142 / 61", or "-" when not measured. */
    public static String cell(RunMetrics metrics) {
        if (metrics == null || metrics.frameCount() == 0) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.0f / %.0f", metrics.averageFps(), metrics.onePercentLowFps());
    }

    /** One line in English, such as "Vulkan is 18% faster with fewer stutters". */
    public static String headline(Verdict verdict) {
        String stutter = switch (verdict.stutter()) {
            case FEWER -> "fewer stutters";
            case SAME -> "about the same stutter";
            case MORE -> "more stutters";
        };
        if (verdict.isTie()) {
            return "Both renderers run about the same";
        }
        return String.format(Locale.ROOT, "%s is %.0f%% faster with %s", verdict.winner(), verdict.percentFaster(), stutter);
    }
}
