package com.julz.rendererbenchmark.test;

import com.julz.rendererbenchmark.client.BenchmarkController;
import com.julz.rendererbenchmark.client.BenchmarkScreen;
import com.julz.rendererbenchmark.client.Renderers;
import com.julz.rendererbenchmark.client.ResultsScreen;
import com.julz.rendererbenchmark.config.ComparisonState;
import com.julz.rendererbenchmark.config.ModConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Runs a whole comparison with short runs. The game can't restart inside a test, so the second
 * half runs on the same renderer, under the other renderer's name; that is enough to check the
 * flow, the stored results and that the player's settings come back.
 */
public class RendererBenchmarkClientGameTest implements FabricClientGameTest {
    private static final int LOAD_TIMEOUT_TICKS = 20 * 120;
    private static final int RUN_TIMEOUT_TICKS = 20 * 60;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.setScreen(() -> new BenchmarkScreen(null));
        context.waitForScreen(BenchmarkScreen.class);
        context.takeScreenshot("renderer-benchmark-screen");

        context.runOnClient(client -> {
            BenchmarkController.setTimingForTesting(4, 1, false);
            ModConfig.getInstance().runsPerRenderer = 2;
            client.options.renderDistance().set(8);
            client.options.fov().set(90);
            client.options.framerateLimit().set(120);
        });
        String originalPreference = context.computeOnClient(client -> Renderers.preference(client).getSerializedName());

        // First half: flies the route and asks for a restart
        context.setScreen(TitleScreen::new);
        context.runOnClient(BenchmarkController::start);
        context.waitFor(client -> BenchmarkController.step() == BenchmarkController.Step.RUNNING, LOAD_TIMEOUT_TICKS);
        context.runOnClient(client -> {
            assertEquals(8, client.options.renderDistance().get(), "render distance while flying");
            assertEquals(70, client.options.fov().get(), "FOV while flying");
            assertEquals(260, client.options.framerateLimit().get(), "frame limit while flying");
        });
        context.waitTicks(20);
        context.takeScreenshot("renderer-benchmark-flying");
        context.waitFor(client -> client.gui.screen() instanceof ConfirmScreen && client.level == null, RUN_TIMEOUT_TICKS);
        context.takeScreenshot("renderer-benchmark-restart-prompt");

        ComparisonState afterFirst = context.computeOnClient(client -> ComparisonState.load());
        assertEquals(ComparisonState.Phase.RESTART, afterFirst.phase, "phase after the first half");
        assertEquals(2, afterFirst.firstRuns.size(), "runs in the first half");
        assertTrue(afterFirst.firstRuns.get(0).frameCount() > 0, "frames recorded in the first run");
        context.runOnClient(client -> {
            assertEquals(afterFirst.secondPreference, Renderers.preference(client).getSerializedName(), "preference for the second half");
            assertEquals(90, client.options.fov().get(), "FOV restored after the first half");
            assertEquals(120, client.options.framerateLimit().get(), "frame limit restored after the first half");
        });

        // "Later" goes back to the title screen; the second half then starts as after a restart
        context.runOnClient(client -> {
            client.gui.setScreen(new TitleScreen());
            BenchmarkController.resume(client, ComparisonState.load());
        });
        context.waitFor(client -> client.gui.screen() instanceof ResultsScreen && client.level == null,
                LOAD_TIMEOUT_TICKS + RUN_TIMEOUT_TICKS);
        context.waitTicks(5);
        context.takeScreenshot("renderer-benchmark-results");

        ComparisonState done = context.computeOnClient(client -> ComparisonState.load());
        assertEquals(ComparisonState.Phase.DONE, done.phase, "phase after the second half");
        assertEquals(2, done.secondRuns.size(), "runs in the second half");
        assertTrue(done.hasBothResults(), "both halves have results");

        // Closing the results without a choice keeps the player's own setting
        context.runOnClient(client -> client.gui.screen().onClose());
        context.waitTick();
        context.runOnClient(client -> {
            assertEquals(originalPreference, Renderers.preference(client).getSerializedName(), "preference after closing the results");
            assertTrue(ComparisonState.load() == null, "comparison cleared");
            assertTrue(ComparisonState.loadLast() != null, "last result kept");
            assertEquals(8, client.options.renderDistance().get(), "render distance restored");
            assertEquals(90, client.options.fov().get(), "FOV restored");
        });

        // The last result opens from the main screen
        context.setScreen(() -> new ResultsScreen(null, ComparisonState.loadLast(), false));
        context.waitForScreen(ResultsScreen.class);
        context.takeScreenshot("renderer-benchmark-last-result");
        context.setScreen(TitleScreen::new);
        context.runOnClient(client -> BenchmarkController.setTimingForTesting(-1, -1, true));
        // Let the title screen delete the temporary world
        context.waitTicks(5);
        context.runOnClient(client -> assertTrue(!client.getLevelSource().levelExists(BenchmarkController.WORLD_ID),
                "temporary world deleted"));
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
