package com.julz.rendererbenchmark.core;

import java.util.Arrays;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * What one timed run measured.
 *
 * @param averageFps            frames divided by the time they took
 * @param onePercentLowFps      FPS of the slowest 1% of frames
 * @param pointOnePercentLowFps FPS of the slowest 0.1% of frames
 * @param spikes                frames that took longer than {@link #SPIKE_NANOS}
 * @param frameCount            number of frames measured
 * @param seconds               time the frames cover
 */
public record RunMetrics(double averageFps, double onePercentLowFps, double pointOnePercentLowFps,
                         int spikes, int frameCount, double seconds) {
    /** A frame over 50 ms (under 20 FPS) is a stutter anyone notices. */
    public static final long SPIKE_NANOS = 50_000_000L;
    public static final RunMetrics EMPTY = new RunMetrics(0, 0, 0, 0, 0, 0);

    public static RunMetrics of(long[] durations) {
        if (durations.length == 0) {
            return EMPTY;
        }
        long[] sorted = durations.clone();
        Arrays.sort(sorted);
        long total = 0;
        int spikes = 0;
        for (long duration : sorted) {
            total += duration;
            if (duration > SPIKE_NANOS) {
                spikes++;
            }
        }
        return new RunMetrics(
                sorted.length * 1e9 / total,
                lowFps(sorted, 100),
                lowFps(sorted, 1000),
                spikes,
                sorted.length,
                total / 1e9);
    }

    /** Spikes per minute, so runs of slightly different length compare fairly. */
    public double spikesPerMinute() {
        return seconds > 0 ? spikes * 60 / seconds : 0;
    }

    /**
     * The median of each metric over repeated runs, which cuts the noise of a single run.
     * Each metric is taken on its own, so the result may not equal any one run.
     */
    public static RunMetrics median(List<RunMetrics> runs) {
        if (runs.isEmpty()) {
            return EMPTY;
        }
        if (runs.size() == 1) {
            return runs.getFirst();
        }
        return new RunMetrics(
                median(runs, RunMetrics::averageFps),
                median(runs, RunMetrics::onePercentLowFps),
                median(runs, RunMetrics::pointOnePercentLowFps),
                (int) Math.round(median(runs, RunMetrics::spikes)),
                (int) Math.round(median(runs, RunMetrics::frameCount)),
                median(runs, RunMetrics::seconds));
    }

    /** FPS of the slowest 1/divisor of the frames (at least one frame), from durations sorted ascending. */
    private static double lowFps(long[] sorted, int divisor) {
        int count = Math.max(1, sorted.length / divisor);
        long slowTotal = 0;
        for (int i = sorted.length - count; i < sorted.length; i++) {
            slowTotal += sorted[i];
        }
        return count * 1e9 / slowTotal;
    }

    private static double median(List<RunMetrics> runs, ToDoubleFunction<RunMetrics> metric) {
        double[] values = runs.stream().mapToDouble(metric).sorted().toArray();
        int middle = values.length / 2;
        return values.length % 2 == 1 ? values[middle] : (values[middle - 1] + values[middle]) / 2;
    }
}
