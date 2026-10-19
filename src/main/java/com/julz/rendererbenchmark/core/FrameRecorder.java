package com.julz.rendererbenchmark.core;

import java.util.Arrays;

/**
 * Records the duration of every frame in a run. Unlike a rolling window it keeps all of them,
 * since a run is short (90 seconds at 1000 FPS is 90,000 frames) and every frame counts.
 * Not thread safe; frames are recorded on the render thread.
 */
public final class FrameRecorder {
    private long[] durations = new long[16384];
    private int size;
    private long lastFrameNanos = -1;

    /** Called once per rendered frame with a monotonic timestamp, such as {@link System#nanoTime()}. */
    public void onFrame(long nowNanos) {
        if (lastFrameNanos >= 0) {
            long duration = nowNanos - lastFrameNanos;
            if (duration > 0) {
                if (size == durations.length) {
                    durations = Arrays.copyOf(durations, size * 2);
                }
                durations[size++] = duration;
            }
        }
        lastFrameNanos = nowNanos;
    }

    /** Forgets all frames. The next frame only starts a new measurement. */
    public void reset() {
        size = 0;
        lastFrameNanos = -1;
    }

    public int frameCount() {
        return size;
    }

    public long[] durations() {
        return Arrays.copyOf(durations, size);
    }
}
