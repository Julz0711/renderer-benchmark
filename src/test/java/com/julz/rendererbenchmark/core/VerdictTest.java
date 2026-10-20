package com.julz.rendererbenchmark.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerdictTest {
    @Test
    void fasterWithFewerStutters() {
        Verdict verdict = Verdict.compare(
                "Vulkan", new RunMetrics(118, 80, 50, 0, 7080, 60),
                "OpenGL", new RunMetrics(100, 60, 40, 4, 6000, 60));
        assertEquals("Vulkan", verdict.winner());
        assertEquals("OpenGL", verdict.loser());
        assertEquals(18, verdict.percentFaster(), 0.01);
        assertEquals(Verdict.Stutter.FEWER, verdict.stutter());
        assertEquals("Vulkan is 18% faster with fewer stutters", ShareText.headline(verdict));
    }

    @Test
    void secondApiCanWinWithMoreStutters() {
        Verdict verdict = Verdict.compare(
                "Vulkan", new RunMetrics(100, 70, 50, 0, 6000, 60),
                "OpenGL", new RunMetrics(125, 50, 30, 6, 7500, 60));
        assertEquals("OpenGL", verdict.winner());
        assertEquals(25, verdict.percentFaster(), 0.01);
        assertEquals(Verdict.Stutter.MORE, verdict.stutter());
    }

    @Test
    void smallDifferenceIsATie() {
        Verdict verdict = Verdict.compare(
                "Vulkan", new RunMetrics(102, 60, 40, 0, 6120, 60),
                "OpenGL", new RunMetrics(100, 60, 40, 0, 6000, 60));
        assertTrue(verdict.isTie());
        assertEquals(Verdict.Stutter.SAME, verdict.stutter());
        assertEquals("Both renderers run about the same", ShareText.headline(verdict));
    }

    @Test
    void spikesDecideWhenLowsAreClose() {
        Verdict verdict = Verdict.compare(
                "Vulkan", new RunMetrics(120, 60, 40, 1, 7200, 60),
                "OpenGL", new RunMetrics(100, 61, 40, 5, 6000, 60));
        assertEquals(Verdict.Stutter.FEWER, verdict.stutter());
    }
}
