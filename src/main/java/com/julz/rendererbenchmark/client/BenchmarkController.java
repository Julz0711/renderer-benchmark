package com.julz.rendererbenchmark.client;

import com.julz.rendererbenchmark.config.ModConfig;
import com.julz.rendererbenchmark.config.ResultStore;
import com.julz.rendererbenchmark.core.BenchmarkResult;
import com.julz.rendererbenchmark.core.FrameRecorder;
import com.julz.rendererbenchmark.core.Route;
import com.julz.rendererbenchmark.core.RunMetrics;
import com.julz.rendererbenchmark.core.Scenario;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Runs a benchmark on the renderer the game is running now: creates a temporary world, finds a
 * spot for each kind of scene, flies the camera through every {@link Scenario} while timing each
 * frame, then saves the result and puts the player's settings back.
 */
public final class BenchmarkController {
    /** Folder name of the temporary world, deleted again afterwards. */
    public static final String WORLD_ID = "renderer-benchmark-temp";
    private static final String WORLD_NAME = "Renderer Benchmark (temporary)";
    /** A fixed seed, so every player and every run sees the same terrain. */
    private static final long SEED = 8_312_405_771_220_694L;
    /** Options#framerateLimit uses this value for "Unlimited". */
    private static final int UNLIMITED_FRAMERATE = 260;
    private static final int SEA_LEVEL = 63;
    /** How far from spawn to look for each kind of biome, as the /locate command does. */
    private static final int SEARCH_RADIUS = 6400;
    private static final int MOB_COUNT = 150;
    private static final String[] MOB_TYPES = {"cow", "sheep", "pig", "chicken", "horse", "villager",
            "llama", "goat", "wolf", "rabbit", "iron_golem", "cat"};
    /** Loop seconds flown per real second: about 18 blocks per second, like a fast elytra flight. */
    private static final double FLIGHT_SPEED = 2;
    /** Warm-up between scenes at the same place, where the terrain is already loaded. */
    private static final double SHORT_WARMUP_SECONDS = 3;
    private static final long LOAD_TIMEOUT_NANOS = 240_000_000_000L;
    private static final long LOAD_GRACE_NANOS = 5_000_000_000L;
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public enum Step { IDLE, LOADING, LOCATING, WARMUP, RUNNING }

    private static final FrameRecorder RECORDER = new FrameRecorder();

    private static Step step = Step.IDLE;
    private static BenchmarkResult result;
    private static ResultStore.SettingsSnapshot savedSettings;
    private static final List<long[]> allFrames = new ArrayList<>();
    private static int sceneIndex;
    private static long stepStartNanos;
    private static double originX;
    private static double originY;
    private static double originZ;
    // Filled on the server thread; the client only reads it after it has been published.
    private static volatile Map<Scenario.Place, double[]> places;
    // Set while a menu is open or the window is in the background; the current scene starts over.
    private static boolean disturbed;
    private static boolean checkedRestoreThisLaunch;
    private static boolean worldToDelete;

    // Replaced by the game tests to keep a benchmark short.
    private static double sceneSecondsOverride = -1;
    private static double warmupSecondsOverride = -1;
    // The test window on CI never has focus, so the tests turn the focus check off.
    private static boolean requireFocus = true;

    private BenchmarkController() {
    }

    public static Step step() {
        return step;
    }

    public static boolean isRunning() {
        return step != Step.IDLE;
    }

    public static boolean isFlying() {
        return step == Step.WARMUP || step == Step.RUNNING;
    }

    public static Scenario currentScenario() {
        return isFlying() ? Scenario.values()[sceneIndex] : null;
    }

    public static boolean isDisturbed() {
        return disturbed;
    }

    /** Starts a benchmark on the renderer the game is running now. Leaves the current world, if any. */
    public static void start(Minecraft client) {
        result = new BenchmarkResult();
        result.renderer = Renderers.current();
        result.gpu = Renderers.gpu();
        allFrames.clear();
        places = null;
        savedSettings = snapshot(client.options);
        ResultStore.saveRestore(savedSettings);

        if (client.level != null) {
            client.disconnectFromWorld(Component.translatable("renderer-benchmark.leaving"));
        }
        deleteWorld(client);
        applyBenchmarkSettings(client.options);
        step = Step.LOADING;
        stepStartNanos = System.nanoTime();
        sceneIndex = 0;
        disturbed = false;
        RECORDER.reset();
        LevelSettings settings = new LevelSettings(WORLD_NAME, GameType.SPECTATOR,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true,
                WorldDataConfiguration.DEFAULT);
        client.createWorldOpenFlows().createFreshLevel(WORLD_ID, settings, new WorldOptions(SEED, true, false),
                WorldPresets::createNormalWorldDimensions, new TitleScreen());
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
            // The timed part continues the loop where the warm-up left it, so the camera never jumps.
            seconds += warmupSeconds(currentScenario());
        }
        Route.Pose pose = Route.DEFAULT.sample(seconds * FLIGHT_SPEED);
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
                locatePlaces(client);
                step = Step.LOCATING;
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
        if (step == Step.LOCATING) {
            if (places != null) {
                beginScene(client, 0);
            } else if (now - stepStartNanos > LOAD_TIMEOUT_NANOS) {
                abort(client, "loadFailed");
            }
            return;
        }

        if (!isUndisturbed(client)) {
            disturbed = true;
            return;
        }
        if (disturbed) {
            // Back from a menu or another window: frames since then do not count, so the scene starts over.
            disturbed = false;
            step = Step.WARMUP;
            stepStartNanos = now;
            RECORDER.reset();
            return;
        }

        double seconds = (now - stepStartNanos) / 1e9;
        if (step == Step.WARMUP && seconds >= warmupSeconds(currentScenario())) {
            step = Step.RUNNING;
            stepStartNanos = now;
            RECORDER.reset();
        } else if (step == Step.RUNNING && seconds >= sceneSeconds()) {
            finishScene(client);
        }
    }

    /** Seconds left in the current warm-up or scene, for the HUD. */
    public static double secondsLeft() {
        double total = step == Step.WARMUP ? warmupSeconds(currentScenario()) : sceneSeconds();
        return Math.max(0, total - (System.nanoTime() - stepStartNanos) / 1e9);
    }

    public static int sceneNumber() {
        return sceneIndex + 1;
    }

    /** About how long a whole benchmark takes, for the start button. */
    public static int estimatedMinutes() {
        double seconds = 30;
        for (Scenario scenario : Scenario.values()) {
            seconds += warmupSeconds(scenario) + sceneSeconds();
        }
        return (int) Math.max(1, Math.round(seconds / 60));
    }

    public static void setTimingForTesting(double sceneSeconds, double warmupSeconds, boolean needFocus) {
        sceneSecondsOverride = sceneSeconds;
        warmupSecondsOverride = warmupSeconds;
        requireFocus = needFocus;
    }

    /**
     * Finds the nearest plains, forest, ocean and snowy biome. Runs on the server thread, since the
     * search and the height lookups generate terrain.
     */
    private static void locatePlaces(Minecraft client) {
        LocalPlayer player = client.player;
        IntegratedServer server = client.getSingleplayerServer();
        UUID playerId = player.getUUID();
        BlockPos spawn = BlockPos.containing(player.getX(), 64, player.getZ());
        if (server == null) {
            places = fallbackPlaces(spawn, client.level.getHeight(Heightmap.Types.MOTION_BLOCKING, spawn.getX(), spawn.getZ()));
            return;
        }
        server.execute(() -> {
            GameRules rules = server.getGameRules();
            rules.set(GameRules.ADVANCE_TIME, false, server);
            rules.set(GameRules.ADVANCE_WEATHER, false, server);
            rules.set(GameRules.PLAYER_MOVEMENT_CHECK, false, server);
            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(playerId);
            if (serverPlayer != null) {
                serverPlayer.setGameMode(GameType.SPECTATOR);
            }
            ServerLevel level = server.overworld();
            Map<Scenario.Place, double[]> found = new EnumMap<>(Scenario.Place.class);
            for (Scenario.Place place : Scenario.Place.values()) {
                BlockPos pos = locate(level, spawn, place);
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
                found.put(place, new double[]{pos.getX() + 0.5, Math.max(ground, SEA_LEVEL), pos.getZ() + 0.5});
                RendererBenchmarkClient.LOGGER.info("{} scenes at {} {}", place, pos.getX(), pos.getZ());
            }
            places = found;
        });
    }

    private static BlockPos locate(ServerLevel level, BlockPos from, Scenario.Place place) {
        List<Predicate<Holder<Biome>>> wanted = switch (place) {
            case PLAINS -> List.of(biome -> biome.is(Biomes.PLAINS) || biome.is(Biomes.SUNFLOWER_PLAINS));
            case FOREST -> List.of(biome -> biome.is(Biomes.DARK_FOREST), biome -> biome.is(Biomes.FOREST));
            case OCEAN -> List.of(biome -> biome.is(BiomeTags.IS_OCEAN));
            case SNOW -> List.of(biome -> biome.is(Biomes.SNOWY_TAIGA), biome -> biome.is(Biomes.SNOWY_PLAINS));
        };
        for (Predicate<Holder<Biome>> biome : wanted) {
            Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(biome, from, SEARCH_RADIUS, 32, 64);
            if (found != null) {
                return found.getFirst();
            }
        }
        // Not within reach: fly that scene over spawn instead.
        return from;
    }

    private static Map<Scenario.Place, double[]> fallbackPlaces(BlockPos spawn, int ground) {
        Map<Scenario.Place, double[]> fallback = new EnumMap<>(Scenario.Place.class);
        for (Scenario.Place place : Scenario.Place.values()) {
            fallback.put(place, new double[]{spawn.getX() + 0.5, Math.max(ground, SEA_LEVEL), spawn.getZ() + 0.5});
        }
        return fallback;
    }

    /** Moves to the scene's place and sets its time, weather and mobs. */
    private static void beginScene(Minecraft client, int index) {
        sceneIndex = index;
        Scenario scenario = Scenario.values()[index];
        double[] place = places.get(scenario.place());
        originX = place[0];
        originY = place[1];
        originZ = place[2];
        IntegratedServer server = client.getSingleplayerServer();
        if (server != null) {
            UUID playerId = client.player.getUUID();
            Route.Pose start = Route.DEFAULT.sample(0);
            String teleport = String.format(Locale.ROOT, "tp %s %.1f %.1f %.1f", playerId,
                    originX + start.x(), originY + start.y(), originZ + start.z());
            server.execute(() -> {
                command(server, teleport);
                command(server, scenario.time() == Scenario.Time.NOON ? "time set noon" : "time set midnight");
                command(server, scenario.weather() == Scenario.Weather.RAIN ? "weather rain" : "weather clear");
                if (scenario == Scenario.MOBS) {
                    spawnHerd(server, place);
                }
            });
        }
        step = Step.WARMUP;
        stepStartNanos = System.nanoTime();
        disturbed = false;
        RECORDER.reset();
    }

    /** Animals along the route, standing still so every run sees the same scene. */
    private static void spawnHerd(IntegratedServer server, double[] place) {
        ServerLevel level = server.overworld();
        Random random = new Random(SEED);
        for (int i = 0; i < MOB_COUNT; i++) {
            Route.Pose pose = Route.DEFAULT.sample(Route.DEFAULT.durationSeconds() * i / MOB_COUNT);
            int x = (int) Math.floor(place[0] + pose.x() + random.nextInt(33) - 16);
            int z = (int) Math.floor(place[2] + pose.z() + random.nextInt(33) - 16);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            command(server, String.format(Locale.ROOT, "summon minecraft:%s %d %d %d {NoAI:1b,PersistenceRequired:1b,Silent:1b}",
                    MOB_TYPES[i % MOB_TYPES.length], x, y, z));
        }
    }

    private static void command(IntegratedServer server, String command) {
        CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, command);
    }

    private static void finishScene(Minecraft client) {
        long[] frames = RECORDER.durations();
        RECORDER.reset();
        RunMetrics metrics = RunMetrics.of(frames);
        Scenario scenario = Scenario.values()[sceneIndex];
        result.scenes.add(new BenchmarkResult.SceneResult(scenario.id(), metrics));
        allFrames.add(frames);
        RendererBenchmarkClient.LOGGER.info("{} {}: {}", result.renderer, scenario.id(), metrics);
        if (sceneIndex + 1 < Scenario.values().length) {
            beginScene(client, sceneIndex + 1);
        } else {
            finishBenchmark(client);
        }
    }

    private static void finishBenchmark(Minecraft client) {
        int total = allFrames.stream().mapToInt(frames -> frames.length).sum();
        long[] everything = new long[total];
        int offset = 0;
        for (long[] frames : allFrames) {
            System.arraycopy(frames, 0, everything, offset, frames.length);
            offset += frames.length;
        }
        allFrames.clear();
        result.overall = RunMetrics.of(everything);
        result.settings = settingsSummary(client);
        result.finishedAt = LocalDateTime.now().format(TIMESTAMP);
        ResultStore.put(result);
        RendererBenchmarkClient.LOGGER.info("{} overall: {}", result.renderer, result.overall);

        step = Step.IDLE;
        restoreSettings(client.options, savedSettings);
        client.disconnectWithSavingScreen();
        worldToDelete = true;
        result = null;
        client.gui.setScreen(new BenchmarkScreen(new TitleScreen()));
    }

    private static void abort(Minecraft client, String reason) {
        RendererBenchmarkClient.LOGGER.warn("Benchmark stopped: {}", reason);
        step = Step.IDLE;
        restoreSettings(client.options, savedSettings);
        result = null;
        allFrames.clear();
        if (client.level != null) {
            client.disconnectWithSavingScreen();
        }
        worldToDelete = true;
        client.gui.setScreen(new NoticeScreen(new BenchmarkScreen(new TitleScreen()),
                Component.translatable("renderer-benchmark.stopped.title"),
                Component.translatable("renderer-benchmark.stopped." + reason)));
    }

    /** Cleans up the temporary world, and after a crash puts back the settings a benchmark changed. */
    private static void idleTick(Minecraft client) {
        if (client.level != null || client.gui.overlay() != null || !(client.gui.screen() instanceof TitleScreen)) {
            return;
        }
        if (worldToDelete && client.getSingleplayerServer() == null) {
            worldToDelete = false;
            deleteWorld(client);
        }
        if (checkedRestoreThisLaunch) {
            return;
        }
        checkedRestoreThisLaunch = true;
        deleteWorld(client);
        ResultStore.SettingsSnapshot leftover = ResultStore.loadRestore();
        if (leftover != null) {
            RendererBenchmarkClient.LOGGER.info("The game closed during a benchmark, putting the settings back");
            restoreSettings(client.options, leftover);
        }
    }

    private static boolean isUndisturbed(Minecraft client) {
        return client.gui.screen() == null && (client.isWindowActive() || !requireFocus);
    }

    private static double sceneSeconds() {
        return sceneSecondsOverride > 0 ? sceneSecondsOverride : ModConfig.getInstance().sceneSeconds;
    }

    private static double warmupSeconds(Scenario scenario) {
        if (warmupSecondsOverride >= 0) {
            return warmupSecondsOverride;
        }
        return scenario.movesToNewPlace() ? ModConfig.getInstance().warmupSeconds : SHORT_WARMUP_SECONDS;
    }

    private static ResultStore.SettingsSnapshot snapshot(Options options) {
        ResultStore.SettingsSnapshot snapshot = new ResultStore.SettingsSnapshot();
        snapshot.framerateLimit = options.framerateLimit().get();
        snapshot.vsync = options.enableVsync().get();
        snapshot.pauseOnLostFocus = options.pauseOnLostFocus;
        snapshot.inactivityFpsLimit = options.inactivityFpsLimit().get().name();
        return snapshot;
    }

    /**
     * Only what would hide the difference between the renderers changes: no frame cap, no VSync,
     * and no lower frame rate when the player has not touched anything for a while, which would
     * otherwise cap the flight at 30 FPS. Everything else stays as the player set it.
     */
    private static void applyBenchmarkSettings(Options options) {
        options.framerateLimit().set(UNLIMITED_FRAMERATE);
        options.enableVsync().set(false);
        options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
        options.pauseOnLostFocus = false;
    }

    private static void restoreSettings(Options options, ResultStore.SettingsSnapshot settings) {
        if (settings != null) {
            options.framerateLimit().set(settings.framerateLimit);
            options.enableVsync().set(settings.vsync);
            options.pauseOnLostFocus = settings.pauseOnLostFocus;
            for (InactivityFpsLimit limit : InactivityFpsLimit.values()) {
                if (limit.name().equals(settings.inactivityFpsLimit)) {
                    options.inactivityFpsLimit().set(limit);
                }
            }
            options.save();
        }
        ResultStore.clearRestore();
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

    /** The settings that decide how fast the game runs, as one line. Both renderers should match. */
    public static String settingsSummary(Minecraft client) {
        Options options = client.options;
        StringBuilder text = new StringBuilder();
        text.append(options.renderDistance().get()).append(" chunks, simulation ").append(options.simulationDistance().get())
                .append(", FOV ").append(options.fov().get())
                .append(", ").append(client.getWindow().getWidth()).append('x').append(client.getWindow().getHeight());
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium")) {
            text.append(", Sodium");
        }
        return text.toString();
    }
}
