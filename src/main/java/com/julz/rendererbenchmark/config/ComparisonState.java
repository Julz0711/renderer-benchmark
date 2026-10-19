package com.julz.rendererbenchmark.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.julz.rendererbenchmark.client.RendererBenchmarkClient;
import com.julz.rendererbenchmark.core.RunMetrics;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A comparison in progress. It has to survive the restart between the two renderers, so it lives
 * in a file next to the results, and it remembers everything needed to put the player's settings
 * back afterwards.
 */
public class ComparisonState {
    public static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("renderer-benchmark");
    private static final Path FILE = DIR.resolve("pending.json");
    /** The last finished comparison, for the "Last Result" button. */
    private static final Path LAST_FILE = DIR.resolve("last-result.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public enum Phase {
        /** Running on the renderer the game started with. */
        FIRST,
        /** First renderer done and the other one selected; waiting for the player to restart. */
        RESTART,
        /** Restarted on the second renderer, runs start when the title screen opens. */
        SECOND,
        /** Both renderers measured; the results screen decides what to keep. */
        DONE
    }

    public Phase phase = Phase.FIRST;
    /** The Graphics API option as the player had it, such as "default" or "vulkan". */
    public String originalPreference;
    /** The backend that actually ran each half, as the game reports it. */
    public String firstRenderer;
    public String secondRenderer;
    /** The Graphics API option to select for the second half, "vulkan" or "opengl". */
    public String secondPreference;
    public String firstGpu = "";
    public String secondGpu = "";
    /** Window size in pixels during each half; a different size makes the comparison unfair. */
    public String firstWindow = "";
    public String secondWindow = "";
    /** When the comparison finished, for the share text and file names. */
    public String finishedAt = "";
    public int runsPerRenderer = 3;
    public SettingsSnapshot settings;
    public List<RunMetrics> firstRuns = new ArrayList<>();
    public List<RunMetrics> secondRuns = new ArrayList<>();
    /** Set when the second renderer could not start, for example when the game fell back to OpenGL. */
    public String problem;

    /** The video settings that are held fixed during the runs, so they can be restored afterwards. */
    public static class SettingsSnapshot {
        public int renderDistance;
        public int simulationDistance;
        public int fov;
        public int framerateLimit;
        public boolean vsync;
        public boolean pauseOnLostFocus;
    }

    public static ComparisonState load() {
        return read(FILE);
    }

    public static ComparisonState loadLast() {
        return read(LAST_FILE);
    }

    public void save() {
        write(FILE);
    }

    public void saveAsLast() {
        write(LAST_FILE);
    }

    public boolean hasBothResults() {
        return !firstRuns.isEmpty() && !secondRuns.isEmpty();
    }

    private static ComparisonState read(Path file) {
        if (!Files.exists(file)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            ComparisonState state = GSON.fromJson(reader, ComparisonState.class);
            if (state != null && state.settings != null && state.firstRenderer != null) {
                return state;
            }
        } catch (IOException | JsonParseException e) {
            RendererBenchmarkClient.LOGGER.error("Failed to read {}, ignoring it", file, e);
        }
        return null;
    }

    private void write(Path file) {
        try {
            Files.createDirectories(DIR);
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            RendererBenchmarkClient.LOGGER.error("Failed to save {}", file, e);
        }
    }

    public static void clear() {
        try {
            Files.deleteIfExists(FILE);
        } catch (IOException e) {
            RendererBenchmarkClient.LOGGER.error("Failed to delete {}", FILE, e);
        }
    }
}
