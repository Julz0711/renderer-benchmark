package com.julz.rendererbenchmark.test;

import com.julz.rendererbenchmark.client.BenchmarkController;
import com.julz.rendererbenchmark.client.BenchmarkScreen;
import com.julz.rendererbenchmark.client.Renderers;
import com.julz.rendererbenchmark.config.ResultStore;
import com.julz.rendererbenchmark.core.BenchmarkResult;
import com.julz.rendererbenchmark.core.RunMetrics;
import com.julz.rendererbenchmark.core.Scenario;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Runs a whole benchmark with short scenes on the renderer the test runs on, then fakes a result
 * for the other renderer (a test can't restart the game) to check the comparison.
 */
public class RendererBenchmarkClientGameTest implements FabricClientGameTest {
    private static final int LOAD_TIMEOUT_TICKS = 20 * 240;
    private static final int SCENE_TIMEOUT_TICKS = 20 * 60;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> ResultStore.clear());
        context.setScreen(() -> new BenchmarkScreen(null));
        context.waitForScreen(BenchmarkScreen.class);
        context.takeScreenshot("renderer-benchmark-empty");

        context.runOnClient(client -> {
            BenchmarkController.setTimingForTesting(3, 1, false);
            client.options.framerateLimit().set(120);
            client.options.enableVsync().set(true);
        });

        // The real benchmark creates its own world; here it flies in the test's world, since leaving a
        // world from inside a client tick deadlocks with the game test's lock-step threading.
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null);
            context.setScreen(() -> null);
            context.runOnClient(BenchmarkController::startInCurrentWorldForTesting);
            context.waitFor(client -> BenchmarkController.step() == BenchmarkController.Step.WARMUP, LOAD_TIMEOUT_TICKS);
            context.runOnClient(client -> {
                assertEquals(260, client.options.framerateLimit().get(), "frame limit while flying");
                assertEquals(false, client.options.enableVsync().get(), "VSync while flying");
            });
            for (Scenario scenario : Scenario.values()) {
                context.waitFor(client -> BenchmarkController.currentScenario() == scenario
                        && BenchmarkController.step() == BenchmarkController.Step.RUNNING, SCENE_TIMEOUT_TICKS);
                context.waitTicks(20);
                context.takeScreenshot("renderer-benchmark-scene-" + scenario.id());
            }
            context.waitFor(client -> client.gui.screen() instanceof BenchmarkScreen && !BenchmarkController.isRunning(),
                    SCENE_TIMEOUT_TICKS);
            context.waitTicks(5);
            context.takeScreenshot("renderer-benchmark-one-renderer");
            context.setScreen(() -> null);
        }

        String renderer = context.computeOnClient(client -> Renderers.current());
        BenchmarkResult measured = context.computeOnClient(client -> ResultStore.get(renderer));
        assertTrue(measured != null, "result saved for " + renderer);
        assertEquals(Scenario.values().length, measured.scenes.size(), "scenes measured");
        for (BenchmarkResult.SceneResult scene : measured.scenes) {
            assertTrue(scene.metrics().frameCount() > 0, "frames recorded in " + scene.scenario());
        }
        assertTrue(measured.overall.frameCount() > 0, "frames recorded overall");
        context.runOnClient(client -> {
            assertEquals(120, client.options.framerateLimit().get(), "frame limit restored");
            assertEquals(true, client.options.enableVsync().get(), "VSync restored");
            assertTrue(ResultStore.loadRestore() == null, "nothing left to restore");
        });

        // The other renderer, as if measured after a restart: a bit faster everywhere
        context.runOnClient(client -> {
            BenchmarkResult other = new BenchmarkResult();
            other.renderer = Renderers.other(renderer);
            other.gpu = measured.gpu;
            other.settings = measured.settings;
            other.finishedAt = measured.finishedAt;
            for (BenchmarkResult.SceneResult scene : measured.scenes) {
                other.scenes.add(new BenchmarkResult.SceneResult(scene.scenario(), faster(scene.metrics())));
            }
            other.overall = faster(measured.overall);
            ResultStore.put(other);
        });
        context.setScreen(() -> new BenchmarkScreen(new TitleScreen()));
        context.waitForScreen(BenchmarkScreen.class);
        context.takeScreenshot("renderer-benchmark-comparison");
        context.clickScreenButton("renderer-benchmark.screen.copy");
        context.runOnClient(client -> {
            String copied = client.keyboardHandler.getClipboard();
            assertTrue(copied.contains("Overall") && copied.contains("Lots of mobs"), "copied text: " + copied);
        });

        context.runOnClient(client -> {
            ResultStore.clear();
            BenchmarkController.setTimingForTesting(-1, -1, true);
        });
        context.setScreen(TitleScreen::new);
    }

    private static RunMetrics faster(RunMetrics metrics) {
        return new RunMetrics(metrics.averageFps() * 1.2, metrics.onePercentLowFps() * 1.1,
                metrics.pointOnePercentLowFps() * 1.1, metrics.spikes(), metrics.frameCount(), metrics.seconds());
    }

    private static void assertTrue(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }

    private static void assertEquals(Object expected, Object actual, String what) {
        if (!expected.equals(actual)) {
            throw new AssertionError(what + ": expected " + expected + " but was " + actual);
        }
    }
}
