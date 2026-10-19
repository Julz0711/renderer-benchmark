package com.julz.rendererbenchmark.core;

import java.util.Locale;

/** The result as plain text for Discord, Reddit or a bug report. */
public final class ShareText {
    private ShareText() {
    }

    public static String format(String apiA, RunMetrics a, String apiB, RunMetrics b, Verdict verdict,
                                String gpu, String settings) {
        StringBuilder text = new StringBuilder();
        text.append("**Renderer Benchmark** (Minecraft 26.3)\n");
        text.append(headline(verdict)).append('\n');
        text.append("```\n");
        text.append(String.format(Locale.ROOT, "%-14s %9s %9s%n", "", apiA, apiB));
        row(text, "Average FPS", a.averageFps(), b.averageFps());
        row(text, "1% low FPS", a.onePercentLowFps(), b.onePercentLowFps());
        row(text, "0.1% low FPS", a.pointOnePercentLowFps(), b.pointOnePercentLowFps());
        text.append(String.format(Locale.ROOT, "%-14s %9d %9d%n", "Spikes >50ms", a.spikes(), b.spikes()));
        text.append("```\n");
        text.append("GPU: ").append(gpu).append('\n');
        text.append("Settings: ").append(settings);
        return text.toString();
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

    private static void row(StringBuilder text, String label, double a, double b) {
        text.append(String.format(Locale.ROOT, "%-14s %9.0f %9.0f%n", label, a, b));
    }
}
