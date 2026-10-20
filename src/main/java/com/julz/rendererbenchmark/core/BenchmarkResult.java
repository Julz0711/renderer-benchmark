package com.julz.rendererbenchmark.core;

import java.util.ArrayList;
import java.util.List;

/** One benchmark on one renderer: what each scene measured, and the setup it ran on. */
public class BenchmarkResult {
    /** "Vulkan" or "OpenGL". */
    public String renderer;
    public String gpu = "";
    /** Render distance, FOV, window size and so on, as one line; both renderers should match. */
    public String settings = "";
    public String finishedAt = "";
    public List<SceneResult> scenes = new ArrayList<>();
    /** All frames of all scenes together. */
    public RunMetrics overall = RunMetrics.EMPTY;

    public record SceneResult(String scenario, RunMetrics metrics) {
    }

    /** The result of a scene, or null when this benchmark did not fly it. */
    public RunMetrics scene(Scenario scenario) {
        for (SceneResult scene : scenes) {
            if (scene.scenario().equals(scenario.id())) {
                return scene.metrics();
            }
        }
        return null;
    }
}
