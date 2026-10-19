package com.julz.rendererbenchmark.core;

/**
 * Turns two results into a plain answer: which API is faster, by how much, and whether it
 * stutters more or less.
 *
 * @param winner          the faster API, or null when the difference is too small to matter
 * @param percentFaster   how much higher the winner's average FPS is, in percent
 * @param stutter         how the winner's stutter compares to the other API's
 */
public record Verdict(String winner, String loser, double percentFaster, Stutter stutter) {
    /** Under this difference in average FPS, two runs on the same API can differ just as much. */
    public static final double TIE_PERCENT = 3;
    /** 1% lows must differ by this much to count as more or fewer stutters. */
    public static final double STUTTER_PERCENT = 5;

    public enum Stutter { FEWER, SAME, MORE }

    public boolean isTie() {
        return winner == null;
    }

    public static Verdict compare(String apiA, RunMetrics a, String apiB, RunMetrics b) {
        boolean aFaster = a.averageFps() >= b.averageFps();
        String fast = aFaster ? apiA : apiB;
        String slow = aFaster ? apiB : apiA;
        RunMetrics fastMetrics = aFaster ? a : b;
        RunMetrics slowMetrics = aFaster ? b : a;

        double percent = slowMetrics.averageFps() > 0
                ? (fastMetrics.averageFps() / slowMetrics.averageFps() - 1) * 100
                : 0;
        Stutter stutter = stutter(fastMetrics, slowMetrics);
        if (percent < TIE_PERCENT) {
            return new Verdict(null, null, percent, stutter);
        }
        return new Verdict(fast, slow, percent, stutter);
    }

    /** Compares the 1% lows first, and the spike count when those are close. */
    private static Stutter stutter(RunMetrics first, RunMetrics second) {
        if (second.onePercentLowFps() > 0) {
            double lowDiff = (first.onePercentLowFps() / second.onePercentLowFps() - 1) * 100;
            if (lowDiff >= STUTTER_PERCENT) {
                return Stutter.FEWER;
            }
            if (lowDiff <= -STUTTER_PERCENT) {
                return Stutter.MORE;
            }
        }
        double spikesFirst = first.spikesPerMinute();
        double spikesSecond = second.spikesPerMinute();
        if (Math.abs(spikesFirst - spikesSecond) >= 1) {
            return spikesFirst < spikesSecond ? Stutter.FEWER : Stutter.MORE;
        }
        return Stutter.SAME;
    }
}
