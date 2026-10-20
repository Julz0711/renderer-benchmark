package com.julz.rendererbenchmark.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShareTextTest {
    private static BenchmarkResult result(String renderer, double fps) {
        BenchmarkResult result = new BenchmarkResult();
        result.renderer = renderer;
        result.gpu = "Test GPU";
        result.settings = "12 chunks";
        for (Scenario scenario : Scenario.values()) {
            result.scenes.add(new BenchmarkResult.SceneResult(scenario.id(), new RunMetrics(fps, fps / 2, fps / 3, 0, 100, 1)));
        }
        result.overall = new RunMetrics(fps, fps / 2, fps / 3, 0, 700, 7);
        return result;
    }

    @Test
    void bothRenderersGetAHeadlineAndEveryScene() {
        String text = ShareText.format(result("OpenGL", 100), result("Vulkan", 120));
        assertTrue(text.contains("Vulkan is 20% faster"), text);
        for (Scenario scenario : Scenario.values()) {
            assertTrue(text.contains(scenario.englishName()), scenario.englishName());
        }
        assertTrue(text.contains("120 / 60"), text);
        assertTrue(text.contains("GPU: Test GPU"), text);
    }

    @Test
    void oneRendererHasNoHeadlineAndDashesForTheOther() {
        String text = ShareText.format(result("OpenGL", 100), null);
        assertFalse(text.contains("faster"), text);
        assertTrue(text.contains("100 / 50"), text);
        assertTrue(text.contains(" -"), text);
    }

    @Test
    void sceneLookupAndPlaces() {
        BenchmarkResult result = result("OpenGL", 100);
        assertEquals(100, result.scene(Scenario.RAIN).averageFps());
        assertTrue(Scenario.PLAINS.movesToNewPlace());
        assertFalse(Scenario.MOBS.movesToNewPlace());
        assertTrue(Scenario.FOREST.movesToNewPlace());
        assertFalse(Scenario.RAIN.movesToNewPlace());
        assertEquals(Scenario.SNOW, Scenario.byId("snow"));
    }
}
