package com.julz.rendererbenchmark.core;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RunMetricsTest {
    private static final long MS = 1_000_000L;

    @Test
    void steadyFrames() {
        RunMetrics metrics = RunMetrics.of(frames(1000, 10 * MS));
        assertEquals(100, metrics.averageFps(), 0.01);
        assertEquals(100, metrics.onePercentLowFps(), 0.01);
        assertEquals(100, metrics.pointOnePercentLowFps(), 0.01);
        assertEquals(0, metrics.spikes());
        assertEquals(10, metrics.seconds(), 0.001);
    }

    @Test
    void lowsAndSpikesReflectStutter() {
        // 990 frames at 100 FPS, 9 at 25 FPS and one 100 ms hitch
        long[] durations = new long[1000];
        Arrays.fill(durations, 10 * MS);
        for (int i = 0; i < 9; i++) {
            durations[i * 100 + 1] = 40 * MS;
        }
        durations[500] = 100 * MS;
        RunMetrics metrics = RunMetrics.of(durations);
        // Slowest 10 frames: 9 x 40 ms + 100 ms = 460 ms
        assertEquals(10 / 0.46, metrics.onePercentLowFps(), 0.01);
        // Slowest frame alone
        assertEquals(10, metrics.pointOnePercentLowFps(), 0.01);
        assertEquals(1, metrics.spikes());
        assertEquals(1000 / (989 * 0.01 + 9 * 0.04 + 0.1 + 0.01), metrics.averageFps(), 0.01);
    }

    @Test
    void emptyRun() {
        assertEquals(RunMetrics.EMPTY, RunMetrics.of(new long[0]));
    }

    @Test
    void medianTakesTheMiddleOfEachMetric() {
        RunMetrics median = RunMetrics.median(List.of(
                new RunMetrics(100, 60, 40, 3, 6000, 60),
                new RunMetrics(140, 50, 30, 1, 8400, 60),
                new RunMetrics(120, 70, 20, 9, 7200, 60)));
        assertEquals(new RunMetrics(120, 60, 30, 3, 7200, 60), median);
    }

    @Test
    void medianOfTwoIsTheMean() {
        RunMetrics median = RunMetrics.median(List.of(
                new RunMetrics(100, 60, 40, 2, 6000, 60),
                new RunMetrics(120, 80, 50, 4, 7200, 60)));
        assertEquals(110, median.averageFps(), 0.001);
        assertEquals(3, median.spikes());
    }

    @Test
    void recorderSkipsTheFirstFrameAndResets() {
        FrameRecorder recorder = new FrameRecorder();
        long now = 5_000 * MS;
        for (int i = 0; i < 20_001; i++) {
            recorder.onFrame(now);
            now += 2 * MS;
        }
        assertEquals(20_000, recorder.frameCount());
        assertEquals(500, RunMetrics.of(recorder.durations()).averageFps(), 0.01);
        recorder.reset();
        recorder.onFrame(now + 30_000 * MS);
        assertEquals(0, recorder.frameCount());
    }

    private static long[] frames(int count, long duration) {
        long[] durations = new long[count];
        Arrays.fill(durations, duration);
        return durations;
    }
}
