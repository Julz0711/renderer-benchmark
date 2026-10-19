package com.julz.rendererbenchmark.client;

import com.julz.rendererbenchmark.config.ComparisonState;
import com.julz.rendererbenchmark.config.ModConfig;
import com.julz.rendererbenchmark.core.FrameRecorder;
import com.julz.rendererbenchmark.core.Route;
import com.julz.rendererbenchmark.core.RunMetrics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Runs a comparison: the timed flights on the renderer the game started with, the switch and
 * restart, the flights on the other renderer, and the results. Everything that has to survive the
 * restart is in {@link ComparisonState}; the rest lives here and only matters while flying.
 */
public final class BenchmarkController {
    /** Folder name of the temporary world, deleted again after each half. */
    public static final String WORLD_ID = "renderer-benchmark-temp";
    private static final String WORLD_NAME = "Renderer Benchmark (temporary)";
    /** A fixed seed, so every player and every run sees the same terrain. */
    private static final long SEED = 8_312_405_771_220_694L;
    private static final int FOV = 70;
    /** Options#framerateLimit uses this value for "Unlimited". */
    private static final int UNLIMITED_FRAMERATE = 260;
    /** Spectators may fly anywhere; the route never goes lower than this above sea level. */
    private static final int SEA_LEVEL = 63;
    private static final long LOAD_TIMEOUT_NANOS = 180_000_000_000L;
    private static final long LOAD_GRACE_NANOS = 5_000_000_000L;
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public enum Step { IDLE, LOADING, WARMUP, RUNNING }

    private static final FrameRecorder RECORDER = new FrameRecorder();

    private static Step step = Step.IDLE;
    private static ComparisonState state;
    private static long stepStartNanos;
    private static int runIndex;
    private static double originX;
    private static double originY;
    private static double originZ;
    // Set while a menu is open or the window is in the background; the current run starts over.
    private static boolean disturbed;
    private static boolean checkedPendingThisLaunch;
    private static boolean worldToDelete;

    // Replaced by the game tests to keep a comparison short.
    private static double routeSecondsOverride = -1;
    private static double warmupSecondsOverride = -1;
    // The test window on CI never has focus, so the tests turn the focus check off.
    private static boolean requireFocus = true;

    private BenchmarkController() {
    }

    public static Step step() {
        return step;
    }

    public static boolean isFlying() {
        return step == Step.WARMUP || step == Step.RUNNING;
    }

    /** Starts a new comparison on the renderer the game is running now. */
    public static void start(Minecraft client) {
        ComparisonState fresh = new ComparisonState();
        fresh.phase = ComparisonState.Phase.FIRST;
        fresh.originalPreference = Renderers.preference(client).getSerializedName();
        fresh.firstRenderer = Renderers.current();
        fresh.secondRenderer = Renderers.other(fresh.firstRenderer);
        fresh.secondPreference = Renderers.preferenceFor(fresh.secondRenderer).getSerializedName();
        fresh.firstGpu = Renderers.gpu();
        fresh.runsPerRenderer = ModConfig.getInstance().runsPerRenderer;
        fresh.settings = snapshot(client.options);
        fresh.save();
        state = fresh;
        beginHalf(client);
    }

    /** After the restart: measures the second renderer. */
    public static void resume(Minecraft client, ComparisonState pending) {
        state = pending;
        state.phase = ComparisonState.Phase.SECOND;
        state.secondGpu = Renderers.gpu();
        state.secondRuns.clear();
        state.save();
        beginHalf(client);
    }

    /** Called at the start of every rendered frame: moves the camera along the route and times the frame. */
    public static void onFrame(Minecraft client) {
        LocalPlayer player = client.player;
        if (!isFlying() || player == null) {
            return;
        }
        long now = System.nanoTime();
        if (step == Step.RUNNING && !disturbed && isUndisturbed(client)) {
            RECORDER.onFrame(now);
        }
        double seconds = (now - stepStartNanos) / 1e9;
        if (step == Step.RUNNING) {
            // The timed runs continue the loop where the warm-up left it, so the camera never jumps.
            seconds += warmupSeconds();
        }
        Route.Pose pose = Route.DEFAULT.sample(seconds * routeSpeed());
        player.snapTo(originX + pose.x(), originY + pose.y(), originZ + pose.z(), pose.yaw(), pose.pitch());
        player.setOldPosAndRot();
        player.setDeltaMovement(0, 0, 0);
    }

    /** Called at the end of every client tick. */
    public static void tick(Minecraft client) {
        if (step == Step.IDLE) {
            idleTick(client);
            return;
        }
        long now = System.nanoTime();
        if (step == Step.LOADING) {
            if (client.player != null && client.level != null && client.gui.screen() == null) {
                setUpWorld(client);
                step = Step.WARMUP;
                stepStartNanos = now;
            } else if (now - stepStartNanos > LOAD_TIMEOUT_NANOS
                    || (now - stepStartNanos > LOAD_GRACE_NANOS && client.level == null
                        && client.gui.screen() instanceof TitleScreen)) {
                // World creation failed and went back to the title screen.
                abort(client, "loadFailed");
            }
            return;
        }

        if (client.level == null) {
            // The player left the world through the pause menu.
            abort(client, "cancelled");
            return;
        }
        if (!isUndisturbed(client)) {
            disturbed = true;
            return;
        }
        if (disturbed) {
            // Back from a menu or another window: frames since then do not count, so start the run over.
            disturbed = false;
            RECORDER.reset();
            stepStartNanos = now;
            return;
        }

        double seconds = (now - stepStartNanos) / 1e9;
        if (step == Step.WARMUP && seconds >= warmupSeconds()) {
            step = Step.RUNNING;
            stepStartNanos = now;
            RECORDER.reset();
        } else if (step == Step.RUNNING && seconds >= routeSeconds()) {
            finishRun(client, now);
        }
    }

    /** Seconds left in the current warm-up or run, and which run it is, for the HUD. */
    public static double secondsLeft() {
        double total = step == Step.WARMUP ? warmupSeconds() : routeSeconds();
        return Math.max(0, total - (System.nanoTime() - stepStartNanos) / 1e9);
    }

    public static int runNumber() {
        return runIndex + 1;
    }

    public static ComparisonState state() {
        return state;
    }

    public static boolean isDisturbed() {
        return disturbed;
    }

    /** Puts the player's settings back and forgets the comparison, e.g. when they cancel it. */
    public static void cancel(Minecraft client, ComparisonState pending) {
        restoreSettings(client.options, pending.settings);
        Renderers.setPreference(client, Renderers.parsePreference(pending.originalPreference));
        ComparisonState.clear();
        state = null;
    }

    public static void setTimingForTesting(double routeSeconds, double warmupSeconds, boolean needFocus) {
        routeSecondsOverride = routeSeconds;
        warmupSecondsOverride = warmupSeconds;
        requireFocus = needFocus;
    }

    private static void beginHalf(Minecraft client) {
        if (client.level != null) {
            client.disconnectFromWorld(Component.translatable("renderer-benchmark.leaving"));
        }
        deleteWorld(client);
        applyBenchmarkSettings(client.options, state.settings);
        step = Step.LOADING;
        stepStartNanos = System.nanoTime();
        runIndex = 0;
        disturbed = false;
        RECORDER.reset();
        LevelSettings settings = new LevelSettings(WORLD_NAME, GameType.SPECTATOR,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true,
                WorldDataConfiguration.DEFAULT);
        client.createWorldOpenFlows().createFreshLevel(WORLD_ID, settings, new WorldOptions(SEED, true, false),
                WorldPresets::createNormalWorldDimensions, new TitleScreen());
    }

    /** Fixed time and weather, no movement checks, and the route anchored over the spawn point. */
    private static void setUpWorld(Minecraft client) {
        LocalPlayer player = client.player;
        IntegratedServer server = client.getSingleplayerServer();
        if (server != null) {
            UUID playerId = player.getUUID();
            server.execute(() -> {
                GameRules rules = server.getGameRules();
                rules.set(GameRules.ADVANCE_TIME, false, server);
                rules.set(GameRules.ADVANCE_WEATHER, false, server);
                rules.set(GameRules.PLAYER_MOVEMENT_CHECK, false, server);
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set noon");
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "weather clear");
                ServerPlayer serverPlayer = server.getPlayerList().getPlayer(playerId);
                if (serverPlayer != null) {
                    serverPlayer.setGameMode(GameType.SPECTATOR);
                }
            });
        }
        originX = player.getX();
        originZ = player.getZ();
        int ground = client.level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(originX), (int) Math.floor(originZ));
        originY = Math.max(ground, SEA_LEVEL);
    }

    private static void finishRun(Minecraft client, long now) {
        RunMetrics metrics = RunMetrics.of(RECORDER.durations());
        RECORDER.reset();
        runs().add(metrics);
        state.save();
        RendererBenchmarkClient.LOGGER.info("{} run {}: {}", currentRenderer(), runIndex + 1, metrics);
        runIndex++;
        stepStartNanos = now;
        if (runIndex >= state.runsPerRenderer) {
            finishHalf(client);
        }
    }

    private static void finishHalf(Minecraft client) {
        String window = client.getWindow().getWidth() + "x" + client.getWindow().getHeight();
        step = Step.IDLE;
        restoreSettings(client.options, state.settings);
        client.disconnectWithSavingScreen();
        worldToDelete = true;

        if (state.phase == ComparisonState.Phase.FIRST) {
            state.firstWindow = window;
            state.phase = ComparisonState.Phase.RESTART;
            Renderers.setPreference(client, Renderers.parsePreference(state.secondPreference));
            state.save();
            client.gui.setScreen(restartScreen(client));
        } else {
            state.secondWindow = window;
            state.phase = ComparisonState.Phase.DONE;
            state.finishedAt = LocalDateTime.now().format(TIMESTAMP);
            state.save();
            state.saveAsLast();
            client.gui.setScreen(new ResultsScreen(new TitleScreen(), state, true));
        }
    }

    private static Screen restartScreen(Minecraft client) {
        Component other = Component.literal(state.secondRenderer);
        return new ConfirmScreen(quitNow -> {
            if (quitNow) {
                client.stop();
            } else {
                client.gui.setScreen(new TitleScreen());
            }
        },
                Component.translatable("renderer-benchmark.restart.title", other),
                Component.translatable("renderer-benchmark.restart.message", Component.literal(state.firstRenderer), other),
                Component.translatable("renderer-benchmark.restart.quit"),
                Component.translatable("renderer-benchmark.restart.later"));
    }

    private static void abort(Minecraft client, String reason) {
        RendererBenchmarkClient.LOGGER.warn("Comparison stopped: {}", reason);
        step = Step.IDLE;
        if (state != null) {
            cancel(client, state);
        }
        if (client.level != null) {
            client.disconnectWithSavingScreen();
        }
        worldToDelete = true;
        client.gui.setScreen(new NoticeScreen(new TitleScreen(),
                Component.translatable("renderer-benchmark.stopped.title"),
                Component.translatable("renderer-benchmark.stopped." + reason)));
    }

    /** Cleans up the temporary world, and after a restart picks up a comparison where it stopped. */
    private static void idleTick(Minecraft client) {
        if (client.level != null || client.gui.overlay() != null || !(client.gui.screen() instanceof TitleScreen)) {
            return;
        }
        if (worldToDelete && client.getSingleplayerServer() == null) {
            worldToDelete = false;
            deleteWorld(client);
        }
        if (checkedPendingThisLaunch) {
            return;
        }
        checkedPendingThisLaunch = true;
        deleteWorld(client);
        ComparisonState pending = ComparisonState.load();
        if (pending != null) {
            client.gui.setScreen(screenForPending(client, pending));
        }
    }

    private static Screen screenForPending(Minecraft client, ComparisonState pending) {
        Screen title = new TitleScreen();
        switch (pending.phase) {
            case RESTART -> {
                if (Renderers.current().equals(pending.secondRenderer)) {
                    Component renderer = Component.literal(pending.secondRenderer);
                    return new ConfirmScreen(proceed -> {
                        if (proceed) {
                            resume(client, pending);
                        } else {
                            cancel(client, pending);
                            client.gui.setScreen(title);
                        }
                    },
                            Component.translatable("renderer-benchmark.continue.title", renderer),
                            Component.translatable("renderer-benchmark.continue.message", renderer),
                            Component.translatable("renderer-benchmark.continue.start"),
                            Component.translatable("renderer-benchmark.continue.cancel"));
                }
                // The game did not start on the other renderer: it fell back, or a launch argument forces one.
                cancel(client, pending);
                return new NoticeScreen(title,
                        Component.translatable("renderer-benchmark.unavailable.title", pending.secondRenderer),
                        Component.translatable("renderer-benchmark.unavailable.message",
                                pending.secondRenderer, Renderers.current()));
            }
            case DONE -> {
                state = pending;
                return new ResultsScreen(title, pending, true);
            }
            default -> {
                // The game closed in the middle of a half.
                cancel(client, pending);
                return new NoticeScreen(title,
                        Component.translatable("renderer-benchmark.stopped.title"),
                        Component.translatable("renderer-benchmark.stopped.interrupted"));
            }
        }
    }

    /** Applies the player's choice on the results screen and forgets the comparison. */
    public static void finish(Minecraft client, ComparisonState done, String keepRenderer) {
        if (keepRenderer == null) {
            Renderers.setPreference(client, Renderers.parsePreference(done.originalPreference));
        } else {
            Renderers.setPreference(client, Renderers.preferenceFor(keepRenderer));
        }
        ComparisonState.clear();
        state = null;
    }

    private static boolean isUndisturbed(Minecraft client) {
        return client.gui.screen() == null && (client.isWindowActive() || !requireFocus);
    }

    private static List<RunMetrics> runs() {
        return state.phase == ComparisonState.Phase.FIRST ? state.firstRuns : state.secondRuns;
    }

    public static String currentRenderer() {
        if (state == null) {
            return Renderers.current();
        }
        return state.phase == ComparisonState.Phase.FIRST ? state.firstRenderer : state.secondRenderer;
    }

    private static double routeSeconds() {
        return routeSecondsOverride > 0 ? routeSecondsOverride : Route.DEFAULT.durationSeconds();
    }

    private static double warmupSeconds() {
        return warmupSecondsOverride >= 0 ? warmupSecondsOverride : ModConfig.getInstance().warmupSeconds;
    }

    /** Flies the whole loop faster when the tests shorten a run. */
    private static double routeSpeed() {
        return Route.DEFAULT.durationSeconds() / routeSeconds();
    }

    private static ComparisonState.SettingsSnapshot snapshot(Options options) {
        ComparisonState.SettingsSnapshot snapshot = new ComparisonState.SettingsSnapshot();
        snapshot.renderDistance = options.renderDistance().get();
        snapshot.simulationDistance = options.simulationDistance().get();
        snapshot.fov = options.fov().get();
        snapshot.framerateLimit = options.framerateLimit().get();
        snapshot.vsync = options.enableVsync().get();
        snapshot.pauseOnLostFocus = options.pauseOnLostFocus;
        return snapshot;
    }

    /**
     * The same settings for both renderers, so only the renderer differs: the player's own render
     * and simulation distance, a fixed FOV, and no frame cap, since a cap would hide the difference.
     */
    private static void applyBenchmarkSettings(Options options, ComparisonState.SettingsSnapshot settings) {
        options.renderDistance().set(settings.renderDistance);
        options.simulationDistance().set(settings.simulationDistance);
        options.fov().set(FOV);
        options.framerateLimit().set(UNLIMITED_FRAMERATE);
        options.enableVsync().set(false);
        options.pauseOnLostFocus = false;
    }

    private static void restoreSettings(Options options, ComparisonState.SettingsSnapshot settings) {
        options.renderDistance().set(settings.renderDistance);
        options.simulationDistance().set(settings.simulationDistance);
        options.fov().set(settings.fov);
        options.framerateLimit().set(settings.framerateLimit);
        options.enableVsync().set(settings.vsync);
        options.pauseOnLostFocus = settings.pauseOnLostFocus;
        options.save();
    }

    private static void deleteWorld(Minecraft client) {
        LevelStorageSource levels = client.getLevelSource();
        if (!levels.levelExists(WORLD_ID)) {
            return;
        }
        try (LevelStorageSource.LevelStorageAccess access = levels.createAccess(WORLD_ID)) {
            access.deleteLevel();
        } catch (Exception e) {
            RendererBenchmarkClient.LOGGER.warn("Could not delete the temporary world, will retry on the next start", e);
        }
    }

    /** One line about the settings used, for the results and the share text. */
    public static String settingsSummary(ComparisonState done) {
        StringBuilder text = new StringBuilder();
        text.append(done.settings.renderDistance).append(" chunks, FOV ").append(FOV).append(", no frame cap");
        if (!done.firstWindow.isEmpty()) {
            text.append(", ").append(done.firstWindow);
        }
        text.append(", ").append(done.runsPerRenderer).append(done.runsPerRenderer == 1 ? " run" : " runs").append(" each");
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium")) {
            text.append(", Sodium");
        }
        return text.toString();
    }
}
